package ai.naru.app.camping.morning

import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.VehicleBackup
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.VehicleCapabilities
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestorerTest {

    private val original = VehicleBackup(
        targetTempC = 22f,
        ambient = AmbientLight(on = false, brightness = 100, colorRgb = 0xFFFFFF),
    )

    /** 밤 설정이 적용된 상태의 차량 */
    private fun nightVehicle(capabilities: VehicleCapabilities? = null) = FakeVehicleGateway(
        targetTemperature = 25f,
        ambientLight = AmbientLight(on = true, brightness = 30, colorRgb = 0x2E8B57),
        capabilities = capabilities ?: VehicleCapabilities(15f..30f, 0..100, true),
    )

    @Test
    fun `백업한 온도와 조명을 되돌리고 반영을 확인한다`() = runBlocking {
        val fake = nightVehicle()
        val report = SettingsRestorer(fake).restore(original)

        assertEquals(RestoreReport(ControlResult.Applied, ControlResult.Applied), report)
        assertFalse(report.needsManualCheck)
        assertEquals(VehicleResult.Success(22f), fake.getTargetTemperature())
        assertEquals(false, (fake.getAmbientLight() as VehicleResult.Success).value.on)
    }

    @Test
    fun `한 항목이 실패해도 나머지는 계속 복구한다`() = runBlocking {
        val fake = nightVehicle()
        fake.nextFailure = FailureReason.CONNECTION // 첫 제어(온도)만 실패

        val report = SettingsRestorer(fake).restore(original)

        assertEquals(ControlResult.Failed(FailureReason.CONNECTION), report.temperature)
        assertEquals(ControlResult.Applied, report.ambient)
        assertTrue(report.needsManualCheck)
    }

    @Test
    fun `백업하지 못한 항목은 건너뛴다`() = runBlocking {
        val report = SettingsRestorer(nightVehicle()).restore(VehicleBackup(targetTempC = 22f, ambient = null))
        assertEquals(RestoreReport(ControlResult.Applied, null), report)
    }

    @Test
    fun `백업이 없으면 아무것도 하지 않는다`() = runBlocking {
        val fake = nightVehicle()
        assertEquals(RestoreReport(null, null), SettingsRestorer(fake).restore(null))
        assertEquals(VehicleResult.Success(25f), fake.getTargetTemperature())
    }

    @Test
    fun `지원하지 않는 제어는 시도하지 않는다`() = runBlocking {
        val fake = nightVehicle(VehicleCapabilities(targetTemperatureRange = null, ambientBrightnessRange = null, ambientColorSupported = false))
        val report = SettingsRestorer(fake).restore(original)
        assertEquals(RestoreReport(ControlResult.NotSupported, ControlResult.NotSupported), report)
        assertTrue(report.needsManualCheck)
    }
}
