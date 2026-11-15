package ai.naru.app.camping.bedtime

import ai.naru.app.camping.arrival.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class BedtimeTextsTest {

    private fun settings(wakeAt: LocalTime, basis: WakeBasis) =
        BedtimeSettings(targetTempC = 22f, wakeAt = wakeAt, wakeBasis = basis, theme = PlaceType.FOREST)

    @Test
    fun `일출을 알고 아직 일출 기상이 아니면 일출 칩`() {
        assertEquals(listOf("더 따뜻하게", "배터리 아끼기", "일출에 깨워줘"),
            BedtimeTexts.suggestionChips(settings(LocalTime.of(7, 0), WakeBasis.MANUAL), LocalTime.of(7, 12)))
    }

    @Test
    fun `이미 7시면 7시 칩 대신 다른 시각`() {
        assertEquals("6시 반에 깨워줘", BedtimeTexts.suggestionChips(settings(LocalTime.of(7, 0), WakeBasis.MANUAL), null)[2])
        assertEquals("7시에 깨워줘", BedtimeTexts.suggestionChips(settings(LocalTime.of(6, 52), WakeBasis.SUNRISE), LocalTime.of(7, 12))[2])
    }
}
