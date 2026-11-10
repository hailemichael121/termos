package dev.termos

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/** Memory: recreating the UI must not keep old Activity instances alive. */
class LeakTest : TermTest() {

    @Test fun recreatedActivitiesAreGarbageCollected() {
        val refs = mutableListOf<WeakReference<Activity>>()
        repeat(8) {
            scenario.onActivity { refs.add(WeakReference(it)) }
            scenario.recreate()
        }
        scenario.close()
        repeat(15) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(150)
        }
        val leaked = refs.count { it.get() != null }
        assertTrue("leaked $leaked of ${refs.size} Activity instances", leaked <= 1)
        // re-launch so tearDown() has something to close
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test fun sessionHoldsNoActivityAfterStop() {
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)   // -> onStop
        assertTrue("sink must be detached after onStop", Session.sink == null)
        assertTrue("state listener must be detached", Session.stateListener == null)
        assertTrue("permission requester must be detached", Session.shell.permRequester == null)
    }
}
