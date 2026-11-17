package com.macrobro.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macrobro.app.ai.Ai
import com.macrobro.app.ai.AiMessage
import com.macrobro.app.ai.OpenRouter
import com.macrobro.app.ai.OrModel
import com.macrobro.app.ai.Prompts
import com.macrobro.app.data.AppData
import com.macrobro.app.data.BarcodeResult
import com.macrobro.app.data.ChatMsg
import com.macrobro.app.data.DayStat
import com.macrobro.app.data.FoodDraft
import com.macrobro.app.data.FoodEntry
import com.macrobro.app.data.FoodLibrary
import com.macrobro.app.data.LibFood
import com.macrobro.app.data.MY_FOODS
import com.macrobro.app.data.OpenFoodFacts
import com.macrobro.app.data.RECENT
import com.macrobro.app.data.Settings
import com.macrobro.app.data.SnapItem
import com.macrobro.app.data.Store
import com.macrobro.app.data.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = Store(application)
    private val saveMutex = Mutex()
    private var idSeq = 0L

    private val initial = store.load()

    var settings by mutableStateOf(store.loadSettings())
        private set
    var entries by mutableStateOf(initial.entries)
        private set
    var chat by mutableStateOf(initial.chat)
        private set
    var customFoods by mutableStateOf(initial.customFoods)
        private set
    var weights by mutableStateOf(initial.weights.sortedBy { it.date })
        private set
    var water by mutableStateOf(initial.water)
        private set
    var selectedDate by mutableStateOf(LocalDate.now())

    /** Bundled USDA foods, loaded in the background at start. */
    var libraryFoods by mutableStateOf<List<LibFood>>(emptyList())
        private set

    // Snap / describe AI
    var snapBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var snapItems by mutableStateOf<List<SnapItem>>(emptyList())
        private set
    var snapNotes by mutableStateOf("")
        private set
    var snapNote by mutableStateOf("")
    var snapLoading by mutableStateOf(false)
        private set
    var snapError by mutableStateOf<String?>(null)

    // Gym Bro
    var broLoading by mutableStateOf(false)
        private set
    var broError by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            libraryFoods = withContext(Dispatchers.IO) {
                try { FoodLibrary.loadAssets(getApplication()) } catch (e: Exception) { emptyList() }
            }
        }
    }

    /** True when the chosen AI provider has a key. */
    val hasKey: Boolean get() = Ai.client(settings) != null

    private fun newId(): Long = System.currentTimeMillis() * 100 + (idSeq++ % 100)

    private fun persist() {
        val snapshot = AppData(entries, chat, customFoods, weights, water)
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try { store.save(snapshot) } catch (_: Exception) { }
            }
        }
    }

    // ---------- Food log ----------

    fun entriesFor(date: LocalDate): List<FoodEntry> = entries.filter { it.date == date.toString() }

    fun recentFoods(): List<FoodEntry> =
        entries.sortedByDescending { it.id }.distinctBy { it.name.lowercase() }.take(12)

    /** Recently logged foods that had a weight, turned back into per-100 g foods. */
    fun recentLibFoods(): List<LibFood> = recentFoods().filter { it.grams > 0 }.map { e ->
        val k = 100.0 / e.grams
        LibFood(
            name = e.name, display = e.name, category = RECENT,
            kcal = e.kcal * k, protein = e.protein * k, carbs = e.carbs * k, fat = e.fat * k,
            portionLabel = "Last time", portionG = e.grams.toDouble(), source = "recent",
        )
    }

    fun defaultMeal(): String {
        val h = LocalTime.now().hour
        return when {
            h < 11 -> "Breakfast"
            h < 16 -> "Lunch"
            h < 21 -> "Dinner"
            else -> "Snacks"
        }
    }

    fun addFoods(drafts: List<FoodDraft>, meal: String, source: String) {
        if (drafts.isEmpty()) return
        val date = selectedDate.toString()
        entries = entries + drafts.map {
            FoodEntry(newId(), date, meal, it.name, it.grams, it.kcal, it.protein, it.carbs, it.fat, source)
        }
        persist()
    }

    fun updateEntry(updated: FoodEntry) {
        entries = entries.map { if (it.id == updated.id) updated else it }
        persist()
    }

    fun deleteEntry(id: Long) {
        entries = entries.filterNot { it.id == id }
        persist()
    }

    /** Copies one meal from the previous day into the selected day. Returns how many foods were copied. */
    fun copyMealFromPreviousDay(meal: String): Int {
        val from = entriesFor(selectedDate.minusDays(1)).filter { it.meal == meal }
        if (from.isEmpty()) return 0
        val date = selectedDate.toString()
        entries = entries + from.map { it.copy(id = newId(), date = date) }
        persist()
        return from.size
    }

    // ---------- Own foods & barcodes ----------

    fun addCustomFood(food: LibFood): LibFood {
        val saved = food.copy(
            id = newId(), custom = true, category = MY_FOODS, source = "custom",
            display = food.display.ifBlank { food.name },
            name = if (food.source == "off") food.name else food.display.ifBlank { food.name },
        )
        customFoods = customFoods.filterNot { saved.barcode.isNotBlank() && it.barcode == saved.barcode } + saved
        persist()
        return saved
    }

    fun deleteCustomFood(id: Long) {
        customFoods = customFoods.filterNot { it.id == id }
        persist()
    }

    /** Checks your own foods first (works offline), then Open Food Facts. */
    suspend fun lookupBarcode(code: String): BarcodeResult {
        val digits = code.filter { it.isDigit() }
        customFoods.firstOrNull { it.barcode.isNotBlank() && it.barcode == digits }?.let { return BarcodeResult.Found(it) }
        val result = OpenFoodFacts.lookup(digits)
        if (result is BarcodeResult.Found) {
            // Keep scanned products in My foods so the next scan works offline.
            return BarcodeResult.Found(addCustomFood(result.food))
        }
        return result
    }

    suspend fun searchOnline(query: String): List<LibFood> = OpenFoodFacts.search(query)

    // ---------- Water ----------

    fun waterFor(date: LocalDate): Int = water[date.toString()] ?: 0

    fun addWater(date: LocalDate, deltaMl: Int) {
        val key = date.toString()
        val next = ((water[key] ?: 0) + deltaMl).coerceIn(0, 10_000)
        water = water + (key to next)
        persist()
    }

    // ---------- Weight ----------

    fun logWeight(date: LocalDate, kg: Double) {
        if (kg <= 0) return
        val key = date.toString()
        weights = (weights.filterNot { it.date == key } + WeightEntry(key, kg)).sortedBy { it.date }
        persist()
        // The newest weigh-in becomes the profile weight (used for targets and Gym Bro).
        if (weights.lastOrNull()?.date == key) updateSettings(settings.copy(weightKg = kg))
    }

    fun deleteWeight(date: String) {
        weights = weights.filterNot { it.date == date }
        persist()
    }

    // ---------- Stats ----------

    fun dayStats(days: Int, end: LocalDate = LocalDate.now()): List<DayStat> {
        val byDate = entries.groupBy { it.date }
        return (days - 1 downTo 0).map { back ->
            val d = end.minusDays(back.toLong())
            val list = byDate[d.toString()].orEmpty()
            DayStat(
                date = d,
                kcal = list.sumOf { it.kcal },
                protein = list.sumOf { it.protein },
                carbs = list.sumOf { it.carbs },
                fat = list.sumOf { it.fat },
                count = list.size,
            )
        }
    }

    /** Days in a row with something logged. Today counts once you log; until then the streak runs to yesterday. */
    fun streak(): Int {
        val logged = entries.mapTo(HashSet()) { it.date }
        var d = LocalDate.now()
        if (d.toString() !in logged) d = d.minusDays(1)
        var n = 0
        while (d.toString() in logged) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    // ---------- Settings & data ----------

    fun updateSettings(s: Settings) {
        settings = s
        viewModelScope.launch(Dispatchers.IO) { store.saveSettings(s) }
    }

    fun exportBackup(): String = Store.backupJson(AppData(entries, chat, customFoods, weights, water), settings)

    /** Parses without applying, so the UI can confirm first. Throws with a readable message. */
    fun parseBackup(text: String): Pair<AppData, Settings> = Store.parseBackup(text, settings)

    fun applyBackup(data: AppData, s: Settings) {
        entries = data.entries
        chat = data.chat
        customFoods = data.customFoods
        weights = data.weights.sortedBy { it.date }
        water = data.water
        persist()
        updateSettings(s)
    }

    fun deleteAllData() {
        entries = emptyList()
        chat = emptyList()
        customFoods = emptyList()
        weights = emptyList()
        water = emptyMap()
        clearSnap()
        persist()
    }

    // ---------- Snap / describe AI ----------

    fun loadSnapImage(uri: Uri) {
        viewModelScope.launch {
            snapError = null
            val bmp = withContext(Dispatchers.IO) {
                try { ImageUtil.load(getApplication(), uri) } catch (e: Exception) { null }
            }
            if (bmp == null) {
                snapError = "Couldn't open that photo."
            } else {
                snapBitmap = bmp
                snapItems = emptyList()
                snapNotes = ""
            }
        }
    }

    fun removeSnapPhoto() {
        snapBitmap = null
    }

    fun clearSnap() {
        snapBitmap = null
        snapItems = emptyList()
        snapNotes = ""
        snapNote = ""
        snapError = null
    }

    fun updateSnapItem(index: Int, item: SnapItem) {
        snapItems = snapItems.toMutableList().also { if (index in it.indices) it[index] = item }
    }

    /** Works with a photo, a typed description, or both. */
    fun analyzeSnap() {
        val bmp = snapBitmap
        val note = snapNote.trim()
        if (bmp == null && note.isEmpty()) { snapError = "Take a photo or describe what you ate."; return }
        val client = Ai.client(settings)
        if (client == null) { snapError = Ai.missingKeyMessage(settings); return }
        if (snapLoading) return
        snapLoading = true
        snapError = null
        viewModelScope.launch {
            try {
                val text = Prompts.snapUserText(note, hasPhoto = bmp != null)
                val b64 = if (bmp != null) withContext(Dispatchers.Default) { ImageUtil.toJpegBase64(bmp) } else null
                val reply = client.complete(
                    Prompts.SNAP_SYSTEM, listOf(AiMessage("user", text, b64)),
                    json = true, temperature = 0.2,
                )
                val (items, notes) = parseSnap(reply)
                snapItems = items
                snapNotes = notes.ifBlank { if (items.isEmpty()) "I couldn't find any food here." else "" }
            } catch (e: Exception) {
                snapError = e.message ?: "Something went wrong."
            } finally {
                snapLoading = false
            }
        }
    }

    private fun parseSnap(text: String): Pair<List<SnapItem>, String> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw Exception("Couldn't read the AI's answer. Try again.")
        val obj = JSONObject(text.substring(start, end + 1))
        val arr = obj.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val grams = o.optDouble("grams", 100.0).let { if (it.isNaN() || it <= 0) 100.0 else it }
            fun per(key: String, alt: String): Double {
                val v = o.optDouble(key, o.optDouble(alt, 0.0))
                return if (v.isNaN() || v < 0) 0.0 else v / grams
            }
            SnapItem(
                name = o.optString("name", "Food"),
                grams = grams,
                kcalPerG = per("kcal", "calories"),
                proteinPerG = per("protein_g", "protein"),
                carbsPerG = per("carbs_g", "carbs"),
                fatPerG = per("fat_g", "fat"),
            )
        }
        return items to obj.optString("notes", "")
    }

    fun addSnapToLog(meal: String) {
        addFoods(snapItems.filter { it.selected && it.grams > 0 }.map { it.toDraft() }, meal, "snap")
        clearSnap()
    }

    // ---------- Gym Bro ----------

    fun sendBro(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || broLoading) return
        val client = Ai.client(settings)
        if (client == null) { broError = Ai.missingKeyMessage(settings); return }
        chat = chat + ChatMsg("user", msg)
        persist()
        broLoading = true
        broError = null
        val s = settings
        val today = LocalDate.now()
        val system = Prompts.broSystem(s, entriesFor(today), today, dayStats(7, today), weights, waterFor(today))
        val history = chat.takeLast(24)
        viewModelScope.launch {
            try {
                val reply = client.complete(system, toMessages(history), json = false, temperature = 0.9)
                chat = chat + ChatMsg("model", reply)
                persist()
            } catch (e: Exception) {
                broError = e.message ?: "Something went wrong."
            } finally {
                broLoading = false
            }
        }
    }

    fun clearChat() {
        chat = emptyList()
        broError = null
        persist()
    }

    /** Alternate user/assistant turns: merge repeats and start with a user turn. */
    private fun toMessages(history: List<ChatMsg>): List<AiMessage> {
        val merged = mutableListOf<AiMessage>()
        for (m in history) {
            val role = if (m.role == "model") "assistant" else "user"
            if (merged.isEmpty() && role == "assistant") continue
            if (merged.isNotEmpty() && merged.last().role == role) {
                merged[merged.size - 1] = AiMessage(role, merged.last().text + "\n\n" + m.text)
            } else merged.add(AiMessage(role, m.text))
        }
        return merged
    }

    /** Sends a tiny request to check a key and model. Returns (ok, message). */
    suspend fun testConnection(test: Settings): Pair<Boolean, String> {
        val client = Ai.client(test) ?: return false to "Paste a key first."
        return try {
            client.complete(null, listOf(AiMessage("user", "Reply with just: OK")), json = false, temperature = 0.0)
            true to (if (test.provider == Ai.OPENROUTER) "Key works. OpenRouter is connected." else "Key works. Gemini is connected.")
        } catch (e: Exception) {
            false to (e.message ?: "Couldn't connect.")
        }
    }

    /** OpenRouter's public model list, for the model picker. */
    suspend fun openRouterModels(): List<OrModel> = OpenRouter.listModels()
}
