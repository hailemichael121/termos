package dev.termos

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

    // browser state
    private var links: List<String> = emptyList()
    private var lastUrl = ""
    private val backStack = ArrayDeque<String>()

    /** Commands that need root (sudo / su). */
    private val priv = setOf("settings", "uninstall", "passwd", "wipe", "su")

    fun prompt(): String = when (mode) {
        Mode.SET1 -> "new password: "
        Mode.SET2 -> "confirm password: "
        Mode.LOGIN -> "password: "
        Mode.SUDO -> "[sudo] password: "
        Mode.NORMAL -> if (root) "root@termos:~# " else "user@termos:~$ "
    }

    fun start() {
        out(BANNER)
        if (mode == Mode.SET1) out("first boot: create a password. it unlocks the terminal and authorises sudo.")
        else out("terminal locked.")
    }

    fun lock() {
        mode = Mode.LOGIN
        root = false
        clearScreen()
        out("terminal locked.")
        onState()
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
                    out("password set. type 'help'.")
                } else {
                    out("mismatch, try again.")
                    mode = Mode.SET1
                }
                tmp = ""
            }
            Mode.LOGIN -> {
                if (Auth.check(ctx, raw)) { mode = Mode.NORMAL; out("access granted. type 'help'.") }
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

    // ------------------------------------------------------------ dispatcher
    private fun run(line: String, elevated: Boolean) {
        val parts = line.trim().split(Regex("\\s+"), 2)
        val cmd = parts[0]
        val arg = parts.getOrElse(1) { "" }.trim()
        val isRoot = root || elevated

        if (cmd in priv && !isRoot) { out("permission denied. try: sudo $line"); return }

        when (cmd) {
            "help" -> out(HELP)
            "clear" -> clearScreen()
            "ls", "apps", "grep", "search" -> listApps(arg)
            "open", "run" -> openApp(arg)
            "sudo" -> sudo(arg)
            "su" -> { root = true; out("root shell. 'exit' to drop.") }
            "exit" -> if (root) { root = false; out("dropped root.") } else out("use 'lock' to lock.")
            "lock" -> lock()
            "passwd" -> { mode = Mode.SET1; out("choose a new password.") }
            "whoami" -> out(if (isRoot) "root" else "user")
            "date" -> out(Date().toString())
            "echo" -> out(arg)
            "history" -> out(history.mapIndexed { i, h -> "${i + 1}  $h" }.joinToString("\n"))
            "battery" -> battery()
            "neofetch", "uname" -> neofetch()
            "settings" -> sys(Settings.ACTION_SETTINGS)
            "wifi" -> sys(Settings.ACTION_WIFI_SETTINGS)
            "bt" -> sys(Settings.ACTION_BLUETOOTH_SETTINGS)
            "uninstall" -> uninstall(arg)
            "copy" -> copy(arg)
            "paste" -> paste()
            "call" -> call(arg)
            "note" -> note(arg)
            "notes" -> notes(arg)
            "browse", "web" -> browse(arg)
            "go" -> go(arg)
            "back" -> back()
            "ai" -> ai(arg, isRoot)
            else -> out("$cmd: command not found. (try: ai $line)")
        }
    }

    private fun sudo(arg: String) {
        if (arg.isEmpty()) { out("usage: sudo <command>"); return }
        if (root) run(arg, true) else { pending = arg; mode = Mode.SUDO }
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
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${t.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sys(action: String) =
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    // ---------------------------------------------------------------- system
    private fun battery() {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        out("battery: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%")
    }

    private fun neofetch() = out(
        """
        os      : TermOS 0.1 on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        host    : ${Build.MANUFACTURER} ${Build.MODEL}
        cpu abi : ${Build.SUPPORTED_ABIS.firstOrNull()}
        apps    : ${apps.all().size}
        """.trimIndent()
    )

    private fun copy(text: String) {
        if (text.isEmpty()) { out("usage: copy <text>"); return }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("termos", text))
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
            "   |_|\\___|_|  |_| |_| |_|\\___/|____/   v0.1\n"

        const val HELP = """commands:
  ls [filter]          list apps (alias: apps, grep, search)
  open <name|n>        launch app by name or list number
  browse <url|words>   text browser / DuckDuckGo search
  go <n> | back        follow link / go back
  ai <question>        ask Gemini      (sudo ai key <KEY> to set key)
  copy <text> | paste  clipboard
  note <text> | notes  quick notes (notes clear)
  battery | neofetch | date | whoami | history | clear
  wifi | bt            open system panels
  call <number>        open dialer
  lock                 lock terminal (also auto-locks on screen off)
root (sudo <cmd> or su):
  settings | uninstall <app> | passwd"""
    }
}
