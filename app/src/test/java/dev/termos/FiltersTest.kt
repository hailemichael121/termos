package dev.termos

import org.junit.Assert.*
import org.junit.Test

class FiltersTest {
    private val text = "apple\nBanana\ncherry\napple"

    @Test fun grep() {
        assertEquals("apple\napple", Filters.apply("grep", listOf("apple"), text))
        assertEquals("Banana", Filters.apply("grep", listOf("-i", "BAN"), text))
        assertEquals("Banana\ncherry", Filters.apply("grep", listOf("-v", "apple"), text))
        assertEquals("grep: missing pattern", Filters.apply("grep", emptyList(), text))
    }

    @Test fun headTail() {
        assertEquals("apple\nBanana", Filters.apply("head", listOf("-n", "2"), text))
        assertEquals("apple\nBanana", Filters.apply("head", listOf("2"), text))
        assertEquals("cherry\napple", Filters.apply("tail", listOf("-2"), text))
        assertEquals(4, Filters.apply("head", emptyList(), text).split("\n").size)
    }

    @Test fun wc() {
        assertEquals("4", Filters.apply("wc", listOf("-l"), text))
        assertEquals("1 1 5", Filters.apply("wc", emptyList(), "hello"))
        assertEquals("0", Filters.apply("wc", listOf("-l"), ""))
    }

    @Test fun sortUniq() {
        assertEquals("Banana\napple\napple\ncherry", Filters.apply("sort", emptyList(), text))
        assertEquals("cherry\napple\napple\nBanana", Filters.apply("sort", listOf("-r"), text))
        assertEquals("a\nb\na", Filters.apply("uniq", emptyList(), "a\na\nb\na"))
    }

    @Test fun namesCovered() = assertEquals(setOf("grep", "head", "tail", "wc", "sort", "uniq"), Filters.names)
}
