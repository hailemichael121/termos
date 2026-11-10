package dev.termos

/**
 * Human-readable layout for [help], [cmds], and single-command help (pure — unit tested).
 */
object HelpFmt {
    private val catOrder = listOf(
        Cat.HELP, Cat.APPS, Cat.WEB, Cat.TOOLS, Cat.AI, Cat.LINUX,
        Cat.COMMS, Cat.AUTO, Cat.TEXT, Cat.SYSTEM, Cat.SECURITY, Cat.UI
    )

    /** Soft wrap [text] to [width]; returns one or more lines. */
    fun wrap(text: String, width: Int): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return listOf("")
        if (width < 8) return listOf(t)
        if (t.length <= width) return listOf(t)
        val words = t.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (cur.isEmpty()) cur.append(w)
            else if (cur.length + 1 + w.length <= width) cur.append(' ').append(w)
            else {
                lines.add(cur.toString())
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    fun overview(version: String, commands: List<Cmd>): String {
        val nameW = commands.maxOf { it.name.length }.coerceIn(8, 14)
        val gap = "  "
        val descCol = 2 + nameW + gap.length
        val descW = 48
        val sb = StringBuilder()
        sb.append("TermOS $version — command reference\n\n")
        sb.append("tip: help <name>  |  guide start  |  cmds  |  TAB completes  |  * = sudo\n")
        sb.append("visual: look  |  guide look  |  theme  |  setup\n")
        sb.append("────────────────────────────────────────────────────────\n\n")
        val quick = listOf("ls", "open", "browse", "help", "guide", "ai", "linux", "look", "perms", "lock")
        sb.append("start here: ").append(quick.joinToString("  ")).append("\n\n")
        val byCat = commands.groupBy { it.cat }
        for (cat in catOrder) {
            val list = byCat[cat] ?: continue
            if (list.isEmpty()) continue
            sb.append('[').append(cat.label).append("]\n")
            for (cmd in list.sortedBy { it.name }) {
                val desc = cmd.desc + if (cmd.root) " (sudo)" else ""
                val lines = wrap(desc, descW)
                val head = "  ${cmd.name.padEnd(nameW)}$gap"
                sb.append(head).append(lines.first()).append('\n')
                for (i in 1 until lines.size) {
                    sb.append(" ".repeat(descCol)).append(lines[i]).append('\n')
                }
                if (cmd.aliases.isNotEmpty()) {
                    val aka = "aka: ${cmd.aliases.joinToString(", ")}"
                    sb.append(" ".repeat(descCol)).append(aka.take(descW + 12)).append('\n')
                }
            }
            sb.append('\n')
        }
        sb.append("────────────────────────────────────────────────────────\n")
        sb.append("more: guide pipes  |  guide scripts  |  guide look\n")
        return sb.toString().trimEnd()
    }

    fun one(cmd: Cmd): String {
        val bodyW = 52
        val labelW = 6
        val bodyCol = 2 + labelW
        val sb = StringBuilder()
        sb.append("── ").append(cmd.name)
        if (cmd.root) sb.append("  (sudo)")
        sb.append(" ──\n\n")
        section(sb, "What", wrap(cmd.desc, bodyW), labelW, bodyCol)
        section(sb, "Usage", listOf(cmd.usage), labelW, bodyCol)
        if (cmd.example.isNotEmpty()) section(sb, "Try", listOf(cmd.example), labelW, bodyCol)
        if (cmd.aliases.isNotEmpty()) {
            section(sb, "Also", listOf(cmd.aliases.joinToString(", ")), labelW, bodyCol)
        }
        if (cmd.root) sb.append("\n  sudo password required.\n")
        sb.append("\ntip: guide ").append(guideHint(cmd.name)).append('\n')
        if (cmd.cat == Cat.UI || cmd.name == "look" || cmd.name == "theme" || cmd.name == "banner") {
            sb.append("visual: look  |  guide look  |  look preview\n")
        }
        return sb.toString().trimEnd()
    }

    private fun section(sb: StringBuilder, key: String, lines: List<String>, labelW: Int, bodyCol: Int) {
        if (lines.isEmpty()) return
        sb.append("  ").append(key.padEnd(labelW)).append(lines.first()).append('\n')
        for (i in 1 until lines.size) {
            sb.append(" ".repeat(bodyCol)).append(lines[i]).append('\n')
        }
    }

    private fun guideHint(name: String): String = when (Registry.find(name)?.cat) {
        Cat.LINUX -> "linux"
        Cat.AUTO -> "scripts"
        Cat.COMMS -> "comms"
        Cat.SECURITY -> "lock"
        Cat.UI -> "look"
        else -> "start"
    }

    fun cmdsTable(commands: List<Cmd>, status: (Cmd) -> String): String {
        val sb = StringBuilder("Command status (what works on this phone now)\n\n")
        sb.append("────────────────────────────────────────────────────────\n\n")
        val nameW = commands.maxOf { it.name.length }.coerceIn(8, 12)
        val stW = 14
        for (cat in catOrder) {
            val list = commands.filter { it.cat == cat }
            if (list.isEmpty()) continue
            sb.append('[').append(cat.label).append("]\n")
            for (cmd in list.sortedBy { it.name }) {
                val st = status(cmd).take(stW).padEnd(stW)
                val use = cmd.usage.take(40)
                sb.append("  ").append(cmd.name.padEnd(nameW)).append(' ').append(st).append("  ").append(use).append('\n')
            }
            sb.append('\n')
        }
        sb.append("ready = ok now   sudo = password   needs … = run perms or guide\n")
        return sb.toString().trimEnd()
    }
}
