package ai.naru.app.camping.morning

import ai.naru.app.camping.arrival.MorningWeather
import ai.naru.app.camping.arrival.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class MorningPresenterTest {

    @Test
    fun `일출 문장`() {
        assertEquals("12분 뒤 07:02에 해가 떠요.", MorningPresenter.sunriseSentence(LocalTime.of(6, 50), LocalTime.of(7, 2)))
        assertEquals("07:02에 해가 떴어요.", MorningPresenter.sunriseSentence(LocalTime.of(7, 30), LocalTime.of(7, 2)))
        assertEquals("12분 뒤 07:02", MorningPresenter.sunriseCard(LocalTime.of(6, 50), LocalTime.of(7, 2)))
        assertNull(MorningPresenter.sunriseSentence(LocalTime.of(6, 50), null))
    }

    @Test
    fun `머문 시간`() {
        assertEquals("7시간 40분 머물렀어요", MorningPresenter.staySentence(LocalDateTime.of(2026, 11, 13, 23, 10), LocalDateTime.of(2026, 11, 14, 6, 50)))
    }

    @Test
    fun `배터리 사용량과 예상 범위 비교`() {
        assertEquals("밤새 11% 사용, 예상 58~63% 안", MorningPresenter.batteryDetail(61, 72, 58, 63))
        assertEquals("밤새 20% 사용, 예상(58~63%)보다 많이 썼어요", MorningPresenter.batteryDetail(52, 72, 58, 63))
        assertEquals("밤새 5% 사용", MorningPresenter.batteryDetail(67, 72, null, null))
        assertNull(MorningPresenter.batteryDetail(61, null, 58, 63))
    }

    @Test
    fun `실내 범위와 실외 줄`() {
        assertEquals("20~22°C", MorningPresenter.cabinRange(20f..22f))
        assertEquals("21°C", MorningPresenter.cabinRange(21f..21f))
        assertEquals("실외는 4°C까지 내려갔어요", MorningPresenter.outsideLine(4f, 6f))
        assertEquals("예보 최저는 6°C였어요", MorningPresenter.outsideLine(null, 6f))
    }

    @Test
    fun `아침 날씨`() {
        val w = MorningWeather(4f, Sky.CLEAR, dayMaxTempC = 13f, windMs = 2f)
        assertEquals("4°C 맑음", MorningPresenter.weatherValue(w))
        assertEquals("낮 최고 13°C, 바람 2m/s", MorningPresenter.weatherDetail(w))
        assertNull(MorningPresenter.weatherDetail(MorningWeather(4f, Sky.CLEAR)))
    }
}
