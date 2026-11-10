package dev.termos

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Date

class Shell(
    app: Context,
    private val scope: CoroutineScope,
    private val emit: (String) -> Unit,
    private val clearScreen: () -> Unit,
    private val onState: () -> Unit
) {
    enum class Mode { SET1, SET2, LOGIN, SUDO, WIZ, NORMAL }

    private val ctx: Context = app.applicationContext   // never an Activity: no leaks
    var permRequester: ((String) -> Unit)? = null

    var mode = if (Auth.hasPassword(ctx)) Mode.LOGIN else Mode.SET1
        private set
    val secret get() = mode == Mode.SET1 || mode == Mode.SET2 || mode == Mode.LOGIN || mode == Mode.SUDO

    private var out: (String) -> Unit = emit
    private var firstBoot = mode == Mode.SET1
    private var root = false
    private var tmp = ""
    private var pending: String? = null
    private var sudoUntil = 0L
    private var wizStep = 1
    private var pendingPlan: List<String>? = null
    private var job: Job? = null

    private val history = mutableListOf<String>()
    private val aliases = LinkedHashMap<String, String>()
    private val apps = AppIndex(ctx)

    private data class T(val job: Job, val label: String, val endMs: Long)
    private val timers = LinkedHashMap<Int, T>()
    private var nextTimer = 0

    private var links: List<String> = emptyList()
    private var lastUrl = ""
    private val backStack = ArrayDeque<String>()

    init { loadHistory(); loadAliases() }

    private fun file(n: String) = File(ctx.filesDir, n)
    private fun now() = System.currentTimeMillis()

    fun historyList(): List<String> = history

    // ------------------------------------------------------------- prompt/UI
    fun prompt(): String = when (mode) {
        Mode.SET1 -> "new password: "
        Mode.SET2 -> "confirm password: "
        Mode.LOGIN -> "password: "
        Mode.SUDO -> "[sudo] password: "
        Mode.WIZ -> when (wizStep) {
            1 -> "theme [${Config.theme(ctx)}]> "
            2 -> "vim navigation? [y/N]> "
            else -> "Gemini API key (Enter=skip)> "
        }
        Mode.NORMAL -> when {
            Modules.promptOverride() != null -> Modules.promptOverride()!!
            pendingPlan != null -> "run plan? [y/N] "
            root -> "root@termos:~# "
            else -> "${Config.user(ctx)}@termos:~$ "
        }
    }

    private fun header(event: String?) {
        Look.header(ctx).forEach { emit(it) }
        emit(Look.stampLine(ctx, event))
        if (event == null) {
            val last = Store.get(ctx, "last_active")?.toLongOrNull()
            if (last != null) emit(Look.marked(Banner.TMARK, "last session closed: " + Look.stamp(ctx, last)))
        }
    }

    private fun loginStamp() {
        val now = System.currentTimeMillis()
        val last = Store.get(ctx, "last_login")?.toLongOrNull()
        emit(Look.marked(Banner.TMARK, "login: " + Look.stamp(ctx, now)))
        if (last != null) emit(Look.marked(Banner.TMARK, "last login: " + Look.stamp(ctx, last)))
        Store.put(ctx, "last_login", now.toString())
    }

    fun start() {
        header(null)
        if (mode == Mode.SET1) emit("first boot: create a password (6+ characters). it unlocks the terminal and authorises sudo.")
        else emit("terminal locked.")
    }

    fun lock(reason: String = "logged out") {
        mode = Mode.LOGIN
        root = false; sudoUntil = 0; pending = null; pendingPlan = null
        Modules.cancel()
        clearScreen()
        header(reason)
        emit("terminal locked.")
        onState()
    }

    fun onScreenOff() {
        if (Config.autolock(ctx) && mode != Mode.LOGIN && mode != Mode.SET1 && mode != Mode.SET2) lock("screen off")
    }

    /** ^C */
    fun interrupt() {
        Modules.cancel()
        when {
            mode == Mode.SUDO -> { pending = null; mode = Mode.NORMAL }
            mode == Mode.WIZ -> { mode = Mode.NORMAL; emit("setup skipped. run 'setup' anytime.") }
            mode == Mode.SET2 -> mode = Mode.SET1
            pendingPlan != null -> pendingPlan = null
            job?.isActive == true -> job?.cancel()
        }
        emit("^C")
        onState()
    }

    private val source = object : CompletionSource {
        override fun apps(): List<String> = this@Shell.apps.all().map { it.label }
        override fun aliases(): Collection<String> = this@Shell.aliases.keys
        override fun scripts(): List<String> = Scripts.list(ctx)
    }

    fun complete(text: String): List<String> =
        if (secret || mode == Mode.WIZ) emptyList() else Completer.complete(text, source)

    // ------------------------------------------------------------ line input
    fun onLine(raw: String) {
        when (mode) {
            Mode.SET1 -> if (raw.length < 6) emit("min 6 characters.") else { tmp = raw; mode = Mode.SET2 }
            Mode.SET2 -> {
                if (raw == tmp) {
                    Auth.setPassword(ctx, raw)
                    resetFails()
                    if (firstBoot) {
                        firstBoot = false
                        emit("password set.")
                        startWizard()
                    } else { mode = Mode.NORMAL; emit("password changed.") }
                } else { emit("mismatch, try again."); mode = Mode.SET1 }
                tmp = ""
            }
            Mode.LOGIN -> {
                val wait = lockWait()
                if (wait > 0) emit("too many attempts. wait ${wait}s.")
                else if (Auth.check(ctx, raw)) { resetFails(); mode = Mode.NORMAL; emit("access granted."); loginStamp(); motd(); tip(); Session.flushHeld() }
                else { fail(); emit("access denied. attempts: ${fails()}") }
            }
            Mode.SUDO -> {
                val cmd = pending ?: ""
                pending = null
                mode = Mode.NORMAL
                val wait = lockWait()
                if (wait > 0) emit("too many attempts. wait ${wait}s.")
                else if (Auth.check(ctx, raw)) { resetFails(); sudoUntil = now() + 5 * 60_000; execute(cmd, true) }
                else { fail(); emit("sudo: incorrect password") }
            }
            Mode.WIZ -> wizard(raw.trim())
            Mode.NORMAL -> normal(raw)
        }
        onState()
    }

    private fun fails() = Store.get(ctx, "fails")?.toIntOrNull() ?: 0
    private fun lastFail() = Store.get(ctx, "lastfail")?.toLongOrNull() ?: 0L
    private fun fail() { Store.put(ctx, "fails", (fails() + 1).toString()); Store.put(ctx, "lastfail", now().toString()) }
    private fun resetFails() { Store.put(ctx, "fails", "0") }
    private fun lockWait() = Lockout.remaining(fails(), lastFail(), now())

    private fun normal(raw: String) {
        if (Modules.intercept(raw, env())) return
        val line = raw.trim()
        pendingPlan?.let { plan ->
            pendingPlan = null
            when (line.lowercase()) {
                "y", "yes" -> { plan.forEach { emit(">> $it"); execute(it, false) }; return }
                "n", "no", "" -> { emit("plan cancelled."); return }
                else -> {}
            }
        }
        if (line.isEmpty()) return
        execute(line, false)
    }

    // ------------------------------------------------------------- wizard
    private fun startWizard() {
        mode = Mode.WIZ; wizStep = 1
        emit("quick setup (Enter keeps the default, ^C skips)\nthemes: ${Themes.names().joinToString(", ")}")
    }

    private fun wizard(s: String) {
        when (wizStep) {
            1 -> {
                if (s.isNotEmpty()) {
                    if (Themes.get(s) != null) { Store.put(ctx, "theme", s.lowercase()); emit("theme -> ${s.lowercase()}") }
                    else emit("unknown theme '$s' (kept ${Config.theme(ctx)})")
                }
                wizStep = 2
            }
            2 -> { Store.put(ctx, "vim", if (s.lowercase().startsWith("y")) "on" else "off"); wizStep = 3 }
            else -> {
                if (s.isNotEmpty()) { Store.put(ctx, "gemini_key", s); emit("key saved.") }
                mode = Mode.NORMAL
                emit("setup complete. try: help | guide start | lockscreen")
                tip()
            }
        }
    }

    private fun androidLockOn(): Boolean =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    private fun tip() {
        if (androidLockOn()) emit("tip: Android screen lock is ON. run 'lockscreen' to make TermOS your only lock.")
    }

    private fun batteryPct(): Int =
        (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun openTodos(): Int =
        file("todo.txt").takeIf { it.exists() }?.readLines()?.count { it.startsWith("[ ]") } ?: 0

    private fun motd() = emit("battery ${batteryPct()}%  |  ${openTodos()} open todo(s)  |  ${timers.size} timer(s)  |  help  |  look = visuals")

    // ------------------------------------------------------------ history/alias
    private fun loadHistory() {
        file("history.txt").takeIf { it.exists() }?.readLines()?.takeLast(200)?.let { history.addAll(it) }
    }

    private fun remember(line: String) {
        val r = Parser.redact(line)
        if (history.lastOrNull() == r) return
        history.add(r)
        if (history.size > 200) { history.removeAt(0); file("history.txt").writeText(history.joinToString("\n") + "\n") }
        else file("history.txt").appendText(r + "\n")
    }

    private fun loadAliases() {
        val f = file("aliases.txt")
        if (f.exists()) f.readLines().forEach { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { aliases[it[0]] = it[1] } }
        else { aliases["ll"] = "ls"; aliases["cls"] = "clear" }
    }

    private fun saveAliases() = file("aliases.txt").writeText(aliases.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")

    // ------------------------------------------------------------ dispatcher
    private fun execute(line: String, elevated: Boolean) {
        var l = line.trim()
        Parser.expandHistory(l, history)?.let {
            if (it.isEmpty()) { emit("event not found: $l"); return }
            emit(it); l = it
        }
        if (!elevated) remember(l)
        for (stmt in Parser.statements(l)) pipeline(Parser.expandAlias(stmt, aliases), elevated)
    }

    private fun pipeline(stmt: String, elevated: Boolean) {
        val stages = Parser.pipes(stmt)
        if (stages.isEmpty()) return
        val filters = stages.drop(1)
        for (f in filters) {
            val n = Parser.tokens(f).firstOrNull()
            if (n == null || n !in Filters.names) {
                emit("pipe: '${n ?: ""}' is not a filter (${Filters.names.joinToString(" ")})")
                return
            }
        }
        val base = out
        val sink: (String) -> Unit = if (filters.isEmpty()) base else { text ->
            var t = text
            for (f in filters) { val tk = Parser.tokens(f); t = Filters.apply(tk[0], tk.drop(1), t) }
            if (t.isNotEmpty()) base(t)
        }
        val prev = out
        out = sink
        try { run(stages[0], elevated) } finally { out = prev }
    }

    private fun run(line: String, elevated: Boolean) {
        val parts = line.trim().split(Regex("\\s+"), 2)
        val rawCmd = parts[0]
        val arg = parts.getOrElse(1) { "" }.trim()
        val def = Registry.find(rawCmd)
        val cmd = def?.name ?: rawCmd
        val isRoot = root || elevated

        if (def?.root == true && !isRoot) { out("permission denied. try: sudo $line"); return }

        when (cmd) {
            "help" -> help(arg)
            "guide" -> out(Guide.page(arg))
            "cmds" -> cmds()
            "which" -> which(arg)
            "setup" -> startWizard()
            "clear" -> clearScreen()
            "ls" -> listApps(arg)
            "open" -> openApp(arg)
            "uninstall" -> uninstall(arg)
            "sudo" -> if (arg == "-k") { sudoUntil = 0; out("sudo timestamp cleared.") } else sudo(arg)
            "su" -> when {
                elevated -> { root = true; out("root shell. 'exit' to drop.") }
                root -> out("already root.")
                else -> sudo("su")
            }
            "exit" -> if (root) { root = false; out("dropped root.") } else out("use 'lock' to lock.")
            "lock" -> lock()
            "lockscreen" -> lockscreen()
            "passwd" -> { mode = Mode.SET1; out("choose a new password.") }
            "whoami" -> out(if (isRoot) "root" else Config.user(ctx))
            "date" -> out(Date().toString())
            "echo" -> out(Parser.words(arg))
            "history" -> if (arg == "clear") { history.clear(); file("history.txt").delete(); out("history cleared.") }
                         else out(history.mapIndexed { i, h -> "${i + 1}  $h" }.joinToString("\n"))
            "battery" -> out("battery: ${batteryPct()}%")
            "neofetch" -> neofetch()
            "df" -> df()
            "free" -> free()
            "settings" -> sys(Settings.ACTION_SETTINGS)
            "wifi" -> sys(Settings.ACTION_WIFI_SETTINGS)
            "bt" -> sys(Settings.ACTION_BLUETOOTH_SETTINGS)
            "copy" -> copy(Parser.words(arg))
            "paste" -> paste()
            "call" -> if (!Modules.dispatch("call", arg, env())) call(arg)
            "note" -> note(Parser.words(arg))
            "notes" -> notes(arg)
            "calc" -> calc(arg)
            "timer" -> Modules.dispatch("timer", arg, env())
            "todo" -> todo(arg)
            "torch" -> torch(arg)
            "browse" -> browse(arg)
            "go" -> go(arg)
            "back" -> back()
            "ai" -> ai(arg, isRoot)
            "linux" -> linux(arg)
            "alias" -> alias(arg)
            "unalias" -> if (aliases.remove(arg.trim()) != null) { saveAliases(); out("removed.") } else out("no such alias.")
            "config" -> config(arg, isRoot)
            "theme" -> config("theme $arg", isRoot)
            "vim" -> config("vim $arg", isRoot)
            else -> {
                if (def != null && Modules.dispatch(cmd, arg, env())) return
                val s = Suggest.closest(rawCmd, Registry.names() + aliases.keys)
                out("$rawCmd: command not found." + (if (s != null) " did you mean '$s'?" else "") + "  (try: help)")
            }
        }
    }

    private var headless = false

    private fun env(): ModEnv = ModEnv(
        ctx, scope, { s -> out(s) }, emit, root, headless,
        { l -> execCapture(l) },
        { p -> val r = permRequester; if (r != null) { r(p); true } else false }
    )

    private val asyncCmds = setOf("browse", "go", "back", "ai", "linux")

    /** Run a line and capture its output; ok = no error-looking line. Async commands print normally. */
    fun execCapture(line: String, elevated: Boolean = false): Pair<Boolean, String> {
        val first = Registry.find(Parser.tokens(line).firstOrNull() ?: "")?.name
        if (first != null && first in asyncCmds) { execute(line, elevated); return true to "" }
        val sb = StringBuilder()
        val prev = out
        out = { s -> if (sb.isNotEmpty()) sb.append('\n'); sb.append(s) }
        try {
            for (stmt in Parser.statements(line)) pipeline(Parser.expandAlias(stmt, aliases), elevated)
        } finally { out = prev }
        val text = sb.toString()
        return text.lines().none { LineStyle.classify(it) == Kind.ERROR } to text
    }

    /** Scheduled jobs: only Headless.allowed commands, output held while locked. */
    fun runHeadless(line: String) {
        headless = true
        try {
            for (stmt in Parser.statements(line)) {
                val first = Parser.tokens(stmt).firstOrNull() ?: continue
                if (!Headless.ok(first)) { Session.background("job: '$first' is not allowed in background jobs"); continue }
                val text = execCapture(stmt).second
                if (text.isNotEmpty()) Session.background(text)
            }
        } finally { headless = false }
    }

    private fun sudo(arg: String) {
        if (arg.isEmpty()) { out("usage: sudo <command>"); return }
        if (root || now() < sudoUntil) execute(arg, true) else { pending = arg; mode = Mode.SUDO }
    }

    // ------------------------------------------------------------------ help
    private fun help(arg: String) {
        if (arg.isEmpty()) out(HelpFmt.overview(VERSION, Registry.all))
        else {
            val c = Registry.find(arg) ?: run { out("no help for '$arg'. try: help  or  help ls"); return }
            out(HelpFmt.one(c))
        }
    }

    private fun avail(c: Cmd): String = when (c.name) {
        "ai" -> if (Store.get(ctx, "gemini_key").isNullOrEmpty()) "needs key" else "ready"
        "linux" -> when {
            !Linux.installed(ctx) -> "needs Termux"
            !Linux.hasPerm(ctx) -> "needs permission"
            else -> "ready"
        }
        "torch" -> if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) "ready" else "no flash"
        "notifs" -> if (Notifs.enabled(ctx)) "ready" else "needs access"
        "contacts" -> if (Comms.has(ctx, Comms.P_CONTACTS)) "ready" else "needs permission"
        "sms" -> if (Comms.has(ctx, Comms.P_READ_SMS) && Comms.has(ctx, Comms.P_SEND_SMS)) "ready" else "needs permission"
        "perms" -> if (Setup.startup.all { Setup.ok(ctx, it) }) "all set" else "items missing"
        "lockscreen" -> if (androidLockOn()) "android lock ON" else "ready (lock off)"
        else -> if (c.root) "sudo" else "ready"
    }

    private fun cmds() {
        out(HelpFmt.cmdsTable(Registry.all) { avail(it) })
    }

    private fun which(arg: String) {
        val n = arg.trim()
        if (n.isEmpty()) { out("usage: which <command>"); return }
        val a = aliases[n]
        val d = Registry.find(n)
        out(when {
            a != null -> "$n: alias for '$a'"
            d != null -> "$n: builtin (${d.cat.label})" + (if (d.name != n) ", alias of ${d.name}" else "") + (if (d.root) ", needs sudo" else "")
            else -> "$n: not found"
        })
    }

    private fun alias(arg: String) {
        if (arg.isEmpty()) {
            out(if (aliases.isEmpty()) "(no aliases)" else aliases.entries.joinToString("\n") { "alias ${it.key}='${it.value}'" })
            return
        }
        val eq = arg.indexOf('=')
        if (eq < 1) { out("usage: alias name='command'"); return }
        val name = arg.substring(0, eq).trim()
        val value = Parser.words(arg.substring(eq + 1))
        when {
            !Regex("^[a-z][a-z0-9_-]*$").matches(name) -> out("invalid alias name (a-z 0-9 _ -)")
            Registry.find(name) != null -> out("'$name' is a builtin; pick another name")
            value.isEmpty() -> out("usage: alias name='command'")
            else -> { aliases[name] = value; saveAliases(); out("alias $name='$value'") }
        }
    }

    // ---------------------------------------------------------------- config
    private fun config(arg: String, isRoot: Boolean) {
        val p = arg.trim().split(Regex("\\s+"), 2)
        val k = p[0]
        val v = p.getOrElse(1) { "" }.trim()
        when (k) {
            "" -> out(
                "theme    : ${Config.theme(ctx)}\nvim      : ${if (Config.vim(ctx)) "on" else "off"}\n" +
                    "font     : ${Config.font(ctx)}\nautolock : ${if (Config.autolock(ctx)) "on" else "off"}\n" +
                    "username : ${Config.user(ctx)}\nautofocus: ${if (Config.autofocus(ctx)) "on" else "off"}\n" +
                    "keyboard : ${Config.keyboard(ctx)}\n\n" +
                    "config theme <name> | vim on|off | font <8-30> | autolock on|off | autofocus on|off | keyboard term|system | username <name>   (more: look)"
            )
            "theme" -> when {
                v.isEmpty() -> out("themes: ${Themes.names().joinToString(", ")}\ncurrent: ${Config.theme(ctx)}")
                Themes.get(v) == null -> out("unknown theme. available: ${Themes.names().joinToString(", ")}")
                else -> { Store.put(ctx, "theme", v.lowercase()); out("theme -> ${v.lowercase()}") }
            }
            "vim" -> if (v == "on" || v == "off") {
                Store.put(ctx, "vim", v)
                out(if (v == "on") "vim mode on. ESC = normal mode, i = insert. see: guide vim" else "vim mode off.")
            } else out("usage: vim on|off")
            "font" -> {
                val n = v.toIntOrNull()
                if (n == null || n !in 8..30) out("usage: config font <8-30>") else { Store.put(ctx, "font", n.toString()); out("font -> $n") }
            }
            "autofocus" -> if (v == "on" || v == "off") { Store.put(ctx, "autofocus", v); out("autofocus $v") } else out("usage: config autofocus on|off")
            "keyboard" -> when (v) {
                "term", "system" -> {
                    Store.put(ctx, "keyboard", "term")
                    Store.put(ctx, "keyboard.collapsed", "")
                    out("keyboard -> term (built-in only; system IME is disabled)")
                }
                else -> out("usage: config keyboard term   (in-app keyboard only; system keyboard is never shown)")
            }
            "autolock" -> when {
                v != "on" && v != "off" -> out("usage: config autolock on|off")
                v == "off" && !isRoot -> out("permission denied. try: sudo config autolock off")
                else -> { Store.put(ctx, "autolock", v); out("autolock $v") }
            }
            "username" -> if (Regex("^[a-z][a-z0-9_-]{0,15}$").matches(v)) { Store.put(ctx, "user", v); out("username -> $v") }
                          else out("usage: config username <a-z, 0-9, _ -, max 16>")
            else -> out("unknown option '$k'. try: config")
        }
    }

    private fun lockscreen() {
        if (androidLockOn()) {
            val hint = SettingsNav.openLockScreen(ctx)
            out("Android screen lock: ON (a second lock before TermOS).\n$hint\n(see: guide lock)")
        } else out("Android screen lock: OFF. TermOS password is your only lock.\n(autolock on screen-off: ${if (Config.autolock(ctx)) "on" else "off"})")
    }

    // ----------------------------------------------------------------- linux
    private fun linux(arg: String) {
        val p = arg.split(Regex("\\s+"), 2)
        val sub = p[0]
        val rest = p.getOrElse(1) { "" }.trim()
        if (sub == "guide" || sub == "help") { out(Guide.page("linux")); return }
        if (!Linux.installed(ctx)) { out("Termux is not installed. see: guide linux"); return }
        if (sub == "shell") {
            ctx.packageManager.getLaunchIntentForPackage(Linux.TERMUX)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        if (!Linux.hasPerm(ctx)) {
            val r = permRequester
            if (r != null) { r(Linux.PERM); out("permission requested. allow it, then run the command again.") }
            else out("grant the Termux RUN_COMMAND permission to TermOS in Android app settings.")
            return
        }
        val cmd = when (sub) {
            "", "ubuntu" -> "proot-distro login ubuntu"
            "setup" -> "pkg update -y && pkg install -y proot-distro && proot-distro install ubuntu"
            "run" -> rest
            "exec" -> if (rest.isEmpty()) "" else "proot-distro login ubuntu -- bash -lc " + Linux.q(rest)
            else -> { out("usage: linux [ubuntu|shell|setup|run <cmd>|exec <cmd>|guide]"); return }
        }
        if (cmd.isBlank()) { out("usage: linux $sub <command>"); return }
        val err = Linux.run(ctx, cmd)
        out(if (err != null) "linux error: $err\nhint: Termux needs allow-external-apps=true (guide linux)" else "opening Termux session...")
    }

    // ------------------------------------------------------------------ apps
    private fun listApps(filter: String) {
        val l = apps.list(Parser.words(filter))
        if (l.isEmpty()) { out("no apps found."); return }
        out(l.mapIndexed { i, a -> "%3d  %-28s %s".format(i + 1, a.label.take(28), a.pkg) }.joinToString("\n"))
    }

    private fun openApp(arg: String) {
        if (arg.isEmpty()) { out("usage: open <name|number>"); return }
        val t = apps.resolve(Parser.words(arg)) ?: run { out("no app matches '$arg'"); return }
        val i = ctx.packageManager.getLaunchIntentForPackage(t.pkg)
        if (i == null) out("cannot launch ${t.label}") else { out("launching ${t.label}..."); ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun uninstall(arg: String) {
        val t = apps.resolve(Parser.words(arg)) ?: run { out("usage: sudo uninstall <name|number>"); return }
        out("uninstalling ${t.label} (system dialog will confirm)...")
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${t.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sys(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    // ---------------------------------------------------------------- system
    private fun neofetch() = out(
        """
        os      : TermOS $VERSION on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        host    : ${Build.MANUFACTURER} ${Build.MODEL}
        cpu abi : ${Build.SUPPORTED_ABIS.firstOrNull()}
        apps    : ${apps.all().size}
        theme   : ${Config.theme(ctx)}   vim: ${if (Config.vim(ctx)) "on" else "off"}
        android lock : ${if (androidLockOn()) "on" else "off"}
        termux  : ${if (Linux.installed(ctx)) "installed" else "not installed"}
        """.trimIndent()
    )

    private fun df() {
        val s = StatFs(Environment.getDataDirectory().path)
        out("storage: %.1f GB free of %.1f GB".format(s.availableBytes / 1e9, s.totalBytes / 1e9))
    }

    private fun free() {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        out("ram    : %d MB free of %d MB\ntermos : %d MB heap".format(mi.availMem / 1048576, mi.totalMem / 1048576, (rt.totalMemory() - rt.freeMemory()) / 1048576))
    }

    private fun copy(text: String) {
        if (text.isEmpty()) { out("usage: copy <text>"); return }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("termos", text))
        out("copied.")
    }

    private fun paste() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        out(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: "(clipboard empty)")
    }

    private fun call(num: String) {
        if (num.isEmpty()) { out("usage: call <number>"); return }
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun note(text: String) {
        if (text.isEmpty()) { out("usage: note <text>"); return }
        file("notes.txt").appendText(text + "\n"); out("saved.")
    }

    private fun notes(arg: String) {
        if (arg == "clear") { file("notes.txt").delete(); out("notes cleared."); return }
        out(file("notes.txt").takeIf { it.exists() }?.readText()?.trimEnd() ?: "(no notes)")
    }

    // ----------------------------------------------------------------- tools
    private fun calc(arg: String) {
        if (arg.isEmpty()) { out("usage: calc <expression>"); return }
        try { out(Calc.fmt(Calc.eval(Parser.words(arg)))) } catch (e: Exception) { out("calc error: ${e.message}") }
    }

    private fun timer(arg: String) {
        val p = Parser.tokens(arg)
        if (p.isEmpty()) {
            out(if (timers.isEmpty()) "no timers." else timers.entries.joinToString("\n") {
                "#${it.key}  ${it.value.label.ifEmpty { "timer" }}  ${Dur.fmt((it.value.endMs - now()) / 1000)} left"
            })
            return
        }
        if (p[0] == "cancel") {
            val id = p.getOrNull(1)?.toIntOrNull()
            val t = id?.let { timers.remove(it) }
            if (t == null) out("usage: timer cancel <n>") else { t.job.cancel(); out("timer #$id cancelled.") }
            return
        }
        val sec = Dur.parse(p[0])
        if (sec == null || sec <= 0 || sec > 86_400) { out("usage: timer <90s|5m|1h30m> [label]  (max 24h)"); return }
        val label = p.drop(1).joinToString(" ")
        val id = ++nextTimer
        val j = scope.launch {
            delay(sec * 1000)
            timers.remove(id)
            emit("timer #$id done${if (label.isEmpty()) "" else ": $label"}")
            buzz()
        }
        timers[id] = T(j, label, now() + sec * 1000)
        out("timer #$id set for ${Dur.fmt(sec)}.")
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        try {
            (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    private fun todo(arg: String) {
        val f = file("todo.txt")
        val items = if (f.exists()) f.readLines().filter { it.isNotBlank() }.toMutableList() else mutableListOf<String>()
        val p = arg.split(Regex("\\s+"), 2)
        val sub = p[0]
        val rest = p.getOrElse(1) { "" }.trim()
        fun save() = f.writeText(items.joinToString("\n") + if (items.isEmpty()) "" else "\n")
        val idx = rest.toIntOrNull()?.minus(1)
        when (sub) {
            "", "ls", "list" -> out(if (items.isEmpty()) "(nothing to do)" else items.mapIndexed { i, t -> "%2d  %s".format(i + 1, t) }.joinToString("\n"))
            "add" -> if (rest.isEmpty()) out("usage: todo add <text>") else { items.add("[ ] " + Parser.words(rest)); save(); out("added.") }
            "done" -> if (idx == null || idx !in items.indices) out("no such item") else { items[idx] = items[idx].replaceFirst("[ ]", "[x]"); save(); out("done.") }
            "rm" -> if (idx == null || idx !in items.indices) out("no such item") else { items.removeAt(idx); save(); out("removed.") }
            "clear" -> { items.removeAll { it.startsWith("[x]") }; save(); out("cleared finished items.") }
            else -> out("usage: todo [add <text>|done <n>|rm <n>|clear]")
        }
    }

    private fun torch(arg: String) {
        if (arg != "on" && arg != "off") { out("usage: torch on|off"); return }
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: run { out("no flashlight found."); return }
            cm.setTorchMode(id, arg == "on")
            out("torch $arg")
        } catch (e: Exception) { out("torch error: ${e.message}") }
    }

    // --------------------------------------------------------------- browser
    private fun browse(arg: String) {
        if (arg.isEmpty()) { out("usage: browse <url | search words>"); return }
        val a = Parser.words(arg)
        val url = when {
            a.startsWith("http") -> a
            Regex("^\\S+\\.\\S{2,}$").matches(a) -> "https://$a"
            else -> "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(a, "UTF-8")
        }
        load(url, true)
    }

    private fun load(url: String, push: Boolean) {
        out("fetching $url ...  (^C cancels)")
        val o = out
        job?.cancel()
        job = scope.launch {
            try {
                val page = withContext(Dispatchers.IO) { Browser.fetch(url) }
                if (push && lastUrl.isNotEmpty()) backStack.addLast(lastUrl)
                lastUrl = url
                links = page.links.map { it.second }
                o("== ${page.title} ==\n${page.text}\n\n-- links --\n" +
                    page.links.mapIndexed { i, l -> "[${i + 1}] ${l.first}" }.joinToString("\n") +
                    "\n\n(go <n> | back | browse <...>)")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { o("browse error: ${e.message}") }
        }
    }

    private fun go(arg: String) {
        val u = arg.toIntOrNull()?.let { links.getOrNull(it - 1) }
        if (u == null) out("usage: go <link number>") else load(u, true)
    }

    private fun back() {
        if (backStack.isEmpty()) out("nothing to go back to.") else load(backStack.removeLast(), false)
    }

    // -------------------------------------------------------------------- ai
    private fun ai(arg: String, isRoot: Boolean) {
        when {
            arg.isEmpty() -> out("usage: ai <question> | ai do <task> | sudo ai key <KEY> | ai model <name>")
            arg.startsWith("key ") -> {
                if (!isRoot) { out("permission denied. try: sudo ai $arg"); return }
                Store.put(ctx, "gemini_key", arg.removePrefix("key ").trim()); out("key saved.")
            }
            arg.startsWith("model ") -> { Store.put(ctx, "gemini_model", arg.removePrefix("model ").trim()); out("model set.") }
            else -> {
                val key = Store.get(ctx, "gemini_key")
                if (key.isNullOrEmpty()) { out("no key. run: sudo ai key <GEMINI_API_KEY>"); return }
                val model = Store.get(ctx, "gemini_model") ?: "gemini-2.5-flash"
                val planMode = arg.startsWith("do ")
                val q = if (planMode) arg.removePrefix("do ").trim() else arg
                out(if (planMode) "planning... (^C cancels)" else "thinking... (^C cancels)")
                val o = out
                job?.cancel()
                job = scope.launch {
                    try {
                        val r = withContext(Dispatchers.IO) { Ai.ask(key, model, q, if (planMode) Ai.planSystem() else Ai.CHAT_SYSTEM) }
                        if (!planMode) o(r)
                        else {
                            val plan = Plan.parse(r)
                            if (plan.isEmpty()) o("no safe plan.\n" + r.take(300))
                            else { pendingPlan = plan; o("plan:\n" + plan.joinToString("\n") { "  $it" } + "\nanswer y to run, n to cancel"); onState() }
                        }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { o("ai error: ${e.message}") }
                }
            }
        }
    }

    companion object {
        const val BANNER = "  _____                   ___  ____\n" +
            " |_   _|__ _ __ _ __ ___ / _ \\/ ___|\n" +
            "   | |/ _ \\ '__| '_ ` _ \\ | | \\___ \\\n" +
            "   | |  __/ |  | | | | | | |_| |___) |\n" +
            "   |_|\\___|_|  |_| |_| |_|\\___/|____/   v$VERSION\n"
    }
}
