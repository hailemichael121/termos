package dev.termos

import org.junit.Assert.*
import org.junit.Test

class SetupPolicyTest {
    private val t = 10_000_000L

    @Test fun neverAskedIsDue() = assertTrue(SetupPolicy.due(t, null))

    @Test fun laterSnoozesForOneHour() {
        assertFalse(SetupPolicy.due(t + 59 * 60_000L, t))
        assertTrue(SetupPolicy.due(t + 60 * 60_000L, t))
    }

    @Test fun justDismissedIsNotDue() = assertFalse(SetupPolicy.due(t, t))

    @Test fun startGuideNoLongerSaysOnceADay() {
        assertFalse(Guide.page("start").contains("once a day"))
        assertTrue(Guide.page("start").contains("every time you open"))
    }
}
