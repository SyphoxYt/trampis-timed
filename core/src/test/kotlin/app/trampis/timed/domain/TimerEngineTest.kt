package app.trampis.timed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {
    @Test fun elapsedTimeComesFromMonotonicTimestamps() {
        var now = 1_000_000_000L
        val timer = TimerEngine { now }
        timer.start()
        now += 1_234_567_890L
        assertEquals(1_234_567_890L, timer.elapsedNanos())
        assertEquals(1_234_567_890L, timer.stop()!!)
        assertFalse(timer.isRunning)
    }

    @Test fun repeatedStartDoesNotReplaceTheOriginalTimestamp() {
        var now = 10L
        val timer = TimerEngine { now }
        timer.start()
        now += 500L
        timer.start()
        now += 500L
        assertEquals(1_000L, timer.stop()!!)
    }

    @Test fun stopIsIdempotentAndResetClearsTheResult() {
        var now = 0L
        val timer = TimerEngine { now }
        assertNull(timer.stop())
        timer.start()
        now = 42L
        assertEquals(42L, timer.stop()!!)
        assertNull(timer.stop())
        timer.reset()
        assertEquals(0L, timer.elapsedNanos())
    }

    @Test fun inspectionUsesTheSameMonotonicClockAndTransitionsIntoRun() {
        var now = 2_000L
        val timer = TimerEngine { now }
        timer.startInspection()
        now += 15_500_000_000L
        assertTrue(timer.isInspecting)
        assertEquals(15_500_000_000L, timer.inspectionElapsedNanos())
        assertNull(timer.stop())
        timer.start()
        assertEquals(TimerMode.RUNNING, timer.mode)
        assertEquals(0L, timer.inspectionElapsedNanos())
        now += 1_500_000_000L
        assertEquals(1_500_000_000L, timer.stop()!!)
        assertEquals(TimerMode.STOPPED, timer.mode)
    }

    @Test fun aSavedRunningTimestampCanBeRestoredInTheSameBoot() {
        var now = 1_000L
        val timer = TimerEngine { now }
        timer.restoreRunning(400L)
        assertTrue(timer.isRunning)
        assertEquals(600L, timer.elapsedNanos())
    }
}
