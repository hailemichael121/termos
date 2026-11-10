package dev.termos

data class Theme(
    val name: String,
    val bg: Int,
    val fg: Int,
    val input: Int,
    val accent: Int,
    val bar: Int
)

object Themes {
    private fun t(n: String, bg: Long, fg: Long, input: Long, accent: Long, bar: Long) =
        Theme(n, (0xFF000000L or bg).toInt(), (0xFF000000L or fg).toInt(),
            (0xFF000000L or input).toInt(), (0xFF000000L or accent).toInt(), (0xFF000000L or bar).toInt())

    val all = listOf(
        t("green",           0x000000, 0x33FF66, 0xFFFFFF, 0x33FF66, 0x111111),
        t("amber",           0x000000, 0xFFB000, 0xFFE0A0, 0xFFB000, 0x141008),
        t("matrix",          0x0D0208, 0x00FF41, 0xB6FFC8, 0x008F11, 0x0A140C),
        t("dracula",         0x282A36, 0xF8F8F2, 0xFFFFFF, 0xBD93F9, 0x1E1F29),
        t("nord",            0x2E3440, 0xD8DEE9, 0xECEFF4, 0x88C0D0, 0x242933),
        t("solarized-dark",  0x002B36, 0x93A1A1, 0xEEE8D5, 0xB58900, 0x00212B),
        t("solarized-light", 0xFDF6E3, 0x586E75, 0x073642, 0x268BD2, 0xEEE8D5),
        t("light",           0xFFFFFF, 0x222222, 0x000000, 0x0066CC, 0xEEEEEE),
        t("cyber",           0x0A0A23, 0x00F0FF, 0xFFFFFF, 0xFF2A6D, 0x12122E),
        t("mono",            0x000000, 0xCCCCCC, 0xFFFFFF, 0xFFFFFF, 0x161616)
    )

    fun get(name: String): Theme? = all.firstOrNull { it.name == name.trim().lowercase() }
    fun names(): List<String> = all.map { it.name }
}
