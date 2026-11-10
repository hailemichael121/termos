package dev.termos

import org.junit.Assert.*
import org.junit.Test

class AmharicLayoutTest {
    @Test fun familiesCycle() {
        val n = AmharicLayout.families.size
        assertEquals(AmharicLayout.families[0].name, AmharicLayout.familyLabel(n))
    }

    @Test fun rowsNonEmpty() {
        val rows = AmharicLayout.rows(3)
        assertEquals(3, rows.size)
        assertTrue(rows.flatten().any { it == "ራ" })
    }
}
