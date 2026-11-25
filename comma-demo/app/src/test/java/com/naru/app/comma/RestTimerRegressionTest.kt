package com.naru.app.comma

import org.junit.Assert.*
import org.junit.Test

class RestTimerRegressionTest {
    private class Clock : RestClock {
        var now = 0L
        override fun elapsedMillis() = now
        override fun wallMillis() = 1_800_000_000_000L + now
    }
    private val clock = Clock()
    private val cabin = AppCabinController()
    private val original = cabin.settings.value
    private val manager = RestSessionManager(clock, cabin)

    private fun start() { manager.selectMinutes(1); manager.start() }

    @Test fun expectedEndRemainsFixedThroughoutConfirmation() {
        start()
        val end = manager.state.value.expectedEndMillis
        clock.now = 60_000
        manager.tick()
        assertEquals(30_000L, manager.state.value.confirmationRemainingMillis)
        clock.now = 75_000
        manager.tick()
        assertEquals(end, manager.state.value.expectedEndMillis)
        assertEquals(15_000L, manager.state.value.confirmationRemainingMillis)
    }
    @Test fun extensionOneMillisecondBeforeGraceEndPreservesSnapshot() {
        start()
        clock.now = 89_999
        assertTrue(manager.extend(10))
        assertEquals(600_000L, manager.state.value.remainingMillis)
        assertEquals(0L, manager.state.value.confirmationRemainingMillis)
        assertEquals(11, manager.state.value.plannedMinutes)
        manager.finish()
        assertEquals(original, cabin.settings.value)
    }
    @Test fun extensionAtExactGraceEndIsRejectedAndTimerFinalizesOnce() {
        start()
        clock.now = 90_000
        assertFalse(manager.extend(5))
        val record = manager.tick()!!
        assertEquals(EndReason.TIMER, record.reason)
        assertEquals(60_000L, record.elapsedMillis)
        assertEquals(0L, manager.state.value.confirmationRemainingMillis)
        assertNull(manager.finish())
        assertEquals(original, cabin.settings.value)
    }
    @Test fun manualFinishDuringConfirmationKeepsTimerReasonAndExcludesGrace() {
        start()
        clock.now = 77_000
        val record = manager.finish()!!
        assertEquals(EndReason.TIMER, record.reason)
        assertEquals(60_000L, record.elapsedMillis)
        assertEquals(original, cabin.settings.value)
    }
    @Test fun cleanupBeforeDeadlineUsesExplicitReasonAndIsIdempotent() {
        start()
        clock.now = 10_000
        val record = manager.finish(EndReason.VIEWMODEL_CLEARED)!!
        assertEquals(EndReason.VIEWMODEL_CLEARED, record.reason)
        assertEquals(10_000L, record.elapsedMillis)
        assertNull(manager.finish(EndReason.VIEWMODEL_CLEARED))
        assertEquals(original, cabin.settings.value)
    }
    @Test fun extensionDoesNotChangeNextSessionSelectionOrReuseRecordId() {
        start()
        manager.extend(5)
        val first = manager.finish()!!
        assertEquals(6, first.plannedMinutes)
        assertEquals(1, manager.state.value.selectedMinutes)
        manager.start()
        assertEquals(60_000L, manager.state.value.remainingMillis)
        assertNotEquals(first.id, manager.finish()!!.id)
        assertEquals(original, cabin.settings.value)
    }
}
