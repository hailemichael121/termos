package dev.termos

import org.junit.Assert.*
import org.junit.Test

class UtilTest {
    @Test fun suggestFindsTypos() {
        val names = Registry.names()
        assertEquals("help", Suggest.closest("hepl", names))
        assertEquals("battery", Suggest.closest("batery", names))
        assertEquals("browse", Suggest.closest("brwose", names))
        assertNull(Suggest.closest("zzzzzzzz", names))
    }

    @Test fun distanceBasics() {
        assertEquals(0, Suggest.dist("a", "a"))
        assertEquals(1, Suggest.dist("ab", "ba"))
        assertEquals(3, Suggest.dist("kitten", "sitting"))
    }

    @Test fun durations() {
        assertEquals(90L, Dur.parse("90s"))
        assertEquals(300L, Dur.parse("5m"))
        assertEquals(5400L, Dur.parse("1h30m"))
        assertEquals(600L, Dur.parse("10"))
        assertNull(Dur.parse("abc"))
        assertNull(Dur.parse(""))
        assertNull(Dur.parse("5x"))
        assertEquals("1h01m01s", Dur.fmt(3661))
        assertEquals("2m05s", Dur.fmt(125))
        assertEquals("9s", Dur.fmt(9))
    }

    @Test fun lockoutPolicy() {
        assertEquals(listOf(0L, 0L, 0L, 5L, 15L, 30L, 60L, 60L), (0..7).map { Lockout.delaySeconds(it) })
        assertEquals(0L, Lockout.remaining(2, 1000, 1000))
        assertEquals(5L, Lockout.remaining(3, 1000, 1000))
        assertEquals(3L, Lockout.remaining(3, 1000, 3000))
        assertEquals(0L, Lockout.remaining(3, 1000, 6001))
    }

    @Test fun lineStyles() {
        assertEquals(Kind.PROMPT, LineStyle.classify("user@termos:~$ ls"))
        assertEquals(Kind.PROMPT, LineStyle.classify("root@termos:~# whoami"))
        assertEquals(Kind.ERROR, LineStyle.classify("permission denied. try: sudo settings"))
        assertEquals(Kind.ERROR, LineStyle.classify("hepl: command not found."))
        assertEquals(Kind.DIM, LineStyle.classify("tip: do this"))
        assertEquals(Kind.NORMAL, LineStyle.classify("hello world"))
    }

    @Test fun planKeepsOnlySafeCommands() {
        val raw = "ls\nsudo settings\n```\nopen chrome\nlinux run rm -rf /\nls; clear\n# cannot\nbattery\nai hi\nuninstall x"
        assertEquals(listOf("ls", "open chrome", "battery"), Plan.parse(raw))
    }

    @Test fun planIsCapped() = assertEquals(6, Plan.parse((1..20).joinToString("\n") { "date" }).size)

    @Test fun planForbidsDangerousCommands() {
        listOf("sudo", "su", "passwd", "linux", "ai", "uninstall", "settings", "lock", "exit")
            .forEach { assertFalse("$it must not be plannable", Plan.allowed(it)) }
        assertTrue(Plan.allowed("torch")); assertTrue(Plan.allowed("battery"))
        assertFalse(Plan.allowed("nonsense"))
    }
}
