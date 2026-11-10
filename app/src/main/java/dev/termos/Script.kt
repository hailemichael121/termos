package dev.termos

import java.util.Calendar
import java.util.TimeZone

object Vars {
    /** Expands NAME, {NAME}, 1.., ?, @ and (cmd) after a dollar sign. Single quotes and \$ stay literal. */
    fun expand(s: String, vars: Map<String, String>, sub: (String) -> String): String {
        val sb = StringBuilder()
        var i = 0
        var q = false
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\'' -> { q = !q; sb.append(c); i++ }
                q -> { sb.append(c); i++ }
                c == '\\' && i + 1 < s.length && s[i + 1] == '$' -> { sb.append('$'); i += 2 }
                c == '$' && i + 1 < s.length -> {
                    val n = s[i + 1]
                    when {
                        n == '(' -> {
                            var depth = 1
                            var j = i + 2
                            while (j < s.length && depth > 0) { if (s[j] == '(') depth++ else if (s[j] == ')') depth--; j++ }
                            if (depth != 0) { sb.append(c); i++ }
                            else { sb.append(sub(s.substring(i + 2, j - 1)).trim()); i = j }
                        }
                        n == '{' -> {
                            val j = s.indexOf('}', i + 2)
                            if (j < 0) { sb.append(c); i++ } else { sb.append(vars[s.substring(i + 2, j)] ?: ""); i = j + 1 }
                        }
                        n == '?' || n == '@' -> { sb.append(vars[n.toString()] ?: ""); i += 2 }
                        n.isLetterOrDigit() || n == '_' -> {
                            var j = i + 1
                            while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                            sb.append(vars[s.substring(i + 1, j)] ?: "")
                            i = j
                        }
                        else -> { sb.append(c); i++ }
                    }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}

object Cond {
    fun eval(l: String, op: String, r: String): Boolean {
        val a = l.trim().trim('"', '\'')
        val b = r.trim().trim('"', '\'')
        val x = a.toDoubleOrNull()
        val y = b.toDoubleOrNull()
        val num = x != null && y != null
        return when (op) {
            "==" -> if (num) x == y else a == b
            "!=" -> if (num) x != y else a != b
            "<" -> if (num) x!! < y!! else a < b
            ">" -> if (num) x!! > y!! else a > b
            "<=" -> if (num) x!! <= y!! else a <= b
            ">=" -> if (num) x!! >= y!! else a >= b
            "contains", "has" -> a.contains(b, ignoreCase = true)
            else -> false
        }
    }
}

object Chain {
    /** Split on ; && || outside quotes. Each part carries the joiner that precedes it (first = ";"). */
    fun split(s: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val sb = StringBuilder()
        var q: Char? = null
        var joiner = ";"
        var i = 0
        fun flush(next: String) {
            val t = sb.toString().trim()
            if (t.isNotEmpty()) out.add(t to joiner)
            sb.setLength(0)
            joiner = next
        }
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> { sb.append(c).append(s[i + 1]); i += 2; continue }
                q != null -> { sb.append(c); if (c == q) q = null }
                c == '"' || c == '\'' -> { q = c; sb.append(c) }
                c == ';' -> flush(";")
                c == '&' && i + 1 < s.length && s[i + 1] == '&' -> { flush("&&"); i++ }
                c == '|' && i + 1 < s.length && s[i + 1] == '|' -> { flush("||"); i++ }
                else -> sb.append(c)
            }
            i++
        }
        flush(";")
        return out
    }
}

sealed class Line {
    object Skip : Line()
    object Exit : Line()
    data class Assign(val name: String, val value: String) : Line()
    data class IfLine(val l: String, val op: String, val r: String, val then: String, val els: String?) : Line()
    data class RepeatLine(val n: Int, val body: String) : Line()
    data class Sleep(val sec: Long) : Line()
    data class RunLine(val text: String) : Line()
}

