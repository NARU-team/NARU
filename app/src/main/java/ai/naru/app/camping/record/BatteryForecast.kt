package ai.naru.app.camping.record

import ai.naru.app.camping.night.NightThresholds
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** 예상 잔량 기준값. 기획서에 숫자가 없어 정한 제안값이다 (2026-11-13, 이슈 #25). */
object ForecastThresholds {
    /** 밤이 이보다 짧은 기록은 예측에 쓰지 않는다 (사용량이 안정되지 않음) */
    const val MIN_NIGHT_HOURS = 2.0

    /** (설정 온도 − 바깥 최저기온) 차이가 이 안이면 비슷한 조건으로 본다 */
    const val SIMILAR_DELTA_C = 5f

    /** 비슷한 기록이 이만큼 있어야 예상 범위를 보여준다 ("실측 데이터가 충분할 때") */
    const val MIN_SIMILAR_RECORDS = 3
}

/**
 * 아침 예상 잔량 범위.
 * @property basedOn 근거로 쓴 비슷한 기록 수
 * @property belowLimit 낮은 쪽이 배터리 하한보다 낮음 → 온도를 낮추라고 안내
 */
data class MorningForecast(
    val lowPercent: Int,
    val highPercent: Int,
    val basedOn: Int,
    val belowLimit: Boolean,
)

/** 아침 예상 잔량 규칙. 규칙 기반 순수 함수이며 생성형 AI는 쓰지 않는다. */
object BatteryForecast {

    /** 기록의 시간당 사용량(%/h). 예측에 쓸 수 없는 기록이면 null. */
    fun usagePerHour(record: SessionRecord): Double? {
        val used = record.batteryUsed ?: return null
        if (used < 0) return null // 충전된 기록
        val hours = record.nightHours
        if (hours < ForecastThresholds.MIN_NIGHT_HOURS) return null
        return used / hours
    }

    /**
     * 비슷한 조건의 기록이 충분하면 기상 시각의 잔량 범위를 낸다. 아니면 null (화면은 "데이터가 쌓이면" 문구).
     * 바깥 최저기온을 모르면 조건을 비교할 수 없어 예측하지 않는다.
     */
    fun forecast(
        records: List<SessionRecord>,
        currentBattery: Int?,
        targetTempC: Float,
        minTempC: Float?,
        now: LocalDateTime,
        wakeAt: LocalDateTime,
        limitPercent: Int = NightThresholds.BATTERY_LIMIT_PERCENT,
    ): MorningForecast? {
        if (currentBattery == null || minTempC == null) return null
        val delta = targetTempC - minTempC
        val rates = records.mapNotNull { record ->
            val recordMin = record.minTempC ?: return@mapNotNull null
            if (abs((record.targetTempC - recordMin) - delta) > ForecastThresholds.SIMILAR_DELTA_C) return@mapNotNull null
            usagePerHour(record)
        }
        if (rates.size < ForecastThresholds.MIN_SIMILAR_RECORDS) return null

        val hours = Duration.between(now, wakeAt).toMinutes().coerceAtLeast(0) / 60.0
        val low = floor(currentBattery - rates.max() * hours).toInt().coerceIn(0, 100)
        val high = ceil(currentBattery - rates.min() * hours).toInt().coerceIn(0, 100)
        return MorningForecast(low, high, rates.size, belowLimit = low < limitPercent)
    }
}
