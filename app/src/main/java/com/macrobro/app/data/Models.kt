package com.macrobro.app.data

import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

val MEALS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

const val DEFAULT_MODEL = "gemini-flash-latest"
const val DEFAULT_OPENROUTER_MODEL = "openrouter/free"

data class FoodEntry(
    val id: Long,
    val date: String,          // yyyy-MM-dd
    val meal: String,
    val name: String,
    val grams: Int,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val source: String,        // "manual" or "snap"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("meal", meal).put("name", name)
        .put("grams", grams).put("kcal", kcal)
        .put("protein", protein).put("carbs", carbs).put("fat", fat)
        .put("source", source)

    companion object {
        fun fromJson(o: JSONObject) = FoodEntry(
            id = o.optLong("id"),
            date = o.optString("date"),
            meal = o.optString("meal", "Snacks"),
            name = o.optString("name", "Food"),
            grams = o.optInt("grams", 0),
            kcal = o.optInt("kcal", 0),
            protein = o.optDouble("protein", 0.0),
            carbs = o.optDouble("carbs", 0.0),
            fat = o.optDouble("fat", 0.0),
            source = o.optString("source", "manual"),
        )
    }
}

/** A food before it has an id/date/meal. */
data class FoodDraft(
    val name: String,
    val grams: Int,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class ChatMsg(val role: String, val text: String) {
    fun toJson(): JSONObject = JSONObject().put("role", role).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = ChatMsg(o.optString("role", "user"), o.optString("text"))
    }
}

data class Settings(
    val provider: String = "gemini", // "gemini" or "openrouter"
    val apiKey: String = "",          // Gemini key
    val model: String = DEFAULT_MODEL,
    val openRouterKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    val goalKcal: Int = 2400,
    val goalProtein: Int = 160,
    val goalCarbs: Int = 260,
    val goalFat: Int = 75,
    val sex: String = "male",
    val age: Int = 25,
    val heightCm: Int = 180,
    val weightKg: Double = 80.0,
    val activity: Double = 1.55,
    val goalType: String = "maintain", // cut / maintain / bulk
    val waterGoalMl: Int = 2500,
)

data class WeightEntry(val date: String, val kg: Double) {
    fun toJson(): JSONObject = JSONObject().put("date", date).put("kg", kg)

    companion object {
        fun fromJson(o: JSONObject) = WeightEntry(o.optString("date"), o.optDouble("kg", 0.0))
    }
}

/** Everything the app stores (except settings). Also the backup file format. */
data class AppData(
    val entries: List<FoodEntry> = emptyList(),
    val chat: List<ChatMsg> = emptyList(),
    val customFoods: List<LibFood> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val water: Map<String, Int> = emptyMap(), // date -> ml
)

/** Nutrition read from a photo of a package label, per 100 g. Null = not readable. */
data class LabelValues(
    val name: String?,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val sugar: Double?,
    val fat: Double?,
    val fiber: Double?,
    val servingG: Double?,
    val packG: Double?,
)

/** One day's totals, for charts and Gym Bro's weekly context. */
data class DayStat(
    val date: LocalDate,
    val kcal: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val count: Int,
)

data class Totals(val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

fun totalsOf(list: List<FoodEntry>) = Totals(
    kcal = list.sumOf { it.kcal },
    protein = list.sumOf { it.protein },
    carbs = list.sumOf { it.carbs },
    fat = list.sumOf { it.fat },
)

/** Item suggested by Snap Food AI. Nutrition is stored per gram so editing grams rescales it. */
data class SnapItem(
    val name: String,
    val grams: Double,
    val kcalPerG: Double,
    val proteinPerG: Double,
    val carbsPerG: Double,
    val fatPerG: Double,
    val selected: Boolean = true,
) {
    val kcal: Int get() = (kcalPerG * grams).roundToInt()
    val protein: Double get() = proteinPerG * grams
    val carbs: Double get() = carbsPerG * grams
    val fat: Double get() = fatPerG * grams

    fun toDraft() = FoodDraft(name.ifBlank { "Food" }, grams.roundToInt(), kcal, protein, carbs, fat)
}

/** Formats grams of a macro: whole numbers, or one decimal under 10. */
fun fmtG(v: Double): String =
    if (v < 10 && v != v.roundToInt().toDouble()) String.format(Locale.US, "%.1f", v)
    else v.roundToInt().toString()

/** One decimal without a trailing .0: 21.3, 617. */
fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/** Body weight with one decimal: 78.4, 80. */
fun fmtKg(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/** Litres from millilitres: 1.25, 2.5, 3. */
fun fmtL(ml: Int): String = String.format(Locale.US, "%.2f", ml / 1000.0).trimEnd('0').trimEnd('.')

/** Mifflin–St Jeor estimate with sensible floors. Returns (kcal, protein, carbs, fat). */
fun calculateGoals(s: Settings): IntArray {
    val bmr = 10 * s.weightKg + 6.25 * s.heightCm - 5 * s.age + if (s.sex == "male") 5 else -161
    val tdee = bmr * s.activity
    val raw = when (s.goalType) {
        "cut" -> tdee * 0.80
        "bulk" -> tdee * 1.10
        else -> tdee
    }
    val floor = if (s.sex == "male") 1500.0 else 1200.0
    val kcal = maxOf(raw, floor).roundToInt()
    val proteinPerKg = if (s.goalType == "cut") 2.2 else 2.0
    val protein = (s.weightKg * proteinPerKg).roundToInt()
    val fat = (kcal * 0.25 / 9).roundToInt()
    val carbs = maxOf(0, ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt())
    return intArrayOf(kcal, protein, carbs, fat)
}
