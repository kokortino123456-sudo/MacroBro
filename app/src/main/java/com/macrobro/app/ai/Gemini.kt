package com.macrobro.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiException(message: String) : Exception(message)

/** Tiny Gemini REST client: no SDK, just HttpURLConnection and org.json. */
object Gemini {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    fun userText(text: String): JSONObject = JSONObject()
        .put("role", "user")
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    fun userTextAndImage(text: String, jpegBase64: String): JSONObject = JSONObject()
        .put("role", "user")
        .put(
            "parts", JSONArray()
                .put(JSONObject().put("text", text))
                .put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)
                    )
                )
        )

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String?,
        contents: JSONArray,
        jsonOutput: Boolean = false,
        temperature: Double = 0.7,
    ): String = withContext(Dispatchers.IO) {
        val modelId = model.trim().removePrefix("models/").ifBlank { "gemini-flash-latest" }
        val body = JSONObject().put("contents", contents)
        if (system != null) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val gen = JSONObject().put("temperature", temperature)
        if (jsonOutput) gen.put("responseMimeType", "application/json")
        body.put("generationConfig", gen)

        val conn = try {
            (URL("$BASE/models/$modelId:generateContent").openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw GeminiException("Couldn't reach Gemini. Check your internet connection.")
        }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey.trim())
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw GeminiException(friendlyError(code, resp, modelId))
            extractText(resp)
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException("Couldn't reach Gemini. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(resp: String): String {
        val root = try { JSONObject(resp) } catch (e: Exception) {
            throw GeminiException("Gemini sent back something unreadable. Try again.")
        }
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(
                if (reason.isNotBlank()) "Gemini refused this request ($reason)." else "Gemini returned no answer. Try again."
            )
        }
        val cand = candidates.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                sb.append(p.optString("text", ""))
            }
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) {
            val finish = cand.optString("finishReason", "")
            throw GeminiException(
                if (finish == "SAFETY") "Gemini blocked that answer for safety reasons." else "Gemini returned an empty answer. Try again."
            )
        }
        return text
    }

    private fun friendlyError(code: Int, resp: String, model: String): String {
        val msg = try {
            JSONObject(resp).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) { "" }
        return when {
            msg.contains("API key not valid", true) || msg.contains("API_KEY_INVALID", true) ->
                "That API key isn't valid. Check it in Settings."
            code == 404 -> "Model \"$model\" wasn't found. In Settings, set the model to gemini-flash-latest."
            code == 429 -> "Gemini's free limit was hit. Wait a minute and try again."
            code == 403 -> "This key isn't allowed to use Gemini. Make a new key at aistudio.google.com."
            code >= 500 -> "Gemini is having trouble right now. Try again in a moment."
            msg.isNotBlank() -> "Gemini error: $msg"
            else -> "Gemini error (HTTP $code)."
        }
    }
}
