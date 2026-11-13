package ai.naru.app.camping

import ai.naru.app.camping.arrival.DemoSiteRepository
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.camping.night.NightSound
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * 세션 흐름 통합 테스트. Fake 차량은 바로 응답하고, Unconfined 디스패처라 호출 즉시 끝까지 실행된다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampingViewModelTest {

    private val originalAmbient = AmbientLight(on = true, brightness = 40, colorRgb = 0xFFFFFF)
    private val fake = FakeVehicleGateway(targetTemperature = 22f, ambientLight = originalAmbient)
    private val sound = RecordingSound()
    private val alarm = RecordingAlarm()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vehicle: VehicleGateway = fake) = CampingViewModel(vehicle, DemoSiteRepository(), sound, alarm)

    /** 숲 캠핑장에서 24°C로 밤을 시작하기 직전까지 진행한다. */
    private fun CampingViewModel.goToBedtime() {
        start()
        selectSite(state.value.sites.first { it.placeType == PlaceType.FOREST })
        confirmArrival()
        adjustTemperature(4) // 22 → 24°C (0.5 단위)
    }

    private fun vehicleState() = runBlocking { fake.getTargetTemperature() to fake.getAmbientLight() }

    @Test
    fun `밤에 기어가 P를 벗어나면 소리를 멈추고 설정을 복구한 뒤 끝낸다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        assertEquals(CampingStage.NIGHT, vm.state.value.stage)
        assertEquals(VehicleResult.Success(24f), vehicleState().first)

        fake.setGear(Gear.DRIVE)

        val state = vm.state.value
        assertEquals(CampingStage.IDLE, state.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, state.lastEndReason)
        assertEquals(RestoreReport(ControlResult.Applied, ControlResult.Applied), state.restore)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
        assertFalse(sound.playing)
    }

    @Test
    fun `아침에 기어가 P를 벗어나면 알람을 멈추고 설정을 복구한 뒤 끝낸다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.simulateWakeTime()
        assertEquals(CampingStage.MORNING, vm.state.value.stage)
        assertEquals(true, alarm.ringing)

        fake.setGear(Gear.DRIVE)

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
        assertFalse(alarm.ringing)
    }

    @Test
    fun `아침에 일어나 복구하고 마치면 정상 종료된다`() {
        val vm = viewModel()
        vm.goToBedtime()
        vm.startNight()
        vm.simulateWakeTime()

        vm.finishSession() // 복구 전에는 마칠 수 없다
        assertEquals(CampingStage.MORNING, vm.state.value.stage)

        vm.wakeUp()
        vm.finishSession()

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.COMPLETED, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f) to VehicleResult.Success(originalAmbient), vehicleState())
    }

    @Test
    fun `차량을 준비하는 사이 기어가 바뀌면 바꾼 온도를 되돌린다`() {
        // 설정 온도를 적용하자마자 기어가 D로 바뀌는 차량
        val gearFlipping = object : VehicleGateway by fake {
            override suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit> =
                fake.setTargetTemperature(celsius).also { fake.setGear(Gear.DRIVE) }
        }
        val vm = viewModel(gearFlipping)
        vm.goToBedtime()

        vm.startNight()

        assertEquals(CampingStage.IDLE, vm.state.value.stage)
        assertEquals(EndReason.GEAR_LEFT_PARK, vm.state.value.lastEndReason)
        assertEquals(VehicleResult.Success(22f), vehicleState().first)
        assertFalse(sound.playing)
    }

    private class RecordingSound : NightSound {
        var playing = false
        override fun play(theme: PlaceType): Boolean { playing = true; return true }
        override fun stop() { playing = false }
    }

    private class RecordingAlarm : MorningAlarm {
        var ringing = false
        override fun start(): Boolean { ringing = true; return true }
        override fun stop() { ringing = false }
    }
}
