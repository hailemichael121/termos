package dev.termos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything a module command needs from the Shell. */
class ModEnv(
    val ctx: Context,
    val scope: CoroutineScope,
    val out: (String) -> Unit,
    val emit: (String) -> Unit,
    val isRoot: Boolean,
    val headless: Boolean,
    val exec: (String) -> Pair<Boolean, String>,
    val requestPerm: (String) -> Boolean
)

object Scripts {
    private fun dir(c: Context) = File(c.filesDir, "scripts").apply { mkdirs() }
    fun valid(n: String) = Regex("^[a-z][a-z0-9_-]{0,31}$").matches(n)
    fun list(c: Context): List<String> =
        dir(c).listFiles()?.filter { it.extension == "tsh" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    fun read(c: Context, n: String): List<String>? = File(dir(c), "$n.tsh").takeIf { it.exists() }?.readLines()
    fun save(c: Context, n: String, lines: List<String>) = File(dir(c), "$n.tsh").writeText(lines.joinToString("\n") + "\n")
    fun delete(c: Context, n: String) = File(dir(c), "$n.tsh").delete()
}

object Builtins {
    fun map(c: Context): Map<String, String> {
        fun f(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(Date())
        val bat = (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return mapOf(
            "BATTERY" to bat.toString(), "TIME" to f("HH:mm"), "DATE" to f("yyyy-MM-dd"),
            "HOUR" to f("H"), "DAY" to f("EEE"), "USER" to Config.user(c)
        )
    }
}

object Modules {
    private class Editor(val name: String, val lines: MutableList<String>)

    private var editor: Editor? = null
    private var pendingSms: Triple<String, String, String>? = null   // number, name, text
    private var scriptJob: Job? = null
    private var lastMsgs: List<Msg> = emptyList()

    fun promptOverride(): String? {
        val e = editor
        return when {
            e != null -> "${e.name}> "
            pendingSms != null -> "send sms? [y/N] "
            else -> null
        }
    }

    fun cancel() {
        editor = null
        pendingSms = null
        scriptJob?.cancel()
        scriptJob = null
    }

    /** Lets a module swallow a line (script editor, sms confirmation). */
    fun intercept(raw: String, env: ModEnv): Boolean {
        val e = editor
        if (e != null) {
            when (raw.trim()) {
                "." -> { Scripts.save(env.ctx, e.name, e.lines); editor = null; env.out("saved script '${e.name}' (${e.lines.size} lines). run: tsh ${e.name}") }
                ".cancel" -> { editor = null; env.out("editing cancelled.") }
                ".list" -> env.out(e.lines.mapIndexed { i, l -> "%2d  %s".format(i + 1, l) }.joinToString("\n").ifEmpty { "(empty)" })
                ".undo" -> { if (e.lines.isNotEmpty()) e.lines.removeAt(e.lines.size - 1); env.out("ok") }
                else -> e.lines.add(raw)
            }
            return true
        }
        val p = pendingSms
        if (p != null) {
            pendingSms = null
            if (raw.trim().lowercase() in setOf("y", "yes")) sendNow(env, p.first, p.third) else env.out("sms cancelled.")
            return true
        }
        return false
    }

    fun dispatch(cmd: String, arg: String, env: ModEnv): Boolean {
        if (env.headless && !Headless.ok(cmd)) { env.out("error: '$cmd' is not allowed in background jobs"); return true }
        when (cmd) {
            "timer" -> timer(arg, env)
            "notify" -> notify(arg, env)
            "perms" -> perms(arg, env)
            "keepalive" -> keepalive(arg, env)
            "notifs" -> notifs(arg, env)
            "contacts" -> contacts(arg, env)
            "sms" -> sms(arg, env)
            "script" -> script(arg, env)
            "tsh" -> tsh(arg, env)
            "at", "every" -> addJob(cmd, arg, env)
            "job" -> job(arg, env)
            "call" -> return call(arg, env)
            else -> return false
        }
        return true
    }

    private fun fmtTime(ms: Long) = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(ms))

    // ------------------------------------------------------------- timers
    private fun timer(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        val now = System.currentTimeMillis()
        val list = Timers.load(env.ctx)
        if (p.isEmpty()) {
            env.out(if (list.isEmpty()) "no timers." else list.joinToString("\n") {
                "#${it.id}  ${it.label.ifEmpty { "timer" }}  ${Dur.fmt((it.end - now) / 1000)} left"
            })
            return
        }
        if (p[0] == "cancel") {
            val id = p.getOrNull(1)?.toIntOrNull()
            if (id == null || list.none { it.id == id }) env.out("usage: timer cancel <n>")
            else { Alarms.cancel(env.ctx, "timer", id); Timers.remove(env.ctx, id); env.out("timer #$id cancelled.") }
            return
        }
        val sec = Dur.parse(p[0])
        if (sec == null || sec <= 0 || sec > 86_400) { env.out("usage: timer <90s|5m|1h30m> [label]  (max 24h)"); return }
        val label = p.drop(1).joinToString(" ")
        val id = Timers.nextId(env.ctx)
        val end = now + sec * 1000
        Timers.add(env.ctx, TimerRec(id, end, label))
        Alarms.set(env.ctx, "timer", id, label, end)
        env.out("timer #$id set for ${Dur.fmt(sec)}." + (if (Alarms.exactOk(env.ctx)) "" else " (approximate: run 'perms alarms' for exact timing)"))
    }

    private fun notify(arg: String, env: ModEnv) {
        val t = Parser.words(arg)
        if (t.isEmpty()) { env.out("usage: notify <text>"); return }
        if (!Notifier.canPost(env.ctx)) { env.out("notifications are off for TermOS. run: perms notifications"); return }
        Notifier.post(env.ctx, "TermOS", t)
        env.out("notification sent.")
    }

    // -------------------------------------------------- permissions / keepalive
    private fun perms(arg: String, env: ModEnv) {
        val a = arg.trim().lowercase()
        if (a.isEmpty()) { env.out(Setup.table(env.ctx)); return }
        if (a == "all") {
            val next = Setup.startup.firstOrNull { Setup.pending(env.ctx, it) }
            if (next == null) { env.out("everything is set up."); return }
            env.out("fixing '${next.key}' first: " + Setup.fix(env.ctx, next, env.requestPerm))
            return
        }
        val id = Setup.Id.values().firstOrNull { it.key == a }
        if (id == null) { env.out("unknown item. try: " + Setup.Id.values().joinToString(" ") { it.key }); return }
        env.out(Setup.fix(env.ctx, id, env.requestPerm))
    }

    private fun keepalive(arg: String, env: ModEnv) {
        when (arg.trim()) {
            "on" -> { Store.put(env.ctx, "keepalive", "on"); KeepAlive.start(env.ctx); env.out("keep-alive on: TermOS stays running so timers, jobs and auto-lock stay reliable.") }
            "off" -> { Store.put(env.ctx, "keepalive", "off"); KeepAlive.stop(env.ctx); env.out("keep-alive off.") }
            "" -> env.out("keep-alive: " + (if (Config.keepalive(env.ctx)) "on" else "off") +
                "\nbattery exemption: " + (if (Setup.ok(env.ctx, Setup.Id.BATTERY)) "yes" else "no (run: perms battery)"))
            else -> env.out("usage: keepalive on|off")
        }
    }

    // --------------------------------------------------------- notifications
    private fun notifs(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        val sub = p.firstOrNull() ?: "ls"
        if (sub == "watch") {
            val v = p.getOrNull(1)
            if (v == "on" || v == "off") { Store.put(env.ctx, "notifwatch", v); env.out("notif echo $v (new notifications appear here while unlocked).") }
            else env.out("usage: notifs watch on|off")
            return
        }
        if (!Notifs.enabled(env.ctx)) { env.out("notification access is off. run: perms notifs"); return }
        val items = Notifs.items
        fun item(i: Int?) = if (i == null) null else items.getOrNull(i - 1)
        when (sub) {
            "ls" -> env.out(if (items.isEmpty()) "(no notifications, or the listener is still connecting)" else
                items.mapIndexed { i, n -> "%2d  %-12s %s".format(i + 1, n.app.take(12), "${n.title}: ${n.text}".take(80) + if (n.canReply) "  [reply]" else "") }.joinToString("\n"))
            "open" -> { val n = item(p.getOrNull(1)?.toIntOrNull()); if (n == null) env.out("usage: notifs open <n>") else { Notifs.open(n.key); env.out("opened.") } }
            "clear" -> {
                if (p.getOrNull(1) == "all") { Notifs.clear(null); env.out("cleared all.") }
                else { val n = item(p.getOrNull(1)?.toIntOrNull()); if (n == null) env.out("usage: notifs clear <n|all>") else { Notifs.clear(n.key); env.out("cleared.") } }
            }
            "reply" -> {
                val n = item(p.getOrNull(1)?.toIntOrNull())
                val text = p.drop(2).joinToString(" ")
                when {
                    n == null || text.isEmpty() -> env.out("usage: notifs reply <n> <text>")
                    !n.canReply -> env.out("that notification has no reply action.")
                    else -> env.out(if (Notifs.reply(env.ctx, n.key, text)) "reply sent." else "reply failed.")
                }
            }
            else -> env.out("usage: notifs [open <n>|clear <n|all>|reply <n> <text>|watch on|off]")
        }
    }

    // ------------------------------------------------------ contacts and sms
    private fun need(env: ModEnv, vararg perms: String): Boolean {
        val missing = perms.filter { !Comms.has(env.ctx, it) }
        if (missing.isEmpty()) return true
        val asked = env.requestPerm(missing.joinToString(","))
        env.out(if (asked) "permission needed: allow the popup, then run the command again."
                else "permission needed: allow it in Android app settings (or run: perms contacts | perms sms).")
        return false
    }

    private fun contacts(arg: String, env: ModEnv) {
        if (!need(env, Comms.P_CONTACTS)) return
        val l = Comms.contacts(env.ctx, Parser.words(arg))
        env.out(if (l.isEmpty()) "no contacts match." else l.joinToString("\n") { "%-24s %s".format(it.name.take(24), it.number) })
    }

    private fun call(arg: String, env: ModEnv): Boolean {
        val w = Parser.words(arg)
        if (w.isEmpty() || PhoneNum.looksLikeNumber(w)) return false
        if (!need(env, Comms.P_CONTACTS)) return true
        val c = Comms.contacts(env.ctx, w, 1).firstOrNull()
        if (c == null) { env.out("no contact matches '$w'."); return true }
        env.out("calling ${c.name} (${c.number})...")
        env.ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun sendNow(env: ModEnv, number: String, text: String) {
        try { Comms.send(env.ctx, number, text); env.out("sms sent to $number.") } catch (e: Exception) { env.out("sms failed: ${e.message}") }
    }

    private fun showMsgs(env: ModEnv, l: List<Msg>) {
        lastMsgs = l
        val f = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        env.out(if (l.isEmpty()) "(no messages)" else l.mapIndexed { i, m ->
            "%2d  %-14s %s  %s".format(i + 1, Comms.nameFor(env.ctx, m.addr).take(14), f.format(Date(m.date)), m.body.replace("\n", " ").take(50))
        }.joinToString("\n"))
    }

    private fun sms(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        val sub = p.firstOrNull() ?: "ls"
        when {
            sub == "send" -> {
                val yes = "-y" in p
                val rest = p.drop(1).filter { it != "-y" }
                if (rest.size < 2) { env.out("usage: sms send <name|number> <text> [-y]"); return }
                if (!need(env, Comms.P_SEND_SMS)) return
                val to = Comms.resolve(env.ctx, rest[0])
                if (to == null) { env.out("no contact matches '${rest[0]}'. (contacts need permission: perms contacts)"); return }
                val text = rest.drop(1).joinToString(" ")
                if (yes) sendNow(env, to.number, text)
                else { pendingSms = Triple(to.number, to.name, text); env.out("to ${to.name} (${to.number}): \"$text\"\nsend? answer y/n") }
            }
            sub == "read" -> {
                val m = p.getOrNull(1)?.toIntOrNull()?.let { lastMsgs.getOrNull(it - 1) }
                if (m == null) env.out("no such message. run 'sms' first, then 'sms read <n>'.")
                else env.out("from: ${Comms.nameFor(env.ctx, m.addr)}\ndate: ${Date(m.date)}\n\n${m.body}")
            }
            sub == "from" -> {
                if (!need(env, Comms.P_READ_SMS)) return
                val who = p.drop(1).joinToString(" ")
                val num = Comms.resolve(env.ctx, who)?.number
                if (num == null) env.out("no contact matches '$who'.") else showMsgs(env, Comms.inbox(env.ctx, 20, num))
            }
            sub == "ls" || sub.toIntOrNull() != null -> {
                if (!need(env, Comms.P_READ_SMS)) return
                showMsgs(env, Comms.inbox(env.ctx, sub.toIntOrNull() ?: 10))
            }
            else -> env.out("usage: sms [n] | sms from <who> | sms read <n> | sms send <who> <text> [-y]")
        }
    }

    // ------------------------------------------------------------- scripts
    private fun script(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        val name = p.getOrNull(1) ?: ""
        when (p.firstOrNull() ?: "ls") {
            "ls" -> env.out(Scripts.list(env.ctx).let { if (it.isEmpty()) "(no scripts) create one: script new <name>" else it.joinToString("\n") })
            "new", "edit" -> {
                if (!Scripts.valid(name)) { env.out("usage: script new <name>  (a-z 0-9 _ -, starts with a letter)"); return }
                editor = Editor(name, Scripts.read(env.ctx, name)?.toMutableList() ?: mutableListOf())
                env.out("editing '$name'. type lines; '.' saves, '.cancel' aborts, '.list' shows, '.undo' removes the last line.")
            }
            "show" -> env.out(Scripts.read(env.ctx, name)?.mapIndexed { i, l -> "%2d  %s".format(i + 1, l) }?.joinToString("\n") ?: "no such script.")
            "rm" -> env.out(if (Scripts.valid(name) && Scripts.delete(env.ctx, name)) "removed." else "no such script.")
            "add" -> {
                val line = arg.trim().removePrefix("add").trim().removePrefix(name).trim()
                if (!Scripts.valid(name) || line.isEmpty()) env.out("usage: script add <name> <line>")
                else { Scripts.save(env.ctx, name, (Scripts.read(env.ctx, name) ?: emptyList()) + line); env.out("added.") }
            }
            "run" -> runScript(name, p.drop(2), env)
            else -> env.out("usage: script [ls|new <n>|edit <n>|show <n>|add <n> <line>|rm <n>|run <n> [args]]")
        }
    }

    private fun tsh(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        if (p.isEmpty()) { env.out("usage: tsh <script> [args]"); return }
        runScript(p[0], p.drop(1), env)
    }

    private fun refusal(env: ModEnv, cmd: String): String? {
        val word = Parser.tokens(cmd).firstOrNull() ?: return null
        val name = Registry.find(word)?.name ?: word
        return when {
            name == "tsh" || name == "script" -> "nested scripts are not allowed"
            name == "sudo" || name == "su" || name == "passwd" -> "'$name' is not allowed in scripts"
            env.headless && !Headless.ok(word) -> "'$name' is not allowed in background jobs"
            else -> null
        }
    }

    private fun runScript(name: String, args: List<String>, env: ModEnv) {
        val lines = Scripts.read(env.ctx, name)
        if (lines == null) { env.out("no such script '$name'. try: script ls"); return }
        scriptJob?.cancel()
        val o: (String) -> Unit = if (env.headless) Session::background else env.out
        scriptJob = env.scope.launch {
            val host = object : ScriptHost {
                override fun exec(cmd: String): Pair<Boolean, String> {
                    val why = refusal(env, cmd)
                    if (why != null) return false to "error: $why"
                    return env.exec(cmd)
                }
                override fun show(s: String) = o(s)
                override fun builtins() = Builtins.map(env.ctx)
                override suspend fun sleep(sec: Long) = delay(minOf(sec, 3600L) * 1000)
            }
            val r = Script.run(lines, args, host)
            if (r.stopped != null) o("script '$name': ${r.stopped}")
        }
    }

    // ---------------------------------------------------------------- jobs
    private fun describe(j: SchedJob) = when (j.kind) {
        "at" -> "daily at ${j.spec}"
        "once" -> "once at ${j.spec}"
        else -> "every ${j.spec}"
    }

    private fun addJob(kind: String, arg: String, env: ModEnv) {
        val args = Parser.tokens(arg)
        val id = Jobs.nextId(env.ctx)
        val job = Schedule.create(id, kind, args, System.currentTimeMillis())
        if (job == null) {
            env.out(if (kind == "at") "usage: at HH:mm [once] <command>   e.g. at 07:30 tsh morning"
                    else "usage: every <dur, 1m or more> <command>   e.g. every 30m notify stretch")
            return
        }
        val bad = Parser.statements(job.cmd).map { Parser.tokens(it).firstOrNull() ?: "" }.firstOrNull { !Headless.ok(it) }
        if (bad != null) {
            env.out("error: jobs run in the background; '$bad' is not allowed there. allowed: ${Headless.allowed.sorted().joinToString(" ")}")
            return
        }
        Jobs.add(env.ctx, job)
        Alarms.set(env.ctx, "job", id, "", job.next)
        env.out("job #$id ${describe(job)} (next ${fmtTime(job.next)}): ${job.cmd}" +
            if (Alarms.exactOk(env.ctx)) "" else "\n(tip: 'perms alarms' makes jobs exact)")
    }

    private fun job(arg: String, env: ModEnv) {
        val p = Parser.tokens(arg)
        val jobs = Jobs.load(env.ctx)
        val id = p.getOrNull(1)?.toIntOrNull()
        when (p.firstOrNull() ?: "ls") {
            "ls" -> env.out(if (jobs.isEmpty()) "(no jobs) add one: at 07:30 tsh morning | every 30m notify stretch"
                            else jobs.joinToString("\n") { "#${it.id}  ${describe(it)}  next ${fmtTime(it.next)}  ${it.cmd}" })
            "rm" -> if (id == null || jobs.none { it.id == id }) env.out("usage: job rm <id>")
                    else { Alarms.cancel(env.ctx, "job", id); Jobs.remove(env.ctx, id); env.out("job #$id removed.") }
            "run" -> { val j = jobs.firstOrNull { it.id == id }; if (j == null) env.out("usage: job run <id>") else { env.out("running job #${j.id}..."); Session.shell.runHeadless(j.cmd) } }
            else -> env.out("usage: job [ls|rm <id>|run <id>]")
        }
    }
}
