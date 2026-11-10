package dev.termos

import org.junit.Assert.*
import org.junit.Test

class ThemesTest {
    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
    }
    private fun contrast(a: Int, b: Int): Double {
        val l1 = maxOf(lum(a), lum(b)); val l2 = minOf(lum(a), lum(b)); return (l1 + 0.05) / (l2 + 0.05)
    }

    @Test fun defaultThemeExists() = assertNotNull(Themes.get("green"))
    @Test fun lookupIsCaseInsensitive() = assertNotNull(Themes.get("DRACULA"))
    @Test fun unknownThemeIsNull() = assertNull(Themes.get("nope"))
    @Test fun namesUnique() = assertEquals(Themes.names().size, Themes.names().toSet().size)

    @Test fun textIsReadable() {
        Themes.all.forEach {
            assertTrue("${it.name} fg/bg contrast", contrast(it.fg, it.bg) >= 4.0)
            assertTrue("${it.name} input/bg contrast", contrast(it.input, it.bg) >= 4.0)
        }
    }
}
