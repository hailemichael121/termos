package dev.termos

import androidx.core.graphics.ColorUtils

/** The `look` and `banner` commands. */
object LookCmd {
    private val roleHelp = LookRole.all.joinToString(", ")

    fun run(cmd: String, arg: String, env: ModEnv) {
        if (cmd == "banner") { banner(arg, env); return }
        val p = arg.trim().split(Regex("\\s+"), 2)
        val k = p[0].lowercase()
        val v = p.getOrElse(1) { "" }.trim()
        when (k) {
            "" -> env.out(panel(env))
            "color", "colour" -> color(v, env)
            "style" -> style(v, env)
            "title" -> title(v, env)
            "banner" -> banner(v, env)
            "fill" -> fill(v, env)
            "sub" -> sub(v, env)
            "date" -> date(v, env)
            "autofocus" -> autofocus(v, env)
            "lock" -> lockStyle(v, env)
            "locksize" -> lockSize(v, env)
            "preview" -> preview(env)
            "reset" -> reset(env)
            else -> env.out("unknown option '$k'. try: look")
        }
    }

    private fun panel(env: ModEnv): String {
        val c = env.ctx
        val mode = Look.bannerMode(c)
        val bannerDesc = if (mode == "custom") "custom \"${Look.bannerText(c)}\" (fill ${Look.fill(c)})" else mode
        val tagline = Look.sub(c).ifEmpty { "(none)" }
        val sb = StringBuilder()
        sb.append("theme     : ${Config.theme(c)}\n")
        sb.append("title     : ${Look.titleRaw(c)}  ->  ${Look.title(c)}\n")
        sb.append("banner    : $bannerDesc\n")
        sb.append("tagline   : $tagline\n")
        sb.append("date fmt  : ${Look.dateFmt(c)}  ->  ${Look.stamp(c, System.currentTimeMillis())}\n")
        sb.append("autofocus : ${if (Config.autofocus(c)) "on" else "off"}\n")
        sb.append("lock UI   : ${Look.lockStyle(c)}  (size ${Look.lockFontSize(c)}sp)\n\n")
        sb.append("role     colour   style\n")
        for (r in LookRole.all) {
            val col = Look.color(c, r)?.let { ColorParse.hex(it) } ?: "auto"
            sb.append("%-8s %-8s %s\n".format(r, col, Look.style(c, r).key))
        }
        sb.append("\nlook color <role> <#rrggbb|name|reset> | look style <role> <normal|bold|italic|bolditalic>\n")
        sb.append("banner <words> | look title <text> | look fill <char> | look sub <text> | look date <pattern>\n")
        sb.append("look preview | look reset | look autofocus on|off | guide look")
        return sb.toString()
    }

    private fun words(v: String): List<String> = v.split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun color(v: String, env: ModEnv) {
        val p = words(v)
        val names = ColorParse.names.keys.joinToString(", ")
        if (p.isEmpty()) { env.out("look color <role> <#rrggbb | name | reset>\nroles: $roleHelp\nnames: $names"); return }
        if (p[0].lowercase() == "reset") {
            LookRole.all.forEach { Store.put(env.ctx, "color.$it", "") }
            env.out("all colours are back to the theme.")
            return
        }
        val role = LookRole.resolve(p[0])
        if (role == null) { env.out("no such role '${p[0]}'. roles: $roleHelp"); return }
        val arg = p.getOrNull(1)
        if (arg == null) { env.out("look color $role <#rrggbb | name | reset>"); return }
        if (arg.lowercase() == "reset") { Store.put(env.ctx, "color.$role", ""); env.out("$role colour -> theme default"); return }
        val col = ColorParse.parse(arg)
        if (col == null) { env.out("invalid colour '$arg'. use #rrggbb, #rgb or a name: $names"); return }
        Store.put(env.ctx, "color.$role", ColorParse.hex(col))
        val bg = (Themes.get(Config.theme(env.ctx)) ?: Themes.all[0]).bg
        val warn = if (ColorUtils.calculateContrast(col, bg) < 2.0) "  (low contrast on this theme)" else ""
        env.out("$role colour -> ${ColorParse.hex(col)}$warn")
    }

    private fun style(v: String, env: ModEnv) {
        val p = words(v)
        val role = p.getOrNull(0)?.let { LookRole.resolve(it) }
        val st = p.getOrNull(1)?.let { TxtStyle.parse(it) }
        if (role == null || st == null) { env.out("look style <role> <normal|bold|italic|bolditalic>\nroles: $roleHelp"); return }
        Store.put(env.ctx, "style.$role", st.key)
        env.out("$role style -> ${st.key}")
    }

    private fun title(v: String, env: ModEnv) {
        val t = Parser.words(v)
        when {
            v.isEmpty() -> env.out("look title <text | reset>   tokens: {version} {user}\ncurrent: ${Look.titleRaw(env.ctx)}")
            t.lowercase() == "reset" -> { Store.put(env.ctx, "look.title", ""); env.out("title -> ${Look.title(env.ctx)}") }
            t.length > 40 -> env.out("too long (max 40 characters).")
            else -> { Store.put(env.ctx, "look.title", t); env.out("title -> ${Look.title(env.ctx)}") }
        }
    }

