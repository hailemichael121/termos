package dev.termos

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gemini REST client. Call from Dispatchers.IO. */
object Ai {
    private const val SYSTEM =
        "You are the AI assistant inside TermOS, a terminal-style Android launcher. " +
        "Reply in plain text only (no markdown), concise, terminal-friendly. " +
        "Available TermOS commands: ls, open <app>, browse <url|query>, go <n>, back, " +
        "copy, paste, note, notes, battery, wifi, bt, settings, call <number>, lock."

    fun ask(key: String, model: String, prompt: String): String = try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", prompt)))))

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val ok = conn.responseCode in 200..299
        val raw = (if (ok) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (!ok) "ai error ${conn.responseCode}: ${raw.take(300)}"
        else JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
    } catch (e: Exception) {
        "ai error: ${e.message}"
    }
}
