package dev.termos

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class StyleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val src = object : CompletionSource {
        override fun apps() = emptyList<String>()
        override fun aliases() = emptyList<String>()
    }

    @Test fun colours() {
        assertEquals(0xFFFF0000L.toInt(), ColorParse.parse("#ff0000"))
        assertEquals(0xFFFFFFFFL.toInt(), ColorParse.parse("#fff"))
        assertEquals(0xFFFF5555L.toInt(), ColorParse.parse("RED"))
        assertNull(ColorParse.parse("#12"))
        assertNull(ColorParse.parse("#gggggg"))
        assertNull(ColorParse.parse("notacolour"))
        assertEquals("#FF0000", ColorParse.hex(0xFFFF0000L.toInt()))
    }

    @Test fun stylesAndRoles() {
        assertEquals(TxtStyle.BOLDITALIC, TxtStyle.parse("bold-italic"))
        assertEquals(TxtStyle.NORMAL, TxtStyle.parse(" Normal "))
        assertNull(TxtStyle.parse("fancy"))
        assertEquals("user", LookRole.resolve("username"))
        assertEquals("error", LookRole.resolve("ERR"))
        assertEquals("time", LookRole.resolve("time"))
        assertNull(LookRole.resolve("bogus"))
    }

    @Test fun bannerDrawsBlockLetters() {
        val hi = Banner.render("HI", '#')
        assertEquals(5, hi.size)
        assertTrue(hi[0].contains('#'))
        assertTrue(Banner.render("").isEmpty())
        assertTrue(Banner.render("   ").isEmpty())
        assertEquals(Banner.render("?"), Banner.render("\u00e9"))
        assertTrue(Banner.supports('a'))
        assertFalse(Banner.supports('\u00e9'))
    }

    @Test fun bannerWrapsAndTruncates() {
        assertEquals(11, Banner.render("AAAA BBBB", '#', 20).size)
        val long = Banner.render("A".repeat(40), '#', 40)
        assertEquals(5, long.size)
        assertTrue(long.all { it.length <= 40 })
    }

    @Test fun bannerModes() {
        assertTrue(Banner.lines("off", "x", '#', "d").isEmpty())
        assertEquals(listOf("x"), Banner.lines("plain", "x", '#', "d"))
        assertEquals(listOf("TermOS"), Banner.lines("plain", "", '#', "d"))
        assertEquals(listOf("a", "b"), Banner.lines("default", "", '#', "a\nb\n"))
        assertEquals(listOf("a", "b"), Banner.lines("custom", "", '#', "a\nb\n"))
        assertEquals(5, Banner.lines("custom", "hi", '#', "a\nb\n").size)
        assertEquals(listOf("${Banner.MARK}a", "${Banner.MARK}"), Banner.mark(listOf("a", "")))
    }

    @Test fun dateLines() {
        assertEquals("1970-01-01 00:00", LookFmt.stamp(0L, "yyyy-MM-dd HH:mm", utc, Locale.US))
        assertEquals("Thu 1 Jan 1970 00:00:00", LookFmt.stamp(0L, "qqq", utc, Locale.US))
        assertTrue(LookFmt.validDate("yyyy-MM-dd"))
        assertFalse(LookFmt.validDate(""))
        assertFalse(LookFmt.validDate("qqq"))
        assertEquals("started", LookFmt.label(null))
        assertEquals("logged out", LookFmt.label("logged out"))
    }

    @Test fun titleTokens() {
        assertEquals("yihun OS 0.7.0", LookFmt.title("{user} OS {version}", "0.7.0", "yihun"))
        assertEquals("TermOS 0.7.0", LookFmt.title(LookFmt.DEFAULT_TITLE, "0.7.0", "x"))
    }

    @Test fun promptSplit() {
        assertEquals(15, LookFmt.promptEnd("user@termos:~$ ls"))
        assertEquals(0, LookFmt.promptEnd("hello"))
    }

    @Test fun commandsAndGuide() {
        assertEquals("lock", Registry.find("logout")?.name)
        assertNotNull(Registry.find("look"))
        assertNotNull(Registry.find("banner"))
        assertTrue(Guide.page("look").contains("banner"))
        assertFalse(Plan.allowed("lock"))
    }

    @Test fun completion() {
        assertEquals(listOf("look color"), Completer.complete("look co", src))
        assertEquals(listOf("banner off"), Completer.complete("banner of", src))
    }
}
