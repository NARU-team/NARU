package ai.naru.app.comma

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestFeedbackStoreTest {
    @Test
    fun `같은 세션의 피드백은 한 번만 저장한다`() = runTest {
        val repository = FeedbackRepository()
        val store = RestHistoryStore(repository, this)
        var first = false
        var second = true

        store.submitFeedback("session", 5, TemperatureFeedback.COMFORTABLE, 100L) {
            first = it
        }
        runCurrent()
        store.submitFeedback("session", 3, TemperatureFeedback.TOO_WARM, 200L) {
            second = it
        }
        runCurrent()

        assertTrue(first)
        assertFalse(second)
        assertEquals(5, repository.record.feedback)
        assertEquals(TemperatureFeedback.COMFORTABLE, repository.record.temperatureFeedback)
    }

    private class FeedbackRepository : RestHistoryRepository {
        var record = RestRecord(
            id = "session",
            startedAt = 0,
            endedAt = 1,
            plannedMinutes = 15,
            elapsedMillis = 1,
            reason = EndReason.TIMER,
            source = RecordSource.REAL,
        )

        override suspend fun save(record: RestRecord) {
            this.record = record
        }

        override suspend fun recent(): List<RestRecord> = listOf(record)

        override suspend fun submitFeedback(
            sessionId: String,
            satisfaction: Int,
            temperatureFeedback: TemperatureFeedback,
            submittedAt: Long,
        ): Boolean {
            if (record.id != sessionId || record.feedbackSubmittedAt != null) return false
            record = record.copy(
                feedback = satisfaction,
                temperatureFeedback = temperatureFeedback,
                feedbackSubmittedAt = submittedAt,
            )
            return true
        }
    }
}
