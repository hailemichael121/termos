package dev.termos

import org.junit.Test

/** AC-17 .. AC-24 : setup, timers, jobs, scripts, keep-alive. */
class AutomationTest : TermTest() {

    @Test fun ac17_permsTableListsEverything() {
        login(); type("perms")
        Setup.Id.values().forEach { screenHas(it.title) }
        screenHas("perms <name>")
    }

    @Test fun ac18_timersAreListedAndCancelled() {
        login(); type("timer 5m tea")
        screenHas("timer #1 set")
        type("timer"); screenHas("tea")
        type("timer cancel 1"); screenHas("cancelled")
        type("timer"); screenHas("no timers")
    }

    @Test fun ac19_jobsAreScheduledListedAndRemoved() {
        login(); type("at 07:30 notify hello")
        screenHas("job #1")
        type("job"); screenHas("daily at 07:30")
        type("every 30s notify x"); screenHas("usage: every")
        type("at 08:00 sms send abebe hi"); screenHas("not allowed")
        type("job rm 1"); screenHas("removed")
    }

    @Test fun ac20_scriptRoundTrip() {
        login(); type("script new demo")
        screenHas("editing 'demo'")
        type("set n=5")
        type("calc \$n*1111")
        type("if \$n > 3 then calc 7*6*101 else calc 1+1")
        type("repeat 2 do calc 91*91")
        type(".")
        screenHas("saved script 'demo'")
        type("tsh demo")
        screenHas("5555"); screenHas("4242"); screenHas("8281")
        type("script ls"); screenHas("demo")
    }

    @Test fun ac21_scriptsCannotNestOrEscalate() {
        login(); type("script new nest")
        type("tsh nest"); type("sudo settings"); type(".")
        type("tsh nest")
        screenHas("nested scripts are not allowed")
        screenHas("not allowed in scripts")
    }

    @Test fun ac22_keepAliveToggle() {
        login(); type("keepalive off")
        screenHas("keep-alive off")
        type("keepalive"); screenHas("keep-alive: off")
        type("keepalive on"); screenHas("keep-alive on")
        type("keepalive off")
    }

    @Test fun ac23_notifyIsHandled() {
        login(); type("notify hello")
        screenHas("notif")
    }

    @Test fun ac24_notifsWatchToggleNeedsNoAccess() {
        login(); type("notifs watch on")
        screenHas("notif echo on")
        type("notifs watch off")
    }
}
