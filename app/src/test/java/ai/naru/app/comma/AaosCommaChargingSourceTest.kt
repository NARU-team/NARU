package ai.naru.app.comma

import android.car.hardware.property.EvChargeState
import org.junit.Assert.assertEquals
import org.junit.Test

class AaosCommaChargingSourceTest {
    @Test
    fun `공식 AAOS 충전 상태를 도메인 상태로 변환한다`() {
        assertEquals(ChargingState.CHARGING, chargingStateFromAaos(EvChargeState.STATE_CHARGING))
        assertEquals(ChargingState.NOT_CHARGING, chargingStateFromAaos(EvChargeState.STATE_NOT_CHARGING))
        assertEquals(ChargingState.NOT_CHARGING, chargingStateFromAaos(EvChargeState.STATE_FULLY_CHARGED))
        assertEquals(ChargingState.UNKNOWN, chargingStateFromAaos(EvChargeState.STATE_ERROR))
    }

    @Test
    fun `순간 충전률 0은 비충전 증거로 사용하지 않는다`() {
        assertEquals(ChargingState.CHARGING, chargingStateFromRate(100f))
        assertEquals(ChargingState.NOT_CHARGING, chargingStateFromRate(-100f))
        assertEquals(ChargingState.UNKNOWN, chargingStateFromRate(0f))
        assertEquals(ChargingState.UNKNOWN, chargingStateFromRate(null))
    }
}
