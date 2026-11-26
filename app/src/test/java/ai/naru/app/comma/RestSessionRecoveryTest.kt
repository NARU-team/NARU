package ai.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestSessionRecoveryTest {
    private val session = PersistedRestSession(
        sessionId = "id",
        phase = RestPhase.RESTING,
        startedAtWallMillis = 1_000_000L,
        endAtWallMillis = 1_900_000L,
        startedAtElapsedMillis = 100_000L,
        deadlineElapsedMillis = 1_000_000L,
        savedAtWallMillis = 1_300_000L,
        savedAtElapsedMillis = 400_000L,
        selectedMinutes = 15,
        plannedMinutes = 15,
        extensionMinutes = 0,
        settings = CabinSettings(),
        approvedClimateCelsius = null,
        restoreState = "PENDING",
        source = RecordSource.REAL,
        alarmIssued = false,
        completionRecorded = false,
    )

    @Test
    fun `같은 부팅의 프로세스 재시작은 elapsed deadline을 유지한다`() {
        val result = decideRestRecovery(session, 1_400_000L, 500_000L)
        assertEquals(RecoveryKind.PROCESS_RESTART, result.kind)
        assertEquals(1_000_000L, result.restoredDeadlineElapsed)
    }

    @Test
    fun `재부팅은 wall 종료 시각으로 새 elapsed deadline을 만든다`() {
        val result = decideRestRecovery(session, 1_400_000L, 10_000L)
        assertEquals(RecoveryKind.DEVICE_REBOOT, result.kind)
        assertEquals(510_000L, result.restoredDeadlineElapsed)
    }

    @Test
    fun `시스템 시각 변경은 자동 재개하지 않고 백업 복구만 요구한다`() {
        val result = decideRestRecovery(session, 1_700_000L, 500_000L)
        assertEquals(RecoveryKind.SYSTEM_TIME_CHANGED, result.kind)
        assertTrue(result.shouldRestoreSettingsOnly)
    }

    @Test
    fun `확인 시간을 지난 세션은 만료 처리한다`() {
        val result = decideRestRecovery(session, 1_940_000L, 1_040_000L)
        assertEquals(RecoveryKind.EXPIRED, result.kind)
        assertNotNull(result)
    }
}
