package ai.naru.app.vehicle

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVehicleGatewayTest {

    @Test
    fun `기어를 바꾸면 구독 중인 쪽이 새 값을 받는다`() = runBlocking {
        val fake = FakeVehicleGateway()
        assertEquals(Gear.PARK, fake.gear.first())
        fake.setGear(Gear.DRIVE)
        assertEquals(Gear.DRIVE, fake.gear.first())
    }

    @Test
    fun `범위 안의 온도는 반영되고 범위 밖은 거절된다`() = runBlocking {
        val fake = FakeVehicleGateway()
        assertEquals(VehicleResult.Success(Unit), fake.setTargetTemperature(24.0f))
        assertEquals(VehicleResult.Success(24.0f), fake.getTargetTemperature())

        val result = fake.setTargetTemperature(40.0f)
        assertTrue(result is VehicleResult.Failure && result.reason == FailureReason.NOT_SUPPORTED)
        assertEquals(VehicleResult.Success(24.0f), fake.getTargetTemperature())
    }

    @Test
    fun `미지원 차량에서는 제어가 NOT_SUPPORTED로 실패한다`() = runBlocking {
        val fake = FakeVehicleGateway(
            capabilities = VehicleCapabilities(
                targetTemperatureRange = null,
                ambientBrightnessRange = null,
                ambientColorSupported = false,
            ),
        )
        val result = fake.setAmbientLight(AmbientLight(on = true, brightness = 40, colorRgb = 0x0000FF))
        assertTrue(result is VehicleResult.Failure && result.reason == FailureReason.NOT_SUPPORTED)
    }

    @Test
    fun `예약한 실패는 한 번만 발생한다`() = runBlocking {
        val fake = FakeVehicleGateway()
        fake.nextFailure = FailureReason.CONNECTION
        val first = fake.setTargetTemperature(23.0f)
        assertTrue(first is VehicleResult.Failure && first.reason == FailureReason.CONNECTION)
        assertEquals(VehicleResult.Success(Unit), fake.setTargetTemperature(23.0f))
    }
}
