package ai.naru.app.camping.bedtime

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.VehicleCapabilities
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class NightPreparerTest {

    private val settings = BedtimeSettings(
        targetTempC = 24f,
        wakeAt = LocalTime.of(7, 0),
        wakeBasis = WakeBasis.MANUAL,
        theme = PlaceType.FOREST,
    )

    @Test
    fun `원래 설정을 백업하고 설정 온도를 적용한다`() = runBlocking {
        val originalAmbient = AmbientLight(on = false, brightness = 100, colorRgb = 0xFFFFFF)
        val fake = FakeVehicleGateway(targetTemperature = 22f, ambientLight = originalAmbient)

        val result = NightPreparer(fake).prepare(settings)

        assertEquals(VehicleBackup(targetTempC = 22f, ambient = originalAmbient), result.backup)
        assertEquals(TemperatureApply.Applied, result.temperature)
        assertEquals(VehicleResult.Success(24f), fake.getTargetTemperature())
    }

    @Test
    fun `온도 제어를 지원하지 않으면 시도하지 않는다`() = runBlocking {
        val fake = FakeVehicleGateway(
            targetTemperature = 22f,
            capabilities = VehicleCapabilities(targetTemperatureRange = null, ambientBrightnessRange = null, ambientColorSupported = false),
        )

        val result = NightPreparer(fake).prepare(settings)

        assertEquals(TemperatureApply.NotSupported, result.temperature)
        assertNull(result.backup.ambient)
        assertEquals(VehicleResult.Success(22f), fake.getTargetTemperature())
    }

    @Test
    fun `제어가 실패하면 다시 시도하지 않고 실패를 돌려준다`() = runBlocking {
        val fake = FakeVehicleGateway(targetTemperature = 22f)
        fake.nextFailure = FailureReason.CONNECTION

        val result = NightPreparer(fake).prepare(settings)

        assertEquals(TemperatureApply.Failed(FailureReason.CONNECTION), result.temperature)
        assertEquals(VehicleResult.Success(22f), fake.getTargetTemperature())
    }
}
