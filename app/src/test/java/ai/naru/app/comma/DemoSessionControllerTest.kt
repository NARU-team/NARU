package ai.naru.app.comma

import org.junit.Assert.*
import org.junit.Test

class DemoSessionControllerTest {
    private class Clock : RestClock {
        var now = 0L
        override fun elapsedMillis() = now
        override fun wallMillis() = 1_800_000_000_000L + now
    }
    private val clock = Clock()
    private val demo = DemoSessionController(clock)

    @Test fun demoSkipsDoNotChangeProductionClockSessionOrCabin() {
        val realCabin = AppCabinController()
        val real = RestSessionManager(clock, realCabin)
        real.start()
        val original = realCabin.settings.value
        demo.start(15, CabinSettings(0.1f))
        demo.advanceToLastThirtySeconds()
        assertEquals(30_000L, demo.state.value.remainingMillis)
        real.tick()
        assertEquals(900_000L, real.state.value.remainingMillis)
        assertEquals(0L, clock.now)
        assertEquals(1_800_000_000_000L, clock.wallMillis())
        assertEquals(original, realCabin.settings.value)
    }
    @Test fun demoShowsRampDeadlineAndAutomaticRestoration() {
        val original = demo.cabin.settings.value
        demo.start(45, CabinSettings(0.1f))
        demo.advanceToLastThirtySeconds()
        clock.now += 15_000
        demo.tick()
        assertEquals(0.4f, demo.cabin.settings.value.brightness, 0.001f)
        demo.advanceToDeadline()
        assertEquals(RestPhase.WAKING, demo.state.value.phase)
        assertEquals(30_000L, demo.state.value.confirmationRemainingMillis)
        demo.expireConfirmation()
        assertEquals(RestPhase.FINISHED, demo.state.value.phase)
        assertEquals(original, demo.cabin.settings.value)
        assertTrue(demo.state.value.completed!!.id.startsWith("demo-"))
    }
    @Test fun bothExtensionsUseRealManagerAndManualStopRestoresDemoSettings() {
        val original = demo.cabin.settings.value
        demo.start(15, CabinSettings(0.1f))
        demo.advanceToDeadline()
        assertTrue(demo.extend(5))
        assertEquals(300_000L, demo.state.value.remainingMillis)
        assertTrue(demo.extend(10))
        assertEquals(900_000L, demo.state.value.remainingMillis)
        assertEquals(30, demo.state.value.plannedMinutes)
        demo.finish()
        assertEquals(EndReason.MANUAL, demo.state.value.completed!!.reason)
        assertEquals(original, demo.cabin.settings.value)
    }
    @Test fun jumpsOutsideApplicablePhasesAreNoOps() {
        demo.advanceToDeadline()
        demo.advanceToLastThirtySeconds()
        demo.expireConfirmation()
        assertEquals(RestPhase.READY, demo.state.value.phase)
        demo.start(15, CabinSettings())
        demo.expireConfirmation()
        assertEquals(900_000L, demo.state.value.remainingMillis)
    }
    @Test fun expiredDemoCannotExtendAndCanRestartWithFreshSnapshot() {
        val original = demo.cabin.settings.value
        demo.start(30, CabinSettings(0.1f))
        demo.advanceToDeadline()
        demo.expireConfirmation()
        assertFalse(demo.extend(5))
        demo.start(15, CabinSettings(0.15f))
        demo.finish()
        assertEquals(original, demo.cabin.settings.value)
    }
}
