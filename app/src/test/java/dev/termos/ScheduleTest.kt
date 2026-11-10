package dev.termos

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(d: Int, h: Int, m: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, d, h, m, 0) }.timeInMillis
    private val now = at(10, 6, 0)

    @Test fun nextAtToday() = assertEquals(at(10, 7, 30), Schedule.nextAt(7, 30, now, utc))
    @Test fun nextAtTomorrowWhenPassed() = assertEquals(at(11, 5, 0), Schedule.nextAt(5, 0, now, utc))
    @Test fun nextAtExactlyNowMeansTomorrow() = assertEquals(at(11, 6, 0), Schedule.nextAt(6, 0, now, utc))

    @Test fun parseTime() {
        assertEquals(7 to 5, Schedule.parseTime("7:05"))
        assertEquals(23 to 59, Schedule.parseTime("23:59"))
        assertNull(Schedule.parseTime("24:00"))
        assertNull(Schedule.parseTime("7:5"))
        assertNull(Schedule.parseTime("abc"))
    }

    @Test fun createAt() {
        val j = Schedule.create(1, "at", listOf("07:30", "tsh", "morning"), now, utc)!!
        assertEquals(SchedJob(1, "at", "07:30", at(10, 7, 30), "tsh morning"), j)
        val o = Schedule.create(2, "at", listOf("07:30", "once", "notify", "hi"), now, utc)!!
        assertEquals("once", o.kind)
        assertEquals("notify hi", o.cmd)
        assertNull(Schedule.create(3, "at", listOf("07:30"), now, utc))
        assertNull(Schedule.create(3, "at", listOf("99:99", "x"), now, utc))
    }

    @Test fun createEvery() {
        val j = Schedule.create(1, "every", listOf("30m", "notify", "x"), now, utc)!!
        assertEquals(now + 1_800_000L, j.next)
        assertNull(Schedule.create(2, "every", listOf("30s", "notify", "x"), now, utc))
        assertNull(Schedule.create(2, "every", listOf("30m"), now, utc))
        assertNull(Schedule.create(2, "sometimes", listOf("30m", "x"), now, utc))
    }

    @Test fun nextAfterFiring() {
        val daily = SchedJob(1, "at", "07:30", 0, "x")
        assertEquals(at(11, 7, 30), Schedule.nextAfter(daily, at(10, 7, 30), utc))
        val every = SchedJob(2, "every", "2h", 0, "x")
        assertEquals(now + 7_200_000L, Schedule.nextAfter(every, now, utc))
        assertNull(Schedule.nextAfter(SchedJob(3, "once", "07:30", 0, "x"), now, utc))
    }

    @Test fun serialization() {
        val j = SchedJob(3, "every", "30m", 123L, "notify hi")
        assertEquals(j, SchedJob.parse(j.serialize()))
        assertEquals("notify a b", SchedJob.parse(SchedJob(1, "at", "1:00", 1, "notify a\tb").serialize())!!.cmd.replace("  ", " "))
        assertNull(SchedJob.parse("garbage"))
        assertNull(SchedJob.parse("x\tat\t07:30\t1\tcmd"))
    }

    @Test fun headlessWhitelist() {
        listOf("echo", "notify", "tsh", "timer", "calc", "battery").forEach { assertTrue(it, Headless.ok(it)) }
        listOf("sms", "sudo", "open", "ls", "linux", "script", "nonsense", "man").forEach { assertFalse(it, Headless.ok(it)) }
    }

    @Test fun phoneNumbers() {
        assertTrue(PhoneNum.looksLikeNumber("+251 91 234 5678"))
        assertTrue(PhoneNum.looksLikeNumber("0911234567"))
        assertFalse(PhoneNum.looksLikeNumber("mom"))
        assertFalse(PhoneNum.looksLikeNumber("12"))
        assertEquals("+251912345678", PhoneNum.normalize("+251 (91) 234-5678"))
    }

    @Test fun oemTips() {
        assertTrue(Oem.tip("TECNO").contains("Recents"))
        assertTrue(Oem.tip("Infinix").contains("Autostart"))
        assertTrue(Oem.tip("samsung").contains("Never sleeping"))
        assertTrue(Oem.tip("Xiaomi").contains("Autostart"))
        assertTrue(Oem.tip("Fairphone").contains("dontkillmyapp.com"))
    }
}
