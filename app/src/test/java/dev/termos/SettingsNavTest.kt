package dev.termos

import org.junit.Assert.*
import org.junit.Test

class SettingsNavTest {
    @Test fun lockStyleOptionsExist() {
        assertTrue(Look.lockStyles.contains("center"))
        assertTrue(Look.lockStyles.contains("terminal"))
        assertTrue(Look.lockStyles.contains("minimal"))
        assertTrue(Look.lockStyles.contains("emblem"))
    }

    @Test fun guideDocumentsLockUiAndSettings() {
        assertTrue(Guide.page("look").contains("look lock"))
        assertTrue(Guide.page("lock").contains("Screen lock"))
        assertTrue(Guide.page("look").contains("TermOS-styled dialogs"))
    }
}