object ScriptLine {
    private val setRe = Regex("^(?:set\\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$")
    private val ifRe = Regex("^if\\s+(.+?)\\s+(==|!=|<=|>=|<|>|contains|has)\\s+(.+?)\\s+then\\s+(.+?)(?:\\s+else\\s+(.+))?$")
    private val repRe = Regex("^repeat\\s+(\\d+)\\s+do\\s+(.+)$")

    fun parse(raw: String): Line {
        val t = raw.trim()
        if (t.isEmpty() || t.startsWith("#")) return Line.Skip
        if (t == "exit" || t == "return") return Line.Exit
        setRe.matchEntire(t)?.let { m -> return Line.Assign(m.groupValues[1], m.groupValues[2].trim().trim('"', '\'')) }
        ifRe.matchEntire(t)?.let { m ->
            return Line.IfLine(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4], m.groupValues[5].ifEmpty { null })
        }
        repRe.matchEntire(t)?.let { m -> return Line.RepeatLine(m.groupValues[1].toInt(), m.groupValues[2]) }
        if (t.startsWith("sleep ")) {
            val a = t.removePrefix("sleep ").trim()
            val s = a.toLongOrNull() ?: Dur.parse(a)
            return if (s != null) Line.Sleep(s) else Line.RunLine(t)
        }
        return Line.RunLine(t)
    }
}

interface ScriptHost {
    /** Run one TermOS command line. Returns (no error?, captured output). */
    fun exec(cmd: String): Pair<Boolean, String>
    fun show(s: String)
    fun builtins(): Map<String, String>
    suspend fun sleep(sec: Long)
}

class ScriptResult(val ok: Boolean, val steps: Int, val stopped: String?)

object Script {
    const val MAX_STEPS = 2000

    private class Stop(val why: String?) : Exception()

    suspend fun run(lines: List<String>, args: List<String>, host: ScriptHost): ScriptResult {
        val vars = HashMap<String, String>(host.builtins())
        args.forEachIndexed { i, a -> vars[(i + 1).toString()] = a }
        vars["@"] = args.joinToString(" ")
        vars["?"] = "0"
        var steps = 0
        var lastOk = true

        fun expandAll(s: String): String = Vars.expand(s, vars) { c -> host.exec(expandAll(c)).second }
        fun status(ok: Boolean) { lastOk = ok; vars["?"] = if (ok) "0" else "1" }

        suspend fun runChain(text: String) {
            var ok = true
            for ((cmd, joiner) in Chain.split(text)) {
                if (joiner == "&&" && !ok) continue
                if (joiner == "||" && ok) continue
                if (++steps > MAX_STEPS) throw Stop("step limit ($MAX_STEPS) reached")
                when (val l = ScriptLine.parse(cmd)) {
                    Line.Skip -> {}
                    Line.Exit -> throw Stop(null)
                    is Line.Assign -> { vars[l.name] = expandAll(l.value); ok = true }
                    is Line.Sleep -> { host.sleep(l.sec); ok = true }
                    is Line.IfLine, is Line.RepeatLine -> { host.show("error: if/repeat must start a line"); ok = false; status(false) }
                    is Line.RunLine -> {
                        val (good, output) = host.exec(expandAll(l.text))
                        if (output.isNotEmpty()) host.show(output)
                        ok = good
                        status(good)
                    }
                }
            }
        }

        try {
            for (raw in lines) {
                val t = raw.trim()
                if (t.startsWith("if ") || t.startsWith("repeat ")) {
                    if (++steps > MAX_STEPS) throw Stop("step limit ($MAX_STEPS) reached")
                    when (val l = ScriptLine.parse(t)) {
                        is Line.IfLine -> {
                            val c = Cond.eval(expandAll(l.l), l.op, expandAll(l.r))
                            val b = if (c) l.then else l.els
                            if (b != null) runChain(b)
                        }
                        is Line.RepeatLine -> repeat(minOf(l.n, 100)) { runChain(l.body) }
                        else -> { host.show("error: bad syntax: $t"); status(false) }
                    }
                } else runChain(t)
            }
        } catch (e: Stop) {
            return ScriptResult(lastOk, steps, e.why)
        }
        return ScriptResult(lastOk, steps, null)
    }
}

