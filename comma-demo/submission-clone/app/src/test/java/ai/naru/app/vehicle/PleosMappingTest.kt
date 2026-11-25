package ai.naru.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class PleosMappingTest {

    // --- 기어: AAOS VehicleGear 값 (PARK=4, REVERSE=2, NEUTRAL=1, DRIVE=8, 1단=16 ...) ---

    @Test
    fun `AAOS 기어 값을 앱 기어로 바꾼다`() {
        assertEquals(Gear.PARK, PleosMapping.gearOf(4))
        assertEquals(Gear.REVERSE, PleosMapping.gearOf(2))
        assertEquals(Gear.NEUTRAL, PleosMapping.gearOf(1))
        assertEquals(Gear.DRIVE, PleosMapping.gearOf(8))
    }

    @Test
    fun `수동 단수는 주행 중이므로 DRIVE로 본다`() {
        for (manual in listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096)) {
            assertEquals("값 $manual", Gear.DRIVE, PleosMapping.gearOf(manual))
        }
    }

    @Test
    fun `알 수 없는 값이나 null은 UNKNOWN이다`() {
        assertEquals(Gear.UNKNOWN, PleosMapping.gearOf(0))
        assertEquals(Gear.UNKNOWN, PleosMapping.gearOf(null))
        assertEquals(Gear.UNKNOWN, PleosMapping.gearOf(999))
    }

    // --- 오류: 에뮬레이터에서 실제로 받은 메시지 기준 ---

    @Test
    fun `권한 오류를 구분한다`() {
        assertEquals(
            FailureReason.PERMISSION,
            PleosMapping.failureReasonOf(SecurityException("getCurrentGearSelectionState need Permission")),
        )
        assertEquals(
            FailureReason.PERMISSION,
            PleosMapping.failureReasonOf(Exception("ERROR_SECURITY_PERMISSION")),
        )
    }

    @Test
    fun `미지원 영역 오류를 구분한다`() {
        assertEquals(
            FailureReason.NOT_SUPPORTED,
            PleosMapping.failureReasonOf(
                Exception("java.lang.IllegalArgumentException: area ID: 0x1 not supported for property ID: HVAC_POWER_ON"),
            ),
        )
        assertEquals(FailureReason.NOT_SUPPORTED, PleosMapping.failureReasonOf(Exception("ERROR_NOT_SUPPORTED_AREA")))
    }

    @Test
    fun `연결과 주행 제한 오류를 구분한다`() {
        assertEquals(FailureReason.CONNECTION, PleosMapping.failureReasonOf(Exception("ERROR_SERVICE_CONNECTION_FAILURE")))
        assertEquals(FailureReason.CONNECTION, PleosMapping.failureReasonOf(Exception("ERROR_INTERNAL_SERVICE")))
        assertEquals(FailureReason.DRIVING_RESTRICTED, PleosMapping.failureReasonOf(Exception("ERROR_DRIVING_RESTRICTED")))
    }

    @Test
    fun `분류할 수 없는 오류는 UNKNOWN이다`() {
        assertEquals(FailureReason.UNKNOWN, PleosMapping.failureReasonOf(Exception("ERROR_UNDEFINED")))
        assertEquals(FailureReason.UNKNOWN, PleosMapping.failureReasonOf(Exception()))
    }
}
