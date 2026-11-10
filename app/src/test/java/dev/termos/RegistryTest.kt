package dev.termos

import org.junit.Assert.*
import org.junit.Test

class RegistryTest {
    @Test fun namesAndAliasesAreUnique() {
        val all = Registry.all.flatMap { listOf(it.name) + it.aliases }
        assertEquals("duplicate command/alias", all.size, all.toSet().size)
    }

    @Test fun everyCommandIsDocumented() {
        Registry.all.forEach {
            assertTrue("${it.name} usage", it.usage.isNotBlank())
            assertTrue("${it.name} desc", it.desc.isNotBlank())
        }
    }

    @Test fun aliasResolves() {
        assertEquals("ls", Registry.find("apps")?.name)
        assertEquals("help", Registry.find("man")?.name)
        assertEquals("open", Registry.find("run")?.name)
        assertNull(Registry.find("nope"))
    }

    @Test fun builtinsDoNotCollideWithFilters() {
        Filters.names.forEach { assertNull("$it would shadow a pipe filter", Registry.find(it)) }
    }

    @Test fun rootCommands() {
        listOf("settings", "uninstall", "passwd").forEach { assertTrue("$it must need sudo", Registry.find(it)!!.root) }
        assertFalse(Registry.find("ls")!!.root)
        assertFalse(Registry.find("su")!!.root)
    }

    @Test fun requiredCommandsExist() {
        listOf("help", "guide", "cmds", "which", "setup", "theme", "config", "vim", "linux", "lockscreen", "ai", "browse",
            "lock", "sudo", "calc", "timer", "todo", "torch", "alias", "unalias", "df", "free")
            .forEach { assertNotNull("missing $it", Registry.find(it)) }
    }

    @Test fun guidePagesExist() {
        listOf("start", "lock", "linux", "vim", "themes", "ai", "pipes", "tools").forEach {
            assertTrue("guide $it", Guide.page(it).length > 40)
        }
        assertTrue(Guide.page("").contains("guide pages"))
        assertTrue(Guide.page("bogus").contains("no such page"))
    }

    @Test fun versionIsCurrent() = assertEquals("0.3.0", VERSION)
}
