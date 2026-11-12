package ai.naru.app.camping.arrival

import java.time.LocalTime

/**
 * 장소 정보를 가져오는 곳.
 * 지금은 시연 지역 내장 데이터를 쓰고, 중계 서버가 생기면 서버 구현으로 바꾼다.
 */
interface SiteRepository {
    suspend fun sites(): List<Site>

    suspend fun conditions(site: Site): SiteConditions
}

/**
 * 시연 지역 내장 데이터. **실제 장소·날씨가 아닌 샘플 값**이다.
 * 3가지 등급이 하나씩 나오도록 구성했다.
 *
 * TODO(다음 이슈): 중계 서버(기상청·관광공사·국립해양조사원) 연동 구현으로 교체
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
            weather = NightWeather(minTempC = 6f, precipitation = Precipitation.NONE, strongWind = false),
            highTides = null,
        ),
        // 현장 확인 필요: 허용 여부 불명확, 영하
        "demo-lake" to SiteConditions(
            weather = NightWeather(minTempC = -2f, precipitation = Precipitation.NONE, strongWind = false),
            highTides = null,
        ),
        // 주의·이동 권장: 밤사이 만조
        "demo-beach" to SiteConditions(
            weather = NightWeather(minTempC = 9f, precipitation = Precipitation.NONE, strongWind = false),
            highTides = listOf(LocalTime.of(2, 40), LocalTime.of(14, 55)),
        ),
    )

    override suspend fun sites(): List<Site> = demoSites

    override suspend fun conditions(site: Site): SiteConditions =
        demoConditions[site.id] ?: SiteConditions(weather = null, highTides = null)
}
