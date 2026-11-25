package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.TempFeedback
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

    /**
     * 데모 영상용 지난 기록 4건 (디버그 패널 "시연 기록 넣기" 전용, **실제 측정값이 아니다**).
     * 데모 시나리오에 맞춘다: 가장 최근 밤은 22°C에서 "더웠음" → 오늘 추천 21°C, 칩 "지난 차박 22°C는 더웠음",
     * 홈 칩 "지난번 자라섬 캠핑장, 22°C 더웠음". 4건 모두 비슷한 밤이라 아침 예상 잔량이 나온다.
     * 시간당 사용량은 겨울 난방 기준 1.0~1.5%/h (배터리 70%, 22시에 시작해 06:50 기상이면 약 57~62%).
     */
    fun demo(now: LocalDateTime, minTempC: Float): List<SessionRecord> = DEMO.mapIndexed { i, d ->
        val start = now.minusDays(d.daysAgo).withHour(22).withMinute(0).withSecond(0).withNano(0)
        val hours = 8L
        SessionRecord(
            id = "demo-$i-${start.toLocalDate()}",
            nightStartedAt = start,
            nightEndedAt = start.plusHours(hours),
            siteName = d.site,
            placeType = d.placeType,
            grade = ArrivalGrade.INFO,
            minTempC = minTempC + d.minOffsetC,
            targetTempC = d.targetC,
            theme = d.placeType,
            nightStartBattery = d.startBattery,
            nightEndBattery = (d.startBattery - d.ratePerHour * hours).toInt(),
            endReason = EndReason.WAKE_TIME,
            feedback = d.feedback,
        )
    }

    private data class DemoNight(
        val daysAgo: Long,
        val site: String,
        val placeType: PlaceType,
        val targetC: Float,
        val feedback: TempFeedback?,
        val startBattery: Int,
        val ratePerHour: Double,
        val minOffsetC: Float,
    )

    private val DEMO = listOf(
        DemoNight(1, "자라섬 캠핑장", PlaceType.LAKE, 22f, TempFeedback.HOT, 84, 1.5, 0f),
        DemoNight(5, "가평 숲 캠핑장", PlaceType.FOREST, 22f, TempFeedback.GOOD, 82, 1.2, -1f),
        DemoNight(9, "의암호 주차장", PlaceType.LAKE, 21f, TempFeedback.GOOD, 80, 1.0, 1f),
        DemoNight(14, "태안 해변 야영장", PlaceType.BEACH, 22f, null, 78, 1.3, 0f),
    )
}
