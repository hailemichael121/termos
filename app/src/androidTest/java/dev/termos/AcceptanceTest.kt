package dev.termos

import org.junit.Test

/** Acceptance criteria AC-01 .. AC-16, see docs/ACCEPTANCE.md */
class AcceptanceTest : TermTest() {

    @Test fun ac02_loginAcceptsRightPassword_deniesWrong() {
        screenHas("terminal locked")
        type("nope-nope"); screenHas("access denied")
        login()
        promptHas("@termos")
    }

    @Test fun ac03_lockoutAfterThreeFailures() {
        repeat(3) { type("wrong-pw") }
        type(PW)
        screenHas("too many attempts")
    }

    @Test fun ac04_helpListsCommandGroups() {
        login(); type("help")
        screenHas("[apps]"); screenHas("[linux]"); screenHas("[tools]")
    }

    @Test fun ac05_pipeFiltersOutput() {
        login(); type("echo hello | wc")
        screenHas("1 1 5")
    }

    @Test fun ac06_chainingRunsAllStatements() {
        login(); type("calc 6*7; calc 100/4")
        screenHas("42"); screenHas("25")
    }

    @Test fun ac07_aliasesWorkAndPersist() {
        login(); type("alias hi='calc 6*7'"); type("hi")
        screenHas("42")
        type("which hi"); screenHas("alias for")
    }

    @Test fun ac08_themeChangesLive() {
        login(); type("theme dracula")
        screenHas("theme -> dracula")
        type("config"); screenHas("theme    : dracula")
    }

    @Test fun ac09_vimModeSwitching() {
        login(); type("vim on")
        statusHas("INSERT")
        tap(R.id.btnEsc); statusHas("NORMAL")
        onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.input))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("i"))
        statusHas("INSERT")
    }

    @Test fun ac10_lockCommandRelocks() {
        login(); type("lock")
        promptHas("password: ")
    }

    @Test fun ac11_sessionSurvivesRotation() {
        login(); type("echo still-here")
        scenario.recreate()
        promptHas("@termos")
        screenHas("access granted"); screenHas("still-here")
    }

    @Test fun ac12_didYouMean() {
        login(); type("hepl")
        screenHas("did you mean 'help'")
    }

    @Test fun ac13_rootCommandsNeedSudo() {
        login(); type("settings")
        screenHas("permission denied")
        type("sudo -k"); screenHas("sudo timestamp cleared")
    }

    @Test fun ac14_todoRoundTrip() {
        login(); type("todo add buy milk"); type("todo")
        screenHas("[ ] buy milk")
        type("todo done 1"); type("todo"); screenHas("[x] buy milk")
    }

    @Test fun ac15_historyRerun() {
        login(); type("calc 2+2"); type("!!")
        screenHas("calc 2+2")
    }

    @Test fun ac16_cmdsShowsAvailability() {
        login(); type("cmds")
        screenHas("needs key")
        screenHas("ready")
    }
}
