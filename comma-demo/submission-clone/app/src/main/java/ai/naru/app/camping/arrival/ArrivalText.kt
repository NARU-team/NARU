package ai.naru.app.camping.arrival

import java.time.format.DateTimeFormatter

/**
 * 도착 안내 문구. 화면 카드와 음성 멘트 템플릿이 같은 문장을 쓴다.
 * 장소의 안전이나 휴식 효과를 보장하는 표현은 쓰지 않는다.
 */
private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun ArrivalGrade.title(): String = when (this) {
    ArrivalGrade.INFO -> "정보 확인"
    ArrivalGrade.CHECK -> "현장 확인 필요"
    ArrivalGrade.MOVE -> "주의 · 이동 권장"
}

fun ArrivalGrade.guide(): String = when (this) {
    ArrivalGrade.INFO -> "확인된 정보를 정리했어요. 도착 후 주변 상황도 함께 살펴봐 주세요."
    ArrivalGrade.CHECK -> "확인되지 않은 정보가 있어요. 현장 안내판과 주변 상황을 직접 확인해 주세요."
    ArrivalGrade.MOVE -> "주의가 필요한 정보가 있어요. 다른 장소로 이동하는 것을 권장해요."
}

fun ArrivalReason.label(): String = when (this) {
    ArrivalReason.Prohibited -> "차박이 금지된 장소로 확인돼요"
    ArrivalReason.PermissionUnknown -> "차박 허용 여부가 확인되지 않았어요"
    is ArrivalReason.ExtremeCold -> "야간 최저기온 ${minTempC.formatTemp()}°C로 매우 추워요"
    is ArrivalReason.Cold -> "야간 최저기온 ${minTempC.formatTemp()}°C로 영하예요"
    ArrivalReason.HeavyRain -> "밤사이 강한 비 예보가 있어요"
    ArrivalReason.Rain -> "밤사이 비 예보가 있어요"
    ArrivalReason.StrongWind -> "밤사이 강한 바람 예보가 있어요"
    is ArrivalReason.NightHighTide -> "밤사이 ${at.format(hhmm)}에 만조예요"
    ArrivalReason.WeatherUnavailable -> "날씨 정보를 불러오지 못했어요"
    ArrivalReason.TideUnavailable -> "물때 정보를 불러오지 못했어요"
}

fun PlaceType.label(): String = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

fun Precipitation.label(): String = when (this) {
    Precipitation.NONE -> "비 예보 없음"
    Precipitation.RAIN -> "비 예보"
    Precipitation.HEAVY_RAIN -> "강한 비 예보"
}

internal fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
