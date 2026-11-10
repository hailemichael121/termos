package dev.termos

const val VERSION = "0.2.0"

enum class Cat(val label: String) {
    APPS("apps"), WEB("web"), AI("ai"), LINUX("linux"), TEXT("clipboard & notes"),
    SYSTEM("system"), SECURITY("security"), UI("look & feel"), HELP("help")
}

data class Cmd(
    val name: String,
    val usage: String,
    val desc: String,
    val cat: Cat,
    val root: Boolean = false,
    val aliases: List<String> = emptyList()
)

object Registry {
    val all = listOf(
        Cmd("help", "help [command]", "list commands or explain one", Cat.HELP, aliases = listOf("man", "?")),
        Cmd("guide", "guide [page]", "user guide (start, lock, linux, vim, themes, ai)", Cat.HELP),
        Cmd("cmds", "cmds", "command availability / what still needs setup", Cat.HELP),

        Cmd("ls", "ls [filter]", "list launchable apps (numbered)", Cat.APPS, aliases = listOf("apps", "grep", "search")),
        Cmd("open", "open <name|number>", "launch an app", Cat.APPS, aliases = listOf("run")),
        Cmd("uninstall", "uninstall <name|number>", "remove an app (system confirms)", Cat.APPS, root = true),

        Cmd("browse", "browse <url | words>", "text browser / DuckDuckGo search", Cat.WEB, aliases = listOf("web")),
        Cmd("go", "go <n>", "follow numbered link", Cat.WEB),
        Cmd("back", "back", "previous page", Cat.WEB),

        Cmd("ai", "ai <question> | ai model <name> | sudo ai key <KEY>", "ask Gemini", Cat.AI),

        Cmd("linux", "linux [ubuntu|shell|setup|run <cmd>|exec <cmd>|guide]", "real Linux via Termux + proot-distro", Cat.LINUX),

        Cmd("copy", "copy <text>", "copy text to clipboard", Cat.TEXT),
        Cmd("paste", "paste", "print clipboard", Cat.TEXT),
        Cmd("note", "note <text>", "append a quick note", Cat.TEXT),
        Cmd("notes", "notes [clear]", "show (or clear) notes", Cat.TEXT),

        Cmd("battery", "battery", "battery level", Cat.SYSTEM),
        Cmd("neofetch", "neofetch", "device info", Cat.SYSTEM, aliases = listOf("uname")),
        Cmd("date", "date", "current date/time", Cat.SYSTEM),
        Cmd("whoami", "whoami", "current user", Cat.SYSTEM),
        Cmd("history", "history", "command history", Cat.SYSTEM),
        Cmd("clear", "clear", "clear the screen", Cat.SYSTEM),
        Cmd("echo", "echo <text>", "print text", Cat.SYSTEM),
        Cmd("wifi", "wifi", "open Wi-Fi settings", Cat.SYSTEM),
        Cmd("bt", "bt", "open Bluetooth settings", Cat.SYSTEM),
        Cmd("call", "call <number>", "open dialer with number", Cat.SYSTEM),
        Cmd("settings", "settings", "open Android settings", Cat.SYSTEM, root = true),

        Cmd("lock", "lock", "lock the terminal now", Cat.SECURITY),
        Cmd("lockscreen", "lockscreen", "make TermOS your only lock (Android lock -> None)", Cat.SECURITY),
        Cmd("sudo", "sudo <command>", "run one command as root (asks password)", Cat.SECURITY),
        Cmd("su", "su", "root shell until `exit` (asks password)", Cat.SECURITY),
        Cmd("exit", "exit", "drop root", Cat.SECURITY),
        Cmd("passwd", "passwd", "change TermOS password", Cat.SECURITY, root = true),

        Cmd("config", "config [theme|vim|font|autolock|username] <value>", "show/change settings", Cat.UI),
        Cmd("theme", "theme [name]", "list or set colour theme", Cat.UI),
        Cmd("vim", "vim on|off", "toggle vim-style modal navigation", Cat.UI)
    )

    fun find(n: String): Cmd? = all.firstOrNull { it.name == n || n in it.aliases }

    fun names(): List<String> = all.flatMap { listOf(it.name) + it.aliases }.sorted()

    fun complete(prefix: String): List<String> = names().filter { it.startsWith(prefix.lowercase()) }
}

object Guide {
    val pages: Map<String, String> = linkedMapOf(
        "start" to """TermOS quick start
 1. First boot: create a password. It unlocks the terminal and is your sudo password.
 2. Press Home -> choose TermOS -> Always.
 3. Run: lockscreen   (makes TermOS your only lock, see: guide lock)
 4. ls, open <app>, browse <words>
 5. sudo ai key <GEMINI_KEY>, then: ai <question>
 6. linux guide       (real Ubuntu through Termux)
 7. theme, vim on, help, cmds, guide <page>""",

        "lock" to """Locking
 TermOS locks itself when the screen turns off and on 'lock'.
 Android's own lock screen cannot be removed by an app. To walk straight into TermOS:
   run 'lockscreen' -> Android Security settings -> Screen lock -> None (or Swipe).
 Then TermOS's password is the only lock.
 Limits: notifications, recent-apps and quick settings are still reachable while
 TermOS is locked, and the SIM/Find-My-Device protections are untouched.
 'config autolock off' needs sudo.""",

        "linux" to """Real Linux (Termux + proot-distro)
 One-time setup:
  1. Install Termux from F-Droid (not the old Play Store build). Open it once.
  2. In Termux run:
       mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties
     then fully close and reopen Termux.
  3. Android: allow Termux "Display over other apps".
  4. In TermOS: linux setup   (installs proot-distro + Ubuntu; allow the permission popup)
 Daily use:
  linux            Ubuntu shell        linux shell        plain Termux
  linux run <cmd>  run in Termux       linux exec <cmd>   run inside Ubuntu
 A helper script is in the project: termux/setup-termux.sh""",

        "vim" to """Vim mode  (config vim on  /  vim on)
 INSERT  type commands as usual.        ESC (button or key) -> NORMAL
 NORMAL  j/k scroll a line   d/u half page   gg top   G bottom
         /text search output   n next   N previous
         i, a, o or : back to INSERT
 Up/Down buttons scroll in NORMAL and walk history in INSERT. TAB completes.""",

        "themes" to """Themes
 theme              list themes
 theme dracula      set a theme
 config font 15     font size (8-30)
 config username me prompt name
 Themes: """ + Themes.names().joinToString(", "),

        "ai" to """AI assistant (Gemini)
 sudo ai key <KEY>     save API key (needs password)
 ai <question>         ask anything, e.g. ai how do I list files in linux
 ai model <name>       change model (default gemini-2.5-flash)
 The AI answers in text; it does not run commands yet."""
    )

    fun page(n: String): String =
        if (n.isBlank()) "guide pages: " + pages.keys.joinToString(", ") + "\nusage: guide <page>"
        else pages[n.trim().lowercase()] ?: "no such page.\nguide pages: " + pages.keys.joinToString(", ")
}
