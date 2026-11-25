package ai.naru.app.comma

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RestHistoryState(
    val records: List<RestRecord> = emptyList(),
    val pendingCount: Int = 0,
    val error: String? = null
)

/**
 * Main-thread confined submission; scope belongs to the APPLICATION, never a ViewModel.
 * The pending map is updated before launching, so cancellation of a caller cannot lose a submission.
 * Serial saves and idempotent Room upserts allow retry after failures without duplicate rows.
 * This is a process-lifetime queue, not durable storage until save() succeeds.
 */
class RestHistoryStore(
    private val repository: RestHistoryRepository,
    private val applicationScope: CoroutineScope
) {
    private val mutable = MutableStateFlow(RestHistoryState())
    val state: StateFlow<RestHistoryState> = mutable
    private val pending = linkedMapOf<String, RestRecord>()
    private val mutex = Mutex()

    fun enqueue(record: RestRecord) {
        pending[record.id] = record
        mutable.value = state.value.copy(pendingCount = pending.size)
        retry()
    }

    fun retry() {
        applicationScope.launch {
            mutex.withLock {
                try {
                    for (record in pending.values.toList()) {
                        repository.save(record)
                        pending.remove(record.id)
                        mutable.value = state.value.copy(pendingCount = pending.size)
                    }
                    mutable.value = state.value.copy(records = repository.recent(), error = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = state.value.copy(error =
                        if (pending.isEmpty()) "기록 조회 실패. 다시 저장/조회를 눌러 주세요."
                        else "휴식 기록 저장 실패. 미저장 기록을 다시 저장해 주세요.")
                }
            }
        }
    }
}
