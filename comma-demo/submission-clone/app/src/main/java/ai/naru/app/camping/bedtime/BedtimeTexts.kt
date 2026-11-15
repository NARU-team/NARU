package ai.naru.app.camping.bedtime

import ai.naru.app.camping.record.MorningForecast

/**
 * 취침 준비의 "나루 제안" 문장 (피그마 v2 "취침 준비 2/3", 이슈 #37). 규칙 결과를 문장으로 바꾼다.
 * 예상 잔량은 기록이 충분할 때만 범위로 말한다 (숫자를 지어내지 않음).
 */
object BedtimeTexts {

    fun suggestion(
        minTempC: Float?,
        recommendation: TemperatureRecommendation,
        forecast: MorningForecast?,
        feedback: FeedbackHint? = null,
    ): String {
        val temp = recommendation.celsius.formatTemp()
        val first = when {
            minTempC == null -> "날씨 정보가 없어 ${temp}°C를 기본으로 추천해요."
            feedback != null && RecommendationNote.FEEDBACK_APPLIED in recommendation.notes -> when (feedback.feedback) {
                TempFeedback.HOT -> "지난 비슷한 밤에 ${feedback.targetTempC.formatTemp()}°C가 더웠다고 하셔서 ${temp}°C를 추천해요."
                TempFeedback.COLD -> "지난 비슷한 밤에 ${feedback.targetTempC.formatTemp()}°C가 추웠다고 하셔서 ${temp}°C를 추천해요."
                TempFeedback.GOOD -> "지난 비슷한 밤에 ${feedback.targetTempC.formatTemp()}°C가 딱 좋았다고 하셔서 ${temp}°C를 추천해요."
            }
            RecommendationNote.LOW_BATTERY_SAVING in recommendation.notes ->
                "밤 최저 ${minTempC.formatTemp()}°C지만 배터리를 아끼려고 ${temp}°C를 추천해요."
            else -> "밤 최저 ${minTempC.formatTemp()}°C라 ${temp}°C를 추천해요."
        }
        val second = forecast?.let { " 아침까지 배터리는 ${it.lowPercent}~${it.highPercent}% 남을 것으로 예상해요." } ?: ""
        return first + second
    }

    private fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

    /**
     * 나루 제안 칩. 누르면 그 문장을 발화처럼 해석해 제안을 만든다.
     * 기상 칩은 지금 설정과 겹치지 않게 고른다 (이미 7시면 "7시에 깨워줘"는 바꿀 게 없음).
     */
    fun suggestionChips(settings: BedtimeSettings, sunrise: java.time.LocalTime?): List<String> {
        val wake = when {
            sunrise != null && settings.wakeBasis != WakeBasis.SUNRISE -> "일출에 깨워줘"
            settings.wakeAt != java.time.LocalTime.of(7, 0) -> "7시에 깨워줘"
            else -> "6시 반에 깨워줘"
        }
        return listOf("더 따뜻하게", "배터리 아끼기", wake)
    }
}
