package ai.naru.app.camping.night

import java.time.Duration
import java.time.LocalDateTime

/** 밤사이 온도 기록 한 번 (아침 브리핑 그래프용, 이슈 #41). 차량이 주지 않은 값은 null. */
data class TempSample(val at: LocalDateTime, val cabinC: Float?, val outsideC: Float?)

/**
 * 밤사이 온도 요약.
 * @property cabinRange 실내 최저~최고. 실내 기록이 2번 미만이면 null (그래프·범위를 보여주지 않음)
 */
data class NightTempSummary(
    val cabinRange: ClosedFloatingPointRange<Float>?,
    val outsideMinC: Float?,
    val cabinSamples: List<TempSample>,
)

object NightTemps {

    /** 밤 동안 이 간격마다 온도를 읽는다 (폴링 최소화: 10분) */
    val SAMPLE_INTERVAL: Duration = Duration.ofMinutes(10)

    /** 그래프·범위를 보여주려면 필요한 실내 기록 수 */
    const val MIN_CABIN_SAMPLES = 2

    fun isSampleDue(last: LocalDateTime?, now: LocalDateTime): Boolean =
        last == null || !now.isBefore(last.plus(SAMPLE_INTERVAL))

    fun summarize(samples: List<TempSample>): NightTempSummary {
        val cabin = samples.filter { it.cabinC != null }
        val values = cabin.mapNotNull { it.cabinC }
        return NightTempSummary(
            cabinRange = if (values.size >= MIN_CABIN_SAMPLES) values.min()..values.max() else null,
            outsideMinC = samples.mapNotNull { it.outsideC }.minOrNull(),
            cabinSamples = cabin,
        )
    }
}
