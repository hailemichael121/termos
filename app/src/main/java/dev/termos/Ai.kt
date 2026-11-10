package dev.termos

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gemini REST client. Call from Dispatchers.IO. */
object Ai {
    const val CHAT_SYSTEM =
        "You are the AI assistant inside TermOS, a terminal-style Android launcher. " +
        "Reply in plain text only (no markdown), concise, terminal-friendly. " +
        "TermOS commands include: ls, open <app>, browse <url|query>, go <n>, back, calc, timer, todo, " +
        "torch, copy, paste, note, notes, battery, wifi, bt, call <number>, theme, vim, lock, help."

    fun planSystem(): String =
        "You translate a request into TermOS commands. Output ONLY commands, one per line, at most 6, " +
        "no explanations, no markdown, no numbering. Allowed commands: " +
        Registry.all.filter { Plan.allowed(it.name) }.joinToString(", ") { it.name } + ". " +
        "Usage hints: " + Registry.all.filter { Plan.allowed(it.name) }.joinToString(" | ") { it.usage } + ". " +
        "If the request cannot be done with these commands, output a single line: # cannot"

    fun ask(key: String, model: String, prompt: String, system: String = CHAT_SYSTEM): String = try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))

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
        val raw = (if (ok) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (!ok) "ai error ${conn.responseCode}: ${raw.take(300)}"
        else JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
    } catch (e: Exception) {
        "ai error: ${e.message}"
    }
}
