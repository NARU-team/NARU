package ai.naru.app.camping.narration

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.ArrivalReason
import ai.naru.app.camping.arrival.CampingPermission
import ai.naru.app.camping.arrival.NightWeather
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.Precipitation
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.morning.MorningBriefing
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ai.naru.app.camping.record.MorningForecast
import org.junit.Test
import java.time.LocalTime

class NarrationScriptsTest {

    private val beach = Site("demo-beach", "태안 꽃지해수욕장 (시연)", PlaceType.BEACH, CampingPermission.ALLOWED, listOf("화장실"))

    @Test
    fun `도착 멘트는 날씨, 물때, 이용 정보 순서로 설명하고 등급 안내로 끝낸다`() {
        val conditions = SiteConditions(
            NightWeather(13f, Precipitation.RAIN, false),
            listOf(LocalTime.of(6, 9)),
            lowTides = listOf(LocalTime.of(0, 30)),
        )
        val assessment = ArrivalAssessment(ArrivalGrade.MOVE, listOf(ArrivalReason.Rain, ArrivalReason.NightHighTide(LocalTime.of(6, 9))))

        val request = NarrationScripts.arrival(beach, conditions, assessment)

        assertEquals(NarrationMoment.ARRIVAL, request.moment)
        assertEquals("주의 · 이동 권장", request.facts["안내등급"])
        assertEquals("태안 꽃지해수욕장", request.facts["장소"])
        assertEquals(listOf("밤사이 비 예보가 있어요", "밤사이 06:09에 만조예요"), request.facts["판단근거"])
        assertEquals("06:09", request.facts["만조"])
        assertEquals(true, request.facts["자는동안만조"])
        assertEquals(listOf("화장실"), request.facts["시설"])
        assertEquals(
            "태안 꽃지해수욕장의 오늘 밤 안내예요. 날씨는 최저 13도이고, 비 예보가 있어요. " +
                "물때는 06:09 만조로, 자는 동안 물이 가장 높아져요. " +
                "이용 정보는 등록된 차박 장소지만 오늘 밤 이용 여부는 현장에서 확인해 주세요. " +
                "주의가 필요한 정보가 있어요. 다른 장소로 이동하는 것을 권장해요.",
            request.template,
        )
    }

    @Test
    fun `해변이 아니면 물때 대신 일출을 말하고 강한 바람은 풍속을 말한다`() {
        val forest = Site("camp-1", "숲속 야영장", PlaceType.FOREST, CampingPermission.UNKNOWN, emptyList())
        val conditions = SiteConditions(NightWeather(5f, Precipitation.NONE, true, maxWindMs = 14f), null, sunrise = LocalTime.of(7, 2))
        val request = NarrationScripts.arrival(forest, conditions, ArrivalAssessment(ArrivalGrade.MOVE, listOf(ArrivalReason.StrongWind)))
        assertTrue(request.template.contains("바람이 초속 14미터로 강해요"))
        assertTrue(request.template.contains("내일 일출은 07:02이에요"))
        assertTrue(request.template.contains("자동차야영장이 아니라 차박 가능 여부를 현장에서 확인해 주세요"))
        assertFalse(request.facts.containsKey("만조"))
    }

    @Test
    fun `모르는 날씨는 사실에 넣지 않는다`() {
        val request = NarrationScripts.arrival(beach, SiteConditions(null, null), ArrivalAssessment(ArrivalGrade.CHECK, listOf(ArrivalReason.WeatherUnavailable)))
        assertFalse(request.facts.containsKey("야간최저기온C"))
        assertFalse(request.facts.containsKey("만조"))
    }

    @Test
    fun `잠들기 전 멘트`() {
        val settings = BedtimeSettings(targetTempC = 22.5f, wakeAt = LocalTime.of(7, 10), wakeBasis = WakeBasis.SUNRISE, theme = PlaceType.FOREST)
        val request = NarrationScripts.bedtime(settings, LocalTime.of(7, 10), 80)
        assertEquals("22.5", request.facts["설정온도C"])
        assertEquals("일출", request.facts["기상기준"])
        assertEquals("오늘 밤은 숲 테마로 쉬어요. 설정 온도는 22.5도이고, 해 뜨기 조금 전인 07:10에 깨워 드릴게요. 편히 쉬세요.", request.template)
    }

    @Test
    fun `예상 잔량이 하한 아래면 주의를 문장으로 넘기고 준비된 문장에도 넣는다`() {
        val settings = BedtimeSettings(targetTempC = 24f, wakeAt = LocalTime.of(7, 0), wakeBasis = WakeBasis.MANUAL, theme = PlaceType.FOREST)
        val request = NarrationScripts.bedtime(settings, null, 30, MorningForecast(12, 18, 3, belowLimit = true))
        assertEquals("12~18", request.facts["아침예상잔량퍼센트"])
        assertEquals("아침에 배터리가 20%보다 낮아질 수 있으니 설정 온도를 조금 낮추는 걸 권해요.", request.facts["주의"])
        assertTrue(request.template.contains("12에서 18퍼센트"))
        assertTrue(request.template.contains("설정 온도를 조금 낮추는 걸 권해요"))
    }

    @Test
    fun `기상 멘트는 경고를 먼저 둔다`() {
        val briefing = MorningBriefing(warning = "배터리가 20%예요.", lines = listOf("지금 바깥은 맑음, 8°C예요."), batteryUsedPercent = 30)
        assertEquals("배터리가 20%예요. 지금 바깥은 맑음, 8°C예요.", NarrationScripts.morning(briefing).template)
    }

    @Test
    fun `서버 요청 JSON 모양`() {
        val json = JSONObject(NarrationJson.request(NarrationMoment.BEDTIME, mapOf("테마" to "숲", "배터리퍼센트" to 80, "근거" to listOf("a", "b"))))
        assertEquals("BEDTIME", json.getString("moment"))
        assertEquals(80, json.getJSONObject("facts").getInt("배터리퍼센트"))
        assertEquals("b", json.getJSONObject("facts").getJSONArray("근거").getString(1))
        assertEquals("안녕하세요", NarrationJson.text("""{"text":" 안녕하세요 "}"""))
        assertEquals(null, NarrationJson.text("""{"error":"x"}"""))
    }
}
