package ai.naru.app

import ai.naru.app.camping.CampingViewModel
import ai.naru.app.ui.camping.BedtimeActions
import ai.naru.app.ui.camping.CampingActions
import ai.naru.app.ui.camping.CampingScreen
import ai.naru.app.ui.camping.MorningActions
import ai.naru.app.ui.debug.VehicleDebugPanel
import ai.naru.app.ui.home.ModeSelectScreen
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

/** 지금 보이는 화면. 화면이 2개뿐이라 내비게이션 라이브러리 없이 상태로 바꾼다. */
private enum class Screen { MODE_SELECT, CAMPING }

@Composable
private fun NaruApp(app: NaruApplication) {
    var screen by rememberSaveable { mutableStateOf(Screen.MODE_SELECT) }
    val campingViewModel: CampingViewModel = viewModel(
        factory = viewModelFactory { initializer { CampingViewModel(app.vehicleGateway, app.siteRepository, app.nightSound, app.morningAlarm) } },
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            when (screen) {
                Screen.MODE_SELECT -> ModeSelectScreen(
                    onCampingSelected = { screen = Screen.CAMPING },
                    modifier = Modifier.weight(1f),
                )

                Screen.CAMPING -> {
                    val state by campingViewModel.state.collectAsStateWithLifecycle()
                    CampingScreen(
                        state = state,
                        actions = CampingActions(
                            onStart = campingViewModel::start,
                            onSelectSite = campingViewModel::selectSite,
                            onConfirmArrival = campingViewModel::confirmArrival,
                            bedtime = BedtimeActions(
                                onAdjustTemperature = campingViewModel::adjustTemperature,
                                onAdjustWakeTime = campingViewModel::adjustWakeTime,
                                onWakeAtSunrise = campingViewModel::wakeAtSunrise,
                                onSelectTheme = campingViewModel::selectTheme,
                                onStartNight = campingViewModel::startNight,
                            ),
                            onToggleSound = campingViewModel::toggleSound,
                            morning = MorningActions(
                                onWakeUp = campingViewModel::wakeUp,
                                onFinish = campingViewModel::finishSession,
                            ),
                            onDismissMessage = campingViewModel::dismissMessage,
                            onBack = { screen = Screen.MODE_SELECT },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                VehicleDebugPanel(
                    fake = app.vehicleGateway as? FakeVehicleGateway,
                    onSimulateWakeTime = campingViewModel::simulateWakeTime,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
