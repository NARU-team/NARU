package ai.naru.app.comma

enum class RestorationOutcome { SUCCESS, FAILURE, UNSUPPORTED, UNKNOWN, MANUAL_CHECK }

/**
 * 항목별 복구 근거. SUCCESS는 요청 콜백만이 아니라 가능한 경우 재조회까지 일치한 상태다.
 */
data class RestorationReport(
    val appSettings: RestorationOutcome = RestorationOutcome.UNKNOWN,
    val windowAndAudio: RestorationOutcome = RestorationOutcome.UNKNOWN,
    val vehicle: RestorationOutcome = RestorationOutcome.UNSUPPORTED,
    val hvacPower: RestorationOutcome = vehicle,
    val climateTemperature: RestorationOutcome = vehicle,
    val ambientLight: RestorationOutcome = vehicle,
    val screenBrightness: RestorationOutcome = appSettings,
    val natureSound: RestorationOutcome = windowAndAudio,
    val alarm: RestorationOutcome = windowAndAudio,
) {
    val outcomes: List<RestorationOutcome>
        get() = listOf(hvacPower, climateTemperature, ambientLight, screenBrightness, natureSound, alarm)
}

internal fun restorationLabel(outcome: RestorationOutcome) = when(outcome) {
    RestorationOutcome.SUCCESS -> "복구 완료"
    RestorationOutcome.FAILURE -> "복구 실패"
    RestorationOutcome.UNSUPPORTED -> "미지원"
    RestorationOutcome.UNKNOWN -> "확인 불가"
    RestorationOutcome.MANUAL_CHECK -> "사용자 직접 확인 필요"
}

internal fun restoreAppSettings(cabin: CabinController, previous: CabinSettings?): RestorationReport {
    if(previous == null) return RestorationReport()
    val result = try {
        cabin.apply(previous)
        if(cabin.settings.value == previous) RestorationOutcome.SUCCESS else RestorationOutcome.FAILURE
    } catch (_: Exception) {
        RestorationOutcome.FAILURE
    }
    // Window/audio는 platform acknowledgement가 없으므로 여기서 성공으로 추정하지 않는다.
    return RestorationReport(
        appSettings = result,
        screenBrightness = result,
        natureSound = RestorationOutcome.UNKNOWN,
        alarm = RestorationOutcome.UNKNOWN,
    )
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
