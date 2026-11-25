package com.naru.app.comma

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Lifecycle collection, navigation and dialogs only. Presentational cards do not know ViewModel. */
@Composable
fun CommaRoute(model: RestViewModel) {
    val ui by model.uiState.collectAsStateWithLifecycle()
    var entered by rememberSaveable { mutableStateOf(false) }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val finishedHome: () -> Unit = { if(model.prepareNextRest()) entered=false }
    val newRest: () -> Unit = { if(model.prepareNextRest()) entered=true }
    val back: () -> Unit = {
        if(ui.vehicleIssue!=null) model.dismissVehicleIssue()
        else if(ui.active) confirmExit=true
        else if(ui.session.phase==RestPhase.FINISHED) finishedHome()
        else entered=false
    }
    BackHandler(entered, back)
    val alarmOnly=ui.isDemo && ui.demoVehicle?.mode==DemoRestMode.ALARM_ONLY
    CabinEffects(ui.active, ui.session.phase,
        if(alarmOnly)ui.cabin.copy(sound=RestSound.SILENT) else ui.cabin,manageWindow=!alarmOnly)
    if (!entered) {
        CommaHomeScreen(ui, onEnter = { entered = true; model.onEnterComma() })
    } else {
        Column(Modifier.fillMaxSize()) {
            if(ui.vehicleIssue==null && !ui.isDemo) CommaVehicleStatus(ui,model::showVehicleInfo)
            if (ui.demoEnabled) {
                CommaDemoControls(ui, model::startDemo, model::exitDemo,
                    model::demoLastThirtySeconds, model::demoDeadline, model::demoExpireConfirmation,
                    model::showDemoException,model::setDemoEnvironment)
            }
            Box(Modifier.weight(1f)) {
                if(ui.vehicleIssue!=null) CommaExceptionScreen(ui,model::handleVehicleIssueAction)
                else CommaScreen(ui, model.actions, back, model.wakingActions, finishedHome, newRest)
            }
        }
    }

    // At most one dialog. Confirmation may expire while another dialog is visible.
    when {
        ui.message != null -> AlertDialog(onDismissRequest = model::clearMessage,
            title = { Text("안내") }, text = { Text(ui.message!!) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("확인") } })
        confirmExit -> AlertDialog(onDismissRequest = { confirmExit = false },
            title = { Text("휴식을 종료할까요?") },
            text = { Text(if (ui.isDemo) "데모를 종료하고 앱 설정을 복구합니다. 기록은 저장하지 않습니다."
                else "앱 설정을 복구하고 종료 기록을 저장 큐에 전달합니다.") },
            confirmButton = { TextButton(onClick = {
                model.actions.finish()
                if (ui.isDemo) model.exitDemo()
                confirmExit = false
                entered = false
            }) { Text("종료하고 나가기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 휴식") } })
    }
}

