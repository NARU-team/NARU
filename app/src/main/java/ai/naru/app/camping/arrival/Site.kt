package ai.naru.app.camping.arrival

import java.time.LocalTime

/** 장소 유형. 밤 테마(해변·숲·호수)와도 연결된다. */
enum class PlaceType { BEACH, FOREST, LAKE }

/** 차박 허용 여부. 데이터가 없으면 허용으로 간주하지 않고 [UNKNOWN]으로 둔다. */
enum class CampingPermission { ALLOWED, UNKNOWN, PROHIBITED }

/** 장소의 고정 정보 (관광공사 시설 정보에 해당). */
data class Site(
    val id: String,
    val name: String,
    val placeType: PlaceType,
    val permission: CampingPermission,
    val facilities: List<String>,
)

enum class Precipitation { NONE, RAIN, HEAVY_RAIN }

/**
 * 야간 날씨 (기상청 단기예보에 해당).
 * @property minTempC 야간 최저기온(°C)
 */
data class NightWeather(
    val minTempC: Float,
    val precipitation: Precipitation,
    val strongWind: Boolean,
    /** 밤 최대 풍속(m/s). 모르면 null */
    val maxWindMs: Float? = null,
)

/** 아침 하늘 상태 (기상청 단기예보 SKY·PTY에 해당). */
enum class Sky { CLEAR, CLOUDY, OVERCAST, RAIN, SNOW }

/**
 * 내일 아침 날씨 (기상청 단기예보에 해당). 아침 브리핑에 쓴다.
 * @property tempC 기상 시각 무렵 기온(°C)
 */
data class MorningWeather(
    val tempC: Float,
    val sky: Sky,
)

/**
 * 오늘 밤 장소의 변하는 정보. 불러오지 못한 값은 null이다.
 * @property weather null이면 날씨를 불러오지 못함
 * @property highTides 오늘 밤~내일 아침 만조 시각. null이면 물때를 불러오지 못함 (해변이 아니면 확인하지 않는다)
 * @property sunrise 내일 일출 시각. null이면 모름. TODO(미확정): 일출 시각 출처 (중계 서버 계산 또는 외부 API)
 * @property morningWeather 내일 아침 날씨. null이면 모름. TODO(다음 이슈): 중계 서버에서 기상 시각 기준으로 다시 받기
 */
data class SiteConditions(
    val weather: NightWeather?,
    val highTides: List<LocalTime>?,
    val sunrise: LocalTime? = null,
    val morningWeather: MorningWeather? = null,
    /** 밤~아침 간조 시각. 해변이 아니거나 모르면 null */
    val lowTides: List<LocalTime>? = null,
)
