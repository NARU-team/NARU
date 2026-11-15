package com.naru.app.comma

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naru.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RestViewModel(application: Application) : AndroidViewModel(application) {
    private val vehicle: VehicleRepository = MockVehicleRepository()
    private val cabin: CabinController = AppCabinController()
    private val clock = object : RestClock {
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun wallMillis() = System.currentTimeMillis()
    }
    private val manager = RestSessionManager(clock, cabin)
    private val history = RestApplicationServices.history(application)
    private val lifecycle = RestSessionLifecycle(manager, history)
    private var demo = DemoSessionController(clock)
    private var isDemo = false
    private var message: String? = null
    private val onboarding = application.getSharedPreferences("comma_onboarding",android.content.Context.MODE_PRIVATE)
    private var firstUseAcknowledged = onboarding.getBoolean("app_only_explained",false)
    private var localParkedConfirmed = false
    private var vehicleIssue: VehicleIssue? = null
    private var vehicleControl = if(vehicle.isMock)
        VehicleControlState(ControlOutcome.UNSUPPORTED,"Mock 차량 · 실제 차량 제어 미지원")
        else VehicleControlState()
    private val mutableUi = MutableStateFlow(snapshot())
    val uiState: StateFlow<RestUiState> = mutableUi
    val actions = RestUiActions(::selectMinutes, ::configure, ::start, ::extend,
        ::finish, ::requestClimate, history::retry,
        startAlarmOnly = ::startAlarmOnly,
        finishResting = {
            dispatchRestingFinish(
                currentPhase = { if (isDemo) demo.state.value.phase else manager.state.value.phase },
                finish = ::finish
            )
        })

    // Screen-specific callbacks reject double taps and callbacks delivered after extension.
    val wakingActions = actions.copy(
        extend = { minutes ->
            dispatchWakingAction(
                { if (isDemo) demo.state.value.phase else manager.state.value.phase },
                { extend(minutes) })
        },
        finish = {
            dispatchWakingAction(
                { if (isDemo) demo.state.value.phase else manager.state.value.phase },
                ::finish)
        }
    )

    init {
        history.retry()
        viewModelScope.launch { history.state.collect { publish() } }
        viewModelScope.launch {
            vehicle.state.collect { state ->
                if(state.parkingStatus==ParkingStatus.NOT_PARKED) localParkedConfirmed=false
                if(dispatchDrivingStop(state,vehicle.capabilities,vehicle.isMock,isDemo,
                    {manager.state.value.phase},lifecycle::finishForDriving)) {
                    vehicleIssue=VehicleIssue.DRIVING_STOPPED
                }
                publish()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                lifecycle.tick()
                if (isDemo) demo.tick()
                publish()
                delay(250)
            }
        }
    }

    private fun snapshot() = RestUiState(
        session = if (isDemo) demo.state.value else manager.state.value,
        cabin = if (isDemo) demo.cabin.settings.value else cabin.settings.value,
        vehicle = if(isDemo) demo.vehicle.vehicle else vehicle.state.value,
        isMock = vehicle.isMock,
        history = if (isDemo) RestHistoryState() else history.state.value,
        message = message,
        demoEnabled = BuildConfig.DEBUG && BuildConfig.DEMO_MODE,
        isDemo = isDemo,
        nowMillis = if (isDemo) demo.wallMillis() else clock.wallMillis(),
        capabilities = vehicle.capabilities,
        vehicleControl = if(isDemo) demo.vehicle.controlDisplay() else vehicleControl,
        vehicleIssue = vehicleIssue,
        localParkedConfirmed = localParkedConfirmed,
        demoVehicle = if(isDemo) demo.vehicle else null
    )
    private fun publish() { mutableUi.value = snapshot() }

    private fun selectMinutes(minutes: Int) {
        if(isDemo) demo.selectMinutes(minutes) else manager.selectMinutes(minutes)
        publish()
    }
    private fun configure(settings: CabinSettings) {
        if(isDemo) demo.configure(settings) else manager.configure(settings)
        publish()
    }
    private fun start() {
        if (isDemo) {
            if(demo.vehicle.vehicle.parkingStatus!=ParkingStatus.PARKED) {
                vehicleIssue=if(demo.vehicle.vehicle.parkingStatus==ParkingStatus.NOT_PARKED)
                    VehicleIssue.NOT_PARKED else VehicleIssue.PARKING_UNKNOWN
            } else if(!demo.active) demo.startPrepared()
        } else {
            val issue=startIssue(vehicle.state.value,vehicle.isMock,localParkedConfirmed,firstUseAcknowledged)
            if(issue!=null) vehicleIssue=issue else manager.start()
        }
        publish()
    }
    private fun extend(minutes: Int) {
        val accepted = if (isDemo) demo.extend(minutes) else manager.extend(minutes)
        if (!accepted) {
            if (isDemo) demo.tick() else lifecycle.tick()
            message = "연장 가능 시간이 지났습니다. 새 휴식을 시작하세요."
        }
        publish()
    }
    private fun finish() {
        if (isDemo) demo.finish() else lifecycle.finish()
        publish()
    }
    /** FINISHED -> READY only. Completion/restore/save are never re-run by navigation. */
    fun prepareNextRest(): Boolean {
        val phase = if (isDemo) demo.state.value.phase else manager.state.value.phase
        if (phase != RestPhase.FINISHED) return false
        if(isDemo) {
            val ready=demo.prepareNext();vehicleIssue=null;message=null;publish();return ready
        }
        if (manager.state.value.phase == RestPhase.FINISHED) manager.prepareNext()
        localParkedConfirmed=false
        vehicleIssue=null
        message = null
        publish()
        return manager.state.value.phase == RestPhase.READY
    }

    fun clearMessage() { message = null; publish() }

    fun onEnterComma() {
        if(!firstUseAcknowledged && !isDemo) vehicleIssue=VehicleIssue.FIRST_USE
        publish()
    }
    fun dismissVehicleIssue() { vehicleIssue=null;publish() }
    fun showVehicleInfo() {
        vehicleIssue=when {
            !firstUseAcknowledged -> VehicleIssue.FIRST_USE
            vehicle.state.value.parkingStatus==ParkingStatus.NOT_PARKED -> VehicleIssue.NOT_PARKED
            vehicle.state.value.parkingStatus==ParkingStatus.UNKNOWN && !localParkedConfirmed -> VehicleIssue.PARKING_UNKNOWN
            vehicle.isMock || vehicle.capabilities.climate==ControlSupport.UNSUPPORTED -> VehicleIssue.UNSUPPORTED
            vehicleControl.outcome==ControlOutcome.FAILURE -> VehicleIssue.CONTROL_FAILURE
            else -> VehicleIssue.CONTROL_UNKNOWN
        }
        publish()
    }
    fun handleVehicleIssueAction(action:VehicleIssueAction) {
        if(!issueActionAllowed(snapshot(),action)) return
        if(isDemo) {
            when(action) {
                VehicleIssueAction.CONFIRM_LOCAL_PARKING,VehicleIssueAction.CHECK_PARKING ->
                    demo.setParking(ParkingStatus.PARKED)
                VehicleIssueAction.RETRY_CONTROL -> {
                    if(!demo.requestClimate()) {vehicleIssue=VehicleIssue.UNSUPPORTED;publish();return}
                }
                else -> Unit
            }
            vehicleIssue=null;publish();return
        }
        when(action) {
            VehicleIssueAction.ACKNOWLEDGE -> if(vehicleIssue==VehicleIssue.FIRST_USE) {
                firstUseAcknowledged=true
                onboarding.edit().putBoolean("app_only_explained",true).apply()
                vehicleIssue=null
            }
            VehicleIssueAction.CONFIRM_LOCAL_PARKING -> if(vehicle.isMock &&
                vehicleIssue==VehicleIssue.PARKING_UNKNOWN &&
                vehicle.state.value.parkingStatus!=ParkingStatus.NOT_PARKED) {
                localParkedConfirmed=true
                vehicleIssue=null
            }
            VehicleIssueAction.CHECK_PARKING -> {
                vehicleIssue=when(vehicle.state.value.parkingStatus) {
                    ParkingStatus.PARKED -> null
                    ParkingStatus.NOT_PARKED -> VehicleIssue.NOT_PARKED
                    ParkingStatus.UNKNOWN -> VehicleIssue.PARKING_UNKNOWN
                }
            }
            VehicleIssueAction.RETRY_CONTROL -> {vehicleIssue=null;requestClimate()}
            VehicleIssueAction.CONTINUE_APP_ONLY, VehicleIssueAction.RETURN,
            VehicleIssueAction.VIEW_COMPLETION -> vehicleIssue=null
        }
        publish()
    }

    private fun requestClimate() {
        if(isDemo) {
            vehicleIssue=if(demo.requestClimate()) null else VehicleIssue.UNSUPPORTED
            publish();return
        }
        if(vehicleControl.outcome==ControlOutcome.PENDING) return
        if(vehicle.isMock || vehicle.capabilities.climate==ControlSupport.UNSUPPORTED) {
            vehicleControl=VehicleControlState(ControlOutcome.UNSUPPORTED,"차량 SDK 미연동 · 실제 제어 없음")
            vehicleIssue=VehicleIssue.UNSUPPORTED;publish();return
        }
        if(vehicle.capabilities.climate==ControlSupport.UNKNOWN) {
            vehicleControl=VehicleControlState(ControlOutcome.UNKNOWN,"제어 지원 여부 확인 불가")
            vehicleIssue=VehicleIssue.CONTROL_UNKNOWN;publish();return
        }
        if(vehicle.state.value.parkingStatus!=ParkingStatus.PARKED) {
            vehicleIssue=if(vehicle.state.value.parkingStatus==ParkingStatus.NOT_PARKED)
                VehicleIssue.NOT_PARKED else VehicleIssue.PARKING_UNKNOWN
            publish();return
        }
        val target=recommendClimate(vehicle.state.value).targetCelsius
        if(target==null) {
            vehicleControl=VehicleControlState(ControlOutcome.UNKNOWN,"온도/배터리 정보 부족으로 제어 요청하지 않음")
            vehicleIssue=VehicleIssue.CONTROL_UNKNOWN;publish();return
        }
        vehicleControl=VehicleControlState(ControlOutcome.PENDING,"차량 응답 대기 중");publish()
        viewModelScope.launch {
            val result=try { vehicle.requestClimate(target) }
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {ClimateResult.Unknown("차량 응답 확인 불가 · 적용 성공으로 간주하지 않습니다.")}
            vehicleControl=controlState(result,vehicle.isMock,vehicle.capabilities.climate)
            vehicleIssue=when(vehicleControl.outcome) {
                ControlOutcome.FAILURE -> VehicleIssue.CONTROL_FAILURE
                ControlOutcome.UNSUPPORTED -> VehicleIssue.UNSUPPORTED
                ControlOutcome.UNKNOWN -> VehicleIssue.CONTROL_UNKNOWN
                else -> null
            }
            publish()
        }
    }

    fun startDemo() {
        if (!BuildConfig.DEMO_MODE || manager.active) return
        if(isDemo && demo.active) return
        demo = DemoSessionController(clock)
        demo.prepare(15,manager.state.value.settings)
        isDemo = true
        vehicleIssue = null
        message = null
        publish()
    }
    fun exitDemo() {
        if (!BuildConfig.DEMO_MODE || !isDemo) return
        demo.finish()
        isDemo = false
        vehicleIssue = null
        message = null
        publish()
    }
    private fun startAlarmOnly() {
        if(!BuildConfig.DEBUG || !BuildConfig.DEMO_MODE || !isDemo || demo.active) return
        if(!demo.startPrepared(DemoRestMode.ALARM_ONLY)) {
            vehicleIssue=if(demo.vehicle.vehicle.parkingStatus==ParkingStatus.NOT_PARKED)
                VehicleIssue.NOT_PARKED else VehicleIssue.PARKING_UNKNOWN
        }
        publish()
    }
    fun setDemoEnvironment(warm:Boolean) {
        if(!BuildConfig.DEBUG || !BuildConfig.DEMO_MODE || !isDemo) return
        demo.setEnvironment(warm);publish()
    }
    fun showDemoException(issue:VehicleIssue) {
        if(!BuildConfig.DEBUG || !BuildConfig.DEMO_MODE || !isDemo) return
        when(issue) {
            VehicleIssue.NOT_PARKED -> demo.setParking(ParkingStatus.NOT_PARKED)
            VehicleIssue.PARKING_UNKNOWN -> demo.setParking(ParkingStatus.UNKNOWN)
            VehicleIssue.UNSUPPORTED -> demo.setClimateSupported(false)
            VehicleIssue.CONTROL_FAILURE -> {demo.setClimateSupported(true);demo.failClimate()}
            VehicleIssue.DRIVING_STOPPED -> if(!demo.simulateDrivingStart()) {
                message="DEMO 가상 주행 시작은 휴식 중에 사용하세요.";publish();return
            }
            else -> Unit
        }
        vehicleIssue=issue;publish()
    }
    fun demoLastThirtySeconds() { if (BuildConfig.DEMO_MODE && isDemo) { demo.advanceToLastThirtySeconds(); publish() } }
    fun demoDeadline() { if (BuildConfig.DEMO_MODE && isDemo) { demo.advanceToDeadline(); publish() } }
    fun demoExpireConfirmation() { if (BuildConfig.DEMO_MODE && isDemo) { demo.expireConfirmation(); publish() } }

    override fun onCleared() {
        // viewModelScope may already be cancelled. This submits to an application-owned queue.
        lifecycle.close()
        demo.finish() // Demo completion must never enter the real history queue.
        super.onCleared()
    }
}
