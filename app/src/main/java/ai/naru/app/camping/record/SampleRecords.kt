package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.PlaceType
import java.time.LocalDateTime

/**
 * 시연용 샘플 기록 (디버그 패널 전용). **실제 측정값이 아니다.**
 * 에뮬레이터의 밤은 몇 분이라 실제 기록으로는 예측이 나오지 않아서, 지금 조건과 비슷한 지난 밤 4건을 만든다.
 */
object SampleRecords {

    /** 시간당 사용량(%/h) 샘플. 겨울 난방 기준으로 잡은 값이다. */
    private val RATES = listOf(1.0, 1.3, 1.6, 1.2)

    fun generate(now: LocalDateTime, minTempC: Float, targetTempC: Float): List<SessionRecord> =
        RATES.mapIndexed { i, rate ->
            val start = now.minusDays((i + 1).toLong()).withHour(22).withMinute(0).withSecond(0).withNano(0)
            val hours = 8L
            val startBattery = 85 - i * 3
            SessionRecord(
                id = "sample-$i-${start.toLocalDate()}",
                nightStartedAt = start,
                nightEndedAt = start.plusHours(hours),
                siteName = "샘플 기록 ${i + 1}",
                placeType = PlaceType.entries[i % PlaceType.entries.size],
                grade = ArrivalGrade.INFO,
                minTempC = minTempC + (i % 3 - 1), // 지금 최저기온 ±1°C
                targetTempC = targetTempC,
                theme = PlaceType.entries[i % PlaceType.entries.size],
                nightStartBattery = startBattery,
                nightEndBattery = (startBattery - rate * hours).toInt(),
                endReason = EndReason.WAKE_TIME,
            )
        }
}
