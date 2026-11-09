package dev.termos

import org.jsoup.Jsoup
import java.net.URLDecoder

data class Page(val title: String, val text: String, val links: List<Pair<String, String>>)

/** Minimal text-mode web browser (Jsoup). Call from Dispatchers.IO. */
object Browser {
    fun fetch(url: String): Page {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) TermOS/0.1")
            .timeout(15_000)
            .followRedirects(true)
            .get()

        val text = doc.select("h1,h2,h3,p,li,pre")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(5000)

        val links = doc.select("a[href]").mapNotNull {
            val h = clean(it.absUrl("href"))
            if (!h.startsWith("http")) return@mapNotNull null
            val t = it.text().trim().ifEmpty { h }.take(70)
            t to h
        }.distinctBy { it.second }.take(40)

        return Page(doc.title().ifEmpty { url }, text, links)
    }

    /** DuckDuckGo wraps result URLs in a redirect; unwrap them. */
    private fun clean(u: String): String =
        if (u.contains("uddg=")) {
            runCatching {
                URLDecoder.decode(u.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            }.getOrDefault(u)
        } else u
}
