package dev.termos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Text style of a colour role. */
enum class TxtStyle(val key: String) {
    NORMAL("normal"), BOLD("bold"), ITALIC("italic"), BOLDITALIC("bolditalic");

    companion object {
        fun parse(s: String): TxtStyle? {
            val k = s.trim().lowercase().replace("-", "").replace("_", "").replace("+", "")
            return values().firstOrNull { it.key == k }
        }
    }
}

/** The things you can colour. */
object LookRole {
    val all = listOf("user", "input", "output", "error", "dim", "banner", "time")
    private val alias = mapOf(
        "username" to "user", "prompt" to "user", "in" to "input", "typing" to "input",
        "out" to "output", "text" to "output", "err" to "error", "tip" to "dim",
        "stamp" to "time", "clock" to "time"
    )

    fun resolve(s: String): String? {
        val k = s.trim().lowercase()
        return if (k in all) k else alias[k]
    }
}

object ColorParse {
    val names: Map<String, Int> = linkedMapOf(
        "white" to 0xFFFFFF, "black" to 0x000000, "red" to 0xFF5555, "green" to 0x33FF66,
        "blue" to 0x5599FF, "yellow" to 0xFFD866, "orange" to 0xFFA500, "purple" to 0xBD93F9,
        "pink" to 0xFF79C6, "cyan" to 0x00F0FF, "gray" to 0x999999, "teal" to 0x20C0A0, "gold" to 0xFFB000
    )

    /** "#rrggbb", "rrggbb", "#rgb" or a colour name -> opaque ARGB int, or null. */
    fun parse(s: String): Int? {
        val t = s.trim().lowercase()
        val named = names[t]
        if (named != null) return (0xFF000000L or named.toLong()).toInt()
        val h = t.removePrefix("#")
        val full = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            6 -> h
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return (0xFF000000L or full.toLong(16)).toInt()
    }

    fun hex(c: Int): String = "#%06X".format(c and 0xFFFFFF)
}

/** Turns words into big block letters (3 columns x 5 rows per character). */
object Banner {
    const val MARK = '\u200B'    // line prefix: draw with the "banner" colour
    const val TMARK = '\u200C'   // line prefix: draw with the "time" colour
    const val MAX_CHARS = 24
    const val MAX_ROWS = 3

    private val font: Map<Char, List<String>> = HashMap<Char, List<String>>().apply {
        fun g(c: Char, a: String, b: String, d: String, e: String, f: String) { put(c, listOf(a, b, d, e, f)) }
        g('A', "XXX", "X.X", "XXX", "X.X", "X.X")
        g('B', "XX.", "X.X", "XX.", "X.X", "XX.")
        g('C', "XXX", "X..", "X..", "X..", "XXX")
        g('D', "XX.", "X.X", "X.X", "X.X", "XX.")
        g('E', "XXX", "X..", "XX.", "X..", "XXX")
        g('F', "XXX", "X..", "XX.", "X..", "X..")
        g('G', "XXX", "X..", "X.X", "X.X", "XXX")
        g('H', "X.X", "X.X", "XXX", "X.X", "X.X")
        g('I', "XXX", ".X.", ".X.", ".X.", "XXX")
        g('J', "..X", "..X", "..X", "X.X", "XXX")
        g('K', "X.X", "X.X", "XX.", "X.X", "X.X")
        g('L', "X..", "X..", "X..", "X..", "XXX")
        g('M', "X.X", "XXX", "XXX", "X.X", "X.X")
        g('N', "XXX", "X.X", "X.X", "X.X", "X.X")
        g('O', "XXX", "X.X", "X.X", "X.X", "XXX")
        g('P', "XXX", "X.X", "XXX", "X..", "X..")
        g('Q', "XXX", "X.X", "X.X", "XXX", "..X")
        g('R', "XX.", "X.X", "XX.", "X.X", "X.X")
        g('S', "XXX", "X..", "XXX", "..X", "XXX")
        g('T', "XXX", ".X.", ".X.", ".X.", ".X.")
        g('U', "X.X", "X.X", "X.X", "X.X", "XXX")
        g('V', "X.X", "X.X", "X.X", "X.X", ".X.")
        g('W', "X.X", "X.X", "XXX", "XXX", "X.X")
        g('X', "X.X", "X.X", ".X.", "X.X", "X.X")
        g('Y', "X.X", "X.X", ".X.", ".X.", ".X.")
        g('Z', "XXX", "..X", ".X.", "X..", "XXX")
        g('0', "XXX", "X.X", "X.X", "X.X", "XXX")
        g('1', ".X.", "XX.", ".X.", ".X.", "XXX")
        g('2', "XXX", "..X", "XXX", "X..", "XXX")
        g('3', "XXX", "..X", "XXX", "..X", "XXX")
        g('4', "X.X", "X.X", "XXX", "..X", "..X")
        g('5', "XXX", "X..", "XXX", "..X", "XXX")
        g('6', "XXX", "X..", "XXX", "X.X", "XXX")
        g('7', "XXX", "..X", "..X", "..X", "..X")
        g('8', "XXX", "X.X", "XXX", "X.X", "XXX")
        g('9', "XXX", "X.X", "XXX", "..X", "XXX")
        g('-', "...", "...", "XXX", "...", "...")
        g('_', "...", "...", "...", "...", "XXX")
        g('+', "...", ".X.", "XXX", ".X.", "...")
        g('/', "..X", "..X", ".X.", "X..", "X..")
        g('.', ".", ".", ".", ".", "X")
        g(',', ".", ".", ".", "X", "X")
        g('!', "X", "X", "X", ".", "X")
        g(':', ".", "X", ".", "X", ".")
        g('?', "XXX", "..X", ".XX", "...", ".X.")
    }

