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
            {"weather":{"minTempC":4.5,"precipitation":"RAIN","strongWind":false},
             "highTides":["19:05","02:40"],"sunrise":"07:10",
             "morningWeather":{"tempC":6,"sky":"CLOUDY"}}
        """.trimIndent()
        assertEquals(
            SiteConditions(
                weather = NightWeather(minTempC = 4.5f, precipitation = Precipitation.RAIN, strongWind = false),
                highTides = listOf(LocalTime.of(19, 5), LocalTime.of(2, 40)),
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
}
