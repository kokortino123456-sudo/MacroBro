package com.macrobro.app.data

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
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
    val barcode: String = "",
    // "usda", "bls", "dish:bls", "dish:usda", "off" (Open Food Facts), "custom", "recent"
    val source: String = "",
    val aliases: String = "",
    val note: String = "",
) {
    /** Lower-case words without diacritics, padded with spaces for word-start matching. */
    val searchKey: String = FoodLibrary.searchKeyFor(display, name, aliases)

    // Precomputed at load (background thread) so typing in search stays smooth.
    internal val displayKey: String = " " + FoodLibrary.normalize(display)
    internal val nameKey: String = " " + FoodLibrary.normalize(name)

    val isDish: Boolean get() = source.startsWith("dish")

    /** USDA counts fiber inside carbohydrates; EU tables and labels (BLS, Open Food Facts) don't. */
    val fiberInCarbs: Boolean get() = source == "usda" || source == "dish:usda"

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
        .put("id", id).put("name", name).put("display", display).put("category", category)
        .put("kcal", kcal).put("protein", protein).put("carbs", carbs).put("fat", fat)
        .put("fiber", fiber).put("sugar", sugar)
        .put("portionLabel", portionLabel).put("portionG", portionG)
        .put("barcode", barcode)

    companion object {
        fun customFromJson(o: JSONObject): LibFood {
            val n = o.optString("name", "My food")
            return LibFood(
                name = n,
                display = o.optString("display", "").ifBlank { n },
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
                barcode = o.optString("barcode", ""),
                source = "custom",
            )
        }
    }
}

const val MY_FOODS = "My foods"
const val RECENT = "Recent"

object FoodLibrary {

    const val WORLD = "World dishes"

    /** Cuisines of the curated dishes, in display order. */
    val CUISINES = listOf(
        "Czech", "Slovak", "Polish", "German", "Austrian", "Hungarian", "Italian", "French", "Spanish",
        "Greek", "Turkish", "British", "Ukrainian & Russian", "American", "Mexican & Latin", "Indian",
        "Chinese", "Japanese", "Southeast Asian", "Korean", "Middle Eastern",
    )

    val CATEGORIES = listOf(
        "Fruit", "Vegetables", "Meat & poultry", "Fish & seafood", "Eggs", "Dairy",
        "Grains & pasta", "Bread & bakery", "Potatoes", "Beans & legumes", "Nuts & seeds",
        "Breakfast & cereal", "Snacks", "Sweets & desserts", "Drinks", "Fats & oils",
        "Sauces", "Meals & fast food", "Soups", "Salads", "Spices",
    )

    private val nonWord = Regex("[^\\p{L}\\p{N}%]+")
    private val combiningMarks = Regex("\\p{Mn}+")

    /** "Svíčková", "Żurek", "Knödel" -> "svickova", "zurek", "knodel". */
    fun normalize(s: String): String {
        val lower = s.lowercase()
            .replace("ł", "l").replace("ß", "ss").replace("æ", "ae")
            .replace("ø", "o").replace("œ", "oe").replace("đ", "d")
        val plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarks, "")
        return plain.replace(nonWord, " ").trim()
    }

    /** Search key; German umlauts also match when typed as ae/oe/ue. */
    fun searchKeyFor(vararg parts: String): String {
        val text = parts.filter { it.isNotBlank() }.joinToString(" ")
        val plain = normalize(text)
        val umlauts = if (text.any { it in "äöüÄÖÜ" }) {
            normalize(text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue"))
        } else ""
        return " $plain " + if (umlauts.isNotEmpty() && umlauts != plain) "$umlauts " else ""
    }

    /** Reads the bundled food table (assets/foods.tsv): USDA, BLS and curated dishes. */
    fun loadAssets(context: Context): List<LibFood> {
        var index = 0L
        return context.assets.open("foods.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { parse(it, ++index) }.toList()
        }
    }

    private fun parse(line: String, index: Long): LibFood? {
        val c = line.split('\t')
        if (c.size < 12) return null
        fun d(i: Int) = c[i].toDoubleOrNull() ?: 0.0
        fun s(i: Int) = if (i < c.size) c[i] else ""
        return LibFood(
            name = c[0],
            display = c[1].ifBlank { c[0] },
            category = c[2],
            kcal = d(3), protein = d(4), carbs = d(5), fat = d(6), fiber = d(7), sugar = d(8),
            popular = c[9].toIntOrNull() ?: 0,
            portionLabel = c[10],
            portionG = d(11),
            id = -index, // library foods get negative ids, own foods positive
            source = s(12).ifBlank { "usda" },
            aliases = s(13),
            note = s(14),
        )
    }

    fun search(foods: List<LibFood>, query: String, limit: Int = 150): List<LibFood> {
        val tokens = normalize(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return foods.take(limit)
        val first = tokens.first()
        return foods.asSequence()
            .filter { f -> tokens.all { f.searchKey.contains(it) } }
            .map { f ->
                val score = when {
                    f.displayKey.startsWith(" $first") || f.nameKey.startsWith(" $first") -> 0
                    tokens.all { f.searchKey.contains(" $it") } -> 1
                    else -> 2
                }
                f to score
            }
            .sortedWith(
                compareBy<Pair<LibFood, Int>>(
                    { if (it.first.custom) 0 else 1 },
                    { it.second },
                    {
                        when {
                            it.first.isDish -> 0
                            it.first.popular > 0 -> 1
                            else -> 2
                        }
                    },
                    { it.first.display.length },
                )
            )
            .map { it.first }
            .take(limit)
            .toList()
    }
}
