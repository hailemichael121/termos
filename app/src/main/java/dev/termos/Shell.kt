package dev.termos

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Date

class Shell(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val out: (String) -> Unit,
    private val clearScreen: () -> Unit,
    private val onState: () -> Unit
) {
    enum class Mode { SET1, SET2, LOGIN, SUDO, NORMAL }

    var mode = if (Auth.hasPassword(ctx)) Mode.LOGIN else Mode.SET1
        private set
    val secret get() = mode != Mode.NORMAL

    private var root = false
    private var tmp = ""
    private var pending: String? = null
    private val history = mutableListOf<String>()
    private val apps = AppIndex(ctx)

    private var links: List<String> = emptyList()
    private var lastUrl = ""
    private val backStack = ArrayDeque<String>()

    fun historyList(): List<String> = history

    fun prompt(): String = when (mode) {
        Mode.SET1 -> "new password: "
        Mode.SET2 -> "confirm password: "
        Mode.LOGIN -> "password: "
        Mode.SUDO -> "[sudo] password: "
        Mode.NORMAL -> if (root) "root@termos:~# " else "${Config.user(ctx)}@termos:~$ "
    }

    fun start() {
        out(BANNER)
        if (mode == Mode.SET1) out("first boot: create a password. it unlocks the terminal and authorises sudo.")
        else out("terminal locked.")
    }

    fun lock() {
        mode = Mode.LOGIN
        root = false
        pending = null
        clearScreen()
        out("terminal locked.")
        onState()
    }

    /** Tab completion candidates (full replacement lines). */
    fun complete(text: String): List<String> {
        if (secret) return emptyList()
        val p = text.trimStart()
        if (!p.contains(' ')) return Registry.complete(p)
        val c = p.substringBefore(' ')
        val a = p.substringAfter(' ').lowercase()
        return when (Registry.find(c)?.name) {
            "open", "uninstall" -> apps.all().map { it.label }.filter { it.lowercase().startsWith(a) }.map { "$c $it" }
            "theme" -> Themes.names().filter { it.startsWith(a) }.map { "$c $it" }
            "guide" -> Guide.pages.keys.filter { it.startsWith(a) }.map { "$c $it" }
            "help" -> Registry.names().filter { it.startsWith(a) }.map { "$c $it" }
            else -> emptyList()
        }
    }

    fun onLine(raw: String) {
        when (mode) {
            Mode.SET1 -> {
                if (raw.length < 4) out("min 4 characters.") else { tmp = raw; mode = Mode.SET2 }
            }
            Mode.SET2 -> {
                if (raw == tmp) {
                    Auth.setPassword(ctx, raw)
                    mode = Mode.NORMAL
                    out("password set. type 'help' or 'guide start'.")
                    tip()
                } else {
                    out("mismatch, try again.")
                    mode = Mode.SET1
                }
                tmp = ""
            }
            Mode.LOGIN -> {
                if (Auth.check(ctx, raw)) { mode = Mode.NORMAL; out("access granted. type 'help'."); tip() }
                else out("access denied.")
            }
            Mode.SUDO -> {
                val cmd = pending ?: ""
                pending = null
                mode = Mode.NORMAL
                if (Auth.check(ctx, raw)) run(cmd, true) else out("sudo: incorrect password")
            }
            Mode.NORMAL -> {
                val line = raw.trim()
                if (line.isNotEmpty()) { history += line; run(line, false) }
            }
        }
        onState()
    }

    private fun androidLockOn(): Boolean =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    private fun tip() {
        if (androidLockOn()) out("tip: Android screen lock is ON. run 'lockscreen' to make TermOS your only lock.")
    }

    // ------------------------------------------------------------ dispatcher
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
            "clear" -> clearScreen()
            "ls" -> listApps(arg)
            "open" -> openApp(arg)
            "uninstall" -> uninstall(arg)
            "sudo" -> sudo(arg)
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
            "echo" -> out(arg)
            "history" -> out(history.mapIndexed { i, h -> "${i + 1}  $h" }.joinToString("\n"))
            "battery" -> battery()
            "neofetch" -> neofetch()
            "settings" -> sys(Settings.ACTION_SETTINGS)
            "wifi" -> sys(Settings.ACTION_WIFI_SETTINGS)
            "bt" -> sys(Settings.ACTION_BLUETOOTH_SETTINGS)
            "copy" -> copy(arg)
            "paste" -> paste()
            "call" -> call(arg)
            "note" -> note(arg)
            "notes" -> notes(arg)
            "browse" -> browse(arg)
            "go" -> go(arg)
            "back" -> back()
            "ai" -> ai(arg, isRoot)
            "linux" -> linux(arg)
            "config" -> config(arg, isRoot)
            "theme" -> config("theme $arg", isRoot)
            "vim" -> config("vim $arg", isRoot)
            else -> out("$rawCmd: command not found. try 'help' or: ai $line")
        }
    }

    private fun sudo(arg: String) {
        if (arg.isEmpty()) { out("usage: sudo <command>"); return }
        if (root) run(arg, true) else { pending = arg; mode = Mode.SUDO }
    }

    // ------------------------------------------------------------------ help
    private fun help(arg: String) {
        if (arg.isEmpty()) {
            val sb = StringBuilder("TermOS $VERSION commands (* = needs sudo)\n")
            Cat.values().forEach { cat ->
                val l = Registry.all.filter { it.cat == cat }
                if (l.isNotEmpty()) {
                    sb.append("\n[${cat.label}]\n")
                    l.forEach { sb.append("  %-10s %s%s\n".format(it.name, it.desc, if (it.root) " *" else "")) }
                }
            }
            sb.append("\nhelp <cmd> | guide <page> | cmds (availability) | TAB completes")
            out(sb.toString().trimEnd())
        } else {
            val c = Registry.find(arg) ?: run { out("no help for '$arg'"); return }
            val al = if (c.aliases.isEmpty()) "" else "\naliases: " + c.aliases.joinToString(", ")
            out("${c.name}\nusage  : ${c.usage}\n${c.desc}${if (c.root) "\nneeds sudo" else ""}$al")
        }
    }

    private fun avail(c: Cmd): String = when (c.name) {
        "ai" -> if (Store.get(ctx, "gemini_key").isNullOrEmpty()) "needs key" else "ready"
        "linux" -> when {
            !Linux.installed(ctx) -> "needs Termux"
            !Linux.hasPerm(ctx) -> "needs permission"
            else -> "ready"
        }
        "lockscreen" -> if (androidLockOn()) "android lock ON" else "ready (lock off)"
        else -> if (c.root) "sudo" else "ready"
    }

    private fun cmds() {
        out(Registry.all.joinToString("\n") { "%-10s %-17s %s".format(it.name, avail(it), it.usage.take(44)) } +
            "\n\nready = works now | sudo = ask password | needs ... = see 'guide'")
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
                    "username : ${Config.user(ctx)}\n\nconfig theme <name> | vim on|off | font <8-30> | autolock on|off | username <name>"
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
                if (n == null || n !in 8..30) out("usage: config font <8-30>")
                else { Store.put(ctx, "font", n.toString()); out("font -> $n") }
            }
            "autolock" -> when {
                v != "on" && v != "off" -> out("usage: config autolock on|off")
                v == "off" && !isRoot -> out("permission denied. try: sudo config autolock off")
                else -> { Store.put(ctx, "autolock", v); out("autolock $v") }
            }
            "username" -> if (Regex("^[a-z][a-z0-9_-]{0,15}$").matches(v)) {
                Store.put(ctx, "user", v); out("username -> $v")
            } else out("usage: config username <a-z, 0-9, _ -, max 16>")
            else -> out("unknown option '$k'. try: config")
        }
    }

    // ------------------------------------------------------------ lockscreen
    private fun lockscreen() {
        if (androidLockOn()) {
            out(
                "Android screen lock: ON (a second lock before TermOS).\n" +
                    "To make TermOS your only lock: Security -> Screen lock -> None (or Swipe).\n" +
                    "opening Security settings... (see: guide lock)"
            )
            sys(Settings.ACTION_SECURITY_SETTINGS)
        } else {
            out("Android screen lock: OFF. TermOS password is your only lock.\n(autolock on screen-off: ${if (Config.autolock(ctx)) "on" else "off"})")
        }
    }

    // ----------------------------------------------------------------- linux
    private fun linux(arg: String) {
        val p = arg.split(Regex("\\s+"), 2)
        val sub = p[0]
        val rest = p.getOrElse(1) { "" }.trim()
        if (sub == "guide" || sub == "help") { out(Guide.page("linux")); return }
        if (!Linux.installed(ctx)) { out("Termux is not installed. see: guide linux"); return }
        if (sub == "shell") {
            ctx.packageManager.getLaunchIntentForPackage(Linux.TERMUX)
                ?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        if (!Linux.hasPerm(ctx)) {
            (ctx as? Activity)?.requestPermissions(arrayOf(Linux.PERM), 11)
            out("permission requested. allow it, then run the command again.")
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
        val l = apps.list(filter)
        if (l.isEmpty()) { out("no apps found."); return }
        out(l.mapIndexed { i, a -> "%3d  %-28s %s".format(i + 1, a.label.take(28), a.pkg) }.joinToString("\n"))
    }

    private fun openApp(arg: String) {
        if (arg.isEmpty()) { out("usage: open <name|number>"); return }
        val t = apps.resolve(arg) ?: run { out("no app matches '$arg'"); return }
        val i = ctx.packageManager.getLaunchIntentForPackage(t.pkg)
        if (i == null) out("cannot launch ${t.label}")
        else { out("launching ${t.label}..."); ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun uninstall(arg: String) {
        val t = apps.resolve(arg) ?: run { out("usage: sudo uninstall <name|number>"); return }
        out("uninstalling ${t.label} (system dialog will confirm)...")
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${t.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sys(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    // ---------------------------------------------------------------- system
    private fun battery() {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        out("battery: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%")
    }

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

    private fun copy(text: String) {
        if (text.isEmpty()) { out("usage: copy <text>"); return }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("termos", text))
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

    private val notesFile get() = File(ctx.filesDir, "notes.txt")
    private fun note(text: String) {
        if (text.isEmpty()) { out("usage: note <text>"); return }
        notesFile.appendText(text + "\n"); out("saved.")
    }
    private fun notes(arg: String) {
        if (arg == "clear") { notesFile.delete(); out("notes cleared."); return }
        out(if (notesFile.exists()) notesFile.readText().trimEnd() else "(no notes)")
    }

    // --------------------------------------------------------------- browser
    private fun browse(arg: String) {
        if (arg.isEmpty()) { out("usage: browse <url | search words>"); return }
        val url = when {
            arg.startsWith("http") -> arg
            Regex("^\\S+\\.\\S{2,}$").matches(arg) -> "https://$arg"
            else -> "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(arg, "UTF-8")
        }
        load(url, true)
    }

    private fun load(url: String, push: Boolean) {
        out("fetching $url ...")
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) { Browser.fetch(url) }
                if (push && lastUrl.isNotEmpty()) backStack.addLast(lastUrl)
                lastUrl = url
                links = page.links.map { it.second }
                out(
                    "== ${page.title} ==\n${page.text}\n\n-- links --\n" +
                        page.links.mapIndexed { i, l -> "[${i + 1}] ${l.first}" }.joinToString("\n") +
                        "\n\n(go <n> | back | browse <...>)"
                )
            } catch (e: Exception) {
                out("browse error: ${e.message}")
            }
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
            arg.isEmpty() -> out("usage: ai <question> | sudo ai key <GEMINI_KEY> | ai model <name>")
            arg.startsWith("key ") -> {
                if (!isRoot) { out("permission denied. try: sudo ai $arg"); return }
                Store.put(ctx, "gemini_key", arg.removePrefix("key ").trim()); out("key saved.")
            }
            arg.startsWith("model ") -> {
                Store.put(ctx, "gemini_model", arg.removePrefix("model ").trim()); out("model set.")
            }
            else -> {
                val key = Store.get(ctx, "gemini_key")
                if (key.isNullOrEmpty()) { out("no key. run: sudo ai key <GEMINI_API_KEY>"); return }
                val model = Store.get(ctx, "gemini_model") ?: "gemini-2.5-flash"
                out("thinking...")
                scope.launch { out(withContext(Dispatchers.IO) { Ai.ask(key, model, arg) }) }
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
