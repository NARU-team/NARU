package ai.naru.app.camping.morning

import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.VehicleCapabilities
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningLightApplierTest {

    @Test
    fun `아침 조명을 켜고 반영을 확인한다`() = runBlocking {
        val fake = FakeVehicleGateway()
        assertEquals(ControlResult.Applied, MorningLightApplier(fake).apply())
        assertEquals(VehicleResult.Success(MorningRules.morningLight), fake.getAmbientLight())
    }

    @Test
    fun `조명 제어를 지원하지 않으면 시도하지 않는다`() = runBlocking {
        val fake = FakeVehicleGateway(capabilities = VehicleCapabilities(15f..30f, null, false))
        assertEquals(ControlResult.NotSupported, MorningLightApplier(fake).apply())
    }

    @Test
    fun `제어가 실패하면 실패를 돌려준다`() = runBlocking {
        val fake = FakeVehicleGateway()
        fake.nextFailure = FailureReason.PERMISSION
        assertEquals(ControlResult.Failed(FailureReason.PERMISSION), MorningLightApplier(fake).apply())
    }
}
