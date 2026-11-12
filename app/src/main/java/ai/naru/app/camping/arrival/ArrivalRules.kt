package ai.naru.app.camping.arrival

import java.time.LocalTime

/** 3단계 안내 등급. 선언 순서가 보수적인 순서다 (뒤로 갈수록 강함). */
enum class ArrivalGrade {
    /** 정보 확인 */
    INFO,

    /** 현장 확인 필요 */
    CHECK,

    /** 주의·이동 권장 */
    MOVE,
}

/** 등급을 정한 이유. 화면이 문장으로 바꿔 보여준다. */
sealed interface ArrivalReason {
    val grade: ArrivalGrade

    data object Prohibited : ArrivalReason { override val grade = ArrivalGrade.MOVE }
    data class ExtremeCold(val minTempC: Float) : ArrivalReason { override val grade = ArrivalGrade.MOVE }
    data object StrongWind : ArrivalReason { override val grade = ArrivalGrade.MOVE }
    data object HeavyRain : ArrivalReason { override val grade = ArrivalGrade.MOVE }
    data class NightHighTide(val at: LocalTime) : ArrivalReason { override val grade = ArrivalGrade.MOVE }

    data object PermissionUnknown : ArrivalReason { override val grade = ArrivalGrade.CHECK }
    data object WeatherUnavailable : ArrivalReason { override val grade = ArrivalGrade.CHECK }
    data object TideUnavailable : ArrivalReason { override val grade = ArrivalGrade.CHECK }
    data class Cold(val minTempC: Float) : ArrivalReason { override val grade = ArrivalGrade.CHECK }
    data object Rain : ArrivalReason { override val grade = ArrivalGrade.CHECK }
}

data class ArrivalAssessment(
    val grade: ArrivalGrade,
    val reasons: List<ArrivalReason>,
)

/**
 * 판정 기준값. 기획서·설계 문서에 숫자가 없어 정한 제안값이다 (2026-11-13, 이슈 #11).
 * 바꿀 때는 이곳만 고친다.
 */
object ArrivalThresholds {
    /** 이 온도 이하면 주의·이동 권장 */
    const val EXTREME_COLD_C = -10f

    /** 이 온도 이하면 현장 확인 필요 */
    const val COLD_C = 0f

    /** 만조를 확인하는 밤 시간대. 도착 시점에는 기상 시각을 모르므로 기본값을 쓴다. */
    val NIGHT_START: LocalTime = LocalTime.of(22, 0)
    val NIGHT_END: LocalTime = LocalTime.of(7, 0)
}

/**
 * 도착 단계 3단계 안내 판정. 규칙 기반 순수 함수이며, 생성형 AI는 결과를 바꾸지 않는다.
 * 이유가 여러 개면 가장 보수적인 등급을 쓴다.
 */
object ArrivalRules {

    fun assess(
        site: Site,
        conditions: SiteConditions,
        nightStart: LocalTime = ArrivalThresholds.NIGHT_START,
        nightEnd: LocalTime = ArrivalThresholds.NIGHT_END,
    ): ArrivalAssessment {
        val reasons = buildList {
            when (site.permission) {
                CampingPermission.PROHIBITED -> add(ArrivalReason.Prohibited)
                CampingPermission.UNKNOWN -> add(ArrivalReason.PermissionUnknown)
                CampingPermission.ALLOWED -> Unit
            }

            val weather = conditions.weather
            if (weather == null) {
                add(ArrivalReason.WeatherUnavailable)
            } else {
                when {
                    weather.minTempC <= ArrivalThresholds.EXTREME_COLD_C -> add(ArrivalReason.ExtremeCold(weather.minTempC))
                    weather.minTempC <= ArrivalThresholds.COLD_C -> add(ArrivalReason.Cold(weather.minTempC))
                }
                when (weather.precipitation) {
                    Precipitation.HEAVY_RAIN -> add(ArrivalReason.HeavyRain)
                    Precipitation.RAIN -> add(ArrivalReason.Rain)
                    Precipitation.NONE -> Unit
                }
                if (weather.strongWind) add(ArrivalReason.StrongWind)
            }

            // 물때는 해변에서만 확인한다.
            if (site.placeType == PlaceType.BEACH) {
                val tides = conditions.highTides
                if (tides == null) {
                    add(ArrivalReason.TideUnavailable)
                } else {
                    tides.filter { isInNight(it, nightStart, nightEnd) }
                        .forEach { add(ArrivalReason.NightHighTide(it)) }
                }
            }
        }

        val grade = reasons.maxOfOrNull { it.grade } ?: ArrivalGrade.INFO
        return ArrivalAssessment(grade, reasons)
    }

    /** 자정을 넘는 시간대(22:00~07:00)도 처리한다. 경계 시각은 밤에 포함한다. */
    fun isInNight(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) {
            time in start..end
        } else {
            time >= start || time <= end
        }
}
