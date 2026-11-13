package ai.naru.app.camping

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.ArrivalRules
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.arrival.SiteRepository
import ai.naru.app.camping.bedtime.BedtimeRules
import ai.naru.app.camping.bedtime.BedtimeSettings
import ai.naru.app.camping.bedtime.NightPreparer
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.TemperatureRecommendation
import ai.naru.app.camping.bedtime.VehicleBackup
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.morning.MorningAlarm
import ai.naru.app.camping.morning.MorningBriefing
import ai.naru.app.camping.morning.MorningLightApplier
import ai.naru.app.camping.morning.MorningRules
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.camping.morning.SettingsRestorer
import ai.naru.app.camping.night.NightRules
import ai.naru.app.camping.night.NightSound
import ai.naru.app.camping.night.ThemeLightApplier
import java.time.LocalDateTime
import java.time.LocalTime
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 차박 화면이 그릴 내용 전부. 화면은 이 값만 보고 그린다. */
data class CampingUiState(
    val stage: CampingStage = CampingStage.IDLE,
    val gear: Gear = Gear.UNKNOWN,
    val batteryPercent: Int? = null,
    /** 마지막으로 아침으로 넘어가거나 세션이 끝난 이유 */
    val lastEndReason: EndReason? = null,
    /** 사용자에게 보여줄 안내 (예: P가 아니라 시작 불가) */
    val message: String? = null,
    /** 도착 단계: 고를 수 있는 장소 */
    val sites: List<Site> = emptyList(),
    /** 도착 단계: 장소 목록을 불러오지 못함 (서버 연결 실패) */
    val sitesUnavailable: Boolean = false,
    /** 도착 단계: 고른 장소와 그 판정 결과 */
    val arrival: ArrivalState? = null,
    /** 취침 준비 단계: 오늘 밤 설정 */
    val bedtime: BedtimeState? = null,
    /** 세션 시작 전 원래 차량 설정. 기상 단계에서 복구에 쓴다. */
    val backup: VehicleBackup? = null,
    /** 설정 온도 적용 결과. 밤 화면에서 안내한다. */
    val temperatureApply: ControlResult? = null,
    /** 밤 단계: 테마 조명 적용 결과 */
    val lightApply: ControlResult? = null,
    /** 밤 단계: 사용자가 테마 소리를 켜 두었는지 */
    val soundOn: Boolean = true,
    /** 밤 단계: 실제로 소리가 나고 있는지 (오디오 포커스를 못 받으면 false) */
    val soundPlaying: Boolean = false,
    /** 밤 단계: 아침으로 넘어갈 일시 */
    val wakeDateTime: LocalDateTime? = null,
    /** 밤을 시작할 때의 배터리 잔량. 아침 브리핑에서 밤사이 사용량을 구한다. */
    val nightStartBattery: Int? = null,
    /** 기상 단계 상태. 새 세션을 시작하면 지운다. */
    val morning: MorningState? = null,
    /** 차량 설정 복구 중인지 (아침 "일어났어요" 또는 기어 이탈) */
    val restoring: Boolean = false,
    /** 차량 설정 복구 결과. 세션이 끝난 뒤에도 남겨 두고(대기 화면 안내), 새 세션을 시작하면 지운다. */
    val restore: RestoreReport? = null,
)

data class MorningState(
    /** 알람이 울리는 중인지 */
    val alarmRinging: Boolean = false,
    /** 알람을 울리지 못했는지 (포커스·알람음 없음). 화면 안내용 */
    val alarmUnavailable: Boolean = false,
    /** 아침 조명 결과. 적용 전이면 null */
    val lightApply: ControlResult? = null,
    /** 아침 브리핑. 만들기 전이면 null */
    val briefing: MorningBriefing? = null,
)

data class BedtimeState(
    val settings: BedtimeSettings,
    val recommendation: TemperatureRecommendation,
    val sunrise: LocalTime?,
    val vehicleTempRange: ClosedFloatingPointRange<Float>?,
    /** 차박 시작하기를 누른 뒤 차량 준비 중 */
    val preparing: Boolean = false,
)

data class ArrivalState(
    val site: Site,
    val conditions: SiteConditions,
    val assessment: ArrivalAssessment,
)

/**
 * 차박 세션을 진행하는 ViewModel.
 *
 * 차량 게이트웨이에서 기어·배터리를 구독하고, 사용자 동작과 차량 변화를 사건으로 바꿔
 * [CampingSessionRules]에 넘긴다. 단계 판단은 규칙이 하고, 여기서는 결과를 상태에 반영만 한다.
 */
