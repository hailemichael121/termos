package dev.termos

import org.junit.Assert.*
import org.junit.Test

class HelpFmtTest {
  @Test fun wrapBreaksLongLines() {
        val lines = HelpFmt.wrap("one two three four five six seven eight nine", 12)
        assertTrue(lines.size >= 2)
        assertTrue(lines.joinToString(" ").contains("nine"))
    }

    @Test fun overviewHasCategoriesAndStartHere() {
        val t = HelpFmt.overview("0.8.0", Registry.all)
        assertTrue(t.contains("[apps]"))
        assertTrue(t.contains("[linux]"))
        assertTrue(t.contains("[tools]"))
        assertTrue(t.contains("start here:"))
        assertTrue(t.contains("  ls "))
    }

    @Test fun oneCommandHelpIsStructured() {
        val c = Registry.find("ls")!!
        val t = HelpFmt.one(c)
        assertTrue(t.contains("── ls"))
        assertTrue(t.contains("What"))
        assertTrue(t.contains("Usage"))
        assertTrue(t.contains("Try"))
    }

    @Test fun cmdsTableGroupsByCategory() {
        val t = HelpFmt.cmdsTable(Registry.all) { "ready" }
        assertTrue(t.contains("[security]"))
        assertTrue(t.contains("ready = ok now"))
    }
}
