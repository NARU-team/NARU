package ai.naru.app.camping.morning

import ai.naru.app.camping.arrival.MorningWeather
import ai.naru.app.camping.arrival.Sky
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 기상·아침 브리핑 화면 문구 (피그마 v2, 이슈 #41). 규칙 기반 순수 함수.
 * 모르는 값은 null을 돌려주고 화면은 그 줄을 빼거나 "기록이 부족해요"로 보여준다 (숫자를 지어내지 않음).
 */
object MorningPresenter {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** "12분 뒤 07:02에 해가 떠요." / "07:02에 해가 떴어요." */
    fun sunriseSentence(now: LocalTime, sunrise: LocalTime?): String? {
        sunrise ?: return null
        return if (sunrise.isAfter(now)) "${duration(Duration.between(now, sunrise))} 뒤 ${sunrise.format(hhmm)}에 해가 떠요." else "${sunrise.format(hhmm)}에 해가 떴어요."
    }

    /** 기상 화면 일출 카드 값: "12분 뒤 07:02" / "07:02에 떴어요" */
    fun sunriseCard(now: LocalTime, sunrise: LocalTime?): String? {
        sunrise ?: return null
        return if (sunrise.isAfter(now)) "${duration(Duration.between(now, sunrise))} 뒤 ${sunrise.format(hhmm)}" else "${sunrise.format(hhmm)}에 떴어요"
    }

    /** "7시간 40분 머물렀어요" */
    fun staySentence(nightStartedAt: LocalDateTime?, now: LocalDateTime): String? {
        nightStartedAt ?: return null
        return "${duration(Duration.between(nightStartedAt, now).coerceAtLeast(Duration.ZERO))} 머물렀어요"
    }

    /**
     * 배터리 카드 아래 줄: "밤새 11% 사용, 예상 58~63% 안"
     * 예상 범위가 없으면 사용량만, 시작 잔량을 모르면 null.
     */
    fun batteryDetail(now: Int?, atNightStart: Int?, predictedLow: Int?, predictedHigh: Int?): String? {
        if (now == null || atNightStart == null) return null
        val used = (atNightStart - now).coerceAtLeast(0)
        val prediction = if (predictedLow != null && predictedHigh != null) {
            when {
                now < predictedLow -> ", 예상(${predictedLow}~${predictedHigh}%)보다 많이 썼어요"
                now > predictedHigh -> ", 예상(${predictedLow}~${predictedHigh}%)보다 적게 썼어요"
                else -> ", 예상 ${predictedLow}~${predictedHigh}% 안"
            }
        } else {
            ""
        }
        return "밤새 ${used}% 사용$prediction"
    }

    /** "20~22°C" (실내 기록이 부족하면 null) */
    fun cabinRange(range: ClosedFloatingPointRange<Float>?): String? =
        range?.let { if (it.start.formatTemp() == it.endInclusive.formatTemp()) "${it.start.formatTemp()}°C" else "${it.start.formatTemp()}~${it.endInclusive.formatTemp()}°C" }

    /** 실외 최저: 밤사이 기록이 있으면 실측, 없으면 예보 최저 */
    fun outsideLine(sampledMinC: Float?, forecastMinC: Float?): String? = when {
        sampledMinC != null -> "실외는 ${sampledMinC.formatTemp()}°C까지 내려갔어요"
        forecastMinC != null -> "예보 최저는 ${forecastMinC.formatTemp()}°C였어요"
        else -> null
    }

    /** "4°C 맑음" */
    fun weatherValue(weather: MorningWeather?): String? = weather?.let { "${it.tempC.formatTemp()}°C ${it.sky.label()}" }

    /** "낮 최고 13°C, 바람 2m/s" */
    fun weatherDetail(weather: MorningWeather?): String? {
        weather ?: return null
        val parts = listOfNotNull(
            weather.dayMaxTempC?.let { "낮 최고 ${it.formatTemp()}°C" },
            weather.windMs?.let { "바람 ${it.formatTemp()}m/s" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun duration(d: Duration): String {
        val hours = d.toHours()
        val minutes = d.toMinutes() % 60
        return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    }

    private fun Sky.label() = when (this) {
        Sky.CLEAR -> "맑음"
        Sky.CLOUDY -> "구름 많음"
        Sky.OVERCAST -> "흐림"
        Sky.RAIN -> "비"
        Sky.SNOW -> "눈"
    }

    private fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
}
