package ai.naru.app.camping.night

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.VehicleCapabilities
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeLightApplierTest {

    @Test
    fun `테마 조명을 켜고 반영을 확인한다`() = runBlocking {
        val fake = FakeVehicleGateway()
        assertEquals(ControlResult.Applied, ThemeLightApplier(fake).apply(PlaceType.FOREST))
        assertEquals(VehicleResult.Success(NightThemes.ambientFor(PlaceType.FOREST)), fake.getAmbientLight())
    }

    @Test
    fun `조명 제어를 지원하지 않으면 시도하지 않는다`() = runBlocking {
        val fake = FakeVehicleGateway(
            capabilities = VehicleCapabilities(targetTemperatureRange = 15f..30f, ambientBrightnessRange = null, ambientColorSupported = false),
        )
        assertEquals(ControlResult.NotSupported, ThemeLightApplier(fake).apply(PlaceType.BEACH))
    }

    @Test
    fun `제어가 실패하면 실패를 돌려준다`() = runBlocking {
        val fake = FakeVehicleGateway()
        fake.nextFailure = FailureReason.CONNECTION
        assertEquals(ControlResult.Failed(FailureReason.CONNECTION), ThemeLightApplier(fake).apply(PlaceType.LAKE))
    }
}
