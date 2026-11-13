package ai.naru.app.camping.record

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.ArrivalGrade
import ai.naru.app.camping.arrival.PlaceType
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/** 차박 기록 저장소. 실패해도 세션은 계속하므로 예외를 밖으로 던지지 않는다. */
interface SessionRecordStore {
    /** 오래된 순으로 돌려준다. 읽지 못하면 빈 목록. */
    suspend fun load(): List<SessionRecord>

    /** 기록을 하나 더한다. 저장 후 전체 목록을 돌려준다. */
    suspend fun append(record: SessionRecord): List<SessionRecord>

    suspend fun clear()
}

/**
 * 앱 내부 저장소의 JSON 파일 하나 (이슈 #25: 기록이 수십 건이라 Room 대신 파일).
 * 쓸 때는 임시 파일에 쓰고 이름을 바꿔서, 중간에 앱이 꺼져도 기존 파일이 깨지지 않게 한다.
 */
class FileSessionRecordStore(
    private val file: File,
    /** 실패 기록용. 단위 테스트(JVM)에는 android.util.Log가 없어 바꿔 끼운다. */
    private val warn: (String, Throwable) -> Unit = { message, e -> Log.w(TAG, message, e) },
) : SessionRecordStore {

    private val mutex = Mutex()

    override suspend fun load(): List<SessionRecord> = mutex.withLock { read() }

    override suspend fun append(record: SessionRecord): List<SessionRecord> = mutex.withLock {
        val records = (read() + record).takeLast(MAX_RECORDS)
        write(records)
        records
    }

    override suspend fun clear() {
        mutex.withLock { write(emptyList()) }
    }

    private suspend fun read(): List<SessionRecord> = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) SessionRecordJson.decode(file.readText()) else emptyList()
        } catch (e: Exception) {
            warn("기록 파일을 읽지 못함", e)
            emptyList()
        }
    }

    private suspend fun write(records: List<SessionRecord>): Unit = withContext(Dispatchers.IO) {
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(SessionRecordJson.encode(records))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            warn("기록을 저장하지 못함", e)
        }
    }

    private companion object {
        const val TAG = "SessionRecordStore"

        /** 오래된 기록부터 지운다 */
        const val MAX_RECORDS = 100
    }
}

/** 테스트·미리보기용 */
class InMemorySessionRecordStore(initial: List<SessionRecord> = emptyList()) : SessionRecordStore {
    private val records = initial.toMutableList()
    override suspend fun load(): List<SessionRecord> = records.toList()
    override suspend fun append(record: SessionRecord): List<SessionRecord> {
        records += record
        return records.toList()
    }
    override suspend fun clear() {
        records.clear()
    }
}

/** 기록 JSON. 순수 함수라 단위 테스트한다. 모르는 항목·깨진 항목은 건너뛴다. */
object SessionRecordJson {

    fun encode(records: List<SessionRecord>): String = JSONArray(records.map { it.toJson() }).toString()

    fun decode(json: String): List<SessionRecord> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            try {
                array.getJSONObject(i).toRecord()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun SessionRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("nightStartedAt", nightStartedAt.toString())
        put("nightEndedAt", nightEndedAt.toString())
        put("siteName", siteName)
        put("placeType", placeType.name)
        putOpt("grade", grade?.name)
        putOpt("minTempC", minTempC?.toDouble())
        put("targetTempC", targetTempC.toDouble())
        put("theme", theme.name)
        putOpt("nightStartBattery", nightStartBattery)
        putOpt("nightEndBattery", nightEndBattery)
        put("endReason", endReason.name)
        putOpt("predictedLowPercent", predictedLowPercent)
        putOpt("predictedHighPercent", predictedHighPercent)
    }

    private fun JSONObject.toRecord() = SessionRecord(
        id = getString("id"),
        nightStartedAt = LocalDateTime.parse(getString("nightStartedAt")),
        nightEndedAt = LocalDateTime.parse(getString("nightEndedAt")),
        siteName = getString("siteName"),
        placeType = PlaceType.valueOf(getString("placeType")),
        grade = optStringOrNull("grade")?.let(ArrivalGrade::valueOf),
        minTempC = optDoubleOrNull("minTempC")?.toFloat(),
        targetTempC = getDouble("targetTempC").toFloat(),
        theme = PlaceType.valueOf(getString("theme")),
        nightStartBattery = optIntOrNull("nightStartBattery"),
        nightEndBattery = optIntOrNull("nightEndBattery"),
        endReason = EndReason.valueOf(getString("endReason")),
        predictedLowPercent = optIntOrNull("predictedLowPercent"),
        predictedHighPercent = optIntOrNull("predictedHighPercent"),
    )

    private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.optDoubleOrNull(key: String) = if (isNull(key)) null else getDouble(key)
    private fun JSONObject.optIntOrNull(key: String) = if (isNull(key)) null else getInt(key)
}
