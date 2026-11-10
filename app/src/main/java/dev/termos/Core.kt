package dev.termos

import java.math.BigDecimal
import java.math.MathContext

/** Shell-like parsing: quotes, escapes, ';' statements, '|' stages, history and alias expansion. */
object Parser {
    private fun splitOutside(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && q != '\'' -> { sb.append(c).append(s[i + 1]); i++ }
                q != null -> { sb.append(c); if (c == q) q = null }
                c == '"' || c == '\'' -> { q = c; sb.append(c) }
                c == sep -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun statements(line: String): List<String> = splitOutside(line, ';')
    fun pipes(stmt: String): List<String> = splitOutside(stmt, '|')

    fun tokens(s: String): List<String> {
        val r = mutableListOf<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var has = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && q != '\'' -> { sb.append(s[i + 1]); has = true; i++ }
                q != null -> { if (c == q) q = null else sb.append(c) }
                c == '"' || c == '\'' -> { q = c; has = true }
                c.isWhitespace() -> { if (has || sb.isNotEmpty()) { r.add(sb.toString()); sb.setLength(0); has = false } }
                else -> sb.append(c)
            }
            i++
        }
        if (has || sb.isNotEmpty()) r.add(sb.toString())
        return r
    }

    /** Arguments as plain text with quotes removed. */
    fun words(arg: String): String = tokens(arg).joinToString(" ")

    /** null = not a history reference; "" = event not found. */
    fun expandHistory(line: String, h: List<String>): String? {
        if (!line.startsWith("!") || line.length < 2) return null
        val r = line.drop(1)
        return when {
            r == "!" -> h.lastOrNull() ?: ""
            r.toIntOrNull() != null -> h.getOrNull(r.toInt() - 1) ?: ""
            else -> h.lastOrNull { it.startsWith(r) } ?: ""
        }
    }

    fun expandAlias(line: String, a: Map<String, String>): String {
        var cur = line
        val seen = mutableSetOf<String>()
        repeat(5) {
            val t = cur.trimStart()
            val head = t.substringBefore(' ')
            val v = a[head] ?: return cur
            if (!seen.add(head)) return cur
            cur = v + t.removePrefix(head)
        }
        return cur
    }

    /** Hide secrets before echoing or storing a line. */
    fun redact(line: String): String = line.replace(Regex("(?i)(ai\\s+key\\s+)\\S+"), "$1***")
}

/** Tiny text filters usable after '|'. */
object Filters {
    val names = setOf("grep", "head", "tail", "wc", "sort", "uniq")

    private fun numArg(a: List<String>): Int? {
        val i = a.indexOf("-n")
        if (i >= 0) return a.getOrNull(i + 1)?.toIntOrNull()
        for (x in a) {
            if (x.startsWith("-") && x.drop(1).toIntOrNull() != null) return x.drop(1).toInt()
            if (x.toIntOrNull() != null) return x.toInt()
        }
        return null
    }

    fun apply(cmd: String, args: List<String>, input: String): String {
        val lines = if (input.isEmpty()) emptyList() else input.split("\n")
        return when (cmd) {
            "grep" -> {
                var inv = false
                var ic = false
                var pat: String? = null
                for (x in args) {
                    if (x == "-v") inv = true else if (x == "-i") ic = true else if (pat == null) pat = x
                }
                val p = pat ?: return "grep: missing pattern"
                lines.filter { it.contains(p, ic) != inv }.joinToString("\n")
            }
            "head" -> lines.take(numArg(args) ?: 10).joinToString("\n")
            "tail" -> lines.takeLast(numArg(args) ?: 10).joinToString("\n")
            "wc" -> if ("-l" in args) "${lines.size}"
                    else "${lines.size} ${input.split(Regex("\\s+")).count { it.isNotEmpty() }} ${input.length}"
            "sort" -> lines.sorted().let { if ("-r" in args) it.reversed() else it }.joinToString("\n")
            "uniq" -> {
                val r = mutableListOf<String>()
                for (l in lines) if (r.lastOrNull() != l) r.add(l)
                r.joinToString("\n")
            }
            else -> input
        }
    }
}

object Suggest {
    /** Levenshtein with adjacent transposition. */
    fun dist(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    fun closest(word: String, options: Collection<String>): String? {
        val w = word.lowercase()
        val max = if (w.length <= 3) 1 else 2
        return options.filter { it.length > 1 }.map { it to dist(w, it) }
            .filter { it.second <= max }.minByOrNull { it.second }?.first
    }
}

object Calc {
    fun eval(s: String): Double {
        val p = P(s.lowercase().replace(" ", ""))
        val v = p.expr()
        if (p.i != p.s.length) throw IllegalArgumentException("unexpected '${p.s[p.i]}'")
        return v
    }

    fun fmt(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return d.toString()
        return BigDecimal(d).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private class P(val s: String) {
        var i = 0
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun term(): Double {
            var v = unary()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                val r = unary()
                v = when (op) {
                    '*' -> v * r
                    '/' -> { if (r == 0.0) throw ArithmeticException("division by zero"); v / r }
                    else -> v % r
                }
            }
            return v
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return pow()
        }
        fun pow(): Double {
            val b = atom()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(b, unary()) }
            return b
        }
        fun atom(): Double {
            if (i >= s.length) throw IllegalArgumentException("unexpected end")
            if (s[i] == '(') {
                i++
                val v = expr()
                if (i >= s.length || s[i] != ')') throw IllegalArgumentException("missing )")
                i++
                return v
            }
            val st = i
            if (s[i].isDigit() || s[i] == '.') {
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
            }
            while (i < s.length && s[i].isLetter()) i++
            val name = s.substring(st, i)
            if (name.isEmpty()) throw IllegalArgumentException("unexpected '${s[i]}'")
            if (name == "pi") return Math.PI
            if (name == "e") return Math.E
            if (i >= s.length || s[i] != '(') throw IllegalArgumentException("unknown '$name'")
            i++
            val a = expr()
            if (i >= s.length || s[i] != ')') throw IllegalArgumentException("missing )")
            i++
            return when (name) {
                "sqrt" -> Math.sqrt(a); "abs" -> Math.abs(a); "ln" -> Math.log(a)
                "log" -> Math.log10(a); "sin" -> Math.sin(a); "cos" -> Math.cos(a); "tan" -> Math.tan(a)
                else -> throw IllegalArgumentException("unknown function '$name'")
            }
        }
    }
}

