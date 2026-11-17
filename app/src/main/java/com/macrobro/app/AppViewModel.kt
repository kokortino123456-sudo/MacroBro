package com.macrobro.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.macrobro.app.ai.Gemini
import com.macrobro.app.ai.Prompts
import com.macrobro.app.data.ChatMsg
import com.macrobro.app.data.FoodDraft
import com.macrobro.app.data.FoodEntry
import com.macrobro.app.data.Settings
import com.macrobro.app.data.SnapItem
import com.macrobro.app.data.Store
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

    var settings by mutableStateOf(store.loadSettings())
        private set
    var entries by mutableStateOf(store.loadEntries())
        private set
    var chat by mutableStateOf(store.loadChat())
        private set
    var selectedDate by mutableStateOf(LocalDate.now())

    // Snap Food AI state
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

    // Gym Bro state
    var broLoading by mutableStateOf(false)
        private set
    var broError by mutableStateOf<String?>(null)

    val hasKey: Boolean get() = settings.apiKey.isNotBlank()

    private fun newId(): Long = System.currentTimeMillis() * 100 + (idSeq++ % 100)

    fun entriesFor(date: LocalDate): List<FoodEntry> = entries.filter { it.date == date.toString() }

    fun recentFoods(): List<FoodEntry> =
        entries.sortedByDescending { it.id }.distinctBy { it.name.lowercase() }.take(12)

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
        persistData()
    }

    fun deleteEntry(id: Long) {
        entries = entries.filterNot { it.id == id }
        persistData()
    }

    fun updateSettings(s: Settings) {
        settings = s
        viewModelScope.launch(Dispatchers.IO) { store.saveSettings(s) }
    }

    private fun persistData() {
        val e = entries
        val c = chat
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try { store.saveData(e, c) } catch (_: Exception) { }
            }
        }
    }

    // ---------- Snap Food AI ----------

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

    fun analyzeSnap() {
        val bmp = snapBitmap ?: return
        if (!hasKey) { snapError = "Add your Gemini API key in Settings first."; return }
        if (snapLoading) return
        snapLoading = true
        snapError = null
        val s = settings
        val note = snapNote
        viewModelScope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) { ImageUtil.toJpegBase64(bmp) }
                val contents = JSONArray().put(Gemini.userTextAndImage(Prompts.snapUserText(note), b64))
                val text = Gemini.generate(s.apiKey, s.model, Prompts.SNAP_SYSTEM, contents, jsonOutput = true, temperature = 0.2)
                val (items, notes) = parseSnap(text)
                snapItems = items
                snapNotes = notes
                if (items.isEmpty() && notes.isBlank()) snapNotes = "No food found in this photo."
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
        if (!hasKey) { broError = "Add your Gemini API key in Settings first."; return }
        chat = chat + ChatMsg("user", msg)
        persistData()
        broLoading = true
        broError = null
        val s = settings
        val today = LocalDate.now()
        val system = Prompts.broSystem(s, entriesFor(today), today)
        val history = chat.takeLast(24)
        viewModelScope.launch {
            try {
                val reply = Gemini.generate(s.apiKey, s.model, system, toContents(history), temperature = 0.9)
                chat = chat + ChatMsg("model", reply)
                persistData()
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
        persistData()
    }

    /** Gemini wants user/model turns; merge repeats and start with a user turn. */
    private fun toContents(history: List<ChatMsg>): JSONArray {
        val merged = mutableListOf<ChatMsg>()
        for (m in history) {
            val role = if (m.role == "model") "model" else "user"
            if (merged.isEmpty() && role == "model") continue
            if (merged.isNotEmpty() && merged.last().role == role) {
                merged[merged.size - 1] = ChatMsg(role, merged.last().text + "\n\n" + m.text)
            } else merged.add(ChatMsg(role, m.text))
        }
        val arr = JSONArray()
        merged.forEach {
            arr.put(JSONObject().put("role", it.role).put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        return arr
    }

    /** Sends a tiny request to check the key + model. Returns a human-readable result. */
    suspend fun testConnection(apiKey: String, model: String): String = try {
        Gemini.generate(apiKey, model, null, JSONArray().put(Gemini.userText("Reply with just: OK")), temperature = 0.0)
        "✅ Key works. Gemini is connected."
    } catch (e: Exception) {
        "❌ " + (e.message ?: "Couldn't connect.")
    }
}
