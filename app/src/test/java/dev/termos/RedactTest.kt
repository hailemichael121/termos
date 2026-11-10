package dev.termos

import org.junit.Assert.*
import org.junit.Test

class RedactTest {
    @Test fun hidesKeyWhateverTheCaseAndSpacing() {
        assertEquals("sudo ai key ***", Parser.redact("sudo ai key ABC123"))
        assertEquals("ai key ***", Parser.redact("AI  KEY   secret"))
    }

    @Test fun leavesOtherLinesAlone() {
        assertEquals("ls", Parser.redact("ls"))
        assertEquals("email key foo", Parser.redact("email key foo"))
        assertEquals("ai what is a key", Parser.redact("ai what is a key"))
    }
}
