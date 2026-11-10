package dev.termos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeHost(private val results: Map<String, Pair<Boolean, String>> = emptyMap()) : ScriptHost {
    val ran = mutableListOf<String>()
    val shown = mutableListOf<String>()
    var slept = 0L
    override fun exec(cmd: String): Pair<Boolean, String> {
        ran.add(cmd)
        return results[cmd] ?: (true to if (cmd.startsWith("echo ")) cmd.removePrefix("echo ") else "")
    }
    override fun show(s: String) { shown.add(s) }
    override fun builtins() = mapOf("BATTERY" to "15", "USER" to "yihun")
    override suspend fun sleep(sec: Long) { slept += sec }
}

class ScriptTest {
    private fun run(lines: List<String>, host: FakeHost = FakeHost(), args: List<String> = emptyList()) =
        runBlocking { Script.run(lines, args, host) }

    @Test fun variablesAndExpansion() {
        val h = FakeHost()
        run(listOf("set n=5", "echo n=\$n", "name=Yihun", "echo hi \${name} \$USER"), h)
        assertEquals(listOf("n=5", "hi Yihun yihun"), h.shown)
    }

    @Test fun positionalArgs() {
        val h = FakeHost()
        run(listOf("echo \$1-\$2 \$@"), h, args = listOf("a", "b"))
        assertEquals(listOf("a-b a b"), h.shown)
    }

    @Test fun ifElse() {
        val h = FakeHost()
        run(listOf("if \$BATTERY < 20 then echo low else echo ok", "if 5 > 9 then echo yes else echo no", "if abc contains B then echo hit"), h)
        assertEquals(listOf("low", "no", "hit"), h.shown)
    }

    @Test fun repeatRunsBodyNTimes() {
        val h = FakeHost()
        run(listOf("repeat 3 do echo x"), h)
        assertEquals(listOf("x", "x", "x"), h.shown)
    }

    @Test fun chainingAndStatus() {
        val h = FakeHost(mapOf("fail" to (false to "")))
        run(listOf("fail && echo no", "fail || echo yes", "echo a ; echo b", "fail", "echo status=\$?"), h)
        assertEquals(listOf("yes", "a", "b", "status=1"), h.shown)
    }

    @Test fun commandSubstitution() {
        val h = FakeHost(mapOf("battery" to (true to "battery: 85%")))
        run(listOf("set b=\$(battery)", "echo \$b"), h)
        assertEquals(listOf("battery: 85%"), h.shown)
    }

    @Test fun exitStopsTheScript() {
        val h = FakeHost()
        val r = run(listOf("echo a", "exit", "echo b"), h)
        assertEquals(listOf("a"), h.shown)
        assertNull(r.stopped)
    }

    @Test fun sleepAccumulates() {
        val h = FakeHost()
        run(listOf("sleep 5", "sleep 2m"), h)
        assertEquals(125L, h.slept)
    }

    @Test fun stepLimitStopsRunaways() {
        val r = run((1..30).map { "repeat 100 do echo x" })
        assertNotNull(r.stopped)
        assertTrue(r.stopped!!.contains("step limit"))
    }

    @Test fun nestedIfInChainIsAnError() {
        val h = FakeHost()
        run(listOf("echo a && if 1 == 1 then echo b"), h)
        assertTrue(h.shown.any { it.startsWith("error:") })
    }

    // ----- pure helpers
    @Test fun expandRules() {
        val v = mapOf("name" to "Yihun", "?" to "0")
        assertEquals("hi Yihun", Vars.expand("hi \$name", v) { "" })
        assertEquals("Yihunx", Vars.expand("\${name}x", v) { "" })
        assertEquals("cost \$5", Vars.expand("cost \\\$5", v) { "" })
        assertEquals("'\$name'", Vars.expand("'\$name'", v) { "" })
        assertEquals("", Vars.expand("\$missing", v) { "" })
        assertEquals("b=85", Vars.expand("b=\$(battery)", v) { "85" })
        assertEquals("s=0", Vars.expand("s=\$?", v) { "" })
    }

    @Test fun conditions() {
        assertTrue(Cond.eval("5", "<", "10"))
        assertFalse(Cond.eval("10", "<", "5"))
        assertTrue(Cond.eval("a", "==", "a"))
        assertTrue(Cond.eval("2.0", "==", "2"))
        assertTrue(Cond.eval("Hello", "contains", "ell"))
        assertFalse(Cond.eval("x", "??", "y"))
    }

    @Test fun chainSplit() {
        // each part carries the joiner that comes BEFORE it
        assertEquals(listOf("a" to ";", "b" to ";", "c" to "&&", "d" to "||"), Chain.split("a ; b && c || d"))
        val parts = Chain.split("echo 'a && b' && echo c")
        assertEquals(2, parts.size)
        assertEquals("&&", parts[1].second)
        assertEquals(1, Chain.split("echo a | wc").size)
    }

    @Test fun lineParsing() {
        assertTrue(ScriptLine.parse("# hi") is Line.Skip)
        assertTrue(ScriptLine.parse("exit") is Line.Exit)
        assertEquals(Line.Assign("x", "5"), ScriptLine.parse("set x=5"))
        assertEquals(Line.Assign("x", "5"), ScriptLine.parse("x=5"))
        assertEquals(Line.RepeatLine(2, "echo a"), ScriptLine.parse("repeat 2 do echo a"))
        assertEquals(Line.Sleep(5), ScriptLine.parse("sleep 5"))
        assertEquals(Line.IfLine("1", "<", "2", "echo a", "echo b"), ScriptLine.parse("if 1 < 2 then echo a else echo b"))
        assertEquals(Line.IfLine("1", "<", "2", "echo a", null), ScriptLine.parse("if 1 < 2 then echo a"))
        assertEquals(Line.RunLine("echo a=b"), ScriptLine.parse("echo a=b"))
    }
}
