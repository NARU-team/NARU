package ai.naru.app

import ai.naru.app.camping.CampingViewModel
import ai.naru.app.ui.camping.BedtimeActions
import ai.naru.app.ui.camping.CampingActions
import ai.naru.app.ui.camping.CampingScreen
import ai.naru.app.ui.camping.ArrivalActions
import ai.naru.app.ui.camping.ArrivalScreen
import ai.naru.app.camping.BedtimeStep
import ai.naru.app.ui.camping.AssistantActions
import ai.naru.app.ui.camping.BedtimeScreen
import ai.naru.app.ui.camping.BedtimeScreenActions
import ai.naru.app.ui.camping.MorningScreen
import ai.naru.app.ui.camping.MorningScreenActions
import ai.naru.app.ui.camping.NightScreen
import ai.naru.app.ui.camping.NightScreenActions
import ai.naru.app.ui.camping.ThemeScreen
import ai.naru.app.ui.camping.ThemeScreenActions
import ai.naru.app.ui.camping.MorningActions
import ai.naru.app.ui.debug.VehicleDebugPanel
import ai.naru.app.camping.CampingStage
import ai.naru.app.location.LocationGateway
import ai.naru.app.ui.home.OnboardingScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import ai.naru.app.ui.home.HomeScreen
import ai.naru.app.ui.home.HomeState
import ai.naru.app.ui.theme.NaruTheme
import ai.naru.app.vehicle.FakeVehicleGateway
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NaruApplication
        setContent {
            NaruTheme {
                NaruApp(app)
            }
        }
    }
}

