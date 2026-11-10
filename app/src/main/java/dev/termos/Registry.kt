package dev.termos

const val VERSION = "0.4.0"

enum class Cat(val label: String) {
    APPS("apps"), WEB("web"), AI("ai"), LINUX("linux"), TOOLS("tools"), COMMS("notifications, contacts, sms"),
    AUTO("automation"), TEXT("clipboard & notes"), SYSTEM("system"), SECURITY("security"),
    UI("look & feel"), HELP("help")
}

data class Cmd(
    val name: String,
    val usage: String,
    val desc: String,
    val cat: Cat,
    val root: Boolean = false,
    val aliases: List<String> = emptyList(),
    val example: String = ""
)

object Registry {
    val all = listOf(
        Cmd("help", "help [command]", "list commands or explain one", Cat.HELP, aliases = listOf("man", "?"), example = "help linux"),
        Cmd("guide", "guide [page]", "user guide pages", Cat.HELP, example = "guide scripts"),
        Cmd("cmds", "cmds", "command availability / what still needs setup", Cat.HELP),
        Cmd("which", "which <command>", "builtin, alias or unknown?", Cat.HELP, example = "which ll"),
        Cmd("setup", "setup", "rerun first-run wizard (theme, vim, AI key)", Cat.UI),

        Cmd("ls", "ls [filter]", "list launchable apps (numbered)", Cat.APPS, aliases = listOf("apps", "search"), example = "ls chrome"),
        Cmd("open", "open <name|number>", "launch an app", Cat.APPS, aliases = listOf("run"), example = "open maps"),
        Cmd("uninstall", "uninstall <name|number>", "remove an app (system confirms)", Cat.APPS, root = true),

        Cmd("browse", "browse <url | words>", "text browser / DuckDuckGo search", Cat.WEB, aliases = listOf("web"), example = "browse kotlin coroutines"),
        Cmd("go", "go <n>", "follow numbered link", Cat.WEB),
        Cmd("back", "back", "previous page", Cat.WEB),

        Cmd("ai", "ai <question> | ai do <task> | ai model <name> | sudo ai key <KEY>", "ask Gemini, or let it plan commands", Cat.AI,
            example = "ai do turn on the torch and show battery"),
        Cmd("linux", "linux [ubuntu|shell|setup|run <cmd>|exec <cmd>|guide]", "real Linux via Termux + proot-distro", Cat.LINUX, example = "linux exec uname -a"),

        Cmd("calc", "calc <expression>", "calculator: + - * / % ^ ( ) sqrt abs ln log sin cos tan pi e", Cat.TOOLS, example = "calc (2+3)*4^2"),
        Cmd("timer", "timer [<dur> [label] | cancel <n>]", "countdown timers that survive the app being killed (90s, 5m, 1h30m)", Cat.TOOLS, example = "timer 25m pomodoro"),
        Cmd("todo", "todo [add <text>|done <n>|rm <n>|clear]", "to-do list", Cat.TOOLS, example = "todo add buy injera"),
        Cmd("torch", "torch on|off", "flashlight", Cat.TOOLS),
        Cmd("notify", "notify <text>", "show a notification (handy in scripts)", Cat.TOOLS, example = "notify stretch!"),

        Cmd("notifs", "notifs [open <n>|clear <n|all>|reply <n> <text>|watch on|off]", "read, open, clear and reply to notifications", Cat.COMMS, example = "notifs reply 2 on my way"),
        Cmd("contacts", "contacts [filter]", "search contacts", Cat.COMMS, aliases = listOf("contact"), example = "contacts abebe"),
        Cmd("sms", "sms [n] | sms from <who> | sms read <n> | sms send <who> <text> [-y]", "read and send SMS (asks before sending)", Cat.COMMS, example = "sms send abebe running late"),

        Cmd("script", "script [ls|new <n>|edit <n>|show <n>|add <n> <line>|rm <n>|run <n> [args]]", "create and manage .tsh scripts", Cat.AUTO, example = "script new morning"),
        Cmd("tsh", "tsh <script> [args]", "run a script", Cat.AUTO, example = "tsh morning"),
        Cmd("at", "at HH:mm [once] <command>", "run a command every day at a time (background-safe commands)", Cat.AUTO, example = "at 07:30 tsh morning"),
        Cmd("every", "every <dur> <command>", "repeat a command (minimum 1m)", Cat.AUTO, example = "every 2h notify drink water"),
        Cmd("job", "job [ls|rm <id>|run <id>]", "list, remove or run scheduled jobs", Cat.AUTO, aliases = listOf("jobs")),

        Cmd("copy", "copy <text>", "copy text to clipboard", Cat.TEXT),
        Cmd("paste", "paste", "print clipboard", Cat.TEXT),
        Cmd("note", "note <text>", "append a quick note", Cat.TEXT),
        Cmd("notes", "notes [clear]", "show (or clear) notes", Cat.TEXT),

        Cmd("battery", "battery", "battery level", Cat.SYSTEM),
        Cmd("neofetch", "neofetch", "device info", Cat.SYSTEM, aliases = listOf("uname")),
        Cmd("date", "date", "current date/time", Cat.SYSTEM),
        Cmd("whoami", "whoami", "current user", Cat.SYSTEM),
        Cmd("history", "history [clear]", "command history (!! and !n re-run)", Cat.SYSTEM),
        Cmd("clear", "clear", "clear the screen", Cat.SYSTEM),
        Cmd("echo", "echo <text>", "print text", Cat.SYSTEM),
        Cmd("df", "df", "storage space", Cat.SYSTEM),
        Cmd("free", "free", "memory use (device + TermOS)", Cat.SYSTEM),
        Cmd("wifi", "wifi", "open Wi-Fi settings", Cat.SYSTEM),
        Cmd("bt", "bt", "open Bluetooth settings", Cat.SYSTEM),
        Cmd("call", "call <name|number>", "dial a number or a contact", Cat.SYSTEM),
        Cmd("settings", "settings", "open Android settings", Cat.SYSTEM, root = true),

        Cmd("lock", "lock", "lock the terminal now", Cat.SECURITY),
        Cmd("lockscreen", "lockscreen", "make TermOS your only lock (Android lock -> None)", Cat.SECURITY),
        Cmd("perms", "perms [name|all]", "check/fix: home lock battery notifications alarms overlay notifs contacts sms", Cat.SECURITY, example = "perms battery"),
        Cmd("keepalive", "keepalive [on|off]", "keep TermOS running (foreground notice) so timers/jobs/auto-lock stay reliable", Cat.SECURITY),
        Cmd("sudo", "sudo [-k] <command>", "run one command as root (password cached 5 min; -k forgets)", Cat.SECURITY),
        Cmd("su", "su", "root shell until `exit`", Cat.SECURITY),
        Cmd("exit", "exit", "drop root", Cat.SECURITY),
        Cmd("passwd", "passwd", "change TermOS password", Cat.SECURITY, root = true),

        Cmd("alias", "alias [name='command']", "show or define a shortcut", Cat.UI, example = "alias w='browse weather addis ababa'"),
        Cmd("unalias", "unalias <name>", "remove a shortcut", Cat.UI),
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
 1. First boot: create a password (6+ chars). It unlocks the terminal and is your sudo password.
 2. A short wizard sets theme, vim mode and your Gemini key (Enter keeps defaults). Rerun: setup
 3. TermOS asks (once a day) to become your default launcher, switch Android's lock to None,
    allow unrestricted battery, notifications, exact alarms and 'lock over apps'. See: guide setup
 4. ls, open <app>, browse <words>, calc 2+2, todo add ..., timer 5m tea
 5. help, cmds, guide <page>   |   TAB completes, ^C cancels, ESC = vim normal mode""",

        "setup" to """Setup prompts and permissions
 On open TermOS offers each of these (Set up / Later / Never ask):
  home          make TermOS the default launcher (system picker)
  lock          set Android's lock to None so TermOS's password is the only lock
  battery       unrestricted battery: stops the system killing TermOS
  notifications lets timers/jobs alert you          alarms   exact timers and jobs
  overlay       'lock over apps': when the screen turns off TermOS comes to the front,
                so waking the phone shows the TermOS password, not the app you left open
 perms             status table      perms <name>   fix one      perms all   fix the next missing one
 keepalive on|off  foreground notice that keeps TermOS alive (default on)
 Some phones (Tecno, Infinix, itel, Xiaomi, Huawei, Samsung...) also need an Autostart / 'never sleeping'
 setting: 'perms battery' prints the steps for your brand.
 Honest limits: an app cannot remove Android's lock itself; notifications and quick settings can still
 be reachable on the lock screen (set Notifications > Lock screen > Don't show).""",

        "lock" to """Locking
 TermOS locks on screen-off (even if the UI was closed) and on 'lock'.
 3 wrong passwords -> 5s wait, then 15s, 30s, 60s. 'sudo' remembers your password 5 min (sudo -k forgets).
 Android's own lock cannot be removed by an app. Run 'lockscreen' or 'perms lock' -> Screen lock -> None.
 With 'overlay' allowed, TermOS jumps to the front on screen-off, so TermOS's lock is what you see.
 'config autolock off' needs sudo.""",

        "linux" to """Real Linux (Termux + proot-distro)
 One-time setup:
  1. Install Termux from F-Droid (not the old Play Store build). Open it once.
  2. In Termux run:  mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties
     then fully close and reopen Termux.   (or run the project's termux/setup-termux.sh)
  3. Android: allow Termux "Display over other apps".
  4. In TermOS: linux setup   (allow the permission popup)
 Daily use:
  linux            Ubuntu shell        linux shell        plain Termux
  linux run <cmd>  run in Termux       linux exec <cmd>   run inside Ubuntu""",

        "vim" to """Vim mode  (vim on / vim off)
 INSERT  type commands.            ESC (button or key) -> NORMAL
 NORMAL  j/k line   d/u half page   gg top   G bottom
         /text search output   n next   N previous   i a o : -> INSERT""",

        "themes" to """Themes
 theme              list themes        theme dracula   set one
 config font 15     font size (8-30)   config username me   prompt name
 Themes: """ + Themes.names().joinToString(", "),

        "ai" to """AI assistant (Gemini)
 sudo ai key <KEY>     save API key           ai <question>   ask anything
 ai model <name>       change model (default gemini-2.5-flash)
 ai do <task>          AI proposes up to 6 commands, you confirm with y / n.
                       It can never plan sudo, su, passwd, linux, uninstall, settings, lock, ai,
                       sms, scripts, jobs, notifs or contacts.""",

        "pipes" to """Pipes, chaining, aliases, history
 ls | grep chrome | head 3      filters: grep [-i] [-v], head [n], tail [n], wc [-l], sort [-r], uniq
 ls; battery; date              run several commands
 echo "hello   world"          quotes keep spacing
 alias w='browse weather'       shortcut (ll and cls exist by default)    unalias w
 !!  re-run last   !3  re-run #3   !op  last command starting with 'op'""",

        "tools" to """Tools
 calc (2+3)*4^2            timer 25m pomodoro   (timer lists, timer cancel 1)
 todo add milk / todo / todo done 1 / todo rm 1 / todo clear
 torch on|off   notify <text>   df   free   which <cmd>
 Timers use the system alarm clock, so they still ring if TermOS was killed (allow 'alarms' for exact timing).""",

        "comms" to """Notifications, contacts, SMS
 notifs                 list (needs Notification access: perms notifs)
 notifs open 2 | clear 2 | clear all | reply 2 on my way | watch on|off (echo new ones while unlocked)
 contacts [filter]      contact numbers (permission asked on first use)
 sms [n]   sms from <who>   sms read <n>   sms send <who> <text> [-y]   (asks y/n unless -y)
 call <name|number>
 SMS permissions are restricted by Google Play: use a sideloaded or F-Droid build.""",

        "scripts" to """Scripts (.tsh) and scheduling
 script new morning     type lines; '.' saves, '.cancel' aborts, '.list' shows, '.undo' removes the last
 script ls | show n | edit n | add n <line> | rm n | run n args        shortcut: tsh <name> [args]
 Language:
   # comment                        set name=Yihun   (or name=Yihun)
   echo hi §name  §{name}  §1  §@  §?  §(battery)      single quotes = literal, \§ = a literal dollar
   built-ins: §BATTERY §TIME §DATE §HOUR §DAY §USER
   if §BATTERY < 20 then notify low battery else echo ok       ops: == != < > <= >= contains
   repeat 3 do torch on          sleep 5  (seconds, or 2m)         exit
   cmd1 && cmd2     cmd1 || cmd2     cmd1 ; cmd2
 Limits: 2000 steps per run, repeat max 100, no nested scripts, sudo/su/passwd refused.
 Scheduling (background-safe commands only: echo notify note todo timer torch battery date calc tsh df free which):
   at 07:30 tsh morning      daily       at 07:30 once notify hi     one time
   every 2h notify drink water           job   job run 1   job rm 1
 Jobs survive reboot. They run even when TermOS is locked, but only the commands listed above.""".replace('§', '$')
    )

    fun page(n: String): String =
        if (n.isBlank()) "guide pages: " + pages.keys.joinToString(", ") + "\nusage: guide <page>"
        else pages[n.trim().lowercase()] ?: "no such page.\nguide pages: " + pages.keys.joinToString(", ")
}
