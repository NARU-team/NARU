package ai.naru.app.camping.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** 서버(`server/api`) 응답 모양과 앱 모델이 맞는지 확인한다. */
class ServerJsonTest {

    @Test
    fun `장소 목록을 읽는다`() {
        val json = """[{"id":"demo-beach","name":"태안 꽃지해수욕장 (시연)","placeType":"BEACH","permission":"ALLOWED","facilities":["화장실","샤워장"]}]"""
        assertEquals(
            listOf(Site("demo-beach", "태안 꽃지해수욕장 (시연)", PlaceType.BEACH, CampingPermission.ALLOWED, listOf("화장실", "샤워장"))),
            ServerJson.parseSites(json),
        )
    }

    @Test
    fun `오늘 밤 정보를 읽는다`() {
        val json = """
            {"weather":{"minTempC":4.5,"precipitation":"RAIN","strongWind":false,"maxWindMs":3.2},
             "highTides":["19:05","02:40"],"lowTides":["21:10"],"sunrise":"07:10",
             "morningWeather":{"tempC":6,"sky":"CLOUDY"}}
        """.trimIndent()
        assertEquals(
            SiteConditions(
                weather = NightWeather(minTempC = 4.5f, precipitation = Precipitation.RAIN, strongWind = false, maxWindMs = 3.2f),
                highTides = listOf(LocalTime.of(19, 5), LocalTime.of(2, 40)),
                lowTides = listOf(LocalTime.of(21, 10)),
                sunrise = LocalTime.of(7, 10),
                morningWeather = MorningWeather(tempC = 6f, sky = Sky.CLOUDY),
            ),
            ServerJson.parseConditions(json),
        )
    }

    @Test
    fun `서버가 불러오지 못한 항목은 null로 둔다`() {
        val result = ServerJson.parseConditions("""{"weather":null,"highTides":null,"sunrise":"07:10","morningWeather":null}""")
        assertNull(result.weather)
        assertNull(result.highTides)
        assertNull(result.morningWeather)
        assertEquals(LocalTime.of(7, 10), result.sunrise)
    }

    @Test
    fun `위치 조회 응답 - 시연 지역이면 장소, 아니면 null`() {
        val cond = """{"weather":null,"highTides":null,"sunrise":"07:10","morningWeather":null}"""
        val matched = ServerJson.parseLocated("""{"site":{"id":"demo-beach","name":"태안 꽃지해수욕장 (시연)","placeType":"BEACH","permission":"ALLOWED","facilities":["화장실"]},"distanceKm":0.4,"conditions":$cond}""")
        assertEquals("demo-beach", matched.site?.id)
        assertEquals(LocalTime.of(7, 10), matched.conditions.sunrise)
        val here = ServerJson.parseLocated("""{"site":null,"distanceKm":null,"conditions":$cond}""")
        assertNull(here.site)
    }

    @Test
    fun `근처 야영장 응답 - 조회 불가면 null`() {
        val ok = ServerJson.parseNearby("""{"available":true,"places":[{"id":"1","name":"가평 오토캠핑장","kind":"자동차야영장","placeType":null,"permission":"ALLOWED","facilities":["화장실"],"distanceKm":1.2,"lat":37.6,"lon":127.49,"address":null}]}""")!!
        assertEquals("가평 오토캠핑장", ok.single().name)
        assertNull(ok.single().placeType)
        assertEquals(1.2, ok.single().distanceKm, 0.001)
        assertNull(ServerJson.parseNearby("""{"available":false,"places":[]}"""))
    }
}
