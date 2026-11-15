package com.naru.app.comma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface RestHistoryRepository {
    suspend fun save(record: RestRecord)
    suspend fun recent(): List<RestRecord>
}
class RoomRestHistoryRepository(private val dao: RestDao) : RestHistoryRepository {
    override suspend fun save(record: RestRecord) = withContext(Dispatchers.IO) {
        dao.save(RestRecordEntity().apply {
            id = record.id
            startedAt = record.startedAt
            endedAt = record.endedAt
            plannedMinutes = record.plannedMinutes
            elapsedMillis = record.elapsedMillis
            reason = record.reason.name
        })
    }
    override suspend fun recent(): List<RestRecord> = withContext(Dispatchers.IO) {
        dao.recent().map { RestRecord(it.id, it.startedAt, it.endedAt, it.plannedMinutes,
            it.elapsedMillis, EndReason.valueOf(it.reason)) }
    }
}
