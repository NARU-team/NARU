package ai.naru.app.comma

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleExceptionTest {
    private val supported=VehicleCapabilities(ControlSupport.SUPPORTED,ControlSupport.SUPPORTED)
    private val driving=VehicleState(parked=false,driving=DrivingStatus.DRIVING)

    @Test fun defaultParkingIsUnknownAndExplicitFalseIsNotParked() {
        assertEquals(ParkingStatus.UNKNOWN,VehicleState().parkingStatus)
        assertEquals(ParkingStatus.PARKED,VehicleState(parked=true).parkingStatus)
        assertEquals(ParkingStatus.NOT_PARKED,VehicleState(parked=false).parkingStatus)
    }
    @Test fun realUnknownCannotBeOverriddenByLocalUserAssertion() {
        assertFalse(canStartAppRest(VehicleState(),false,true))
        assertEquals(VehicleIssue.PARKING_UNKNOWN,startIssue(VehicleState(),false,true,true))
    }
    @Test fun mockAppOnlyConfirmationNeverBecomesVehicleParkingSignal() {
        val state=MockVehicleRepository().state.value
        assertEquals(ParkingStatus.UNKNOWN,state.parkingStatus)
        assertFalse(canStartAppRest(state,true,false))
        assertTrue(canStartAppRest(state,true,true))
        assertEquals(ParkingStatus.UNKNOWN,state.parkingStatus)
        assertEquals(ControlSupport.UNSUPPORTED,MockVehicleRepository().capabilities.drivingSignal)
    }
    @Test fun notParkedBlocksEvenWithLocalConfirmation() {
        listOf(false,true).forEach { mock ->
            assertFalse(canStartAppRest(VehicleState(parked=false),mock,true))
            assertEquals(VehicleIssue.NOT_PARKED,startIssue(VehicleState(parked=false),mock,true,true))
        }
    }
    @Test fun firstUseMustBeAcknowledgedBeforeStarting() {
        assertEquals(VehicleIssue.FIRST_USE,startIssue(VehicleState(parked=true),false,false,false))
        assertNull(startIssue(VehicleState(parked=true),false,false,true))
        assertEquals(VehicleIssueAction.ACKNOWLEDGE,
            vehicleIssueContent(RestUiState(vehicleIssue=VehicleIssue.FIRST_USE)).primary)
    }
    @Test fun realControlResponsesKeepFourDistinctOutcomes() {
        assertEquals(ControlOutcome.SUCCESS,controlState(ClimateResult.Applied(23f),false,ControlSupport.SUPPORTED).outcome)
        assertEquals(ControlOutcome.FAILURE,controlState(ClimateResult.Failed("negative ack"),false,ControlSupport.SUPPORTED).outcome)
        assertEquals(ControlOutcome.UNSUPPORTED,controlState(ClimateResult.Unavailable("unsupported"),false,ControlSupport.SUPPORTED).outcome)
        assertEquals(ControlOutcome.UNKNOWN,controlState(ClimateResult.Unknown("timeout"),false,ControlSupport.SUPPORTED).outcome)
    }
    @Test fun mockOrUnverifiedSupportCannotReportRealControlSuccess() {
        val applied=ClimateResult.Applied(23f)
        assertEquals(ControlOutcome.UNSUPPORTED,controlState(applied,true,ControlSupport.SUPPORTED).outcome)
        assertEquals(ControlOutcome.UNKNOWN,controlState(applied,false,ControlSupport.UNKNOWN).outcome)
        assertEquals(ControlOutcome.UNSUPPORTED,controlState(applied,false,ControlSupport.UNSUPPORTED).outcome)
    }
    @Test fun automaticStopRequiresRealSupportedDrivingSignalAndActiveSession() {
        assertTrue(shouldStopForDriving(driving,supported,false,false,RestPhase.RESTING))
        assertTrue(shouldStopForDriving(driving,supported,false,false,RestPhase.WAKING))
        assertFalse(shouldStopForDriving(driving,supported,true,false,RestPhase.RESTING))
        assertFalse(shouldStopForDriving(driving,supported,false,true,RestPhase.RESTING))
        assertFalse(shouldStopForDriving(driving,VehicleCapabilities(),false,false,RestPhase.RESTING))
        assertFalse(shouldStopForDriving(driving,supported,false,false,RestPhase.FINISHED))
        assertFalse(shouldStopForDriving(driving,supported,false,false,RestPhase.READY))
    }
    @Test fun unknownDrivingAndNonParkingDoNotInventDrivingEvents() {
        assertFalse(shouldStopForDriving(VehicleState(),supported,false,false,RestPhase.RESTING))
        assertFalse(shouldStopForDriving(VehicleState(parked=false),supported,false,false,RestPhase.RESTING))
        assertFalse(shouldStopForDriving(VehicleState(parked=false,driving=DrivingStatus.NOT_DRIVING),
            supported,false,false,RestPhase.RESTING))
    }
    @Test fun repeatedDrivingSignalsFinishRestoreAndStoreOnlyOnce() = runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        try {
            var now=0L
            val clock=object:RestClock {
                override fun elapsedMillis()=now
                override fun wallMillis()=1_800_000_000_000+now
            }
            val cabin=AppCabinController()
            val original=cabin.settings.value
            val manager=RestSessionManager(clock,cabin)
            val records=mutableListOf<RestRecord>()
            val repo=object:RestHistoryRepository {
                override suspend fun save(record:RestRecord) {records.add(record)}
                override suspend fun recent():List<RestRecord> = records.toList()
            }
            val lifecycle=RestSessionLifecycle(manager,RestHistoryStore(repo,scope))
            manager.start();now=1_000
            assertTrue(dispatchDrivingStop(driving,supported,false,false,{manager.state.value.phase},
                lifecycle::finishForDriving))
            assertFalse(dispatchDrivingStop(driving,supported,false,false,{manager.state.value.phase},
                lifecycle::finishForDriving))
            lifecycle.finish();lifecycle.close();lifecycle.tick();advanceUntilIdle()
            assertEquals(1,records.size)
            assertEquals(EndReason.DRIVING,records.single().reason)
            assertEquals(original,cabin.settings.value)
            assertEquals(RestPhase.FINISHED,manager.state.value.phase)
        } finally {scope.cancel()}
    }
    @Test fun mockSignalCannotInvokeFinishEvenWhenMarkedDriving() {
        var calls=0
        repeat(3) {
            assertFalse(dispatchDrivingStop(driving,supported,true,false,{RestPhase.RESTING},{calls++}))
        }
        assertEquals(0,calls)
    }
    @Test fun eachIssueExposesOnlyItsCurrentActionAndReturn() {
        VehicleIssue.entries.forEach { issue ->
            val ui=RestUiState(vehicleIssue=issue)
            assertTrue(issueActionAllowed(ui,vehicleIssueContent(ui).primary))
            assertTrue(issueActionAllowed(ui,VehicleIssueAction.RETURN))
            VehicleIssueAction.entries.filter {it!=VehicleIssueAction.RETURN &&
                it!=vehicleIssueContent(ui).primary}.forEach {assertFalse(issueActionAllowed(ui,it))}
        }
        assertFalse(issueActionAllowed(RestUiState(),VehicleIssueAction.CONFIRM_LOCAL_PARKING))
    }
    @Test fun realUnknownHasNoUserConfirmationBypass() {
        val real=RestUiState(isMock=false,vehicleIssue=VehicleIssue.PARKING_UNKNOWN)
        assertEquals(VehicleIssueAction.CHECK_PARKING,vehicleIssueContent(real).primary)
        assertFalse(issueActionAllowed(real,VehicleIssueAction.CONFIRM_LOCAL_PARKING))
        assertTrue(issueActionAllowed(real.copy(isMock=true),VehicleIssueAction.CONFIRM_LOCAL_PARKING))
    }
    @Test fun exceptionPresentationDoesNotChangeRestPhaseOrTimerOnReentry() {
        RestPhase.entries.forEach { phase ->
            val session=RestSession(phase=phase,plannedMinutes=30,remainingMillis=900_000)
            VehicleIssue.entries.forEach { issue ->
                val ui=RestUiState(session=session,vehicleIssue=issue)
                repeat(3) {vehicleIssueContent(ui)}
                assertEquals(session,ui.session)
                assertEquals(900_000L,ui.session.remainingMillis)
            }
        }
    }
}
