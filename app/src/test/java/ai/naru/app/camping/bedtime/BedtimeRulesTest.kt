package ai.naru.app.camping.bedtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeRulesTest {

    private val range = 15f..30f

    private fun recommend(minTempC: Float?, battery: Int? = 80, vehicleRange: ClosedFloatingPointRange<Float>? = range) =
        BedtimeRules.recommendTemperature(minTempC, battery, vehicleRange)

    @Test
    fun `야간 최저기온 구간별 추천 온도`() {
        assertEquals(23f, recommend(-5f).celsius)
        assertEquals(23f, recommend(0f).celsius)
        assertEquals(22f, recommend(0.1f).celsius)
        assertEquals(22f, recommend(10f).celsius)
        assertEquals(21f, recommend(10.1f).celsius)
        assertEquals(21f, recommend(20f).celsius)
        assertEquals(20f, recommend(20.1f).celsius)
    }

    @Test
    fun `날씨를 모르면 기본값으로 추천하고 이유를 남긴다`() {
        val result = recommend(minTempC = null)
        assertEquals(22f, result.celsius)
        assertTrue(RecommendationNote.WEATHER_UNKNOWN in result.notes)
    }

    @Test
    fun `배터리가 30퍼센트 미만이면 난방 온도를 1도 낮춘다`() {
        val result = recommend(minTempC = 5f, battery = 29)
        assertEquals(21f, result.celsius)
        assertTrue(RecommendationNote.LOW_BATTERY_SAVING in result.notes)
        assertEquals(22f, recommend(minTempC = 5f, battery = 30).celsius)
    }

    @Test
    fun `냉방 상황에서는 배터리가 적어도 낮추지 않는다`() {
        val result = recommend(minTempC = 25f, battery = 10)
        assertEquals(20f, result.celsius)
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun `배터리 잔량을 모르면 조정하지 않는다`() {
        assertEquals(22f, recommend(minTempC = 5f, battery = null).celsius)
    }

    @Test
    fun `차량 범위를 벗어나면 범위 안으로 맞춘다`() {
        val result = recommend(minTempC = -5f, vehicleRange = 16f..22f)
        assertEquals(22f, result.celsius)
        assertTrue(RecommendationNote.CLAMPED_TO_VEHICLE_RANGE in result.notes)
    }

    @Test
    fun `차량이 온도 제어를 지원하지 않아도 추천은 한다`() {
        assertEquals(23f, recommend(minTempC = -5f, vehicleRange = null).celsius)
    }

    @Test
    fun `온도 조절은 0점5도 단위이고 범위를 넘지 않는다`() {
        assertEquals(22.5f, BedtimeRules.adjustTemperature(22f, +1, range))
        assertEquals(21.5f, BedtimeRules.adjustTemperature(22f, -1, range))
        assertEquals(30f, BedtimeRules.adjustTemperature(30f, +1, range))
        assertEquals(15f, BedtimeRules.adjustTemperature(15f, -1, range))
    }
}
