package dev.termos

import org.junit.Test

/** AC-01: a fresh install walks the user through password + wizard. */
class FirstBootTest : TermTest() {
    override val startWithPassword = false

    @Test fun ac01_firstBootCreatesPasswordAndRunsWizard() {
        screenHas("first boot")
        type("abc");     screenHas("min 6 characters")
        type(PW); type(PW)
        screenHas("password set")
        screenHas("quick setup")
        type("nord"); screenHas("theme -> nord")
        type("y")
        type("")
        screenHas("setup complete")
        promptHas("@termos")
        type("config"); screenHas("vim      : on")
    }

    @Test fun ac01b_passwordMismatchRestarts() {
        type(PW); type("different")
        screenHas("mismatch")
        promptHas("new password")
    }
}