/** A scheduled job. kind: at (daily) | once | every */
data class SchedJob(val id: Int, val kind: String, val spec: String, val next: Long, val cmd: String) {
    fun serialize(): String =
        listOf(id.toString(), kind, spec, next.toString(), cmd.replace("\t", " ").replace("\n", " ")).joinToString("\t")

    companion object {
        fun parse(line: String): SchedJob? {
            val p = line.split('\t', limit = 5)
            if (p.size < 5) return null
            val id = p[0].toIntOrNull() ?: return null
            val next = p[3].toLongOrNull() ?: return null
            return SchedJob(id, p[1], p[2], next, p[4])
        }
    }
}

object Schedule {
    fun nextAt(hour: Int, minute: Int, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = nowMs
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun parseTime(s: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h to mi else null
    }

    /** at HH:mm [once|daily] <cmd...>   |   every <dur>=1m+ <cmd...> */
    fun create(id: Int, kind: String, args: List<String>, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): SchedJob? {
        if (kind == "at") {
            val t = parseTime(args.getOrNull(0) ?: return null) ?: return null
            var rest = args.drop(1)
            var k = "at"
            if (rest.firstOrNull() == "once") { k = "once"; rest = rest.drop(1) }
            else if (rest.firstOrNull() == "daily") rest = rest.drop(1)
            if (rest.isEmpty()) return null
            return SchedJob(id, k, args[0], nextAt(t.first, t.second, nowMs, tz), rest.joinToString(" "))
        }
        if (kind == "every") {
            val sec = Dur.parse(args.getOrNull(0) ?: return null) ?: return null
            if (sec < 60 || args.size < 2) return null
            return SchedJob(id, "every", args[0], nowMs + sec * 1000, args.drop(1).joinToString(" "))
        }
        return null
    }

    /** Next run after a firing; null = one-shot, remove it. */
    fun nextAfter(j: SchedJob, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Long? = when (j.kind) {
        "at" -> parseTime(j.spec)?.let { nextAt(it.first, it.second, nowMs, tz) }
        "every" -> Dur.parse(j.spec)?.let { nowMs + it * 1000 }
        else -> null
    }
}

/** Commands allowed to run from scheduled jobs / background (no UI, no messaging, no apps). */
object Headless {
    val allowed = setOf("echo", "notify", "note", "todo", "timer", "torch", "battery", "date", "calc", "tsh", "df", "free", "which")

    fun ok(cmd: String): Boolean {
        val n = Registry.find(cmd)?.name ?: return false
        return n in allowed
    }
}

object PhoneNum {
    fun normalize(s: String): String = s.filter { it.isDigit() || it == '+' }
    fun looksLikeNumber(s: String): Boolean = Regex("^\\+?[0-9][0-9 ()-]{2,}$").matches(s.trim())
}

object Oem {
    fun tip(manufacturer: String): String {
        val x = manufacturer.lowercase()
        return when {
            listOf("tecno", "infinix", "itel", "transsion").any { x.contains(it) } ->
                "Tecno/Infinix/itel: open Phone Master (or Settings > Battery) > App launch / Autostart, set TermOS to manual with every switch on, and lock TermOS in Recents."
            x.contains("xiaomi") || x.contains("redmi") || x.contains("poco") ->
                "Xiaomi: Settings > Apps > TermOS > Autostart ON, Battery saver 'No restrictions', and lock the card in Recents."
            x.contains("huawei") || x.contains("honor") ->
                "Huawei/Honor: Settings > Battery > App launch > TermOS > manage manually, all three switches on."
            x.contains("samsung") ->
                "Samsung: Settings > Battery > Background usage limits > add TermOS to 'Never sleeping apps'."
            x.contains("oppo") || x.contains("realme") || x.contains("vivo") || x.contains("oneplus") ->
                "Allow Auto-launch and Background activity for TermOS in the phone's battery/app settings, and lock it in Recents."
            else -> "If timers still stop, see dontkillmyapp.com for your phone brand."
        }
    }
}
