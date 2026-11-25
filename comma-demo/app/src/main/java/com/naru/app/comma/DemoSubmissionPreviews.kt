package com.naru.app.comma

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

private val filmingUi=RestUiState(isDemo=true,demoEnabled=true,vehicle=DemoVehicleState().vehicle,
    demoVehicle=DemoVehicleState(),nowMillis=1_800_000_000_000)
private val filmingActions=RestUiActions({}, {}, {}, {}, {}, {}, {})

@Preview(name="Filming settings wide",widthDp=1100,heightDp=780)
@Preview(name="Filming settings split",widthDp=560,heightDp=790)
@Composable
internal fun CommaFilmingSettingsPreview() {
    Column {
        CommaDemoControls(filmingUi,{},{},{},{},{},{},{})
        Box(Modifier.weight(1f)) {CommaSettingsScreen(filmingUi,filmingActions,{})}
    }
}
@Preview(name="Filming alarm",widthDp=1100,heightDp=780)
@Composable
internal fun CommaFilmingAlarmPreview() {
    val ui=filmingUi.copy(session=RestSession(phase=RestPhase.WAKING,plannedMinutes=15,
        expectedEndMillis=1_800_000_900_000,confirmationRemainingMillis=30_000))
    Column {
        CommaDemoControls(ui,{},{},{},{},{},{},{})
        Box(Modifier.weight(1f)) {CommaWakingScreen(ui,filmingActions,{})}
    }
}
