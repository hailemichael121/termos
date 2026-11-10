package dev.termos

import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    @Test fun statementsRespectQuotes() {
        assertEquals(listOf("ls", "echo 'a;b'", "clear"), Parser.statements("ls; echo 'a;b' ; clear"))
        assertEquals(listOf("echo \"x;y\""), Parser.statements("echo \"x;y\""))
        assertTrue(Parser.statements("  ;  ; ").isEmpty())
    }

    @Test fun pipesRespectQuotes() {
        assertEquals(3, Parser.pipes("help | grep a | head 3").size)
        assertEquals(1, Parser.pipes("echo 'a|b'").size)
    }

    @Test fun tokensHandleQuotesAndEscapes() {
        assertEquals(listOf("alias", "x=echo hi there", "y"), Parser.tokens("alias x='echo hi there' y"))
        assertEquals(listOf("a b", "c"), Parser.tokens("\"a b\" c"))
        assertEquals(listOf("a b"), Parser.tokens("a\\ b"))
        assertEquals(listOf(""), Parser.tokens("''"))
    }

    @Test fun wordsStripQuotes() = assertEquals("a b c", Parser.words("\"a b\" c"))

    @Test fun aliasExpansion() {
        assertEquals("ls -x", Parser.expandAlias("ll -x", mapOf("ll" to "ls")))
        assertEquals("open chrome", Parser.expandAlias("oc", mapOf("oc" to "open chrome")))
        assertEquals("plain", Parser.expandAlias("plain", mapOf("ll" to "ls")))
    }

    @Test(timeout = 2000) fun aliasLoopTerminates() {
        val r = Parser.expandAlias("a", mapOf("a" to "b", "b" to "a"))
        assertTrue(r == "a" || r == "b")
    }

    @Test fun historyExpansion() {
        val h = listOf("ls", "open x")
        assertEquals("open x", Parser.expandHistory("!!", h))
        assertEquals("ls", Parser.expandHistory("!1", h))
        assertEquals("open x", Parser.expandHistory("!op", h))
        assertEquals("", Parser.expandHistory("!9", h))
        assertEquals("", Parser.expandHistory("!zzz", h))
        assertNull(Parser.expandHistory("hello", h))
        assertNull(Parser.expandHistory("!", h))
    }

    @Test fun redactsKeys() {
        assertEquals("sudo ai key ***", Parser.redact("sudo ai key ABC123"))
        assertEquals("ai key ***", Parser.redact("AI  KEY   secret"))
        assertEquals("ls", Parser.redact("ls"))
    }
}
