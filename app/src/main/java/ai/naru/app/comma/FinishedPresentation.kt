package ai.naru.app.comma

enum class RestorationOutcome { SUCCESS, FAILURE, UNSUPPORTED, UNKNOWN }

/** Session-local evidence, not a vehicle SDK acknowledgement or a persisted Room field. */
data class RestorationReport(
    val appSettings: RestorationOutcome = RestorationOutcome.UNKNOWN,
    val windowAndAudio: RestorationOutcome = RestorationOutcome.UNKNOWN,
    val vehicle: RestorationOutcome = RestorationOutcome.UNSUPPORTED
)

internal fun restorationLabel(outcome: RestorationOutcome) = when(outcome) {
    RestorationOutcome.SUCCESS -> "성공"
    RestorationOutcome.FAILURE -> "실패"
    RestorationOutcome.UNSUPPORTED -> "미지원"
    RestorationOutcome.UNKNOWN -> "확인 불가"
}

internal fun restoreAppSettings(cabin: CabinController, previous: CabinSettings?): RestorationReport {
    if(previous == null) return RestorationReport()
    val result = try {
        cabin.apply(previous)
        if(cabin.settings.value == previous) RestorationOutcome.SUCCESS else RestorationOutcome.FAILURE
    } catch (_: Exception) {
        RestorationOutcome.FAILURE
    }
    // Platform effects have no acknowledgement channel; never infer hardware success.
    return RestorationReport(appSettings=result)
}

internal fun finishedSaveStatus(ui: RestUiState): String {
    if(ui.isDemo) return "데모 기록 · 실제 Room 저장 제외"
    val record=ui.session.completed ?: return "완료 기록 확인 불가"
    return when {
        ui.history.records.any {it.id==record.id} -> "휴식 기록 저장 확인"
        ui.history.error != null -> "기록 저장/조회 확인 필요 · 다시 시도하세요"
        ui.history.pendingCount>0 -> "휴식 기록 저장 중 · 미저장 ${ui.history.pendingCount}개"
        else -> "기록 저장 여부 확인 중"
    }
}
