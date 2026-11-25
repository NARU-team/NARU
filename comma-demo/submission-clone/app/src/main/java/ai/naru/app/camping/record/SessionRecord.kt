package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.TempFeedback
import java.time.Duration
import java.time.LocalDateTime

/**
 * 차박 한 번의 기록 (설계 문서 SLEEP_SESSION + SITE_CHECK 중 앱이 아는 값).
 * 밤 단계를 거친 세션만 남긴다. 모르는 값은 null.
 *
 * @property nightStartedAt 밤을 시작한 시각 ("차박 시작하기")
 * @property nightEndedAt 밤이 끝난 시각 (기상 시각·배터리 하한·기어 이탈)
 * @property predictedLowPercent 밤을 시작할 때 계산한 아침 예상 잔량 범위 (설계 문서 predicted_morning_soc). 실제와 비교용
 */
data class SessionRecord(
    val id: String,
    val nightStartedAt: LocalDateTime,
    val nightEndedAt: LocalDateTime,
    val siteName: String,
    val placeType: PlaceType,
    val grade: ArrivalGrade?,
    val minTempC: Float?,
    val targetTempC: Float,
    val theme: PlaceType,
    val nightStartBattery: Int?,
    val nightEndBattery: Int?,
    val endReason: EndReason,
    val predictedLowPercent: Int? = null,
    val predictedHighPercent: Int? = null,
    /** 아침에 고른 온도 느낌. 다음 추천에 반영한다 */
    val feedback: TempFeedback? = null,
) {
    val nightHours: Double get() = Duration.between(nightStartedAt, nightEndedAt).toMinutes() / 60.0

    /** 밤사이 쓴 배터리(%p). 모르면 null, 늘었으면(충전) 음수 */
    val batteryUsed: Int? get() = if (nightStartBattery != null && nightEndBattery != null) nightStartBattery - nightEndBattery else null
}
