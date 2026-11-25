package ai.naru.app.comma

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinishedPresentationTest {
    private class Clock:RestClock {
        var now=0L
        override fun elapsedMillis()=now
        override fun wallMillis()=1_800_000_000_000+now
    }
    private class Cabin:CabinController {
        override val settings=MutableStateFlow(CabinSettings(0.8f,0.4f,RestSound.SOFT_TONE))
        var calls=0
        var fail=false
        var ignore=false
        override fun apply(settings:CabinSettings) {
            calls++
            if(fail) error("restore failed")
            if(!ignore) this.settings.value=settings
        }
    }
    private val clock=Clock()
    private val cabin=Cabin()
    private var sequence=0
    private val manager=RestSessionManager(clock,cabin) {"finished-${++sequence}"}
    private fun start() {manager.selectMinutes(1);manager.start()}
    private fun ui(history:RestHistoryState=RestHistoryState())=
        RestUiState(session=manager.state.value,history=history)

    @Test fun reentryDoesNotFinishRestoreOrEnqueueAgain() {
        start();clock.now=12_345
        val record=manager.finish()!!
        val calls=cabin.calls
        repeat(5) {
            assertEquals(CommaDestination.FINISHED,commaDestination(ui(),it%2==0))
            finishedSaveStatus(ui())
            assertEquals(record,ui().session.completed)
            assertEquals(12_345L,ui().session.completed!!.elapsedMillis)
        }
        assertEquals(calls,cabin.calls)
        assertNull(manager.finish())
        assertNull(manager.tick())
    }
    @Test fun successMeansAppStateOnlyAndNeverHardwareSuccess() {
        val original=cabin.settings.value
        start();manager.finish()
        val result=manager.state.value.restoration!!
        assertEquals(original,cabin.settings.value)
        assertEquals(RestorationOutcome.SUCCESS,result.appSettings)
        assertEquals(RestorationOutcome.UNKNOWN,result.windowAndAudio)
        assertEquals(RestorationOutcome.UNSUPPORTED,result.vehicle)
    }
    @Test fun restoreExceptionStillCompletesAndReturnsRecordOnce() {
        start();cabin.fail=true
        val record=manager.finish()
        assertNotNull(record)
        assertEquals(RestPhase.FINISHED,manager.state.value.phase)
        assertEquals(RestorationOutcome.FAILURE,manager.state.value.restoration!!.appSettings)
        assertNull(manager.finish())
    }
    @Test fun mismatchedOrMissingRestorationEvidenceIsNotSuccess() {
        val old=cabin.settings.value
        cabin.apply(CabinSettings())
        cabin.ignore=true
        assertEquals(RestorationOutcome.FAILURE,restoreAppSettings(cabin,old).appSettings)
        assertEquals(RestorationOutcome.UNKNOWN,restoreAppSettings(cabin,null).appSettings)
        assertEquals(RestorationOutcome.UNKNOWN,RestorationReport().appSettings)
    }
    @Test fun prepareNextIsAllowedOnlyAfterCompletionAndDoesNotRestoreAgain() {
        assertFalse(manager.prepareNext())
        start();assertFalse(manager.prepareNext())
        clock.now=60_000;manager.tick();assertFalse(manager.prepareNext())
        manager.finish()
        val calls=cabin.calls
        assertTrue(manager.prepareNext())
        assertEquals(RestPhase.READY,manager.state.value.phase)
        assertNull(manager.state.value.completed)
        assertNull(manager.state.value.restoration)
        assertEquals(0,manager.state.value.plannedMinutes)
        assertFalse(manager.prepareNext())
        assertEquals(calls,cabin.calls)
    }
    @Test fun failedSaveSurvivesResetAndNextSessionAndRetryDoesNotDuplicate() = runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {
            val saved=linkedMapOf<String,RestRecord>()
            var fail=true
            val repo=object:RestHistoryRepository {
                override suspend fun save(record:RestRecord) {
                    if(fail) error("disk")
                    saved[record.id]=record
                }
                override suspend fun recent():List<RestRecord> = saved.values.toList()
            }
            val store=RestHistoryStore(repo,scope)
            val lifecycle=RestSessionLifecycle(manager,store)
            start();clock.now=12_345;lifecycle.finish()
            val first=manager.state.value.completed!!
            runCurrent()
            assertEquals(1,store.state.value.pendingCount)
            assertNotNull(store.state.value.error)
            assertTrue(manager.prepareNext())
            assertEquals(1,store.state.value.pendingCount)
            assertTrue(manager.start())
            clock.now+=1_000;lifecycle.finish()
            fail=false;store.retry();advanceUntilIdle()
            assertEquals(2,saved.size)
            assertEquals(first,saved[first.id])
            lifecycle.finish();lifecycle.close();store.retry();advanceUntilIdle()
            assertEquals(2,saved.size)
            assertEquals(0,store.state.value.pendingCount)
        } finally {scope.cancel()}
    }
    @Test fun saveStatusRequiresAcknowledgedRecordAndSeparatesDemo() {
        start();manager.finish()
        val record=manager.state.value.completed!!
        assertTrue(finishedSaveStatus(ui(RestHistoryState(pendingCount=1))).contains("저장 중"))
        assertTrue(finishedSaveStatus(ui(RestHistoryState(error="failed"))).contains("확인 필요"))
        assertTrue(finishedSaveStatus(ui(RestHistoryState(records=listOf(record)))).contains("저장 확인"))
        assertTrue(finishedSaveStatus(ui().copy(isDemo=true)).contains("저장 제외"))
        assertTrue(finishedSaveStatus(RestUiState()).contains("확인 불가"))
    }
    @Test fun automaticFinishSummaryUsesRecordDeadlineNotConfirmationExit() {
        start();clock.now=90_000;val record=manager.tick()!!
        assertEquals(60_000L,record.elapsedMillis)
        assertEquals(1_800_000_060_000L,record.endedAt)
        assertEquals(EndReason.TIMER,record.reason)
    }
    @Test fun demoCompletionContainsIndependentRestorationEvidence() {
        val demo=DemoSessionController(clock)
        demo.start(1,CabinSettings())
        demo.advanceToDeadline();demo.expireConfirmation()
        assertEquals(RestPhase.FINISHED,demo.state.value.phase)
        assertEquals(RestorationOutcome.SUCCESS,demo.state.value.restoration!!.appSettings)
        assertEquals(RestPhase.READY,manager.state.value.phase)
        assertNull(manager.state.value.completed)
    }
    @Test fun allRestorationLabelsAreDistinct() {
        assertEquals(setOf("성공","실패","미지원","확인 불가"),
            RestorationOutcome.entries.map(::restorationLabel).toSet())
    }
}
