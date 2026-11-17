package com.macrobro.app.ai

import com.macrobro.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One chat turn. role is "user" or "assistant". */
data class AiMessage(val role: String, val text: String, val imageJpegB64: String? = null)

/** Something that can answer a conversation: Gemini or OpenRouter. */
interface AiClient {
    suspend fun complete(system: String?, messages: List<AiMessage>, json: Boolean, temperature: Double): String
}

object Ai {
    const val GEMINI = "gemini"
    const val OPENROUTER = "openrouter"

    /** The client for the provider chosen in Settings, or null when its key is missing. */
    fun client(s: Settings): AiClient? = when (s.provider) {
        OPENROUTER -> if (s.openRouterKey.isBlank()) null else OpenRouterClient(s.openRouterKey, s.openRouterModel)
        else -> if (s.apiKey.isBlank()) null else GeminiClient(s.apiKey, s.model)
    }

    fun missingKeyMessage(s: Settings): String =
        if (s.provider == OPENROUTER) "Add your OpenRouter API key in Settings first."
        else "Add your Gemini API key in Settings first."
}

class GeminiClient(private val key: String, private val model: String) : AiClient {
    override suspend fun complete(system: String?, messages: List<AiMessage>, json: Boolean, temperature: Double): String {
        val contents = JSONArray()
        messages.forEach { m ->
            val parts = JSONArray().put(JSONObject().put("text", m.text))
            if (m.imageJpegB64 != null) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", m.imageJpegB64)))
            }
            contents.put(JSONObject().put("role", if (m.role == "assistant") "model" else "user").put("parts", parts))
        }
        return Gemini.generate(key, model, system, contents, jsonOutput = json, temperature = temperature)
    }
}

/** OpenRouter: one key for hundreds of models, OpenAI-compatible chat completions. */
class OpenRouterClient(private val key: String, private val model: String) : AiClient {

    override suspend fun complete(system: String?, messages: List<AiMessage>, json: Boolean, temperature: Double): String =
        withContext(Dispatchers.IO) {
            val modelId = model.trim().ifBlank { OpenRouter.DEFAULT_MODEL }
            val msgs = JSONArray()
            if (system != null) msgs.put(JSONObject().put("role", "system").put("content", system))
            messages.forEach { m ->
                val role = if (m.role == "assistant") "assistant" else "user"
                if (m.imageJpegB64 == null) {
                    msgs.put(JSONObject().put("role", role).put("content", m.text))
                } else {
                    val parts = JSONArray()
                        .put(JSONObject().put("type", "text").put("text", m.text))
                        .put(
                            JSONObject().put("type", "image_url").put(
                                "image_url", JSONObject().put("url", "data:image/jpeg;base64," + m.imageJpegB64)
                            )
                        )
                    msgs.put(JSONObject().put("role", role).put("content", parts))
                }
            }
            val body = JSONObject()
                .put("model", modelId)
                .put("messages", msgs)
                .put("temperature", temperature)
            if (json) body.put("response_format", JSONObject().put("type", "json_object"))

            val (code, resp) = OpenRouter.request("POST", "/chat/completions", key, body.toString())
            if (code !in 200..299) throw GeminiException(OpenRouter.friendlyError(code, resp, modelId))
            extract(resp)
        }

    private fun extract(resp: String): String {
        val root = try { JSONObject(resp) } catch (e: Exception) {
            throw GeminiException("OpenRouter sent back something unreadable. Try again.")
        }
        root.optJSONObject("error")?.let { err ->
            throw GeminiException("OpenRouter error: " + err.optString("message", "unknown"))
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: throw GeminiException("OpenRouter returned no answer. Try again.")
        val content = choice.optJSONObject("message")?.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> (0 until content.length()).joinToString("") { i ->
                content.optJSONObject(i)?.optString("text", "").orEmpty()
            }
            else -> ""
        }.trim()
        if (text.isEmpty()) {
            val reason = choice.optString("finish_reason", "")
            throw GeminiException(
                if (reason == "content_filter") "The model blocked that answer." else "The model returned an empty answer. Try again."
            )
        }
        return text
    }
}

data class OrModel(
    val id: String,
    val name: String,
    val free: Boolean,
    val vision: Boolean,
    val contextLength: Int,
)

object OpenRouter {
    const val DEFAULT_MODEL = "openrouter/free"
    private const val BASE = "https://openrouter.ai/api/v1"

    /** Public model list (no key needed). Sorted: free first, then by name. */
    suspend fun listModels(): List<OrModel> = withContext(Dispatchers.IO) {
        val (code, resp) = request("GET", "/models", null, null)
        if (code !in 200..299) throw GeminiException("Couldn't load the model list (HTTP $code).")
        val arr = try { JSONObject(resp).optJSONArray("data") } catch (e: Exception) { null }
            ?: throw GeminiException("Couldn't read the model list.")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val pricing = o.optJSONObject("pricing")
            val prompt = pricing?.optString("prompt", "")?.toDoubleOrNull()
            val completion = pricing?.optString("completion", "")?.toDoubleOrNull()
            val free = id.endsWith(":free") || (prompt == 0.0 && completion == 0.0)
            val inputs = o.optJSONObject("architecture")?.optJSONArray("input_modalities")
            val vision = inputs != null && (0 until inputs.length()).any { inputs.optString(it) == "image" }
            OrModel(id, o.optString("name", id), free, vision, o.optInt("context_length", 0))
        }.sortedWith(compareBy<OrModel>({ !it.free }, { it.name.lowercase() }))
    }

    internal fun request(method: String, path: String, key: String?, body: String?): Pair<Int, String> {
        val conn = try {
            URL(BASE + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GeminiException("Couldn't reach OpenRouter. Check your internet connection.")
        }
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Accept", "application/json")
            if (key != null) conn.setRequestProperty("Authorization", "Bearer " + key.trim())
            // Optional app attribution on openrouter.ai
            conn.setRequestProperty("HTTP-Referer", "https://github.com/kokortino123456-sudo/MacroBro")
            conn.setRequestProperty("X-Title", "MacroBro")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException("Couldn't reach OpenRouter. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    fun friendlyError(code: Int, resp: String, model: String): String {
        val msg = try {
            JSONObject(resp).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) { "" }
        return when {
            code == 401 -> "That OpenRouter key isn't valid. Check it in Settings."
            code == 402 -> "Your OpenRouter credits ran out. Pick a free model or add credits."
            code == 429 -> "Rate limit hit. Free models allow a limited number of requests; wait a minute and try again."
            msg.contains("image", true) && (msg.contains("support", true) || msg.contains("endpoint", true)) ->
                "Model \"$model\" can't see photos. In Settings, pick a model marked Photos (or use openrouter/free)."
            code == 404 || msg.contains("not a valid model", true) ->
                "Model \"$model\" wasn't found. In Settings, choose a model from the list."
            code == 408 -> "The model took too long. Try again."
            code == 502 || code == 503 -> "That model is unavailable right now. Try again or pick another model."
            msg.isNotBlank() -> "OpenRouter error: $msg"
            else -> "OpenRouter error (HTTP $code)."
        }
    }
}
