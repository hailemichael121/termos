package dev.termos

import android.content.Context

/** Reads the user's look settings. An empty stored value means "default". */
object Look {
    private fun v(c: Context, k: String): String? = Store.get(c, k)?.takeIf { it.isNotEmpty() }

    fun titleRaw(c: Context): String = v(c, "look.title") ?: LookFmt.DEFAULT_TITLE
    fun title(c: Context): String = LookFmt.title(titleRaw(c), VERSION, Config.user(c))
    fun fill(c: Context): Char = v(c, "look.fill")?.firstOrNull() ?: '\u2588'
    fun bannerMode(c: Context): String = v(c, "look.banner") ?: "default"
    fun bannerText(c: Context): String = v(c, "look.banner.text") ?: ""
    fun sub(c: Context): String = v(c, "look.sub") ?: ""
    fun dateFmt(c: Context): String = v(c, "look.date") ?: LookFmt.DEFAULT_DATE
    fun stamp(c: Context, ms: Long): String = LookFmt.stamp(ms, dateFmt(c))
    fun color(c: Context, role: String): Int? = v(c, "color.$role")?.let { ColorParse.parse(it) }
    fun style(c: Context, role: String): TxtStyle = v(c, "style.$role")?.let { TxtStyle.parse(it) } ?: TxtStyle.NORMAL

    /** Banner (or custom text) + optional tagline, every line carrying the banner colour marker. */
    fun header(c: Context): List<String> {
        val lines = Banner.lines(bannerMode(c), bannerText(c), fill(c), Shell.BANNER).toMutableList()
        val s = sub(c)
        if (s.isNotEmpty()) lines.add(s)
        return Banner.mark(lines)
    }

    fun marked(m: Char, s: String): String = "$m$s"

    fun stampLine(c: Context, event: String?, ms: Long = System.currentTimeMillis()): String =
        marked(Banner.TMARK, LookFmt.label(event) + ": " + stamp(c, ms))

    /** Changes whenever a colour or style changes, so the screen is redrawn. */
    fun signature(c: Context): String =
        (LookRole.all.flatMap { listOf("color.$it", "style.$it") }.joinToString("|") { v(c, it) ?: "" }) +
            "|lock:" + lockStyle(c) + ":" + lockFontSize(c)

    val lockStyles = listOf("terminal", "center", "minimal", "emblem")

    fun lockStyle(c: Context): String {
        val s = v(c, "look.lock")?.lowercase() ?: "center"
        return if (s in lockStyles) s else "center"
    }

    fun lockFontSize(c: Context): Int =
        v(c, "look.locksize")?.toIntOrNull()?.coerceIn(24, 72) ?: 42

    fun lockShowEmblem(c: Context): Boolean = lockStyle(c) != "terminal"
}
