package ai.naru.app.comma

import org.junit.Assert.*
import org.junit.Test

class RestingPresentationTest {
    private class Clock : RestClock {
        var elapsed = 0L
        var wall = 1_800_000_000_000L
        override fun elapsedMillis() = elapsed
        override fun wallMillis() = wall
        fun advance(ms: Long) { elapsed += ms; wall += ms }
    }
    private val clock = Clock()
    private val cabin = AppCabinController()
    private val manager = RestSessionManager(clock,cabin) { "resting-ui" }
    private fun ui() = RestUiState(session=manager.state.value)
    private fun start() { manager.selectMinutes(1); manager.start() }

    @Test fun remainingRingUsesMonotonicSnapshotAndSurvivesReentry() {
        start()
        assertEquals(1f,restingRemainingFraction(ui().session),0.0001f)
        clock.advance(15_000)
        clock.wall -= 3_600_000
        manager.tick()
        repeat(3) {
            assertEquals(0.75f,restingRemainingFraction(ui().session),0.0001f)
            assertEquals(CommaDestination.RESTING,commaDestination(ui(),it==0))
        }
        assertEquals(45_000L,manager.state.value.remainingMillis)
    }
    @Test fun extensionsUseCumulativePlanWithoutResettingElapsedRest() {
        start()
        clock.advance(30_000); manager.tick()
        assertEquals(0.5f,restingRemainingFraction(ui().session),0.0001f)
        manager.extend(5)
        assertEquals(330f/360f,restingRemainingFraction(ui().session),0.0001f)
        manager.extend(10)
        assertEquals(930f/960f,restingRemainingFraction(ui().session),0.0001f)
        assertEquals(1,ui().session.selectedMinutes)
    }
    @Test fun deadlineAndConfirmationExtensionRouteToCorrectScreens() {
        start()
        clock.advance(65_000); manager.tick()
        assertEquals(CommaDestination.WAKING,commaDestination(ui(),false))
        assertEquals(0f,restingRemainingFraction(ui().session),0f)
        assertTrue(manager.extend(5))
        assertEquals(CommaDestination.RESTING,commaDestination(ui(),false))
        assertEquals(300f/360f,restingRemainingFraction(ui().session),0.0001f)
    }
    @Test fun automaticCompletionAndReadyKeepExistingRoutes() {
        assertEquals(CommaDestination.SETTINGS,commaDestination(ui(),false))
        start()
        clock.advance(90_000);manager.tick()
        assertEquals(CommaDestination.FINISHED,commaDestination(ui(),false))
        assertEquals(CommaDestination.FINISHED,commaDestination(ui(),true))
        assertEquals(CommaDestination.SETTINGS,commaDestination(RestUiState(isDemo=true),true))
    }
    @Test fun repeatedFinishDispatchRestoresAndProducesExactlyOneRecord() {
        val original=cabin.settings.value
        start();clock.advance(12_345);manager.tick()
        var records=0
        val finish = { if(manager.finish()!=null) records++; Unit }
        assertTrue(dispatchRestingFinish({manager.state.value.phase},finish))
        assertFalse(dispatchRestingFinish({manager.state.value.phase},finish))
        assertEquals(1,records)
        assertEquals(original,cabin.settings.value)
        assertEquals(CommaDestination.FINISHED,commaDestination(ui(),false))
    }
    @Test fun staleRestingCallbackCannotFinishWakingOrReady() {
        var calls=0
        RestPhase.entries.filter {it!=RestPhase.RESTING}.forEach { phase ->
            assertFalse(dispatchRestingFinish({phase},{calls++}))
        }
        assertEquals(0,calls)
    }
    @Test fun invalidOrInactiveSnapshotsHaveSafeProgress() {
        assertEquals(0f,restingRemainingFraction(RestSession()),0f)
        assertEquals(0f,restingRemainingFraction(RestSession(phase=RestPhase.RESTING)),0f)
        assertEquals(0f,restingRemainingFraction(RestSession(phase=RestPhase.RESTING,
            plannedMinutes=1,remainingMillis=-1)),0f)
        assertEquals(1f,restingRemainingFraction(RestSession(phase=RestPhase.RESTING,
            plannedMinutes=1,remainingMillis=90_000)),0f)
    }
}
