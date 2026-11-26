package ai.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingObservationTrackerTest {
    @Test
    fun `전 구간 비충전과 포트 미연결을 확인하면 고신뢰도다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(snapshot(ChargingState.NOT_CHARGING, false, 1))

        val result = tracker.finish(snapshot(ChargingState.NOT_CHARGING, false, 2))

        assertTrue(result.complete)
        assertEquals(EnergyDataQuality.VERIFIED_NOT_CHARGING, result.quality)
        assertEquals(false, result.chargingDetectedDuringRest)
    }

    @Test
    fun `휴식 도중 충전을 관측하면 예측에서 제외한다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(snapshot(ChargingState.NOT_CHARGING, false, 1))
        tracker.observe(snapshot(ChargingState.CHARGING, true, 2))

        val result = tracker.finish(snapshot(ChargingState.NOT_CHARGING, false, 3))

        assertEquals(EnergyDataQuality.CHARGING_DETECTED, result.quality)
        assertEquals(true, result.chargingDetectedDuringRest)
    }

    @Test
    fun `포트만 연결되어도 일반 방전 예측에서 제외한다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(snapshot(ChargingState.NOT_CHARGING, true, 1))

        val result = tracker.finish(snapshot(ChargingState.NOT_CHARGING, true, 2))

        assertEquals(EnergyDataQuality.PORT_CONNECTED, result.quality)
    }

    @Test
    fun `프로세스 복구 관측 공백은 완료로 간주하지 않는다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(snapshot(ChargingState.NOT_CHARGING, false, 1))
        tracker.markObservationGap()

        val result = tracker.finish(snapshot(ChargingState.NOT_CHARGING, false, 3))

        assertFalse(result.complete)
        assertEquals(EnergyDataQuality.OBSERVATION_INCOMPLETE, result.quality)
    }

    @Test
    fun `충전 상태 미지원이지만 포트 미연결을 계속 관측하면 제한 품질이다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(portOnlySnapshot(false, 1))

        val result = tracker.finish(portOnlySnapshot(false, 2))

        assertFalse(result.complete)
        assertEquals(EnergyDataQuality.PORT_ONLY_VERIFIED, result.quality)
        assertEquals(ChargingState.UNKNOWN, result.stateAtStart)
    }

    @Test
    fun `포트 전용 관측도 공백이 있으면 제한 품질로 사용하지 않는다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(portOnlySnapshot(false, 1))
        tracker.markObservationGap()

        val result = tracker.finish(portOnlySnapshot(false, 3))

        assertEquals(EnergyDataQuality.OBSERVATION_INCOMPLETE, result.quality)
    }

    @Test
    fun `알 수 없는 상태는 비충전으로 변환하지 않는다`() {
        val tracker = ChargingObservationTracker()
        tracker.start(snapshot(ChargingState.UNKNOWN, null, 1))

        val result = tracker.finish(snapshot(ChargingState.NOT_CHARGING, false, 2))

        assertFalse(result.complete)
        assertEquals(ChargingState.UNKNOWN, result.stateAtStart)
        assertEquals(EnergyDataQuality.OBSERVATION_INCOMPLETE, result.quality)
    }

    private fun snapshot(state: ChargingState, connected: Boolean?, at: Long) =
        ChargingSnapshot(
            state = state,
            portConnected = connected,
            chargeStateSupported = true,
            chargePortSupported = true,
            observedAtMillis = at,
        )

    private fun portOnlySnapshot(connected: Boolean, at: Long) = ChargingSnapshot(
        state = ChargingState.UNKNOWN,
        portConnected = connected,
        chargeStateSupported = false,
        chargePortSupported = true,
        observedAtMillis = at,
    )
}
