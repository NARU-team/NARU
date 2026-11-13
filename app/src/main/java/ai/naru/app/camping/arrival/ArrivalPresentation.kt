package ai.naru.app.camping.arrival

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 도착 안내 화면에 보여줄 문구 (피그마 v2 "도착 안내 1/3" 4가지, 이슈 #35).
 * 판정(등급·근거)은 [ArrivalRules]가 정하고, 여기서는 그 결과를 제목·배지·버튼·카드 문구로 바꾸기만 한다.
 * 규칙 기반 순수 함수라 단위 테스트한다. 장소의 안전이나 이용 가능을 보장하는 표현은 쓰지 않는다.
 */
enum class ArrivalStatus {
    /** 정보 확인: 특이사항 없음 */
    INFO,

    /** 현장 확인 필요 */
    CHECK,

    /** 주의·이동 권장 */
    MOVE,

    /** 조회 실패: 날씨(또는 해변의 물때)를 불러오지 못함 */
    LOOKUP_FAILED,
}

enum class ArrivalBadge { INFO, CHECK, MOVE }

/** 카드 아이콘 종류 (화면이 아이콘으로 바꾼다) */
enum class ArrivalCardKind { WEATHER, TIDE, SUNRISE, USAGE }

data class ArrivalCard(
    val kind: ArrivalCardKind,
    val label: String,
    val value: String,
    val description: String,
    val source: String,
    /** 주의 색 (연한 빨강) */
    val warning: Boolean,
)

/** 버튼: 주요(진회색) / 보조(연회색). 다시 시도 버튼은 조회 실패일 때 한 번만 */
data class ArrivalButtons(
    val confirmText: String,
    val confirmIsPrimary: Boolean,
    val showRetry: Boolean,
)

data class ArrivalPresentation(
    val state: ArrivalStatus,
    val title: String,
    val subtitle: String?,
    val badge: ArrivalBadge,
    val badgeText: String,
    val cards: List<ArrivalCard>,
    val buttons: ArrivalButtons,
    val note: String,
)

object ArrivalPresenter {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    const val SOURCE_WEATHER = "기상청 단기예보"
    const val SOURCE_TIDE = "국립해양조사원 조석예보"
    const val SOURCE_SUNRISE = "좌표로 계산한 일출 시각"

    /** 이용 정보는 시연 지역에 내장한 값이다 (관광공사 API 연동 전, 출처를 사실대로 적는다) */
    const val SOURCE_USAGE = "시연 지역 내장 정보"

    /**
     * @param retried 다시 시도를 이미 썼는지 (한 번만 허용)
     */
    fun present(site: Site, conditions: SiteConditions, assessment: ArrivalAssessment, retried: Boolean): ArrivalPresentation {
        val reasons = assessment.reasons
        val weatherFailed = conditions.weather == null
        val tideFailed = site.placeType == PlaceType.BEACH && conditions.highTides == null
        // 이동 권장 근거가 있으면 조회 실패보다 먼저 보여준다 (보수적으로)
        val state = when {
            assessment.grade == ArrivalGrade.MOVE -> ArrivalStatus.MOVE
            weatherFailed || tideFailed -> ArrivalStatus.LOOKUP_FAILED
            assessment.grade == ArrivalGrade.CHECK -> ArrivalStatus.CHECK
            else -> ArrivalStatus.INFO
        }

        val (title, subtitle) = when (state) {
            ArrivalStatus.MOVE -> "오늘 밤은 다른 곳을 권해요" to null
            ArrivalStatus.LOOKUP_FAILED -> "일부 정보를 불러오지 못했어요" to
                (if (retried) "다시 불러와도 받지 못했어요. 현장에서 직접 확인해 주세요" else "${if (weatherFailed) "날씨" else "물때"}는 한 번 더 불러올 수 있어요")
            ArrivalStatus.CHECK -> checkTitle(reasons)
            ArrivalStatus.INFO -> "조회한 정보에 특이사항이 없어요" to null
        }

        val badge = when (state) {
            ArrivalStatus.INFO -> ArrivalBadge.INFO
            ArrivalStatus.MOVE -> ArrivalBadge.MOVE
            else -> ArrivalBadge.CHECK
        }
        val badgeText = when (badge) {
            ArrivalBadge.INFO -> "정보 확인"
            ArrivalBadge.CHECK -> "현장 확인 필요"
            ArrivalBadge.MOVE -> "이동 권장"
        }

        val buttons = when (state) {
            ArrivalStatus.INFO -> ArrivalButtons("취침 준비하기", confirmIsPrimary = true, showRetry = false)
            ArrivalStatus.CHECK -> ArrivalButtons("현장 확인 후 계속", confirmIsPrimary = true, showRetry = false)
            // 이동 권장이어도 일부 조회가 실패했으면 다시 시도를 한 번 준다
            ArrivalStatus.MOVE -> ArrivalButtons("그래도 준비하기", confirmIsPrimary = false, showRetry = (weatherFailed || tideFailed) && !retried)
            ArrivalStatus.LOOKUP_FAILED -> ArrivalButtons("취침 준비하기", confirmIsPrimary = retried, showRetry = !retried)
        }

        val note = when (state) {
            ArrivalStatus.INFO -> "조회 시점의 정보예요. 장소의 안전과 이용 가능 여부는 직접 확인해 주세요."
            ArrivalStatus.LOOKUP_FAILED -> "다시 시도는 한 번만 해요. 장소의 안전과 이용 가능 여부는 직접 확인해 주세요."
            else -> "나루는 조회한 정보만 알려 드려요. 장소의 안전과 이용 가능 여부는 직접 확인해 주세요."
        }

        val cards = listOf(
            weatherCard(conditions.weather, reasons),
            if (site.placeType == PlaceType.BEACH) tideCard(conditions, reasons) else sunriseCard(conditions.sunrise),
            usageCard(site),
        )
        return ArrivalPresentation(state, title, subtitle, badge, badgeText, cards, buttons, note)
    }

    /** 현장 확인 필요: 가장 앞선 근거로 제목을 정한다 */
    private fun checkTitle(reasons: List<ArrivalReason>): Pair<String, String?> = when (reasons.firstOrNull { it.grade == ArrivalGrade.CHECK }) {
        ArrivalReason.PermissionUnknown -> "차박 허용 여부를 확인해 주세요" to "현장 안내판에서 확인할 수 있어요"
        is ArrivalReason.Cold -> "밤에 영하로 내려가요" to "방한 준비를 확인해 주세요"
        ArrivalReason.Rain -> "밤사이 비 소식이 있어요" to "주변 지형과 물 빠짐을 확인해 주세요"
        else -> "현장에서 한 번 더 확인해 주세요" to null
    }

    private fun weatherCard(weather: NightWeather?, reasons: List<ArrivalReason>): ArrivalCard {
        if (weather == null) {
            return ArrivalCard(ArrivalCardKind.WEATHER, "오늘 밤 날씨 (조회 실패)", "조회 실패", "연결을 확인한 뒤 다시 시도해 주세요", SOURCE_WEATHER, warning = true)
        }
        val temp = weather.minTempC.formatTemp()
        val wind = weather.maxWindMs?.let { "바람 ${it.formatTemp()}m/s" }
        val rainText = when (weather.precipitation) {
            Precipitation.NONE -> "비 소식 없음"
            Precipitation.RAIN -> "비 예보가 있어요"
            Precipitation.HEAVY_RAIN -> "강한 비 예보가 있어요"
        }
        val warn = reasons.any { it is ArrivalReason.StrongWind || it is ArrivalReason.HeavyRain || it is ArrivalReason.ExtremeCold }
        val label = if (warn) "오늘 밤 날씨 (주의)" else "오늘 밤 날씨"
        return when {
            weather.strongWind -> ArrivalCard(
                ArrivalCardKind.WEATHER, label,
                weather.maxWindMs?.let { "강한 바람 ${it.formatTemp()}m/s" } ?: "강한 바람",
                "밤새 강한 바람이 예보돼 있어요. 최저 ${temp}°C예요", SOURCE_WEATHER, warn,
            )
            weather.precipitation == Precipitation.HEAVY_RAIN -> ArrivalCard(
                ArrivalCardKind.WEATHER, label, "강한 비", "밤새 강한 비가 예보돼 있어요. 최저 ${temp}°C예요", SOURCE_WEATHER, warn,
            )
            else -> ArrivalCard(
                ArrivalCardKind.WEATHER, label, "최저 ${temp}°C", listOfNotNull(wind, rainText).joinToString(", "), SOURCE_WEATHER, warn,
            )
        }
    }

    private fun tideCard(conditions: SiteConditions, reasons: List<ArrivalReason>): ArrivalCard {
        val highs = conditions.highTides
            ?: return ArrivalCard(ArrivalCardKind.TIDE, "내일 새벽 물때 (조회 실패)", "조회 실패", "연결을 확인한 뒤 다시 시도해 주세요", SOURCE_TIDE, warning = true)
        val nightHigh = reasons.filterIsInstance<ArrivalReason.NightHighTide>().firstOrNull()?.at
        val shown = nightHigh ?: highs.firstOrNull()
            ?: return ArrivalCard(ArrivalCardKind.TIDE, "내일 새벽 물때", "만조 없음", "밤사이 만조가 없어요", SOURCE_TIDE, warning = false)
        val low = conditions.lowTides?.firstOrNull()?.let { "간조는 ${it.format(hhmm)}이에요" }
        val desc = listOfNotNull(if (nightHigh != null) "자는 동안 물이 가장 높아져요" else null, low).joinToString("\n")
        return ArrivalCard(
            ArrivalCardKind.TIDE,
            if (nightHigh != null) "내일 새벽 물때 (주의)" else "내일 새벽 물때",
            "만조 ${shown.format(hhmm)}",
            desc,
            SOURCE_TIDE,
            warning = nightHigh != null,
        )
    }

    private fun sunriseCard(sunrise: LocalTime?): ArrivalCard = ArrivalCard(
        ArrivalCardKind.SUNRISE,
        "내일 일출",
        sunrise?.format(hhmm) ?: "알 수 없음",
        if (sunrise != null) "일출에 맞춰 깨워 드릴 수 있어요" else "일출 시각을 계산하지 못했어요",
        SOURCE_SUNRISE,
        warning = false,
    )

    private fun usageCard(site: Site): ArrivalCard {
        val facilities = if (site.facilities.isEmpty()) null else site.facilities.joinToString(", ").let { "$it${subjectParticle(it)} 있어요" }
        return when (site.permission) {
            CampingPermission.ALLOWED -> ArrivalCard(
                ArrivalCardKind.USAGE, "이용 정보", "등록된 차박 장소",
                listOfNotNull(facilities, "오늘 밤 이용 가능 여부는 현장에서 확인해 주세요").joinToString("\n"), SOURCE_USAGE, warning = false,
            )
            CampingPermission.UNKNOWN -> ArrivalCard(
                ArrivalCardKind.USAGE, "이용 정보 (확인 필요)", "차박 허용 여부를 찾지 못했어요",
                listOfNotNull("현장 안내판을 확인해 주세요", facilities).joinToString("\n"), SOURCE_USAGE, warning = false,
            )
            CampingPermission.PROHIBITED -> ArrivalCard(
                ArrivalCardKind.USAGE, "이용 정보 (주의)", "차박 금지 장소예요",
                "다른 장소로 이동해 주세요", SOURCE_USAGE, warning = true,
            )
        }
    }
}

/** 주격 조사: 마지막 글자에 받침이 있으면 "이", 없으면 "가" (예: 샤워장이, 개수대가) */
internal fun subjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "이"
    if (last !in '가'..'힣') return "이"
    return if ((last - '가') % 28 != 0) "이" else "가"
}
