package ai.naru.app.camping.bedtime

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.record.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FeedbackRulesTest {

    private fun record(day: Long, minTemp: Float?, target: Float, feedback: TempFeedback?) = SessionRecord(
        id = "r$day", nightStartedAt = LocalDateTime.of(2026, 11, 1, 22, 0).plusDays(day), nightEndedAt = LocalDateTime.of(2026, 11, 2, 7, 0).plusDays(day),
        siteName = "s", placeType = PlaceType.FOREST, grade = null, minTempC = minTemp, targetTempC = target, theme = PlaceType.FOREST,
        nightStartBattery = 80, nightEndBattery = 70, endReason = EndReason.WAKE_TIME, feedback = feedback,
    )

    @Test
    fun `비슷한 밤의 가장 최근 피드백을 쓴다`() {
        val records = listOf(record(0, 5f, 23f, TempFeedback.COLD), record(1, 7f, 22f, TempFeedback.HOT), record(2, 20f, 20f, TempFeedback.GOOD), record(3, 6f, 21f, null))
        assertEquals(FeedbackHint(22f, TempFeedback.HOT), FeedbackRules.hint(records, 6f))
    }

    @Test
    fun `비슷한 밤이 없거나 지금 최저기온을 모르면 반영하지 않는다`() {
        val records = listOf(record(0, 20f, 20f, TempFeedback.HOT))
        assertNull(FeedbackRules.hint(records, 6f))
        assertNull(FeedbackRules.hint(listOf(record(0, 6f, 22f, TempFeedback.HOT)), null))
    }

    @Test
    fun `더웠으면 1도 내리고 추웠으면 1도 올린다, 딱 좋았으면 그대로`() {
        assertEquals(21f, FeedbackRules.adjusted(FeedbackHint(22f, TempFeedback.HOT)))
        assertEquals(23f, FeedbackRules.adjusted(FeedbackHint(22f, TempFeedback.COLD)))
        assertEquals(22f, FeedbackRules.adjusted(FeedbackHint(22f, TempFeedback.GOOD)))
    }

    @Test
    fun `추천 온도에 피드백을 반영하고 차량 범위·배터리 규칙은 그대로 적용한다`() {
        val r = BedtimeRules.recommendTemperature(6f, 80, 15f..30f, FeedbackHint(22f, TempFeedback.HOT))
        assertEquals(21f, r.celsius)
        assertTrue(RecommendationNote.FEEDBACK_APPLIED in r.notes)
        // 배터리가 적으면 피드백 반영 뒤 1도 더 낮춘다
        assertEquals(20f, BedtimeRules.recommendTemperature(6f, 25, 15f..30f, FeedbackHint(22f, TempFeedback.HOT)).celsius)
        // 차량 범위 밖이면 자른다
        assertEquals(30f, BedtimeRules.recommendTemperature(6f, 80, 15f..30f, FeedbackHint(30f, TempFeedback.COLD)).celsius)
    }

    @Test
    fun `칩과 제안 문장`() {
        assertEquals("지난 차박 22°C는 더웠음", FeedbackRules.chipText(FeedbackHint(22f, TempFeedback.HOT)))
        val hint = FeedbackHint(22f, TempFeedback.HOT)
        val rec = BedtimeRules.recommendTemperature(6f, 80, 15f..30f, hint)
        assertEquals("지난 비슷한 밤에 22°C가 더웠다고 하셔서 21°C를 추천해요.", BedtimeTexts.suggestion(6f, rec, null, hint))
    }
}