    private fun banner(arg: String, env: ModEnv) {
        val c = env.ctx
        val a = arg.trim()
        val low = a.lowercase()
        if (a.isEmpty()) {
            env.out("banner <your words>   -> block letters (max ${Banner.MAX_CHARS} characters)\n" +
                "banner default | off | plain      banner text <words>  (if your words are 'off' etc.)\n" +
                "look fill <char> | look sub <tagline>      current: ${Look.bannerMode(c)}")
            return
        }
        if (low == "default" || low == "off" || low == "plain") {
            Store.put(c, "look.banner", low)
            if (low == "plain" && Look.bannerText(c).isEmpty()) Store.put(c, "look.banner.text", "TermOS")
            env.out("banner -> $low  (shown when the terminal starts or locks)")
            return
        }
        val text = Parser.words(if (low.startsWith("text ")) a.substring(5) else a)
        val lines = Banner.render(text, Look.fill(c))
        if (lines.isEmpty()) { env.out("nothing to draw."); return }
        Store.put(c, "look.banner", "custom")
        Store.put(c, "look.banner.text", text.uppercase().take(Banner.MAX_CHARS))
        env.out(Banner.mark(lines).joinToString("\n"))
        val notes = ArrayList<String>()
        if (text.length > Banner.MAX_CHARS) notes.add("cut to ${Banner.MAX_CHARS} characters")
        if (text.any { !it.isWhitespace() && !Banner.supports(it) }) notes.add("characters without a symbol show as ?")
        env.out("banner set." + (if (notes.isEmpty()) "" else "  (" + notes.joinToString(", ") + ")"))
    }

    private fun fill(v: String, env: ModEnv) {
        val t = Parser.words(v)
        when {
            t.isEmpty() -> env.out("look fill <one character | reset>   e.g. look fill #\ncurrent: ${Look.fill(env.ctx)}")
            t.lowercase() == "reset" -> { Store.put(env.ctx, "look.fill", ""); env.out("fill -> default block") }
            t.length != 1 || t[0].isWhitespace() || t[0].isISOControl() -> env.out("invalid fill: use exactly one visible character.")
            else -> { Store.put(env.ctx, "look.fill", t); env.out("fill -> $t   (run 'banner <words>' to redraw)") }
        }
    }

    private fun sub(v: String, env: ModEnv) {
        val t = Parser.words(v)
        when {
            t.isEmpty() -> env.out("look sub <tagline | off>   shown under the banner\ncurrent: ${Look.sub(env.ctx).ifEmpty { "(none)" }}")
            t.lowercase() == "off" -> { Store.put(env.ctx, "look.sub", ""); env.out("tagline removed.") }
            t.length > 60 -> env.out("too long (max 60 characters).")
            else -> { Store.put(env.ctx, "look.sub", t); env.out("tagline set.") }
        }
    }

    private fun date(v: String, env: ModEnv) {
        val t = v.trim().trim('"', '\'')
        val now = System.currentTimeMillis()
        when {
            t.isEmpty() -> env.out("look date <pattern | reset>   e.g. yyyy-MM-dd HH:mm\ncurrent: ${Look.dateFmt(env.ctx)}  ->  ${Look.stamp(env.ctx, now)}")
            t.lowercase() == "reset" -> { Store.put(env.ctx, "look.date", ""); env.out("date format -> default") }
            !LookFmt.validDate(t) -> env.out("invalid date pattern. letters: yyyy MM dd EEE HH mm ss")
            else -> { Store.put(env.ctx, "look.date", t); env.out("date format -> $t  =  ${Look.stamp(env.ctx, now)}") }
        }
    }

    private fun autofocus(v: String, env: ModEnv) {
        val t = v.trim().lowercase()
        if (t == "on" || t == "off") { Store.put(env.ctx, "autofocus", t); env.out("autofocus $t") }
        else env.out("look autofocus on|off   (now ${if (Config.autofocus(env.ctx)) "on" else "off"})")
    }

    private fun lockStyle(v: String, env: ModEnv) {
        val t = v.trim().lowercase()
        val styles = Look.lockStyles.joinToString(" | ")
        when {
            t.isEmpty() -> env.out("look lock <terminal|center|minimal|emblem>\nterminal = banner in scroll only\ncenter = big clock + battery (centered)\nminimal = emblem + clock, hide scroll\nemblem = large logo + clock\ncurrent: ${Look.lockStyle(env.ctx)}")
            t !in Look.lockStyles -> env.out("unknown style. try: $styles")
            else -> { Store.put(env.ctx, "look.lock", t); env.out("lock screen style -> $t") }
        }
    }

    private fun lockSize(v: String, env: ModEnv) {
        val n = v.trim().toIntOrNull()
        when {
            v.isBlank() -> env.out("look locksize <24-72>   clock size on lock screen\ncurrent: ${Look.lockFontSize(env.ctx)}")
            n == null || n !in 24..72 -> env.out("usage: look locksize <24-72>")
            else -> { Store.put(env.ctx, "look.locksize", n.toString()); env.out("lock clock size -> ${n}sp") }
        }
    }

    private fun preview(env: ModEnv) {
        val c = env.ctx
        val lines = Banner.mark(Banner.lines(Look.bannerMode(c), Look.bannerText(c), Look.fill(c), Shell.BANNER))
        if (lines.isNotEmpty()) env.out(lines.joinToString("\n"))
        env.out(Look.stampLine(c, "preview"))
        env.out("${Config.user(c)}@termos:~$ ls")
        env.out("normal output text")
        env.out("tip: this is a dim line")
        env.out("hepl: command not found.")
    }

    private fun reset(env: ModEnv) {
        val keys = listOf("look.title", "look.fill", "look.banner", "look.banner.text", "look.sub", "look.date", "look.lock", "look.locksize") +
            LookRole.all.flatMap { listOf("color.$it", "style.$it") }
        keys.forEach { Store.put(env.ctx, it, "") }
        env.out("look reset to defaults (theme, vim, font and autofocus unchanged).")
    }
}
