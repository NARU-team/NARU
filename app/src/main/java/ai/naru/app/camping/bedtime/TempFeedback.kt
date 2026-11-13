package ai.naru.app.camping.bedtime

import ai.naru.app.camping.record.SessionRecord
import kotlin.math.abs

/** 아침 브리핑의 "온도는 어땠나요?" (피그마 v2, 이슈 #41) */
enum class TempFeedback { COLD, GOOD, HOT }

/** 추천에 반영할 지난 피드백: 그날 설정 온도와 느낌 */
data class FeedbackHint(val targetTempC: Float, val feedback: TempFeedback)

object FeedbackRules {

    /** 바깥 최저기온이 이만큼 안이면 비슷한 밤으로 본다 (예상 잔량과 같은 기준) */
    const val SIMILAR_MIN_TEMP_C = 5f

    /** 더웠으면 내리고 추웠으면 올리는 폭 */
    const val ADJUST_C = 1f

    /**
     * 비슷한 밤(바깥 최저 ±5°C)의 가장 최근 피드백. 지금 최저기온을 모르면 비교할 수 없어 null.
     */
    fun hint(records: List<SessionRecord>, minTempC: Float?): FeedbackHint? {
        if (minTempC == null) return null
        return records
            .filter { it.feedback != null && it.minTempC != null && abs(it.minTempC - minTempC) <= SIMILAR_MIN_TEMP_C }
            .maxByOrNull { it.nightStartedAt }
            ?.let { FeedbackHint(it.targetTempC, it.feedback!!) }
    }

    /** 피드백을 반영한 기준 온도: 더웠음 −1°C, 추웠음 +1°C, 딱 좋았음 그대로 */
    fun adjusted(hint: FeedbackHint): Float = hint.targetTempC + when (hint.feedback) {
        TempFeedback.HOT -> -ADJUST_C
        TempFeedback.COLD -> ADJUST_C
        TempFeedback.GOOD -> 0f
    }

    /** 나루 제안 칩 문구: "지난 차박 22°C는 더웠음" */
    fun chipText(hint: FeedbackHint): String {
        val temp = if (hint.targetTempC % 1f == 0f) hint.targetTempC.toInt().toString() else "%.1f".format(hint.targetTempC)
        val feel = when (hint.feedback) {
            TempFeedback.HOT -> "더웠음"
            TempFeedback.COLD -> "추웠음"
            TempFeedback.GOOD -> "딱 좋았음"
        }
        return "지난 차박 ${temp}°C는 $feel"
    }
}