object Dur {
    /** seconds, or null. "90s", "5m", "1h30m"; a bare number means minutes. */
    fun parse(s: String): Long? {
        val t = s.trim().lowercase()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { return it * 60 }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").matchEntire(t) ?: return null
        val (h, mi, se) = m.destructured
        if (h.isEmpty() && mi.isEmpty() && se.isEmpty()) return null
        return (h.toLongOrNull() ?: 0L) * 3600 + (mi.toLongOrNull() ?: 0L) * 60 + (se.toLongOrNull() ?: 0L)
    }

    fun fmt(sec: Long): String {
        val s = maxOf(0L, sec)
        val h = s / 3600; val m = (s % 3600) / 60; val r = s % 60
        return if (h > 0) "%dh%02dm%02ds".format(h, m, r) else if (m > 0) "%dm%02ds".format(m, r) else "${r}s"
    }
}

object Lockout {
    fun delaySeconds(fails: Int): Long = when {
        fails < 3 -> 0L
        fails == 3 -> 5L
        fails == 4 -> 15L
        fails == 5 -> 30L
        else -> 60L
    }

    /** Seconds left to wait (0 = may try now). */
    fun remaining(fails: Int, lastMs: Long, nowMs: Long): Long {
        val left = lastMs + delaySeconds(fails) * 1000 - nowMs
        return if (left <= 0) 0L else (left + 999) / 1000
    }
}

enum class Kind { PROMPT, ERROR, DIM, NORMAL }

object LineStyle {
    private val prompt = Regex("^\\S+@termos:\\S*[#$] ")
    private val err = Regex("(?i)(denied|error|not found|incorrect|unknown|usage:|failed|invalid|mismatch|too many)")

    fun classify(l: String): Kind = when {
        prompt.containsMatchIn(l) -> Kind.PROMPT
        err.containsMatchIn(l) -> Kind.ERROR
        l.startsWith("tip:") || l.startsWith("(") -> Kind.DIM
        else -> Kind.NORMAL
    }
}

/** Safety filter for AI-proposed command plans. */
object Plan {
    val forbidden = setOf("sudo", "su", "passwd", "exit", "ai", "linux", "uninstall", "settings", "lock")

    fun allowed(name: String): Boolean {
        val c = Registry.find(name) ?: return false
        return !c.root && c.name !in forbidden
    }

    fun parse(text: String): List<String> = text.lines()
        .map { it.trim().trimStart('$', ' ').trim('`').trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.contains(';') && allowed(it.substringBefore(' ')) }
        .take(6)
}

interface CompletionSource {
    fun apps(): List<String>
    fun aliases(): Collection<String>
}

object Completer {
    val subs: Map<String, List<String>> = mapOf(
        "config" to listOf("theme", "vim", "font", "autolock", "username"),
        "linux" to listOf("ubuntu", "shell", "setup", "run", "exec", "guide"),
        "ai" to listOf("do", "key", "model"),
        "vim" to listOf("on", "off"),
        "torch" to listOf("on", "off"),
        "timer" to listOf("cancel"),
        "todo" to listOf("add", "done", "rm", "clear"),
        "notes" to listOf("clear"),
        "history" to listOf("clear")
    )

    fun complete(text: String, src: CompletionSource): List<String> {
        val cut = maxOf(text.lastIndexOf(';'), text.lastIndexOf('|'))
        val head = if (cut >= 0) text.substring(0, cut + 1) + " " else ""
        val seg = (if (cut >= 0) text.substring(cut + 1) else text).trimStart()
        val afterPipe = cut >= 0 && text[cut] == '|'
        val parts = seg.split(' ')
        val tail: List<String> = if (parts.size == 1) {
            val w = parts[0].lowercase()
            val pool = if (afterPipe) Filters.names.toList() else (Registry.names() + src.aliases()).distinct()
            pool.filter { it.startsWith(w) }.sorted()
        } else {
            val c = Registry.find(parts[0])?.name ?: parts[0]
            val rest = seg.substringAfter(' ').lowercase()
            val last = parts.last().lowercase()
            val before = parts.dropLast(1).joinToString(" ") + " "
            when {
                afterPipe -> emptyList()
                c == "open" || c == "uninstall" -> src.apps().filter { it.lowercase().startsWith(rest) }.map { parts[0] + " " + it }
                c == "config" && parts.size == 3 && parts[1] == "theme" -> Themes.names().filter { it.startsWith(last) }.map { before + it }
                c == "theme" && parts.size == 2 -> Themes.names().filter { it.startsWith(last) }.map { before + it }
                c == "guide" && parts.size == 2 -> Guide.pages.keys.filter { it.startsWith(last) }.map { before + it }
                (c == "help" || c == "which") && parts.size == 2 -> Registry.names().filter { it.startsWith(last) }.map { before + it }
                parts.size == 2 -> (subs[c] ?: emptyList()).filter { it.startsWith(last) }.map { before + it }
                else -> emptyList()
            }
        }
        return tail.map { head + it }
    }
}
