package com.naru.app.comma

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestHistoryStoreTest {
    private class Repository : RestHistoryRepository {
        val saved = linkedMapOf<String, RestRecord>()
        var saveCalls = 0
        var failSave = false
        var failRead = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun save(record: RestRecord) {
            saveCalls++
            gate?.await()
            if (failSave) error("disk failure")
            saved[record.id] = record
        }
        override suspend fun recent(): List<RestRecord> {
            if (failRead) error("read failure")
            return saved.values.toList()
        }
    }
    private fun record(id: String) = RestRecord(id, 1000, 2000, 15, 1000, EndReason.MANUAL)
    private fun manager() = RestSessionManager(object : RestClock {
        override fun elapsedMillis() = 1000L
        override fun wallMillis() = 2000L
    }, AppCabinController())

    @Test fun cleanupSaveSurvivesCallerCancellationAndRemainsPendingUntilRoomAcknowledges() = runTest {
        val applicationScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val callerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository().apply { gate = CompletableDeferred() }
            val store = RestHistoryStore(repository, applicationScope)
            val manager = manager().also { it.start() }
            val lifecycle = RestSessionLifecycle(manager, store)
            callerScope.launch { lifecycle.close() }
            runCurrent()
            callerScope.cancel() // Models the cancelled viewModelScope at onCleared.
            assertEquals(1, store.state.value.pendingCount)
            assertTrue(repository.saved.isEmpty())
            repository.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(0, store.state.value.pendingCount)
            assertEquals(EndReason.VIEWMODEL_CLEARED, repository.saved.values.single().reason)
        } finally { callerScope.cancel(); applicationScope.cancel() }
    }
    @Test fun repeatedCleanupWritesOnlyOneRecord() = runTest {
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository()
            val store = RestHistoryStore(repository, app)
            val lifecycle = RestSessionLifecycle(manager().also { it.start() }, store)
            lifecycle.close()
            lifecycle.close()
            advanceUntilIdle()
            assertEquals(1, repository.saveCalls)
            assertEquals(1, store.state.value.records.size)
        } finally { app.cancel() }
    }
    @Test fun manualFinishThenCleanupDoesNotDuplicateOrChangeReason() = runTest {
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository()
            val store = RestHistoryStore(repository, app)
            val lifecycle = RestSessionLifecycle(manager().also { it.start() }, store)
            lifecycle.finish()
            lifecycle.close()
            advanceUntilIdle()
            assertEquals(1, repository.saveCalls)
            assertEquals(EndReason.MANUAL, repository.saved.values.single().reason)
        } finally { app.cancel() }
    }
    @Test fun failedSaveStaysPendingAndRetryClearsError() = runTest {
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository().apply { failSave = true }
            val store = RestHistoryStore(repository, app)
            store.enqueue(record("one"))
            advanceUntilIdle()
            assertEquals(1, store.state.value.pendingCount)
            assertNotNull(store.state.value.error)
            repository.failSave = false
            store.retry()
            advanceUntilIdle()
            assertEquals(0, store.state.value.pendingCount)
            assertNull(store.state.value.error)
            assertEquals(listOf("one"), store.state.value.records.map { it.id })
        } finally { app.cancel() }
    }
    @Test fun readFailureAfterSaveDoesNotResubmitAcknowledgedRecord() = runTest {
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository().apply { failRead = true }
            val store = RestHistoryStore(repository, app)
            store.enqueue(record("one"))
            advanceUntilIdle()
            assertEquals(0, store.state.value.pendingCount)
            assertNotNull(store.state.value.error)
            repository.failRead = false
            store.retry()
            advanceUntilIdle()
            assertEquals(1, repository.saveCalls)
            assertEquals(1, store.state.value.records.size)
        } finally { app.cancel() }
    }
    @Test fun queuedRecordsRemainAvailableToNewObserversAndSaveInOrder() = runTest {
        val app = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository()
            val store = RestHistoryStore(repository, app)
            store.enqueue(record("one"))
            store.enqueue(record("two"))
            assertEquals(2, store.state.value.pendingCount)
            advanceUntilIdle()
            val newObserverState = store.state.value
            assertEquals(listOf("one", "two"), newObserverState.records.map { it.id })
            assertEquals(2, repository.saveCalls)
        } finally { app.cancel() }
    }
}
