package com.naru.app.comma

import org.junit.Assert.*
import org.junit.Test

class DemoSubmissionTest {
    private class Clock:RestClock {
        var now=0L
        override fun elapsedMillis()=now
        override fun wallMillis()=1_800_000_000_000+now
    }
    private val clock=Clock()
    private val demo=DemoSessionController(clock)
    @Test fun entryPreparesSettingsWithoutStartingOrChangingProductionTime() {
        demo.prepare(15,CabinSettings(0.1f))
        assertEquals(RestPhase.READY,demo.state.value.phase)
        assertFalse(demo.active)
        assertEquals(0L,clock.now)
        assertEquals(CommaDestination.SETTINGS,commaDestination(RestUiState(isDemo=true,session=demo.state.value),true))
    }
    @Test fun settingsSelectionAndConfiguredStartChangeActualDemoState() {
        demo.prepare(15,CabinSettings())
        demo.selectMinutes(30)
        demo.configure(CabinSettings(0.1f,0.5f,RestSound.SOFT_TONE))
        assertTrue(demo.startPrepared())
        assertEquals(1_800_000L,demo.state.value.remainingMillis)
        assertEquals(0.1f,demo.cabin.settings.value.brightness,0.001f)
        assertTrue(demo.vehicle.climateApplied)
        assertEquals(0.5f,demo.vehicle.lightingApplied,0.001f)
        assertFalse(demo.startPrepared())
    }
    @Test fun filmingStoryDeadlineExtendAlarmFinishIsRepeatableAndRestores() {
        val original=demo.cabin.settings.value
        demo.prepare(15,CabinSettings(0.1f,0.4f))
        demo.startPrepared()
        demo.advanceToDeadline()
        assertEquals(RestPhase.WAKING,demo.state.value.phase)
        assertEquals(30_000L,demo.state.value.confirmationRemainingMillis)
        assertTrue(demo.extend(5))
        assertEquals(RestPhase.RESTING,demo.state.value.phase)
        assertEquals(300_000L,demo.state.value.remainingMillis)
        assertEquals(20,demo.state.value.plannedMinutes)
        demo.advanceToDeadline();demo.finish()
        val record=demo.state.value.completed
        demo.finish();demo.tick()
        assertEquals(record,demo.state.value.completed)
        assertEquals(RestPhase.FINISHED,demo.state.value.phase)
        assertEquals(original,demo.cabin.settings.value)
        assertTrue(demo.vehicle.restored)
        assertFalse(demo.vehicle.climateApplied)
        assertEquals(0f,demo.vehicle.lightingApplied,0f)
    }
    @Test fun alarmOnlyDoesNotApplyChosenAppOrVehicleSettings() {
        val original=demo.cabin.settings.value
        demo.prepare(15,CabinSettings(0.05f,1f,RestSound.SOFT_TONE))
        assertTrue(demo.startPrepared(DemoRestMode.ALARM_ONLY))
        assertEquals(DemoRestMode.ALARM_ONLY,demo.vehicle.mode)
        assertEquals(original,demo.cabin.settings.value)
        assertFalse(demo.vehicle.climateApplied)
        assertEquals(0f,demo.vehicle.lightingApplied,0f)
        demo.advanceToDeadline()
        assertEquals(original,demo.cabin.settings.value)
        demo.expireConfirmation()
        assertTrue(demo.vehicle.restored)
    }
    @Test fun exceptionButtonsChangeDeterministicVirtualControlState() {
        demo.setClimateSupported(false)
        assertFalse(demo.requestClimate())
        assertEquals(ControlOutcome.UNSUPPORTED,demo.vehicle.controlDisplay().outcome)
        demo.setClimateSupported(true);demo.failClimate()
        assertEquals(ControlOutcome.FAILURE,demo.vehicle.controlDisplay().outcome)
        assertTrue(demo.requestClimate())
        assertFalse(demo.vehicle.climateFailed)
        assertTrue(demo.vehicle.climateConfigured)
        assertNotEquals(ControlOutcome.SUCCESS,demo.vehicle.controlDisplay().outcome)
    }
    @Test fun virtualParkingActuallyGatesStartWithoutChangingRealMockProvider() {
        demo.prepare(15,CabinSettings())
        demo.setParking(ParkingStatus.UNKNOWN);assertFalse(demo.startPrepared())
        demo.setParking(ParkingStatus.NOT_PARKED);assertFalse(demo.startPrepared())
        demo.setParking(ParkingStatus.PARKED);assertTrue(demo.startPrepared())
        assertEquals(ParkingStatus.UNKNOWN,MockVehicleRepository().state.value.parkingStatus)
    }
    @Test fun virtualDrivingStopsOnceAndRestorationDoesNotReparkVehicle() {
        demo.start(15,CabinSettings(0.1f,0.4f))
        clock.now=2_000
        assertTrue(demo.simulateDrivingStart())
        assertEquals(EndReason.DRIVING,demo.state.value.completed!!.reason)
        assertFalse(demo.simulateDrivingStart())
        assertEquals(ParkingStatus.NOT_PARKED,demo.vehicle.vehicle.parkingStatus)
        assertTrue(demo.vehicle.restored)
        assertFalse(demo.vehicle.climateApplied)
    }
    @Test fun environmentPresetsHaveNoRandomOrPredictedReadings() {
        demo.setEnvironment(false)
        assertEquals(23f,demo.vehicle.vehicle.indoorCelsius)
        assertEquals(22f,demo.vehicle.vehicle.outdoorCelsius)
        assertEquals(80,demo.vehicle.vehicle.batteryPercent)
        demo.setEnvironment(true)
        assertEquals(28f,demo.vehicle.vehicle.indoorCelsius)
        assertEquals(75,demo.vehicle.vehicle.batteryPercent)
        assertEquals(0L,clock.now)
    }
    @Test fun newSettingsAfterCompletionRetainConfiguredChoiceAndUseReadyRoute() {
        demo.prepare(15,CabinSettings(0.1f,0.4f))
        demo.startPrepared(DemoRestMode.ALARM_ONLY);demo.finish()
        assertTrue(demo.prepareNext())
        assertFalse(demo.prepareNext())
        assertNull(demo.state.value.completed)
        assertEquals(0.1f,demo.state.value.settings.brightness,0.001f)
        assertEquals(RestPhase.READY,demo.state.value.phase)
    }
}
