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
        assertEquals("job", Registry.find("jobs")?.name)
        assertEquals("contacts", Registry.find("contact")?.name)
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
            "lock", "sudo", "calc", "timer", "todo", "torch", "alias", "unalias", "df", "free",
            "perms", "keepalive", "notify", "notifs", "contacts", "sms", "script", "tsh", "at", "every", "job",
            "look", "banner", "logout")
            .forEach { assertNotNull("missing $it", Registry.find(it)) }
    }

    @Test fun guidePagesExist() {
        listOf("start", "setup", "lock", "linux", "vim", "themes", "look", "ai", "pipes", "tools", "comms", "scripts").forEach {
            assertTrue("guide $it", Guide.page(it).length > 40)
        }
        assertTrue(Guide.page("scripts").contains("\$BATTERY"))
        assertTrue(Guide.page("").contains("guide pages"))
        assertTrue(Guide.page("bogus").contains("no such page"))
    }

    @Test fun aiCannotPlanRiskyCommands() {
        listOf("sms", "script", "tsh", "at", "every", "job", "notifs", "contacts", "sudo", "linux")
            .forEach { assertFalse("$it must not be plannable", Plan.allowed(it)) }
    }

    @Test fun versionIsCurrent() = assertEquals("0.8.0", VERSION)
}
