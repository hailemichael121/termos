package dev.termos

import android.content.Context
import java.io.File

/** Lightweight word frequency cache for keyboard suggestions (history + typed words). */
object WordCache {
    private const val MAX = 400
    private fun file(c: Context) = File(c.filesDir, "word_freq.txt")

    private fun load(c: Context): LinkedHashMap<String, Int> {
        val m = LinkedHashMap<String, Int>()
        file(c).takeIf { it.exists() }?.readLines()?.forEach { line ->
            val p = line.split('\t', limit = 2)
            if (p.size == 2) p[0].toIntOrNull()?.let { n -> m[p[1]] = n }
        }
        return m
    }

    private fun save(c: Context, m: LinkedHashMap<String, Int>) {
        file(c).writeText(m.entries.sortedByDescending { it.value }.take(MAX)
            .joinToString("\n") { "${it.value}\t${it.key}" } + "\n")
    }

    fun remember(c: Context, raw: String) {
        val words = Regex("[a-zA-Z][a-zA-Z0-9_-]{1,31}").findAll(raw).map { it.value.lowercase() }.toSet()
        if (words.isEmpty()) return
        val m = load(c)
        for (w in words) m[w] = (m[w] ?: 0) + 1
        if (m.size > MAX) {
            val drop = m.entries.sortedBy { it.value }.take(m.size - MAX).map { it.key }
            drop.forEach { m.remove(it) }
        }
        save(c, m)
    }

    fun suggest(c: Context, prefix: String, history: List<String>, limit: Int = 5): List<String> {
        val p = prefix.trim().lowercase()
        if (p.length < 1) return defaultShortcuts()
        val pool = LinkedHashSet<String>()
        history.asReversed().forEach { line ->
            val first = line.trim().substringBefore(' ')
            if (first.startsWith(p, ignoreCase = true)) pool.add(first)
        }
        load(c).entries.sortedByDescending { it.value }.forEach { (w, _) ->
            if (w.startsWith(p)) pool.add(w)
        }
        Registry.names().filter { it.startsWith(p) }.forEach { pool.add(it) }
        return pool.take(limit).toList()
    }

    fun defaultShortcuts(): List<String> = listOf("ls", "clear", "help", "open", "sudo")
}