    fun supports(ch: Char): Boolean = font.containsKey(ch.uppercaseChar())

    private fun strip(word: String, maxCols: Int): List<String> {
        val rows = List(5) { StringBuilder() }
        var width = 0
        for (ch in word) {
            val glyph = font[ch] ?: font['?']!!
            val w = glyph[0].length + (if (width == 0) 0 else 1)
            if (width + w > maxCols) break
            for (r in 0 until 5) {
                if (width != 0) rows[r].append('.')
                rows[r].append(glyph[r])
            }
            width += w
        }
        return rows.map { it.toString() }
    }

    /** Block letters for [text]; words wrap onto new rows of letters (max 3), characters without a symbol become ?. */
    fun render(text: String, fill: Char = '\u2588', maxCols: Int = 40): List<String> {
        val words = text.trim().uppercase().take(MAX_CHARS).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val blocks = ArrayList<List<String>>()
        var cur: List<String>? = null
        for (w in words) {
            val s = strip(w, maxCols)
            if (s[0].isEmpty()) continue
            val c = cur
            if (c != null && c[0].length + 2 + s[0].length <= maxCols) {
                cur = List(5) { r -> c[r] + ".." + s[r] }
            } else {
                if (c != null) blocks.add(c)
                cur = s
            }
        }
        val last = cur
        if (last != null) blocks.add(last)
        val out = ArrayList<String>()
        for ((i, b) in blocks.take(MAX_ROWS).withIndex()) {
            if (i > 0) out.add("")
            for (row in b) out.add(row.replace('.', ' ').replace('X', fill).trimEnd())
        }
        return out
    }

    private fun defaultLines(d: String): List<String> = d.trimEnd('\n').lines()

    /** What to show at the top: mode = default | custom | plain | off. */
    fun lines(mode: String, text: String, fill: Char, default: String, maxCols: Int = 40): List<String> = when (mode) {
        "off" -> emptyList()
        "plain" -> listOf(text.ifBlank { "TermOS" })
        "custom" -> render(text, fill, maxCols).ifEmpty { defaultLines(default) }
        else -> defaultLines(default)
    }

    fun mark(lines: List<String>): List<String> = lines.map { "$MARK$it" }
}

/** Pure formatting helpers (date lines, top-bar title, prompt split). */
object LookFmt {
    const val DEFAULT_DATE = "EEE d MMM yyyy HH:mm:ss"
    const val DEFAULT_TITLE = "TermOS {version}"

    fun validDate(p: String): Boolean {
        if (p.isBlank()) return false
        return try { SimpleDateFormat(p, Locale.US).format(Date(0)); true } catch (_: Exception) { false }
    }

    fun stamp(ms: Long, pattern: String = DEFAULT_DATE, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        val f = SimpleDateFormat(if (validDate(pattern)) pattern else DEFAULT_DATE, locale)
        f.timeZone = tz
        return f.format(Date(ms))
    }

    fun title(template: String, version: String, user: String): String =
        template.replace("{version}", version).replace("{user}", user)

    fun label(event: String?): String = event ?: "started"

    /** Index just after "user@termos:~$ " in an echoed command line (0 = not a prompt line). */
    fun promptEnd(line: String): Int {
        val m = Regex("^\\S+@termos:\\S*[#$] ").find(line) ?: return 0
        return m.range.last + 1
    }
}
