package dev.termos

import org.junit.Assert.*
import org.junit.Test

class WordCacheTest {
    @Test fun defaultShortcuts() = assertTrue(WordCache.defaultShortcuts().contains("ls"))

    @Test fun registryHasLockForLoPrefix() = assertTrue(Registry.names().any { it.startsWith("lo") })
}
