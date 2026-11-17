package com.macrobro.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything is stored on the phone only: food log, chat, own foods, weight and water
 * in one JSON file; settings (including the API key) in shared preferences.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "macrobro.json")
    private val prefs = context.getSharedPreferences("macrobro_settings", Context.MODE_PRIVATE)

    fun load(): AppData = try {
        if (file.exists()) dataFromJson(JSONObject(file.readText())) else AppData()
    } catch (e: Exception) {
        AppData()
    }

    fun save(data: AppData) {
        val text = dataToJson(data).toString()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            provider = prefs.getString("provider", d.provider) ?: d.provider,
            apiKey = prefs.getString("apiKey", "") ?: "",
            model = prefs.getString("model", d.model)?.ifBlank { d.model } ?: d.model,
            openRouterKey = prefs.getString("openRouterKey", "") ?: "",
            openRouterModel = prefs.getString("openRouterModel", d.openRouterModel)?.ifBlank { d.openRouterModel }
                ?: d.openRouterModel,
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
            waterGoalMl = prefs.getInt("waterGoalMl", d.waterGoalMl),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("provider", s.provider)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("openRouterKey", s.openRouterKey)
            .putString("openRouterModel", s.openRouterModel)
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
            .putInt("waterGoalMl", s.waterGoalMl)
            .apply()
    }

    companion object {
        private const val BACKUP_APP = "MacroBro"

        fun dataToJson(d: AppData): JSONObject = JSONObject()
            .put("version", 3)
            .put("entries", JSONArray().apply { d.entries.forEach { put(it.toJson()) } })
            .put("chat", JSONArray().apply { d.chat.takeLast(200).forEach { put(it.toJson()) } })
            .put("customFoods", JSONArray().apply { d.customFoods.forEach { put(it.toJson()) } })
            .put("weights", JSONArray().apply { d.weights.forEach { put(it.toJson()) } })
            .put("water", JSONObject().apply { d.water.forEach { (k, v) -> put(k, v) } })

        fun dataFromJson(root: JSONObject): AppData {
            fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
                val arr = root.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(f) }
            }
            val water = mutableMapOf<String, Int>()
            val waterObj = root.optJSONObject("water")
            if (waterObj != null) {
                val keys = waterObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    water[k] = waterObj.optInt(k, 0)
                }
            }
            return AppData(
                entries = list("entries", FoodEntry::fromJson),
                chat = list("chat", ChatMsg::fromJson),
                customFoods = list("customFoods", LibFood::customFromJson),
                weights = list("weights", WeightEntry::fromJson)
                    .filter { it.kg > 0 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess }
                    .distinctBy { it.date },
                water = water,
            )
        }

        /** Backup file: all data plus settings, but never the API keys. */
        fun backupJson(d: AppData, s: Settings): String {
            val root = dataToJson(d)
                .put("app", BACKUP_APP)
                .put("exportedAt", java.time.LocalDateTime.now().toString())
                .put(
                    "settings", JSONObject()
                        .put("goalKcal", s.goalKcal).put("goalProtein", s.goalProtein)
                        .put("goalCarbs", s.goalCarbs).put("goalFat", s.goalFat)
                        .put("sex", s.sex).put("age", s.age).put("heightCm", s.heightCm)
                        .put("weightKg", s.weightKg).put("activity", s.activity)
                        .put("goalType", s.goalType).put("waterGoalMl", s.waterGoalMl)
                )
            return root.toString(2)
        }

        /** Parses a backup file. Keeps the current AI provider, keys and models. */
        fun parseBackup(text: String, current: Settings): Pair<AppData, Settings> {
            val root = try { JSONObject(text) } catch (e: Exception) {
                throw IllegalArgumentException("That file isn't a MacroBro backup.")
            }
            if (root.optString("app") != BACKUP_APP) throw IllegalArgumentException("That file isn't a MacroBro backup.")
            val data = dataFromJson(root)
            val o = root.optJSONObject("settings") ?: return data to current
            val settings = current.copy(
                goalKcal = o.optInt("goalKcal", current.goalKcal),
                goalProtein = o.optInt("goalProtein", current.goalProtein),
                goalCarbs = o.optInt("goalCarbs", current.goalCarbs),
                goalFat = o.optInt("goalFat", current.goalFat),
                sex = o.optString("sex", current.sex),
                age = o.optInt("age", current.age),
                heightCm = o.optInt("heightCm", current.heightCm),
                weightKg = o.optDouble("weightKg", current.weightKg),
                activity = o.optDouble("activity", current.activity),
                goalType = o.optString("goalType", current.goalType),
                waterGoalMl = o.optInt("waterGoalMl", current.waterGoalMl),
            )
            return data to settings
        }
    }
}
