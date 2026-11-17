package com.macrobro.app.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToInt

/** A food with known nutrition per 100 g. */
data class LibFood(
    val name: String,
    val display: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val popular: Int = 0,
    val portionLabel: String = "",
    val portionG: Double = 0.0,
    val custom: Boolean = false,
    val id: Long = 0,
) {
    /** Lower-case words of display + name, padded with spaces for word-start matching. */
    val searchKey: String = " " + FoodLibrary.normalize("$display $name") + " "

    fun draftFor(grams: Double): FoodDraft {
        val f = grams / 100.0
        return FoodDraft(
            name = display,
            grams = grams.roundToInt(),
            kcal = (kcal * f).roundToInt(),
            protein = protein * f,
            carbs = carbs * f,
            fat = fat * f,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("category", category)
        .put("kcal", kcal).put("protein", protein).put("carbs", carbs).put("fat", fat)
        .put("fiber", fiber).put("sugar", sugar)
        .put("portionLabel", portionLabel).put("portionG", portionG)

    companion object {
        fun customFromJson(o: JSONObject): LibFood {
            val n = o.optString("name", "My food")
            return LibFood(
                name = n,
                display = n,
                category = MY_FOODS,
                kcal = o.optDouble("kcal", 0.0),
                protein = o.optDouble("protein", 0.0),
                carbs = o.optDouble("carbs", 0.0),
                fat = o.optDouble("fat", 0.0),
                fiber = o.optDouble("fiber", 0.0),
                sugar = o.optDouble("sugar", 0.0),
                portionLabel = o.optString("portionLabel", ""),
                portionG = o.optDouble("portionG", 0.0),
                custom = true,
                id = o.optLong("id"),
            )
        }
    }
}

const val MY_FOODS = "My foods"
const val RECENT = "Recent"

object FoodLibrary {

    val CATEGORIES = listOf(
        "Fruit", "Vegetables", "Meat & poultry", "Fish & seafood", "Eggs", "Dairy",
        "Grains & pasta", "Bread & bakery", "Potatoes", "Beans & legumes", "Nuts & seeds",
        "Breakfast & cereal", "Snacks", "Sweets & desserts", "Drinks", "Fats & oils",
        "Sauces", "Meals & fast food", "Soups", "Salads", "Spices",
    )

    private val nonWord = Regex("[^\\p{L}\\p{N}%]+")

    fun normalize(s: String): String = s.lowercase().replace(nonWord, " ").trim()

    /** Reads the bundled USDA food table (assets/foods.tsv). */
    fun loadAssets(context: Context): List<LibFood> =
        context.assets.open("foods.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull(::parse).toList()
        }

    private fun parse(line: String): LibFood? {
        val c = line.split('\t')
        if (c.size < 12) return null
        fun d(i: Int) = c[i].toDoubleOrNull() ?: 0.0
        return LibFood(
            name = c[0],
            display = c[1].ifBlank { c[0] },
            category = c[2],
            kcal = d(3), protein = d(4), carbs = d(5), fat = d(6), fiber = d(7), sugar = d(8),
            popular = c[9].toIntOrNull() ?: 0,
            portionLabel = c[10],
            portionG = d(11),
        )
    }

    fun search(foods: List<LibFood>, query: String, limit: Int = 150): List<LibFood> {
        val tokens = normalize(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return foods.take(limit)
        val first = tokens.first()
        return foods.asSequence()
            .filter { f -> tokens.all { f.searchKey.contains(it) } }
            .map { f ->
                val display = " " + normalize(f.display)
                val name = " " + normalize(f.name)
                val score = when {
                    display.startsWith(" $first") || name.startsWith(" $first") -> 0
                    tokens.all { f.searchKey.contains(" $it") } -> 1
                    else -> 2
                }
                f to score
            }
            .sortedWith(
                compareBy<Pair<LibFood, Int>>(
                    { if (it.first.custom) 0 else 1 },
                    { it.second },
                    { if (it.first.popular > 0) 0 else 1 },
                    { it.first.display.length },
                )
            )
            .map { it.first }
            .take(limit)
            .toList()
    }
}
