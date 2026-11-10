package dev.termos

import org.junit.Assert.*
import org.junit.Test

class CompleterTest {
    private val src = object : CompletionSource {
        override fun apps() = listOf("Chrome", "Maps", "Play Store")
        override fun aliases() = listOf("ll", "w")
    }
    private fun c(s: String) = Completer.complete(s, src)

    @Test fun commands() {
        assertTrue(c("he").contains("help"))
        assertTrue(c("ll").contains("ll"))
        assertTrue(c("zzz").isEmpty())
    }

    @Test fun apps() {
        assertEquals(listOf("open Chrome"), c("open ch"))
        assertEquals(listOf("open Play Store"), c("open play s"))
        assertEquals(3, c("open ").size)
    }

    @Test fun subcommands() {
        assertEquals(listOf("config theme"), c("config th"))
        assertTrue(c("linux s").containsAll(listOf("linux setup", "linux shell")))
        assertTrue(c("ai d").contains("ai do"))
        assertEquals(listOf("vim on"), c("vim on"))
    }

    @Test fun themeNames() {
        assertEquals(listOf("config theme dracula"), c("config theme dr"))
        assertEquals(listOf("theme nord"), c("theme no"))
    }

    @Test fun pipesAndChaining() {
        assertEquals(listOf("help | grep"), c("help | gr"))
        assertTrue(c("ls | ").containsAll(listOf("ls | grep", "ls | head", "ls | wc")))
        assertTrue(c("ls;he").contains("ls; help"))
        assertTrue(c("ls | grep x").isEmpty())
    }

    @Test fun helpAndGuide() {
        assertTrue(c("help lin").contains("help linux"))
        assertTrue(c("guide pi").contains("guide pipes"))
    }
}
