package ai.naru.app.camping

import ai.naru.app.camping.arrival.ArrivalAssessment
import ai.naru.app.camping.arrival.ArrivalRules
import ai.naru.app.camping.arrival.Site
import ai.naru.app.camping.arrival.SiteConditions
import ai.naru.app.camping.arrival.SiteRepository
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

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

    /** 판정 결과를 확인한 뒤에만 다음 단계로 넘어간다. */
    fun confirmArrival() {
        if (_state.value.arrival == null) return
        dispatch(CampingEvent.ConfirmArrival)
    }

    fun startNight() = dispatch(CampingEvent.StartNight)

    /** TODO(다음 이슈): 실제 설정 복구가 끝나면 호출되도록 바꾼다. 지금은 버튼으로 대신한다. */
    fun finishRestore() = dispatch(CampingEvent.Restored)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // --- 개발용: 알람과 배터리 감시가 생기기 전까지 사건을 손으로 발생시킨다 ---

    fun simulateWakeTime() = dispatch(CampingEvent.WakeTimeReached)

    fun simulateBatteryLow() = dispatch(CampingEvent.BatteryLow)

    private fun dispatch(event: CampingEvent) {
        when (val result = CampingSessionRules.next(_state.value.stage, event)) {
            is CampingTransition.MoveTo -> _state.update {
                it.copy(
                    stage = result.stage,
                    // 새 세션을 시작하면 지난 종료 이유는 지운다.
                    lastEndReason = if (event is CampingEvent.Start) null else result.reason ?: it.lastEndReason,
                    message = null,
                    // 새 세션을 시작하면 지난 도착 판정은 지운다.
                    arrival = if (event is CampingEvent.Start) null else it.arrival,
                )
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
}
