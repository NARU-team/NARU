package ai.naru.app

import ai.naru.app.camping.CampingViewModel
import ai.naru.app.ui.camping.BedtimeActions
import ai.naru.app.ui.camping.CampingActions
import ai.naru.app.ui.camping.CampingScreen
import ai.naru.app.ui.camping.AssistantActions
import ai.naru.app.ui.camping.MorningActions
import ai.naru.app.ui.debug.VehicleDebugPanel
import ai.naru.app.camping.CampingStage
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
            ) } },
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // 세션이 없으면(대기) 홈, 있으면 차박 화면 (홈의 [시작]이 바로 차박을 시작한다, 이슈 #33)
            val state by campingViewModel.state.collectAsStateWithLifecycle()
            when (state.stage) {
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
