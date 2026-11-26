package ai.naru.app.comma

/** The single real-session completion path, including ViewModel disposal. */
class RestSessionLifecycle(
    private val manager: RestSessionManager,
    private val history: RestHistoryStore,
    private val decorateRecord: (RestRecord) -> RestRecord = { it },
) {
    fun tick() { manager.tick()?.let(decorateRecord)?.let(history::enqueue) }
    fun finish() { manager.finish()?.let(decorateRecord)?.let(history::enqueue) }
    fun finishForDriving() { manager.finish(EndReason.DRIVING)?.let(decorateRecord)?.let(history::enqueue) }
    fun close() { manager.finish(EndReason.VIEWMODEL_CLEARED)?.let(decorateRecord)?.let(history::enqueue) }
}