@Composable
private fun NaruApp(app: NaruApplication) {
    val campingViewModel: CampingViewModel = viewModel(
        factory = viewModelFactory { initializer { CampingViewModel(
                app.vehicleGateway,
                app.siteRepository,
                app.nightSound,
                app.morningAlarm,
                app.narrationRepository,
                app.voiceGuide,
                app.recordStore,
                app.intentRepository,
                app.speechInput,
                app.locationGateway,
            ) } },
    )

    // 처음 시작하기(권한 동의)와 위치 권한 요청 (이슈 #44)
    var onboardingDone by remember { mutableStateOf(app.onboardingDone) }
    var locationGranted by remember { mutableStateOf(app.locationGateway.hasPermission()) }
    var locateAfterGrant by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted = granted
        app.onboardingDone = true
        onboardingDone = true
        if (granted && locateAfterGrant) campingViewModel.locate()
        locateAfterGrant = false
    }
    val requestLocation = { locateAfter: Boolean ->
        if (app.locationGateway.hasPermission()) {
            if (locateAfter) campingViewModel.locate()
        } else {
            locateAfterGrant = locateAfter
            permissionLauncher.launch(LocationGateway.PERMISSION)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // 세션이 없으면(대기) 홈, 있으면 차박 화면 (홈의 [시작]이 바로 차박을 시작한다, 이슈 #33)
            val state by campingViewModel.state.collectAsStateWithLifecycle()
            when {
                !onboardingDone -> OnboardingScreen(
                    locationGranted = locationGranted,
                    onLater = {
                        app.onboardingDone = true
                        onboardingDone = true
                    },
                    onContinue = {
                        if (locationGranted) {
                            app.onboardingDone = true
                            onboardingDone = true
                        } else {
                            requestLocation(false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                else -> when (state.stage) {
                CampingStage.IDLE -> HomeScreen(
                    state = HomeState(
                        batteryPercent = state.batteryPercent,
                        cabinTempC = state.cabinTempC,
                        outsideTempC = state.outsideTempC,
                        lastRecord = state.records.maxByOrNull { it.nightStartedAt },
                        message = state.message,
                        lastEndReason = state.lastEndReason,
                        restore = state.restore,
                    ),
                    onStartCamping = campingViewModel::start,
                    onDismissMessage = campingViewModel::dismissMessage,
                    onShown = campingViewModel::refreshHomeStatus,
                    modifier = Modifier.weight(1f),
                )

                // 피그마 v2 도착 안내 (이슈 #35)
                CampingStage.ARRIVAL_CHECK -> ArrivalScreen(
                    sites = state.sites,
                    sitesUnavailable = state.sitesUnavailable,
                    loadingSite = state.loadingSite,
                    arrival = state.arrival,
                    narration = state.narration,
                    locating = state.locating,
                    askPlaceType = state.hereConditions != null,
                    actions = ArrivalActions(
                        onLocate = { requestLocation(true) },
                        onChoosePlaceType = campingViewModel::choosePlaceType,
                        onBack = campingViewModel::back,
                        onSelectSite = campingViewModel::selectSite,
                        onRetrySites = campingViewModel::loadSites,
                        onChangeSite = campingViewModel::changeSite,
                        onRetry = campingViewModel::retryConditions,
                        onConfirm = campingViewModel::confirmArrival,
                    ),
                    modifier = Modifier.weight(1f),
                )

                // 피그마 v2 취침 준비 2/3 → 밤 분위기 3/3 (이슈 #37)
                CampingStage.BEDTIME_PREP -> if (state.bedtimeStep == BedtimeStep.SETTINGS) {
                    BedtimeScreen(
                        bedtime = state.bedtime,
                        minTempC = state.arrival?.conditions?.weather?.minTempC,
                        batteryPercent = state.batteryPercent,
                        cabinTempC = state.cabinTempC,
                        assistant = state.assistant,
                        voiceInputAvailable = true,
                        voiceOn = state.voiceOn,
                        actions = BedtimeScreenActions(
                            onBack = campingViewModel::back,
                            onClose = campingViewModel::cancel,
                            onSetTemperature = campingViewModel::setTemperature,
                            onSetSunriseWake = campingViewModel::setSunriseWake,
                            onSetWakeTime = campingViewModel::setWakeTime,
                            onNext = campingViewModel::goToThemeStep,
                            onToggleVoice = campingViewModel::toggleVoice,
                            assistant = AssistantActions(
                                onAsk = campingViewModel::ask,
                                onToggleListening = campingViewModel::toggleListening,
                                onApprove = campingViewModel::approveChanges,
                                onReject = campingViewModel::rejectChanges,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ThemeScreen(
                        bedtime = state.bedtime,
                        recommended = state.arrival?.site?.placeType,
                        actions = ThemeScreenActions(
                            onBack = campingViewModel::back,
                            onClose = campingViewModel::cancel,
                            onSelectTheme = campingViewModel::selectTheme,
                            onStartNight = campingViewModel::startNight,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                // 피그마 v2 차박 중 (이슈 #39)
                CampingStage.NIGHT -> NightScreen(
                    state = state,
                    voiceInputAvailable = true,
                    actions = NightScreenActions(
                        onToggleSound = campingViewModel::toggleSound,
                        onEndNow = campingViewModel::endNow,
                        onToggleVoice = campingViewModel::toggleVoice,
                        assistant = AssistantActions(
                            onAsk = campingViewModel::ask,
                            onToggleListening = campingViewModel::toggleListening,
                            onApprove = campingViewModel::approveChanges,
                            onReject = campingViewModel::rejectChanges,
                        ),
                    ),
                    modifier = Modifier.weight(1f),
                )

                // 피그마 v2 기상 → 아침 브리핑 (이슈 #41)
                CampingStage.MORNING -> MorningScreen(
                    state = state,
                    actions = MorningScreenActions(
                        onWakeUp = campingViewModel::wakeUp,
                        onFeedback = campingViewModel::setFeedback,
                        onFinish = campingViewModel::finishSession,
                    ),
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    CampingScreen(
                        state = state,
                        actions = CampingActions(
                            onStart = campingViewModel::start,
                            onSelectSite = campingViewModel::selectSite,
                            onRetrySites = campingViewModel::loadSites,
                            onConfirmArrival = campingViewModel::confirmArrival,
                            bedtime = BedtimeActions(
                                onAdjustTemperature = campingViewModel::adjustTemperature,
                                onAdjustWakeTime = campingViewModel::adjustWakeTime,
                                onWakeAtSunrise = campingViewModel::wakeAtSunrise,
                                onSelectTheme = campingViewModel::selectTheme,
                                onStartNight = campingViewModel::startNight,
                            ),
                            onToggleSound = campingViewModel::toggleSound,
                            onToggleVoice = campingViewModel::toggleVoice,
                            assistant = AssistantActions(
                                onAsk = campingViewModel::ask,
                                onToggleListening = campingViewModel::toggleListening,
                                onApprove = campingViewModel::approveChanges,
                                onReject = campingViewModel::rejectChanges,
                            ),
                            voiceInputAvailable = true,
                            morning = MorningActions(
                                onWakeUp = campingViewModel::wakeUp,
                                onFinish = campingViewModel::finishSession,
                            ),
                            onDismissMessage = campingViewModel::dismissMessage,
                            onBack = campingViewModel::cancel,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            }

            if (BuildConfig.DEBUG) {
                VehicleDebugPanel(
                    fake = app.vehicleGateway as? FakeVehicleGateway,
                    onSimulateWakeTime = campingViewModel::simulateWakeTime,
                    onAddSampleRecords = campingViewModel::addSampleRecords,
                    onClearRecords = campingViewModel::clearRecords,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
