package ai.naru.app.camping.bedtime

import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 추천 온도 기준값. 기획서에 숫자가 없어 정한 제안값이다 (2026-11-13, 이슈 #13).
 */
object BedtimeThresholds {
    /** 날씨를 모를 때 쓰는 기본 추천 온도 */
    const val DEFAULT_TEMP_C = 22f

    /** 이 잔량(%) 미만이면 난방 온도를 낮춰 에너지를 아낀다 */
    const val LOW_BATTERY_PERCENT = 30

    /** 배터리가 적을 때 낮추는 폭 */
    const val LOW_BATTERY_REDUCTION_C = 1f

    /** 이 최저기온 이하면 난방 상황으로 본다 (배터리 절약 조정은 난방일 때만) */
    const val HEATING_MAX_MIN_TEMP_C = 20f

    /** 차량 온도 조절 단위 (에뮬레이터 실측: 0.5°C) */
    const val STEP_C = 0.5f

    /** "일출에 맞추기": 해 뜨는 걸 볼 수 있게 일출 몇 분 전에 깨운다 (피그마 v2 "해 뜨기 12분 전", 이슈 #37) */
    const val SUNRISE_LEAD_MINUTES = 12L

    /** 온도별 예상 배터리에서 고른 온도 위아래로 보여줄 간격 */
    const val FORECAST_ROW_STEP_C = 1f
}

/** 추천 온도와 그렇게 추천한 이유. */
data class TemperatureRecommendation(
    val celsius: Float,
    val notes: List<RecommendationNote>,
)

enum class RecommendationNote {
    /** 날씨를 몰라 기본값으로 추천 */
    WEATHER_UNKNOWN,

    /** 배터리가 적어 온도를 낮춤 */
    LOW_BATTERY_SAVING,

    /** 차량 범위를 벗어나 범위 안으로 맞춤 */
    CLAMPED_TO_VEHICLE_RANGE,
}

/** 취침 준비 단계 규칙. 규칙 기반 순수 함수다. */
object BedtimeRules {

    /**
     * 야간 최저기온과 배터리로 설정 온도를 추천한다.
     * @param vehicleRange 차량이 지원하는 설정 온도 범위. null이면 제한하지 않는다 (제어 미지원이라도 추천은 보여준다)
     */
    fun recommendTemperature(
        minTempC: Float?,
        batteryPercent: Int?,
        vehicleRange: ClosedFloatingPointRange<Float>?,
    ): TemperatureRecommendation {
        val notes = mutableListOf<RecommendationNote>()

        var celsius = if (minTempC == null) {
            notes += RecommendationNote.WEATHER_UNKNOWN
            BedtimeThresholds.DEFAULT_TEMP_C
        } else {
            baseByMinTemp(minTempC)
        }

        val isHeating = minTempC == null || minTempC <= BedtimeThresholds.HEATING_MAX_MIN_TEMP_C
        if (isHeating && batteryPercent != null && batteryPercent < BedtimeThresholds.LOW_BATTERY_PERCENT) {
            celsius -= BedtimeThresholds.LOW_BATTERY_REDUCTION_C
            notes += RecommendationNote.LOW_BATTERY_SAVING
        }

        if (vehicleRange != null && celsius !in vehicleRange) {
            celsius = celsius.coerceIn(vehicleRange)
            notes += RecommendationNote.CLAMPED_TO_VEHICLE_RANGE
        }

        return TemperatureRecommendation(snapToStep(celsius), notes)
    }

    /** 사용자가 온도를 한 단계 올리거나 내린다. 차량 범위를 넘지 않는다. */
    fun adjustTemperature(current: Float, steps: Int, vehicleRange: ClosedFloatingPointRange<Float>?): Float {
        val next = snapToStep(current + steps * BedtimeThresholds.STEP_C)
        return vehicleRange?.let { next.coerceIn(it) } ?: next
    }

    /** 슬라이더로 고른 온도를 0.5°C 단위로 맞추고 차량 범위 안으로 자른다 */
    fun setTemperature(celsius: Float, vehicleRange: ClosedFloatingPointRange<Float>?): Float = adjustTemperature(celsius, 0, vehicleRange)

    /** "일출에 맞추기"의 기상 시각 */
    fun sunriseWakeTime(sunrise: LocalTime): LocalTime = sunrise.minusMinutes(BedtimeThresholds.SUNRISE_LEAD_MINUTES)

    /** 온도별 예상 배터리 줄: 고른 온도 −1, 고른 온도, +1 (차량 범위 밖은 빼고, 겹치면 하나만) */
    fun forecastTemps(selected: Float, vehicleRange: ClosedFloatingPointRange<Float>?): List<Float> =
        listOf(selected - BedtimeThresholds.FORECAST_ROW_STEP_C, selected, selected + BedtimeThresholds.FORECAST_ROW_STEP_C)
            .filter { vehicleRange == null || it in vehicleRange }
            .distinct()

    private fun baseByMinTemp(minTempC: Float): Float = when {
        minTempC <= 0f -> 23f
        minTempC <= 10f -> 22f
        minTempC <= 20f -> 21f
        else -> 20f
    }

    private fun snapToStep(celsius: Float): Float =
        (celsius / BedtimeThresholds.STEP_C).roundToInt() * BedtimeThresholds.STEP_C
}
