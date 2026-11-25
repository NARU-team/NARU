package ai.naru.app.comma

import org.junit.Assert.*
import org.junit.Test

class RestSessionManagerTest {
    private class Clock : RestClock {
        var elapsed = 0L
        var wall = 1_800_000_000_000L
        override fun elapsedMillis() = elapsed
        override fun wallMillis() = wall
        fun advance(ms: Long) { elapsed += ms; wall += ms }
    }
    private val clock = Clock()
    private val cabin = AppCabinController()
    private val original = CabinSettings(0.8f, 0.6f, RestSound.SOFT_TONE)
    private val manager = RestSessionManager(clock, cabin) { "session-1" }

    private fun start(minutes: Int = 15) {
        cabin.apply(original)
        manager.selectMinutes(minutes)
        manager.configure(CabinSettings(0.1f, 0.2f, RestSound.SILENT))
        assertTrue(manager.start())
    }

    @Test fun delayedTicksUseElapsedTimeAndIgnoreWallClockChanges() {
        start()
        clock.advance(73_456)
        clock.wall -= 3_600_000
        manager.tick()
        assertEquals(900_000 - 73_456L, manager.state.value.remainingMillis)
        assertEquals(clock.wall + manager.state.value.remainingMillis, manager.state.value.expectedEndMillis)
    }
    @Test fun extensionsAccumulateAndUpdateExpectedEnd() {
        start()
        clock.advance(60_000)
        assertTrue(manager.extend(5))
        assertTrue(manager.extend(10))
        assertEquals(30, manager.state.value.plannedMinutes)
        assertEquals(15, manager.state.value.selectedMinutes)
        assertEquals(29 * 60_000L, manager.state.value.remainingMillis)
        assertEquals(clock.wall + 29 * 60_000L, manager.state.value.expectedEndMillis)
    }
    @Test fun extendsAtZeroWithoutLosingOriginalSettings() {
        start(1)
        clock.advance(65_000)
        manager.tick()
        assertEquals(RestPhase.WAKING, manager.state.value.phase)
        assertTrue(manager.extend(5))
        assertEquals(RestPhase.RESTING, manager.state.value.phase)
        assertEquals(300_000L, manager.state.value.remainingMillis)
        manager.finish()
        assertEquals(original, cabin.settings.value)
    }
    @Test fun deadlineShowsWakeAlertThenAutomaticallyRestoresAndReturnsOneRecord() {
        start(1)
        clock.advance(60_000)
        assertNull(manager.tick())
        assertEquals(RestPhase.WAKING, manager.state.value.phase)
        clock.advance(30_000)
        val record = manager.tick()!!
        assertEquals(EndReason.TIMER, record.reason)
        assertEquals(60_000L, record.elapsedMillis)
        assertEquals(record.startedAt + 60_000, record.endedAt)
        assertEquals(original, cabin.settings.value)
        assertEquals(RestPhase.FINISHED, manager.state.value.phase)
        assertNull(manager.tick())
        assertNull(manager.finish())
    }
    @Test fun manualStopRestoresAllSettingsAndMeasuresActualTime() {
        start()
        clock.advance(12_345)
        val record = manager.finish()!!
        assertEquals(12_345L, record.elapsedMillis)
        assertEquals(EndReason.MANUAL, record.reason)
        assertEquals(original, cabin.settings.value)
        assertFalse(manager.extend(5))
    }
    @Test fun brightnessRampsDuringLastThirtySecondsAndExtensionResetsIt() {
        start(1)
        clock.advance(30_000)
        manager.tick()
        assertEquals(0.1f, cabin.settings.value.brightness, 0.001f)
        clock.advance(15_000)
        manager.tick()
        assertEquals(0.45f, cabin.settings.value.brightness, 0.001f)
        manager.extend(5)
        assertEquals(0.1f, cabin.settings.value.brightness, 0.001f)
        manager.finish()
        assertEquals(0.8f, cabin.settings.value.brightness, 0.001f)
    }
    @Test fun repeatedStartDoesNotOverwriteSnapshot() {
        start()
        assertFalse(manager.start())
        manager.configure(CabinSettings(1f, 1f))
        manager.finish()
        assertEquals(original, cabin.settings.value)
    }
    @Test fun lateTickFinishesWithoutOvercountingBackgroundDelay() {
        start(1)
        clock.advance(5 * 60_000)
        assertFalse(manager.extend(10))
        assertEquals(60_000L, manager.tick()!!.elapsedMillis)
        assertEquals(original, cabin.settings.value)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidCustomDurationRejected() { manager.selectMinutes(0) }

    @Test(expected = IllegalArgumentException::class)
    fun invalidExtensionRejected() { manager.extend(180) }

    @Test fun recommendationUsesTemperaturesBatteryAndParking() {
        assertEquals(24f, recommendClimate(VehicleState(28f, 32f, true, 75)).targetCelsius)
        assertEquals(22f, recommendClimate(VehicleState(15f, 5f, true, 75)).targetCelsius)
        assertNull(recommendClimate(VehicleState(28f, 32f, false, 75)).targetCelsius)
        assertNull(recommendClimate(VehicleState(28f, 32f, true, 10)).targetCelsius)
        assertNull(recommendClimate(VehicleState(null, 32f, true, 75)).targetCelsius)
    }
}
