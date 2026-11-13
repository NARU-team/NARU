package ai.naru.app.camping.bedtime

import ai.naru.app.camping.record.MorningForecast

/**
 * 취침 준비의 "나루 제안" 문장 (피그마 v2 "취침 준비 2/3", 이슈 #37). 규칙 결과를 문장으로 바꾼다.
 * 예상 잔량은 기록이 충분할 때만 범위로 말한다 (숫자를 지어내지 않음).
 */
object BedtimeTexts {

    fun suggestion(minTempC: Float?, recommendation: TemperatureRecommendation, forecast: MorningForecast?): String {
        val temp = recommendation.celsius.formatTemp()
        val first = when {
            minTempC == null -> "날씨 정보가 없어 ${temp}°C를 기본으로 추천해요."
            RecommendationNote.LOW_BATTERY_SAVING in recommendation.notes ->
                "밤 최저 ${minTempC.formatTemp()}°C지만 배터리를 아끼려고 ${temp}°C를 추천해요."
            else -> "밤 최저 ${minTempC.formatTemp()}°C라 ${temp}°C를 추천해요."
        }
        val second = forecast?.let { " 아침까지 배터리는 ${it.lowPercent}~${it.highPercent}% 남을 것으로 예상해요." } ?: ""
        return first + second
    }

    private fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
}
