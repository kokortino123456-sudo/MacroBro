package com.macrobro.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything is stored on the phone only: the food log + chat in a JSON file, settings in prefs. */
class Store(context: Context) {
    private val file = File(context.filesDir, "macrobro.json")
    private val prefs = context.getSharedPreferences("macrobro_settings", Context.MODE_PRIVATE)

    private fun readRoot(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    fun loadEntries(): List<FoodEntry> {
        val arr = readRoot().optJSONArray("entries") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(FoodEntry::fromJson) }
    }

    fun loadChat(): List<ChatMsg> {
        val arr = readRoot().optJSONArray("chat") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(ChatMsg::fromJson) }
    }

    fun loadCustomFoods(): List<LibFood> {
        val arr = readRoot().optJSONArray("customFoods") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(LibFood::customFromJson) }
    }

    fun saveData(entries: List<FoodEntry>, chat: List<ChatMsg>, customFoods: List<LibFood>) {
        val root = JSONObject()
            .put("version", 2)
            .put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })
            .put("chat", JSONArray().apply { chat.takeLast(200).forEach { put(it.toJson()) } })
            .put("customFoods", JSONArray().apply { customFoods.forEach { put(it.toJson()) } })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            apiKey = prefs.getString("apiKey", "") ?: "",
            model = prefs.getString("model", d.model)?.ifBlank { d.model } ?: d.model,
            goalKcal = prefs.getInt("goalKcal", d.goalKcal),
            goalProtein = prefs.getInt("goalProtein", d.goalProtein),
            goalCarbs = prefs.getInt("goalCarbs", d.goalCarbs),
            goalFat = prefs.getInt("goalFat", d.goalFat),
            sex = prefs.getString("sex", d.sex) ?: d.sex,
            age = prefs.getInt("age", d.age),
            heightCm = prefs.getInt("heightCm", d.heightCm),
            weightKg = prefs.getFloat("weightKg", d.weightKg.toFloat()).toDouble(),
            activity = prefs.getFloat("activity", d.activity.toFloat()).toDouble(),
            goalType = prefs.getString("goalType", d.goalType) ?: d.goalType,
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putInt("goalKcal", s.goalKcal)
            .putInt("goalProtein", s.goalProtein)
            .putInt("goalCarbs", s.goalCarbs)
            .putInt("goalFat", s.goalFat)
            .putString("sex", s.sex)
            .putInt("age", s.age)
            .putInt("heightCm", s.heightCm)
            .putFloat("weightKg", s.weightKg.toFloat())
            .putFloat("activity", s.activity.toFloat())
            .putString("goalType", s.goalType)
            .apply()
    }
}
