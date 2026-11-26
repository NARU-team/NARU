package ai.naru.app.comma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommaRecommendationEngineTest {
    @Test
    fun `실측 기록이 세 건보다 적으면 에너지를 예측하지 않는다`() {
        assertNull(forecastEnergy(80, 30, listOf(measuredRecord("1", 80, 78))))
    }

    @Test
    fun `유효한 실측 세 건으로 종료 잔량 범위를 계산한다`() {
        val records = listOf(
            measuredRecord("1", 80, 78),
            measuredRecord("2", 70, 67),
            measuredRecord("3", 60, 59),
        )

        val result = forecastEnergy(80, 30, records)

        requireNotNull(result)
        assertEquals(77, result.minimumEndPercent)
        assertEquals(81, result.maximumEndPercent)
        assertEquals(3, result.sampleCount)
        assertEquals(EnergyForecastConfidence.HIGH, result.confidence)
    }

    @Test
    fun `충전 관측이 불완전한 기록은 에너지 예측에서 제외한다`() {
        val records = listOf(
            measuredRecord("1", 80, 78),
            measuredRecord("2", 70, 67),
            measuredRecord("3", 60, 59).copy(
                chargingObservationComplete = false,
                energyDataQuality = EnergyDataQuality.OBSERVATION_INCOMPLETE,
            ),
        )

        assertNull(forecastEnergy(80, 30, records))
    }

    @Test
    fun `충전 또는 포트 연결 기록은 에너지 예측에서 제외한다`() {
        val records = listOf(
            measuredRecord("1", 80, 78),
            measuredRecord("2", 70, 67),
            measuredRecord("3", 60, 59).copy(
                chargingDetectedDuringRest = true,
                energyDataQuality = EnergyDataQuality.CHARGING_DETECTED,
            ),
            measuredRecord("4", 60, 59).copy(
                chargePortConnectedDuringRest = true,
                energyDataQuality = EnergyDataQuality.PORT_CONNECTED,
            ),
        )

        assertNull(forecastEnergy(80, 30, records))
    }

    @Test
    fun `포트 전용 제한 기록은 고신뢰도 기록과 섞지 않고 참고 예측한다`() {
        val limited = listOf(
            limitedRecord("limited-1", 80, 78),
            limitedRecord("limited-2", 70, 67),
            limitedRecord("limited-3", 60, 59),
        )

        val result = forecastEnergy(80, 30, limited)

        requireNotNull(result)
        assertEquals(EnergyForecastConfidence.LIMITED, result.confidence)
        assertEquals(3, result.sampleCount)
    }

    @Test
    fun `고신뢰도와 제한 기록은 합쳐서 최소 표본을 채우지 않는다`() {
        val mixed = listOf(
            measuredRecord("high-1", 80, 78),
            measuredRecord("high-2", 70, 67),
            limitedRecord("limited-1", 60, 59),
        )

        assertNull(forecastEnergy(80, 30, mixed))
    }

    @Test
    fun `만족 피드백이 있는 온도 기록 두 건부터 개인화한다`() {
        val records = listOf(
            measuredRecord("1", 80, 78).copy(targetClimateCelsius = 22f, feedback = 5),
            measuredRecord("2", 70, 68).copy(targetClimateCelsius = 23f, feedback = 4),
            measuredRecord("3", 60, 58).copy(targetClimateCelsius = 19f, feedback = 2),
        )

        assertEquals(22.5f, personalizedClimateTarget(records))
    }

    private fun limitedRecord(id: String, startBattery: Int, endBattery: Int) =
        measuredRecord(id, startBattery, endBattery).copy(
            chargingStateAtStart = ChargingState.UNKNOWN,
            chargingStateAtEnd = ChargingState.UNKNOWN,
            chargingObservationComplete = false,
            energyDataQuality = EnergyDataQuality.PORT_ONLY_VERIFIED,
        )

    private fun measuredRecord(id: String, startBattery: Int, endBattery: Int) = RestRecord(
        id = id,
        startedAt = 0,
        endedAt = 60 * 60_000L,
        plannedMinutes = 60,
        elapsedMillis = 60 * 60_000L,
        reason = EndReason.TIMER,
        startBatteryPercent = startBattery,
        endBatteryPercent = endBattery,
        source = RecordSource.REAL,
        chargingStateAtStart = ChargingState.NOT_CHARGING,
        chargingStateAtEnd = ChargingState.NOT_CHARGING,
        chargePortConnectedDuringRest = false,
        chargingDetectedDuringRest = false,
        chargingObservationComplete = true,
        energyDataQuality = EnergyDataQuality.VERIFIED_NOT_CHARGING,
    )
}