class CampingViewModel(
    private val vehicle: VehicleGateway,
    private val siteRepository: SiteRepository,
    private val sound: NightSound,
    private val alarm: MorningAlarm,
) : ViewModel() {

    private val nightPreparer = NightPreparer(vehicle)
    private val themeLightApplier = ThemeLightApplier(vehicle)
    private val morningLightApplier = MorningLightApplier(vehicle)
    private val settingsRestorer = SettingsRestorer(vehicle)

    /** 밤 단계 동안만 도는 기상 시각 확인. 밤을 벗어나거나 복구를 시작하면 취소한다. */
    private var nightJob: Job? = null

    /** 밤 테마 조명 적용. 복구는 이 작업이 끝난 뒤에 한다 (복구 후 테마 조명이 덮어쓰지 않게). */
    private var themeLightJob: Job? = null

    /** 기상 단계 준비 작업 (조명, 브리핑). 복구는 이 작업이 끝난 뒤에 한다. */
    private var morningJob: Job? = null

    /** 설정 복구 작업. 한 번만 돈다. */
    private var restoreJob: Job? = null

    /** 복구가 끝나면 이 이유로 세션을 끝낸다 (아침에 기어가 P를 벗어난 경우). */
    private var pendingEndReason: EndReason? = null

    private val _state = MutableStateFlow(CampingUiState())
    val state: StateFlow<CampingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vehicle.gear.collect { gear ->
                _state.update { it.copy(gear = gear) }
                dispatch(CampingEvent.GearChanged(gear))
            }
        }
        viewModelScope.launch {
            vehicle.batteryPercent.collect { percent ->
                _state.update { it.copy(batteryPercent = percent) }
                checkBatteryLimit()
            }
        }
        loadSites()
    }

    /** 장소 목록을 불러온다. 실패하면 화면에 다시 시도를 보여준다. */
    fun loadSites() {
        viewModelScope.launch {
            val sites = try {
                siteRepository.sites()
            } catch (e: Exception) {
                Log.w(TAG, "장소 목록을 불러오지 못함", e)
                null
            }
            _state.update { it.copy(sites = sites ?: it.sites, sitesUnavailable = sites == null) }
        }
    }

    // --- 사용자 동작 ---

    fun start() = dispatch(CampingEvent.Start(_state.value.gear))

    /** 도착 단계에서 장소를 고르면 오늘 밤 정보를 불러와 규칙으로 판정한다. */
    fun selectSite(site: Site) {
        viewModelScope.launch {
            val conditions = siteRepository.conditions(site)
            val assessment = ArrivalRules.assess(site, conditions)
            _state.update { it.copy(arrival = ArrivalState(site, conditions, assessment)) }
        }
    }

    /** 판정 결과를 확인한 뒤에만 다음 단계로 넘어간다. 넘어가면 오늘 밤 설정의 추천값을 만든다. */
    fun confirmArrival() {
        val arrival = _state.value.arrival ?: return
        dispatch(CampingEvent.ConfirmArrival)
        if (_state.value.stage != CampingStage.BEDTIME_PREP) return
        viewModelScope.launch {
            val range = vehicle.capabilities().targetTemperatureRange
            val recommendation = BedtimeRules.recommendTemperature(
                minTempC = arrival.conditions.weather?.minTempC,
                batteryPercent = _state.value.batteryPercent,
                vehicleRange = range,
            )
            val settings = BedtimeSettings(
                targetTempC = recommendation.celsius,
                wakeAt = DEFAULT_WAKE_AT,
                wakeBasis = WakeBasis.MANUAL,
                theme = arrival.site.placeType,
            )
            _state.update {
                it.copy(bedtime = BedtimeState(settings, recommendation, arrival.conditions.sunrise, range))
            }
        }
    }

    // --- 취침 준비 단계 ---

    fun adjustTemperature(steps: Int) = updateBedtime { bedtime ->
        val next = BedtimeRules.adjustTemperature(bedtime.settings.targetTempC, steps, bedtime.vehicleTempRange)
        bedtime.copy(settings = bedtime.settings.copy(targetTempC = next))
    }

    fun adjustWakeTime(minutes: Long) = updateBedtime { bedtime ->
        val next = bedtime.settings.wakeAt.plusMinutes(minutes)
        bedtime.copy(settings = bedtime.settings.copy(wakeAt = next, wakeBasis = WakeBasis.MANUAL))
    }

    fun wakeAtSunrise() = updateBedtime { bedtime ->
        val sunrise = bedtime.sunrise ?: return@updateBedtime bedtime
        bedtime.copy(settings = bedtime.settings.copy(wakeAt = sunrise, wakeBasis = WakeBasis.SUNRISE))
    }

    fun selectTheme(theme: PlaceType) = updateBedtime { bedtime ->
        bedtime.copy(settings = bedtime.settings.copy(theme = theme))
    }

    /**
     * 사용자 승인. 원래 설정을 백업하고 설정 온도를 적용한 뒤 밤 단계로 넘어간다.
     * 제어가 실패해도 세션은 계속한다 (화면·음향·알람 중심으로 제공).
     */
    fun startNight() {
        val bedtime = _state.value.bedtime ?: return
        if (bedtime.preparing) return
        _state.update { it.copy(bedtime = bedtime.copy(preparing = true)) }
        viewModelScope.launch {
            val preparation = nightPreparer.prepare(bedtime.settings)
            if (_state.value.stage != CampingStage.BEDTIME_PREP) {
                // 차량을 준비하는 사이 세션이 끝났다 (기어 이탈). 이미 바꾼 설정을 되돌린다.
                val report = settingsRestorer.restore(preparation.backup)
                _state.update { it.copy(restore = report, bedtime = it.bedtime?.copy(preparing = false)) }
                return@launch
            }
            _state.update {
                it.copy(
                    backup = preparation.backup,
                    temperatureApply = preparation.temperature,
                    nightStartBattery = it.batteryPercent,
                    bedtime = it.bedtime?.copy(preparing = false),
                )
            }
            dispatch(CampingEvent.StartNight)
        }
    }

    private fun updateBedtime(transform: (BedtimeState) -> BedtimeState) {
        _state.update { state -> state.bedtime?.let { state.copy(bedtime = transform(it)) } ?: state }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // --- 개발용: 알람과 배터리 감시가 생기기 전까지 사건을 손으로 발생시킨다 ---

    fun simulateWakeTime() = dispatch(CampingEvent.WakeTimeReached)

    // --- 밤 단계 ---

    /** 테마 소리 켜기/끄기 */
    fun toggleSound() {
        val turnOn = !_state.value.soundOn
        _state.update { it.copy(soundOn = turnOn) }
        if (_state.value.stage != CampingStage.NIGHT) return
        if (turnOn) playThemeSound() else stopThemeSound()
    }

    private fun onEnterNight() {
        val settings = _state.value.bedtime?.settings ?: return
        val wakeDateTime = NightRules.nextWakeDateTime(LocalDateTime.now(), settings.wakeAt)
        _state.update { it.copy(wakeDateTime = wakeDateTime) }
        if (_state.value.soundOn) playThemeSound()
        checkBatteryLimit()

        themeLightJob = viewModelScope.launch {
            val lightResult = themeLightApplier.apply(settings.theme)
            _state.update { it.copy(lightApply = lightResult) }
        }

        nightJob = viewModelScope.launch {
            // 앱이 켜져 있는 동안 기상 시각을 확인한다. TODO(미확정): Suspend 상태에서의 알람
            while (isActive) {
                if (NightRules.isWakeTimeReached(LocalDateTime.now(), wakeDateTime)) {
                    dispatch(CampingEvent.WakeTimeReached)
                    break
                }
                delay(WAKE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun onLeaveNight() {
        nightJob?.cancel()
        nightJob = null
        themeLightJob = null
        stopThemeSound()
    }

    /** 밤 단계에서 배터리가 하한에 닿으면 경고와 함께 아침으로 넘긴다. */
    private fun checkBatteryLimit() {
        val state = _state.value
        if (state.stage == CampingStage.NIGHT && NightRules.isBatteryAtLimit(state.batteryPercent)) {
            dispatch(CampingEvent.BatteryLow)
        }
    }

    private fun playThemeSound() {
        val theme = _state.value.bedtime?.settings?.theme ?: return
        val playing = sound.play(theme)
        _state.update { it.copy(soundPlaying = playing) }
    }

    private fun stopThemeSound() {
        sound.stop()
        _state.update { it.copy(soundPlaying = false) }
    }

    // --- 기상 단계 ---

    /** "일어났어요": 알람을 끄고 원래 차량 설정을 되돌린다. */
    fun wakeUp() {
        if (_state.value.stage != CampingStage.MORNING) return
        stopAlarm()
        startRestore()
    }

    /** "차박 마치기": 복구가 끝난 뒤에만 세션을 정상 종료한다. */
    fun finishSession() {
        if (_state.value.restore == null) return
        dispatch(CampingEvent.Restored())
    }

    private fun onEnterMorning() {
        val playing = alarm.start()
        _state.update { it.copy(morning = MorningState(alarmRinging = playing, alarmUnavailable = !playing)) }

        morningJob = viewModelScope.launch {
            val light = morningLightApplier.apply()
            updateMorning { it.copy(lightApply = light) }

            val state = _state.value
            val cabin = (vehicle.getCabinTemperature() as? VehicleResult.Success)?.value
            val briefing = MorningRules.briefing(
                reason = state.lastEndReason,
                weather = state.arrival?.conditions?.morningWeather,
                batteryAtNightStart = state.nightStartBattery,
                batteryNow = state.batteryPercent,
                cabinTempC = cabin,
            )
            updateMorning { it.copy(briefing = briefing) }
        }
    }

    private fun onLeaveMorning() {
        morningJob?.cancel()
        morningJob = null
        stopAlarm()
    }

    /**
     * 밤·아침에 기어가 P를 벗어났다. 소리·알람과 기상 시각 확인을 멈추고, 복구를 먼저 마친 뒤 [reason]으로 끝낸다.
     * 복구가 끝날 때까지 다른 사건(배터리 하한, 기상 시각)은 받지 않는다 ([dispatch] 참고).
     */
    private fun endAfterRestore(reason: EndReason) {
        stopAlarm()
        stopThemeSound()
        nightJob?.cancel()
        nightJob = null
        if (_state.value.restore != null) {
            dispatch(CampingEvent.Restored(reason))
            return
        }
        pendingEndReason = reason
        startRestore()
    }

    private fun startRestore() {
        if (restoreJob != null || _state.value.restore != null) return
        _state.update { it.copy(restoring = true) }
        restoreJob = viewModelScope.launch {
            // 테마·아침 조명을 켜는 중이면 끝난 뒤에 되돌린다. 순서가 바뀌면 그 조명이 남는다.
            themeLightJob?.join()
            morningJob?.join()
            val report = settingsRestorer.restore(_state.value.backup)
            _state.update { it.copy(restoring = false, restore = report) }
            restoreJob = null
            pendingEndReason?.let { reason ->
                pendingEndReason = null
                dispatch(CampingEvent.Restored(reason))
            }
        }
    }

    private fun stopAlarm() {
        alarm.stop()
        updateMorning { it.copy(alarmRinging = false) }
    }

    private fun updateMorning(transform: (MorningState) -> MorningState) {
        _state.update { state -> state.morning?.let { state.copy(morning = transform(it)) } ?: state }
    }

    override fun onCleared() {
        sound.stop()
        alarm.stop()
    }

    private fun dispatch(event: CampingEvent) {
        // 기어 이탈로 복구하는 중에는 복구 완료만 받는다. 그사이 배터리 하한·기상 시각으로 아침에 넘어가지 않게 한다.
        if (pendingEndReason != null && event !is CampingEvent.Restored) return
        val previous = _state.value.stage
        when (val result = CampingSessionRules.next(previous, event)) {
            is CampingTransition.MoveTo -> {
                applyMove(event, result)
                if (previous != CampingStage.NIGHT && result.stage == CampingStage.NIGHT) onEnterNight()
                if (previous == CampingStage.NIGHT && result.stage != CampingStage.NIGHT) onLeaveNight()
                if (previous != CampingStage.MORNING && result.stage == CampingStage.MORNING) onEnterMorning()
                if (previous == CampingStage.MORNING && result.stage != CampingStage.MORNING) onLeaveMorning()
            }
            is CampingTransition.RestoreThenEnd -> endAfterRestore(result.reason)
            is CampingTransition.Rejected -> {
                // 사용자가 직접 누른 시작만 거절 이유를 보여준다.
                // 기어 변경 같은 차량 사건의 거절은 "해당 없음"이라 조용히 무시한다.
                if (event is CampingEvent.Start) {
                    _state.update { it.copy(message = result.why) }
                }
            }
        }
    }

    private fun applyMove(event: CampingEvent, result: CampingTransition.MoveTo) {
        _state.update {
                it.copy(
                    stage = result.stage,
                    // 새 세션을 시작하면 지난 종료 이유는 지운다.
                    lastEndReason = if (event is CampingEvent.Start) null else result.reason ?: it.lastEndReason,
                    message = null,
                    // 새 세션을 시작하면 지난 세션의 판정·설정·백업은 지운다.
                    arrival = if (event is CampingEvent.Start) null else it.arrival,
                    bedtime = if (event is CampingEvent.Start) null else it.bedtime,
                    backup = if (event is CampingEvent.Start) null else it.backup,
                    temperatureApply = if (event is CampingEvent.Start) null else it.temperatureApply,
                    lightApply = if (event is CampingEvent.Start) null else it.lightApply,
                    wakeDateTime = if (event is CampingEvent.Start) null else it.wakeDateTime,
                    nightStartBattery = if (event is CampingEvent.Start) null else it.nightStartBattery,
                    morning = if (event is CampingEvent.Start) null else it.morning,
                    restoring = if (event is CampingEvent.Start) false else it.restoring,
                    restore = if (event is CampingEvent.Start) null else it.restore,
                )
            }
    }

    private companion object {
        const val TAG = "CampingViewModel"
        val DEFAULT_WAKE_AT: LocalTime = LocalTime.of(7, 0)
        const val WAKE_CHECK_INTERVAL_MS = 15_000L
    }
}
