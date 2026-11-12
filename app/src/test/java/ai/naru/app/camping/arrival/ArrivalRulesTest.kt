package ai.naru.app.camping.arrival

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ArrivalRulesTest {

    private fun site(
        type: PlaceType = PlaceType.FOREST,
        permission: CampingPermission = CampingPermission.ALLOWED,
    ) = Site(id = "t", name = "테스트", placeType = type, permission = permission, facilities = emptyList())

    private fun weather(
        minTempC: Float = 10f,
        precipitation: Precipitation = Precipitation.NONE,
        strongWind: Boolean = false,
    ) = NightWeather(minTempC, precipitation, strongWind)

    private fun conditions(weather: NightWeather? = weather(), highTides: List<LocalTime>? = emptyList()) =
        SiteConditions(weather, highTides)

    // --- 정보 확인 ---

    @Test
    fun `허용된 장소에 날씨 문제가 없으면 정보 확인이다`() {
        val result = ArrivalRules.assess(site(), conditions())
        assertEquals(ArrivalGrade.INFO, result.grade)
        assertTrue(result.reasons.isEmpty())
    }

    // --- 차박 허용 여부 ---

    @Test
    fun `차박 금지 장소는 주의·이동 권장이다`() {
        val result = ArrivalRules.assess(site(permission = CampingPermission.PROHIBITED), conditions())
        assertEquals(ArrivalGrade.MOVE, result.grade)
        assertTrue(ArrivalReason.Prohibited in result.reasons)
    }

    @Test
    fun `허용 여부가 불명확하면 현장 확인 필요다`() {
        val result = ArrivalRules.assess(site(permission = CampingPermission.UNKNOWN), conditions())
        assertEquals(ArrivalGrade.CHECK, result.grade)
    }

    // --- 기온 경계값 ---

    @Test
    fun `최저기온 경계값`() {
        fun gradeAt(t: Float) = ArrivalRules.assess(site(), conditions(weather(minTempC = t))).grade
        assertEquals(ArrivalGrade.INFO, gradeAt(0.1f))
        assertEquals(ArrivalGrade.CHECK, gradeAt(0f))
        assertEquals(ArrivalGrade.CHECK, gradeAt(-9.9f))
        assertEquals(ArrivalGrade.MOVE, gradeAt(-10f))
    }

    @Test
    fun `극한 추위면 추위 이유는 중복으로 넣지 않는다`() {
        val reasons = ArrivalRules.assess(site(), conditions(weather(minTempC = -12f))).reasons
        assertEquals(listOf(ArrivalReason.ExtremeCold(-12f)), reasons)
    }

    // --- 강수·바람 ---

    @Test
    fun `비는 현장 확인, 호우와 강풍은 주의·이동 권장이다`() {
        assertEquals(ArrivalGrade.CHECK, ArrivalRules.assess(site(), conditions(weather(precipitation = Precipitation.RAIN))).grade)
        assertEquals(ArrivalGrade.MOVE, ArrivalRules.assess(site(), conditions(weather(precipitation = Precipitation.HEAVY_RAIN))).grade)
        assertEquals(ArrivalGrade.MOVE, ArrivalRules.assess(site(), conditions(weather(strongWind = true))).grade)
    }

    // --- 정보를 못 불러온 경우: 보수적으로 ---

    @Test
    fun `날씨를 못 불러오면 현장 확인 필요다`() {
        val result = ArrivalRules.assess(site(), conditions(weather = null))
        assertEquals(ArrivalGrade.CHECK, result.grade)
        assertTrue(ArrivalReason.WeatherUnavailable in result.reasons)
    }

    @Test
    fun `해변에서 물때를 못 불러오면 현장 확인 필요다`() {
        val result = ArrivalRules.assess(site(type = PlaceType.BEACH), conditions(highTides = null))
        assertEquals(ArrivalGrade.CHECK, result.grade)
        assertTrue(ArrivalReason.TideUnavailable in result.reasons)
    }

    @Test
    fun `해변이 아니면 물때를 확인하지 않는다`() {
        val result = ArrivalRules.assess(site(type = PlaceType.LAKE), conditions(highTides = null))
        assertEquals(ArrivalGrade.INFO, result.grade)
    }

    // --- 물때 ---

    @Test
    fun `해변에서 밤사이 만조가 있으면 주의·이동 권장이다`() {
        val result = ArrivalRules.assess(
            site(type = PlaceType.BEACH),
            conditions(highTides = listOf(LocalTime.of(2, 40), LocalTime.of(14, 55))),
        )
        assertEquals(ArrivalGrade.MOVE, result.grade)
        assertEquals(listOf(ArrivalReason.NightHighTide(LocalTime.of(2, 40))), result.reasons)
    }

    @Test
    fun `해변이라도 만조가 낮이면 정보 확인이다`() {
        val result = ArrivalRules.assess(site(type = PlaceType.BEACH), conditions(highTides = listOf(LocalTime.of(14, 55))))
        assertEquals(ArrivalGrade.INFO, result.grade)
    }

    @Test
    fun `자정을 넘는 밤 시간대와 경계 시각을 처리한다`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertTrue(ArrivalRules.isInNight(LocalTime.of(22, 0), start, end))
        assertTrue(ArrivalRules.isInNight(LocalTime.of(23, 59), start, end))
        assertTrue(ArrivalRules.isInNight(LocalTime.of(0, 0), start, end))
        assertTrue(ArrivalRules.isInNight(LocalTime.of(7, 0), start, end))
        assertFalse(ArrivalRules.isInNight(LocalTime.of(7, 1), start, end))
        assertFalse(ArrivalRules.isInNight(LocalTime.of(21, 59), start, end))
    }

    // --- 여러 이유가 겹칠 때 ---

    @Test
    fun `여러 이유가 겹치면 가장 보수적인 등급을 쓰고 이유는 모두 남긴다`() {
        val result = ArrivalRules.assess(
            site(permission = CampingPermission.UNKNOWN),
            conditions(weather(minTempC = -3f, strongWind = true)),
        )
        assertEquals(ArrivalGrade.MOVE, result.grade)
        assertEquals(3, result.reasons.size)
    }

    // --- 시연 데이터 ---

    @Test
    fun `시연 장소 3곳은 등급이 하나씩 나온다`() = runBlocking {
        val repo = DemoSiteRepository()
        val grades = repo.sites().associate { it.id to ArrivalRules.assess(it, repo.conditions(it)).grade }
        assertEquals(
            mapOf("demo-forest" to ArrivalGrade.INFO, "demo-lake" to ArrivalGrade.CHECK, "demo-beach" to ArrivalGrade.MOVE),
            grades,
        )
    }
}
