package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.PlaceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

class SessionRecordJsonTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val record = SessionRecord(
        id = "a",
        nightStartedAt = LocalDateTime.of(2026, 11, 13, 22, 30),
        nightEndedAt = LocalDateTime.of(2026, 11, 14, 7, 0),
        siteName = "태안 꽃지해수욕장 (시연)",
        placeType = PlaceType.BEACH,
        grade = ArrivalGrade.MOVE,
        minTempC = 13f,
        targetTempC = 21.5f,
        theme = PlaceType.BEACH,
        nightStartBattery = 80,
        nightEndBattery = 70,
        endReason = EndReason.WAKE_TIME,
        predictedLowPercent = 66,
        predictedHighPercent = 72,
        feedback = ai.naru.app.camping.bedtime.TempFeedback.HOT,
    )

    @Test
    fun `저장하고 다시 읽으면 같은 기록`() {
        val unknown = record.copy(id = "b", grade = null, minTempC = null, nightEndBattery = null, predictedLowPercent = null, predictedHighPercent = null, feedback = null)
        assertEquals(listOf(record, unknown), SessionRecordJson.decode(SessionRecordJson.encode(listOf(record, unknown))))
    }

    @Test
    fun `깨진 항목은 건너뛴다`() {
        val json = """[{"id":"x"}, ${SessionRecordJson.encode(listOf(record)).removePrefix("[").removeSuffix("]")}]"""
        assertEquals(listOf(record), SessionRecordJson.decode(json))
    }

    @Test
    fun `파일 저장소는 더하고 지우고 다시 읽는다`() = runBlocking {
        val file = File(folder.root, "records.json")
        val store = FileSessionRecordStore(file)
        assertEquals(emptyList<SessionRecord>(), store.load())
        store.append(record)
        assertEquals(listOf(record), FileSessionRecordStore(file).load())
        store.clear()
        assertEquals(emptyList<SessionRecord>(), store.load())
    }

    @Test
    fun `파일이 깨져 있으면 빈 목록`() = runBlocking {
        val file = File(folder.root, "broken.json").apply { writeText("{not json") }
        assertEquals(emptyList<SessionRecord>(), FileSessionRecordStore(file, warn = { _, _ -> }).load())
    }
}
