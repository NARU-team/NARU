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
import ai.naru.app.camping.night.NightRules
import ai.naru.app.camping.night.NightSound
import ai.naru.app.camping.night.ThemeLightApplier
import java.time.LocalDateTime
import java.time.LocalTime
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
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
) : ViewModel() {

    private val nightPreparer = NightPreparer(vehicle)
    private val themeLightApplier = ThemeLightApplier(vehicle)

    /** 밤 단계 동안만 도는 작업 (조명 적용, 기상 시각 확인). 밤을 벗어나면 취소한다. */
    private var nightJob: Job? = null

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
        viewModelScope.launch {
            val sites = siteRepository.sites()
            _state.update { it.copy(sites = sites) }
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
            _state.update {
                it.copy(
                    backup = preparation.backup,
                    temperatureApply = preparation.temperature,
                    bedtime = it.bedtime?.copy(preparing = false),
                )
            }
            dispatch(CampingEvent.StartNight)
        }
    }

    private fun updateBedtime(transform: (BedtimeState) -> BedtimeState) {
        _state.update { state -> state.bedtime?.let { state.copy(bedtime = transform(it)) } ?: state }
    }

    /** TODO(다음 이슈): 실제 설정 복구가 끝나면 호출되도록 바꾼다. 지금은 버튼으로 대신한다. */
    fun finishRestore() = dispatch(CampingEvent.Restored)

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

        nightJob = viewModelScope.launch {
            val lightResult = themeLightApplier.apply(settings.theme)
            _state.update { it.copy(lightApply = lightResult) }

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

    override fun onCleared() {
        sound.stop()
    }

    private fun dispatch(event: CampingEvent) {
        val previous = _state.value.stage
        when (val result = CampingSessionRules.next(previous, event)) {
            is CampingTransition.MoveTo -> {
                applyMove(event, result)
                if (previous != CampingStage.NIGHT && result.stage == CampingStage.NIGHT) onEnterNight()
                if (previous == CampingStage.NIGHT && result.stage != CampingStage.NIGHT) onLeaveNight()
            }
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
                )
            }
    }

    private companion object {
        val DEFAULT_WAKE_AT: LocalTime = LocalTime.of(7, 0)
        const val WAKE_CHECK_INTERVAL_MS = 15_000L
    }
}
