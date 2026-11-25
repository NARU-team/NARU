package ai.naru.app.camping.arrival

import ai.naru.app.location.LatLng
import java.time.LocalTime

/**
 * 장소 정보를 가져오는 곳. 중계 서버 구현([ServerSiteRepository])과 내장 샘플([DemoSiteRepository])이 있다.
 */
interface SiteRepository {
    suspend fun sites(): List<Site>

    suspend fun conditions(site: Site): SiteConditions

    /**
     * 차량 위치로 오늘 밤 정보 (이슈 #44). 시연 지역 근처면 site가 그 장소, 아니면 null(현재 위치).
     * 위치 조회를 지원하지 않으면 null → 시연 지역 고르기로 대체.
     */
    suspend fun conditionsAt(location: LatLng): LocatedConditions? = null

    /** 근처 야영장. 조회를 지원하지 않거나 실패하면 null, 근처에 없으면 빈 목록 */
    suspend fun nearby(location: LatLng): List<NearbyPlace>? = null
}

data class LocatedConditions(val site: Site?, val conditions: SiteConditions)

/**
 * 차량 근처의 실제 야영장 (한국관광공사 고캠핑, 이슈 #46).
 * @property placeType 입지로 추정한 유형. 모르면 null
 * @property permission 자동차야영장이면 ALLOWED, 아니면 확인 필요(UNKNOWN)
 */
data class NearbyPlace(
    val id: String,
    val name: String,
    val kind: String?,
    val placeType: PlaceType?,
    val permission: CampingPermission,
    val facilities: List<String>,
    val distanceKm: Double,
    val lat: Double,
    val lon: Double,
)

/**
 * 시연 지역 내장 데이터. **실제 장소·날씨가 아닌 샘플 값**이다.
 * 3가지 등급이 하나씩 나오도록 구성했다. 서버 주소를 설정하지 않은 개발 환경과 테스트에서 쓴다.
 */
class DemoSiteRepository : SiteRepository {

    private val demoSites = listOf(
        Site(
            id = "demo-forest",
            name = "시연용 숲 캠핑장",
            placeType = PlaceType.FOREST,
            permission = CampingPermission.ALLOWED,
            facilities = listOf("화장실", "개수대", "주차장"),
        ),
        Site(
            id = "demo-lake",
            name = "시연용 호수 주차장",
            placeType = PlaceType.LAKE,
            permission = CampingPermission.UNKNOWN,
            facilities = listOf("화장실"),
        ),
        Site(
            id = "demo-beach",
            name = "시연용 해변",
            placeType = PlaceType.BEACH,
            permission = CampingPermission.ALLOWED,
            facilities = listOf("화장실", "샤워장"),
        ),
    )

    private val demoConditions = mapOf(
        // 정보 확인: 허용, 영상, 맑음
        "demo-forest" to SiteConditions(
            weather = NightWeather(minTempC = 6f, precipitation = Precipitation.NONE, strongWind = false, maxWindMs = 2f),
            highTides = null,
            sunrise = LocalTime.of(7, 21),
            morningWeather = MorningWeather(tempC = 8f, sky = Sky.CLEAR, dayMaxTempC = 15f, windMs = 2f),
        ),
        // 현장 확인 필요: 허용 여부 불명확, 영하
        "demo-lake" to SiteConditions(
            weather = NightWeather(minTempC = -2f, precipitation = Precipitation.NONE, strongWind = false, maxWindMs = 4f),
            highTides = null,
            sunrise = LocalTime.of(7, 18),
            morningWeather = MorningWeather(tempC = 0f, sky = Sky.CLOUDY, dayMaxTempC = 9f, windMs = 3f),
        ),
        // 주의·이동 권장: 밤사이 만조
        "demo-beach" to SiteConditions(
            weather = NightWeather(minTempC = 9f, precipitation = Precipitation.NONE, strongWind = false, maxWindMs = 5f),
            highTides = listOf(LocalTime.of(2, 40), LocalTime.of(14, 55)),
            lowTides = listOf(LocalTime.of(8, 31)),
            sunrise = LocalTime.of(7, 15),
            morningWeather = MorningWeather(tempC = 11f, sky = Sky.OVERCAST, dayMaxTempC = 16f, windMs = 4f),
        ),
    )

    override suspend fun sites(): List<Site> = demoSites

    override suspend fun conditions(site: Site): SiteConditions =
        demoConditions[site.id] ?: SiteConditions(weather = null, highTides = null)
}
