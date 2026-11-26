package ai.naru.app.comma

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.naru.app.BuildConfig
import ai.naru.app.NaruApplication
import ai.naru.app.camping.intent.PleosSpeechInput
import ai.naru.app.camping.intent.SpeechEvent
import ai.naru.app.camping.intent.SpeechInput
import ai.naru.app.session.SessionOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RestViewModel(application: Application) : AndroidViewModel(application) {
    private val naruApplication = application as NaruApplication
    private val vehicle: VehicleRepository = naruApplication.commaVehicleRepository
    private val chargingSource: CommaChargingSource = naruApplication.commaChargingSource
    private val sessionOwnership = naruApplication.sessionOwnership
    private val cabin: CabinController = AppCabinController()
    private val clock = object : RestClock {
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun wallMillis() = System.currentTimeMillis()
    }
    private val manager = RestSessionManager(clock, cabin)
    private val history = RestApplicationServices.history(application)
    private val activeSessions = RestApplicationServices.activeSession(application)
    private val alarmScheduler = RestAlarmScheduler(application)
    private val intentRepository: CommaIntentRepository = BuildConfig.SERVER_URL
        .takeIf(String::isNotBlank)
        ?.let(::ServerCommaIntentRepository)
        ?: DisabledCommaIntentRepository
    private var intentRequestSequence = 0L
    private var intentRequestJob: Job? = null
    private var intentProcessing = false
    private val speechInput: SpeechInput by lazy { PleosSpeechInput(application) }
    private var speechStatus = CommaSpeechStatus.IDLE
    private var speechText: String? = null
    private var speechMessage: String? = null
    private var restoringPersistedSession = true
    private var lastPersistedPhase: RestPhase? = null
    private var wakeLightingJob: Job? = null
    private var lastWakeLightingStep = -1
    private var wakeLightingBlocked = false
    private var restorationSessionId: String? = null
    private var startVehicleState: VehicleState? = null
    private var appliedClimateTarget: Float? = null
    private var selectedClimateTarget: Float? = null
    private var climateSelectionIsManual = false
    private var pendingIntentProposal: CommaIntentProposal? = null
    private var intentResultMessage: String? = null
    private var alarmTimingWarning: String? = null
    private var latestChargingSnapshot = chargingSource.state.value
    private var chargingTracker: ChargingObservationTracker? = null
    private var lastChargingObservationMillis: Long? = null
    private val lifecycle = RestSessionLifecycle(manager, history, ::decorateRecord)
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
        ::finish, { requestClimate() }, history::retry, ::submitFeedback,
        startAlarmOnly = ::startAlarmOnly,
        selectClimateTarget = ::selectClimateTarget,
        applySelectedClimate = ::applySelectedClimate,
        proposeIntent = ::proposeIntent,
        approveIntent = ::approveIntent,
        cancelIntent = ::cancelIntent,
        startSpeechInput = ::startSpeechInput,
        stopSpeechInput = ::stopSpeechInput,
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
        viewModelScope.launch { restorePersistedSession() }
        viewModelScope.launch { history.state.collect { publish() } }
        viewModelScope.launch {
            GleoCommaActionStore.actions.collect { action ->
                action ?: return@collect
                if (action.isExpired()) {
                    GleoCommaActionStore.consume(action.requestId)
                    return@collect
                }
                val owner = sessionOwnership.owner.value
                val phase = manager.state.value.phase
                val proposal = if (
                    owner == SessionOwner.CAMPING ||
                    vehicle.state.value.parkingStatus != ParkingStatus.PARKED
                ) {
                    null
                } else {
                    GleoCommaContract.proposal(
                        functionName = action.action,
                        parameters = action.parameters,
                        phase = phase,
                        capabilities = vehicle.capabilities,
                    )
                }
                if (proposal == null) {
                    intentResultMessage = when {
                        owner == SessionOwner.CAMPING -> "차박 모드가 진행 중이라 쉼표 요청을 적용할 수 없습니다."
                        vehicle.state.value.parkingStatus != ParkingStatus.PARKED ->
                            "주차 상태를 확인할 수 없어 쉼표 요청을 적용하지 않았습니다."
                        else -> "현재 단계 또는 차량 지원 범위에서 사용할 수 없는 요청입니다."
                    }
                } else {
                    pendingIntentProposal = proposal
                    intentResultMessage = null
                }
                GleoCommaActionStore.consume(action.requestId)
                publish()
            }
        }
        viewModelScope.launch {
            chargingSource.state.collect { snapshot ->
                latestChargingSnapshot = snapshot
                if (manager.active && snapshot.observedAtMillis != lastChargingObservationMillis) {
                    ensureChargingTracker(recovered = false).observe(snapshot)
                    lastChargingObservationMillis = snapshot.observedAtMillis
                }
                publish()
            }
        }
        viewModelScope.launch {
            vehicle.state.collect { state ->
                if(state.parkingStatus==ParkingStatus.NOT_PARKED) localParkedConfirmed=false
                if(dispatchDrivingStop(state,vehicle.capabilities,vehicle.isMock,isDemo,
                    {manager.state.value.phase},::finishForDriving)) {
                    vehicleIssue=VehicleIssue.DRIVING_STOPPED
                }
                publish()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                lifecycle.tick()
                if (!isDemo && manager.active) {
                    ensureChargingTracker(recovered = restoringPersistedSession)
                }
                if (!isDemo && manager.active && manager.state.value.phase != lastPersistedPhase) {
                    persistActiveSession()
                }
                if (!isDemo) {
                    updateWakeLighting()
                    if (manager.state.value.phase == RestPhase.FINISHED) beginRestoration()
                }
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
        demoVehicle = if(isDemo) demo.vehicle else null,
        selectedClimateCelsius = selectedClimateTarget,
        climateSelectionIsManual = climateSelectionIsManual,
        pendingIntentProposal = pendingIntentProposal,
        intentResultMessage = intentResultMessage,
        intentProcessing = intentProcessing,
        speechStatus = speechStatus,
        speechText = speechText,
        speechMessage = speechMessage,
        alarmTimingWarning = alarmTimingWarning,
        sessionStartBatteryPercent = startVehicleState?.batteryPercent,
        charging = latestChargingSnapshot,
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
        if (restoringPersistedSession) {
            message = "저장된 휴식 세션을 확인하고 있습니다."
            publish()
            return
        }
        if (isDemo) {
            if(demo.vehicle.vehicle.parkingStatus!=ParkingStatus.PARKED) {
                vehicleIssue=if(demo.vehicle.vehicle.parkingStatus==ParkingStatus.NOT_PARKED)
                    VehicleIssue.NOT_PARKED else VehicleIssue.PARKING_UNKNOWN
            } else if(!demo.active) demo.startPrepared()
        } else {
            val issue=startIssue(vehicle.state.value,vehicle.isMock,localParkedConfirmed,firstUseAcknowledged)
            if (issue != null) {
                vehicleIssue = issue
            } else if (!sessionOwnership.acquire(SessionOwner.COMMA)) {
                message = "차박 모드가 진행 중이라 쉼표 모드를 시작할 수 없습니다."
            } else {
                startVehicleState = vehicle.state.value
                chargingTracker = ChargingObservationTracker().also { it.start(latestChargingSnapshot) }
                lastChargingObservationMillis = latestChargingSnapshot.observedAtMillis
                if (manager.start()) {
                    val approvedLighting = manager.state.value.settings.lighting
                    viewModelScope.launch {
                        persistActiveSession()
                        vehicle.applyLighting(approvedLighting)
                        persistActiveSession()
                        scheduleCurrentAlarm()
                    }
                } else {
                    sessionOwnership.release(SessionOwner.COMMA)
                }
            }
        }
        publish()
    }
    private fun extend(minutes: Int) {
        val accepted = if (isDemo) demo.extend(minutes) else manager.extend(minutes)
        if (!accepted) {
            if (isDemo) demo.tick() else lifecycle.tick()
            message = "연장 가능 시간이 지났습니다. 새 휴식을 시작하세요."
        }
        if (accepted && !isDemo) viewModelScope.launch {
            cancelWakeLighting(restoreApprovedLevel = true)
            persistActiveSession()
            scheduleCurrentAlarm()
        }
        publish()
    }
    private fun finish() {
        if (isDemo) {
            demo.finish()
        } else {
            cancelWakeLighting()
            manager.persistenceSnapshot(
                if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL,
                appliedClimateTarget,
            )?.sessionId?.let(alarmScheduler::cancel)
            lifecycle.finish()
            beginRestoration()
            sessionOwnership.release(SessionOwner.COMMA)
        }
        publish()
    }

    private fun finishForDriving() {
        cancelWakeLighting()
        manager.persistenceSnapshot(
            if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL,
            appliedClimateTarget,
        )?.sessionId?.let(alarmScheduler::cancel)
        lifecycle.finishForDriving()
        beginRestoration()
        sessionOwnership.release(SessionOwner.COMMA)
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
        startVehicleState = null
        appliedClimateTarget = null
        restorationSessionId = null
        selectedClimateTarget = null
        climateSelectionIsManual = false
        vehicleIssue=null
        message = null
        publish()
        return manager.state.value.phase == RestPhase.READY
    }

    fun clearMessage() { message = null; publish() }

    fun onEnterComma() {
        if (!isDemo) vehicle.refresh()
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

    private fun startSpeechInput() {
        RestAudioCoordinator.stopNature()
        speechStatus = CommaSpeechStatus.RECOGNIZING
        speechMessage = null
        speechText = null
        publish()
        speechInput.listen { event ->
            viewModelScope.launch {
                when (event) {
                    SpeechEvent.Listening -> speechStatus = CommaSpeechStatus.LISTENING
                    is SpeechEvent.Partial -> {
                        speechStatus = CommaSpeechStatus.RECOGNIZING
                        speechText = event.text
                    }
                    is SpeechEvent.Final -> {
                        speechStatus = CommaSpeechStatus.IDLE
                        speechText = event.text
                        proposeIntent(event.text)
                    }
                    is SpeechEvent.Failed -> {
                        speechStatus = if (event.reason == "NOT_AVAILABLE") CommaSpeechStatus.UNSUPPORTED else CommaSpeechStatus.FAILED
                        speechMessage = "음성 입력을 사용할 수 없습니다. 텍스트로 입력해 주세요."
                    }
                    SpeechEvent.Ended -> {
                        speechStatus = CommaSpeechStatus.IDLE
                        speechMessage = "인식된 문장이 없습니다."
                    }
                }
                publish()
            }
        }
    }

    private fun stopSpeechInput() {
        speechInput.stop()
        speechStatus = CommaSpeechStatus.IDLE
        speechMessage = "음성 입력을 취소했습니다."
        publish()
    }

    private fun proposeIntent(utterance: String) {
        val phase = if (isDemo) demo.state.value.phase else manager.state.value.phase
        val requestSequence = ++intentRequestSequence
        val requestId = "comma-${clock.wallMillis()}-$requestSequence"
        intentRequestJob?.cancel()
        pendingIntentProposal = null
        intentResultMessage = null
        intentProcessing = true
        publish()
        intentRequestJob = viewModelScope.launch {
            val recommendation = recommendClimate(vehicle.state.value, history.state.value.records)
            val latestPreference = history.state.value.records
                .firstOrNull { it.source == RecordSource.REAL && it.feedbackSubmittedAt != null }
                ?.targetClimateCelsius
                ?.let { "최근 확인된 선호 온도 ${it}도" }
            val context = CommaIntentContext(
                phase = phase,
                sessionId = manager.persistenceSnapshot(
                    if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL,
                    appliedClimateTarget,
                )?.sessionId,
                selectedMinutes = manager.state.value.selectedMinutes,
                remainingMinutes = manager.state.value.remainingMillis.takeIf { manager.active }
                    ?.let { ((it + 59_999L) / 60_000L).toInt() },
                currentTempC = selectedClimateTarget ?: appliedClimateTarget,
                recommendedTempC = recommendation.targetCelsius,
                temperatureRange = vehicle.capabilities.climateTemperatureRange,
                climateSupported = vehicle.capabilities.climate == ControlSupport.SUPPORTED,
                ambientLightSupported = vehicle.capabilities.lighting == ControlSupport.SUPPORTED,
                sound = manager.state.value.settings.sound,
                screenBrightnessPercent = (manager.state.value.settings.brightness * 100).toInt(),
                recentPreferenceSummary = latestPreference,
            )
            val serverResult = intentRepository.interpret(requestId, utterance, context)
            if (requestSequence != intentRequestSequence) return@launch
            val currentPhase = if (isDemo) demo.state.value.phase else manager.state.value.phase
            if (currentPhase != phase) {
                intentProcessing = false
                intentResultMessage = "세션 상태가 바뀌어 늦게 도착한 제안을 폐기했습니다."
                publish()
                return@launch
            }
            pendingIntentProposal = serverResult
                ?.let { CommaIntentRules.fromServer(utterance, phase, it) }
                ?: CommaIntentRules.interpretLocally(utterance, phase)
            intentResultMessage = when {
                pendingIntentProposal == null -> "현재 단계에서 적용 가능한 제안을 만들지 못했습니다."
                pendingIntentProposal?.source == CommaProposalSource.LOCAL_RULE && intentRepository.available ->
                    "서버 응답을 사용할 수 없어 로컬 규칙으로 제안했습니다."
                pendingIntentProposal?.source == CommaProposalSource.LOCAL_RULE ->
                    "로컬 규칙으로 제안했습니다."
                else -> null
            }
            intentProcessing = false
            publish()
        }
    }

    private fun cancelIntent() {
        intentRequestSequence++
        intentRequestJob?.cancel()
        intentProcessing = false
        pendingIntentProposal = null
        intentResultMessage = "제안을 취소했습니다."
        publish()
    }

    private fun approveIntent() {
        val proposal = pendingIntentProposal ?: return
        val phase = if (isDemo) demo.state.value.phase else manager.state.value.phase
        if (proposal.phaseAtCreation != phase ||
            !CommaIntentRules.validate(proposal.changes, phase, vehicle.capabilities)) {
            pendingIntentProposal = null
            intentResultMessage = "세션 상태나 차량 지원 범위가 바뀌어 제안을 적용하지 않았습니다."
            publish()
            return
        }
        proposal.changes.forEach { change -> applyApprovedIntentChange(change) }
        intentResultMessage = if (proposal.changes.any {
                it is CommaProposedChange.TemperatureAbsolute ||
                    it is CommaProposedChange.TemperatureDelta ||
                    it is CommaProposedChange.AmbientLight
            }) "승인한 변경을 요청했습니다. 차량 항목은 재조회 결과를 확인합니다."
            else "승인한 앱 설정을 반영했습니다."
        pendingIntentProposal = null
        if (!isDemo && manager.active) viewModelScope.launch { persistActiveSession() }
        publish()
    }

    private fun applyApprovedIntentChange(change: CommaProposedChange) {
        when (change) {
            is CommaProposedChange.Duration -> selectMinutes(change.minutes)
            is CommaProposedChange.Extension -> extend(change.minutes)
            is CommaProposedChange.Sound -> manager.applyApprovedSettings(
                manager.state.value.settings.copy(sound = change.sound),
            )
            is CommaProposedChange.Brightness -> manager.applyApprovedSettings(
                manager.state.value.settings.copy(brightness = change.percent / 100f),
            )
            is CommaProposedChange.AmbientLight -> {
                val level = change.percent / 100f
                manager.applyApprovedSettings(manager.state.value.settings.copy(lighting = level))
                if (!isDemo && manager.active) viewModelScope.launch { vehicle.applyLighting(level) }
            }
            is CommaProposedChange.TemperatureAbsolute -> {
                selectedClimateTarget = change.celsius
                climateSelectionIsManual = true
                requestClimate(change.celsius)
            }
            is CommaProposedChange.TemperatureDelta -> {
                val range = vehicle.capabilities.climateTemperatureRange ?: return
                val base = selectedClimateTarget ?: appliedClimateTarget
                    ?: recommendClimate(vehicle.state.value, history.state.value.records).targetCelsius
                    ?: return
                val target = (base + change.deltaCelsius).coerceIn(range.start, range.endInclusive)
                selectedClimateTarget = target
                climateSelectionIsManual = true
                requestClimate(target)
            }
        }
    }

    private fun selectClimateTarget(target: Float?) {
        val range = vehicle.capabilities.climateTemperatureRange
        if (target == null) {
            selectedClimateTarget = recommendClimate(vehicle.state.value, history.state.value.records).targetCelsius
            climateSelectionIsManual = false
        } else if (range != null && target in range) {
            selectedClimateTarget = target
            climateSelectionIsManual = true
        }
        publish()
    }

    private fun applySelectedClimate() = requestClimate(selectedClimateTarget)

    private fun requestClimate(explicitTarget: Float? = null) {
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
        val target = explicitTarget
            ?: selectedClimateTarget
            ?: recommendClimate(vehicle.state.value, history.state.value.records).targetCelsius
        if(target==null) {
            vehicleControl=VehicleControlState(ControlOutcome.UNKNOWN,"온도/배터리 정보 부족으로 제어 요청하지 않음")
            vehicleIssue=VehicleIssue.CONTROL_UNKNOWN;publish();return
        }
        vehicleControl=VehicleControlState(ControlOutcome.PENDING,"차량 응답 대기 중");publish()
        viewModelScope.launch {
            val result=try { vehicle.requestClimate(target) }
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {ClimateResult.Unknown("차량 응답 확인 불가 · 적용 성공으로 간주하지 않습니다.")}
            if (result is ClimateResult.Applied) {
                appliedClimateTarget = result.targetCelsius
                selectedClimateTarget = result.targetCelsius
            }
            if (manager.active) persistActiveSession()
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

    private fun decorateRecord(record: RestRecord): RestRecord {
        val start = startVehicleState
        val end = vehicle.state.value
        val settings = manager.state.value.settings
        val charging = chargingTracker?.finish(latestChargingSnapshot) ?: ChargingObservation(
            stateAtEnd = latestChargingSnapshot.state,
            quality = EnergyDataQuality.OBSERVATION_INCOMPLETE,
        )
        chargingTracker = null
        lastChargingObservationMillis = null
        return record.copy(
            startBatteryPercent = start?.batteryPercent,
            endBatteryPercent = end.batteryPercent,
            startIndoorCelsius = start?.indoorCelsius,
            endIndoorCelsius = end.indoorCelsius,
            outdoorCelsius = start?.outdoorCelsius ?: end.outdoorCelsius,
            targetClimateCelsius = appliedClimateTarget,
            screenBrightness = settings.brightness,
            lightingBrightness = settings.lighting,
            sound = settings.sound,
            source = if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL,
            chargingStateAtStart = charging.stateAtStart,
            chargingStateAtEnd = charging.stateAtEnd,
            chargePortConnectedDuringRest = charging.portConnectedDuringRest,
            chargingDetectedDuringRest = charging.chargingDetectedDuringRest,
            chargingObservationComplete = charging.complete,
            energyDataQuality = charging.quality,
        )
    }

    private fun ensureChargingTracker(recovered: Boolean): ChargingObservationTracker {
        chargingTracker?.let { return it }
        return ChargingObservationTracker().also { tracker ->
            tracker.start(latestChargingSnapshot)
            if (recovered) tracker.markObservationGap()
            chargingTracker = tracker
            lastChargingObservationMillis = latestChargingSnapshot.observedAtMillis
        }
    }

    private suspend fun persistActiveSession() {
        val source = if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL
        val snapshot = manager.persistenceSnapshot(
            source = source,
            approvedClimateCelsius = appliedClimateTarget,
            vehicleBackup = vehicle.backupSnapshot(),
        ) ?: return
        activeSessions.save(snapshot)
        lastPersistedPhase = snapshot.phase
    }

    private fun updateWakeLighting() {
        val session = manager.state.value
        if (wakeLightingBlocked || session.phase != RestPhase.WAKING ||
            vehicle.capabilities.lighting != ControlSupport.SUPPORTED) {
            if (session.phase != RestPhase.WAKING) lastWakeLightingStep = -1
            return
        }
        val progress = (1f - session.confirmationRemainingMillis / 30_000f).coerceIn(0f, 1f)
        val step = (progress * WAKE_LIGHT_STEPS).toInt().coerceIn(0, WAKE_LIGHT_STEPS)
        if (step == lastWakeLightingStep || wakeLightingJob?.isActive == true) return
        lastWakeLightingStep = step
        val base = session.settings.lighting
        val target = base + (1f - base) * (step / WAKE_LIGHT_STEPS.toFloat())
        wakeLightingJob = viewModelScope.launch {
            when (vehicle.applyLighting(target)) {
                is ClimateResult.Failed, is ClimateResult.Unknown -> wakeLightingBlocked = true
                else -> Unit
            }
        }
    }

    private fun cancelWakeLighting(restoreApprovedLevel: Boolean = false) {
        wakeLightingJob?.cancel()
        wakeLightingJob = null
        lastWakeLightingStep = -1
        if (restoreApprovedLevel) wakeLightingBlocked = false
        if (restoreApprovedLevel && manager.active && vehicle.capabilities.lighting == ControlSupport.SUPPORTED) {
            val approved = manager.state.value.settings.lighting
            wakeLightingJob = viewModelScope.launch { vehicle.applyLighting(approved) }
        }
    }

    private fun beginRestoration() {
        val completed = manager.state.value.completed ?: return
        if (restorationSessionId == completed.id) return
        restorationSessionId = completed.id
        RestAudioCoordinator.stopNature()
        alarmScheduler.cancel(completed.id)
        viewModelScope.launch {
            val appReport = manager.state.value.restoration ?: RestorationReport()
            val vehicleReport = try {
                vehicle.restoreAndVerify()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RestorationReport(
                    vehicle = RestorationOutcome.UNKNOWN,
                    hvacPower = RestorationOutcome.MANUAL_CHECK,
                    climateTemperature = RestorationOutcome.MANUAL_CHECK,
                    ambientLight = RestorationOutcome.MANUAL_CHECK,
                )
            }
            val report = vehicleReport.copy(
                appSettings = appReport.appSettings,
                windowAndAudio = RestorationOutcome.SUCCESS,
                screenBrightness = appReport.screenBrightness,
                natureSound = RestorationOutcome.SUCCESS,
                alarm = RestorationOutcome.SUCCESS,
            )
            manager.updateRestoration(report)
            history.updateRestoration(completed.id, report)
            val remainingBackup = vehicle.backupSnapshot()
            if (remainingBackup == null) {
                activeSessions.clear()
            } else {
                activeSessions.load()?.let { stored ->
                    activeSessions.save(stored.copy(
                        restoreState = "NEEDS_MANUAL_CHECK",
                        vehicleBackup = remainingBackup,
                    ))
                }
            }
            publish()
        }
    }

    private fun scheduleCurrentAlarm() {
        val snapshot = manager.persistenceSnapshot(
            source = if (vehicle.isMock) RecordSource.MOCK else RecordSource.REAL,
            approvedClimateCelsius = appliedClimateTarget,
            vehicleBackup = vehicle.backupSnapshot(),
        ) ?: return
        alarmTimingWarning = when (alarmScheduler.schedule(snapshot.sessionId, snapshot.endAtWallMillis)) {
            AlarmScheduleResult.EXACT -> null
            AlarmScheduleResult.INEXACT -> "정확한 알람 권한이 없어 종료 알람이 늦게 울릴 수 있습니다."
            AlarmScheduleResult.EXPIRED -> "종료 시각이 지나 알람을 예약하지 않았습니다."
        }
        publish()
    }

    private suspend fun restorePersistedSession() {
        val stored = runCatching { activeSessions.load() }.getOrNull()
        if (stored == null) {
            restoringPersistedSession = false
            return
        }
        val decision = decideRestRecovery(stored, clock.wallMillis(), clock.elapsedMillis())
        stored.vehicleBackup?.let(vehicle::installBackup)
        val parked = vehicle.state.value.parkingStatus == ParkingStatus.PARKED
        val canResume = decision.restoredDeadlineElapsed != null &&
            (stored.source == RecordSource.MOCK || parked) &&
            sessionOwnership.acquire(SessionOwner.COMMA)
        if (canResume && manager.restore(stored, decision.restoredDeadlineElapsed!!)) {
            appliedClimateTarget = stored.approvedClimateCelsius
            ensureChargingTracker(recovered = true)
            lastPersistedPhase = manager.state.value.phase
            scheduleCurrentAlarm()
            message = when (decision.kind) {
                RecoveryKind.DEVICE_REBOOT -> "기기 재부팅 후 저장된 휴식을 종료 시각 기준으로 복구했습니다."
                RecoveryKind.PROCESS_RESTART, RecoveryKind.SAME_BOOT -> "진행 중이던 휴식을 복구했습니다."
                else -> "저장된 휴식을 복구했습니다."
            }
        } else {
            sessionOwnership.release(SessionOwner.COMMA)
            vehicle.restoreClimate()
            activeSessions.save(stored.copy(restoreState = "NEEDS_MANUAL_CHECK"))
            message = when (decision.kind) {
                RecoveryKind.SYSTEM_TIME_CHANGED -> "시스템 시각 변경으로 재개하지 않았습니다. 저장된 백업을 보존했습니다."
                RecoveryKind.EXPIRED -> "저장된 휴식 종료 시각이 지나 복구 확인이 필요합니다."
                else -> "안전하게 복구할 수 없어 저장된 백업을 보존했습니다."
            }
        }
        restoringPersistedSession = false
        publish()
    }

    private fun submitFeedback(
        sessionId: String,
        satisfaction: Int,
        temperatureFeedback: TemperatureFeedback,
    ) {
        if (isDemo || vehicle.isMock) {
            message = "DEMO·Mock 기록은 개인화 피드백에 사용하지 않습니다."
            publish()
            return
        }
        history.submitFeedback(
            sessionId = sessionId,
            satisfaction = satisfaction,
            temperatureFeedback = temperatureFeedback,
            submittedAt = clock.wallMillis(),
        ) { saved ->
            message = if (saved) "피드백을 저장했습니다." else "피드백을 다시 저장하지 않았습니다."
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
        intentRequestJob?.cancel()
        speechInput.release()
        cancelWakeLighting()
        // viewModelScope may already be cancelled. This submits to an application-owned queue.
        lifecycle.close()
        vehicle.restoreClimate()
        sessionOwnership.release(SessionOwner.COMMA)
        demo.finish() // Demo completion must never enter the real history queue.
        super.onCleared()
    }

    private companion object {
        const val WAKE_LIGHT_STEPS = 6
    }
}
