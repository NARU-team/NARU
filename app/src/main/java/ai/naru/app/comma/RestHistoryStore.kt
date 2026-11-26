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

    fun submitFeedback(
        sessionId: String,
        satisfaction: Int,
        temperatureFeedback: TemperatureFeedback,
        submittedAt: Long,
        onResult: (Boolean) -> Unit = {},
    ) {
        require(satisfaction in 1..5)
        applicationScope.launch {
            mutex.withLock {
                try {
                    val saved = repository.submitFeedback(
                        sessionId,
                        satisfaction,
                        temperatureFeedback,
                        submittedAt,
                    )
                    mutable.value = state.value.copy(
                        records = repository.recent(),
                        error = if (saved) null else "피드백이 이미 저장되었거나 기록을 찾지 못했습니다.",
                    )
                    onResult(saved)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = state.value.copy(error = "피드백 저장에 실패했습니다.")
                    onResult(false)
                }
            }
        }
    }

    fun updateRestoration(
        sessionId: String,
        report: RestorationReport,
        onResult: (Boolean) -> Unit = {},
    ) {
        applicationScope.launch {
            mutex.withLock {
                try {
                    val updated = repository.updateRestoration(sessionId, report)
                    mutable.value = state.value.copy(
                        records = repository.recent(),
                        error = if (updated) null else "복구 결과를 저장할 종료 기록을 찾지 못했습니다.",
                    )
                    onResult(updated)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = state.value.copy(error = "복구 결과 저장에 실패했습니다.")
                    onResult(false)
                }
            }
        }
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
