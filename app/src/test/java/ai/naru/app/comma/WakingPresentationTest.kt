package ai.naru.app.comma

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakingPresentationTest {
    private class Clock : RestClock {
        var elapsed=0L
        var wall=1_800_000_000_000L
        override fun elapsedMillis()=elapsed
        override fun wallMillis()=wall
        fun advance(ms:Long) {elapsed+=ms;wall+=ms}
    }
    private val clock=Clock()
    private val cabin=AppCabinController()
    private val original=CabinSettings(0.8f,0.6f,RestSound.SOFT_TONE)
    private val manager=RestSessionManager(clock,cabin) {"waking-ui"}
    private fun wake(delay:Long=0) {
        cabin.apply(original)
        manager.selectMinutes(1)
        manager.configure(CabinSettings(0.1f,0.2f))
        manager.start()
        clock.advance(60_000+delay);manager.tick()
    }
    private fun dispatch(action:()->Unit)=dispatchWakingAction({manager.state.value.phase},action)

    @Test fun reentryKeepsWakingAndConfirmationSnapshot() {
        wake(5_000)
        repeat(3) {
            val ui=RestUiState(session=manager.state.value)
            assertEquals(CommaDestination.WAKING,commaDestination(ui,it==0))
            assertEquals(25L,wakingConfirmationSeconds(ui.session))
        }
        assertEquals(25_000L,manager.state.value.confirmationRemainingMillis)
    }
    @Test fun delayedEntryDisplaysDeadlineRatherThanEntryTime() {
        val deadline=clock.wall+60_000
        wake(12_345)
        assertEquals(deadline,manager.state.value.expectedEndMillis)
        assertNotEquals(clock.wall,manager.state.value.expectedEndMillis)
        assertEquals(18L,wakingConfirmationSeconds(manager.state.value))
    }
    @Test fun fiveMinuteExtensionReturnsToRestingWithUpdatedRing() {
        wake(5_000)
        assertTrue(dispatch {assertTrue(manager.extend(5))})
        val state=manager.state.value
        assertEquals(RestPhase.RESTING,state.phase)
        assertEquals(300_000L,state.remainingMillis)
        assertEquals(6,state.plannedMinutes)
        assertEquals(300f/360f,restingRemainingFraction(state),0.0001f)
        assertEquals(CommaDestination.RESTING,commaDestination(RestUiState(session=state),false))
        assertEquals(clock.wall+300_000,state.expectedEndMillis)
    }
    @Test fun tenMinuteExtensionReturnsToRestingAndResetsRamp() {
        wake(10_000)
        assertTrue(dispatch {assertTrue(manager.extend(10))})
        val state=manager.state.value
        assertEquals(600_000L,state.remainingMillis)
        assertEquals(11,state.plannedMinutes)
        assertEquals(600f/660f,restingRemainingFraction(state),0.0001f)
        assertEquals(0.1f,cabin.settings.value.brightness,0.0001f)
        assertEquals(0L,state.confirmationRemainingMillis)
    }
    @Test fun confirmationExpiresAtExactlyThirtySecondsAndRestores() {
        wake()
        assertEquals(30L,wakingConfirmationSeconds(manager.state.value))
        clock.advance(29_999);assertNull(manager.tick())
        assertEquals(1L,wakingConfirmationSeconds(manager.state.value))
        clock.advance(1)
        val record=manager.tick()!!
        assertEquals(RestPhase.FINISHED,manager.state.value.phase)
        assertEquals(EndReason.TIMER,record.reason)
        assertEquals(60_000L,record.elapsedMillis)
        assertEquals(original,cabin.settings.value)
        assertNull(manager.tick())
    }
    @Test fun repeatedFinishAndCleanupSaveExactlyOneRecord() = runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {
            val records=mutableListOf<RestRecord>()
            val repo=object:RestHistoryRepository {
                override suspend fun save(record:RestRecord) {records.add(record)}
                override suspend fun recent():List<RestRecord> = records.toList()
            }
            val history=RestHistoryStore(repo,scope)
            val lifecycle=RestSessionLifecycle(manager,history)
            wake(1_000)
            assertTrue(dispatch(lifecycle::finish))
            assertFalse(dispatch(lifecycle::finish))
            lifecycle.finish();lifecycle.tick();lifecycle.close()
            advanceUntilIdle()
            assertEquals(1,records.size)
            assertEquals(EndReason.TIMER,records.single().reason)
            assertEquals(original,cabin.settings.value)
        } finally {scope.cancel()}
    }
    @Test fun callbacksFromOldWakingScreenCannotExtendOrFinishResting() {
        wake()
        dispatch {manager.extend(5)}
        var calls=0
        assertFalse(dispatch {calls++;manager.extend(10)})
        assertFalse(dispatch {calls++;manager.finish()})
        assertEquals(0,calls)
        assertEquals(RestPhase.RESTING,manager.state.value.phase)
        assertEquals(6,manager.state.value.plannedMinutes)
    }
    @Test fun lateExtensionIsRejectedByOriginalDeadlinePolicy() {
        wake()
        clock.advance(30_000)
        assertFalse(manager.extend(5))
        assertNotNull(manager.tick())
        assertFalse(dispatch {manager.finish()})
        assertEquals(RestPhase.FINISHED,manager.state.value.phase)
    }
    @Test fun confirmationPresentationClampsInvalidOrInactiveData() {
        assertEquals(0L,wakingConfirmationSeconds(RestSession()))
        assertEquals(0L,wakingConfirmationSeconds(RestSession(phase=RestPhase.WAKING,
            confirmationRemainingMillis=-1)))
        assertEquals(30L,wakingConfirmationSeconds(RestSession(phase=RestPhase.WAKING,
            confirmationRemainingMillis=45_000)))
    }
}
