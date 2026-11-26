package ai.naru.app.comma

import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCommaVehicleRepositoryTest {
    @Test
    fun `공용 게이트웨이의 기어와 배터리를 쉼표 상태로 변환한다`() = runTest {
        val gateway = FakeVehicleGateway(initialGear = Gear.PARK, initialBatteryPercent = 64)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(gateway, scope)

        advanceUntilIdle()

        assertEquals(ParkingStatus.PARKED, repository.state.value.parkingStatus)
        assertEquals(DrivingStatus.NOT_DRIVING, repository.state.value.driving)
        assertEquals(64, repository.state.value.batteryPercent)
        assertEquals(ControlSupport.SUPPORTED, repository.capabilities.climate)
        scope.cancel()
    }

    @Test
    fun `공조는 적용값 재확인 후 성공으로 보고하고 종료 시 원래 값으로 복구한다`() = runTest {
        val gateway = FakeVehicleGateway(
            initialGear = Gear.PARK,
            targetTemperature = 21f,
            hvacPower = true,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(gateway, scope)
        advanceUntilIdle()

        val applied = repository.requestClimate(24f)
        assertTrue(applied is ClimateResult.Applied)
        assertEquals(24f, (gateway.getTargetTemperature() as VehicleResult.Success).value)

        repository.restoreClimate()
        advanceUntilIdle()

        assertEquals(21f, (gateway.getTargetTemperature() as VehicleResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `SDK 콜백 뒤 온도 반영이 늦어도 제한 시간 동안 재조회한다`() = runTest {
        val base = FakeVehicleGateway(
            initialGear = Gear.PARK,
            targetTemperature = 21f,
            hvacPower = true,
        )
        var requestedTemperature: Float? = null
        var readsAfterSet = 0
        val delayedGateway = object : VehicleGateway by base {
            override suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit> {
                requestedTemperature = celsius
                readsAfterSet = 0
                return VehicleResult.Success(Unit)
            }

            override suspend fun getTargetTemperature(): VehicleResult<Float> {
                val requested = requestedTemperature ?: return base.getTargetTemperature()
                readsAfterSet += 1
                return VehicleResult.Success(if (readsAfterSet >= 4) requested else 21f)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(delayedGateway, scope)
        advanceUntilIdle()

        val applied = repository.requestClimate(23.5f)

        assertTrue(applied is ClimateResult.Applied)
        assertEquals(23.5f, (applied as ClimateResult.Applied).targetCelsius)
        assertEquals(4, readsAfterSet)
        scope.cancel()
    }

    @Test
    fun `공조가 꺼져 있으면 전원을 확인한 뒤 온도를 백업하고 적용한다`() = runTest {
        val gateway = FakeVehicleGateway(
            initialGear = Gear.PARK,
            targetTemperature = 20f,
            hvacPower = false,
        )
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(gateway, scope)
        advanceUntilIdle()

        val applied = repository.requestClimate(23f)

        assertTrue(applied is ClimateResult.Applied)
        assertEquals(23f, (gateway.getTargetTemperature() as VehicleResult.Success).value)

        val restoration = repository.restoreAndVerify()
        assertEquals(RestorationOutcome.SUCCESS, restoration.hvacPower)
        assertTrue(gateway.getTargetTemperature() is VehicleResult.Failure)
        scope.cancel()
    }

    @Test
    fun `승인된 조명을 적용 후 종료 시 원래 값으로 복구한다`() = runTest {
        val original = ai.naru.app.vehicle.AmbientLight(false, 35, 0x123456)
        val gateway = FakeVehicleGateway(initialGear = Gear.PARK, ambientLight = original)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(gateway, scope)
        advanceUntilIdle()

        assertTrue(repository.applyLighting(0.6f) is ClimateResult.Applied)
        val applied = (gateway.getAmbientLight() as VehicleResult.Success).value
        assertTrue(applied.on)
        assertEquals(60, applied.brightness)

        repository.restoreClimate()
        advanceUntilIdle()
        assertEquals(original, (gateway.getAmbientLight() as VehicleResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `주차 상태가 아니면 공조를 변경하지 않는다`() = runTest {
        val gateway = FakeVehicleGateway(initialGear = Gear.DRIVE, targetTemperature = 21f)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val repository = GatewayCommaVehicleRepository(gateway, scope)
        advanceUntilIdle()

        val result = repository.requestClimate(24f)

        assertTrue(result is ClimateResult.Failed)
        assertEquals(21f, (gateway.getTargetTemperature() as VehicleResult.Success).value)
        scope.cancel()
    }
}
