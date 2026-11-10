package dev.termos

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before

const val PW = "secret1"

/** Shared helpers. Every test starts from a clean Session. */
abstract class TermTest {
    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected open val startWithPassword: Boolean = true

    @Before fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Session.resetForTest(if (startWithPassword) PW else null)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() { scenario.close() }

    protected fun ctx(): Context = ApplicationProvider.getApplicationContext()

    protected fun type(s: String) {
        onView(withId(R.id.input)).perform(replaceText(s), pressImeActionButton())
    }

    protected fun screenHas(s: String) {
        onView(withId(R.id.screen)).check(matches(withText(containsString(s))))
    }

    protected fun statusHas(s: String) {
        onView(withId(R.id.status)).check(matches(withText(containsString(s))))
    }

    protected fun promptHas(s: String) {
        onView(withId(R.id.prompt)).check(matches(withText(containsString(s))))
    }

    protected fun login() { type(PW); screenHas("access granted") }

    protected fun tap(id: Int) { onView(withId(id)).perform(click()) }
}
