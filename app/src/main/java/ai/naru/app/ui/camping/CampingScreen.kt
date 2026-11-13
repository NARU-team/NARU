package ai.naru.app.ui.camping

import ai.naru.app.camping.CampingStage
import ai.naru.app.camping.CampingUiState
import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.Site
import ai.naru.app.ui.theme.NaruTheme
import ai.naru.app.vehicle.Gear
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 화면에서 누를 수 있는 동작 묶음. 화면은 무엇을 할지 모르고 호출만 한다. */
data class CampingActions(
    val onStart: () -> Unit = {},
    val onSelectSite: (Site) -> Unit = {},
    val onRetrySites: () -> Unit = {},
    val onConfirmArrival: () -> Unit = {},
    val bedtime: BedtimeActions = BedtimeActions(),
    val onToggleSound: () -> Unit = {},
    val onToggleVoice: () -> Unit = {},
    val morning: MorningActions = MorningActions(),
    val onDismissMessage: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** 차박 진행 화면. 위에는 단계·차량 상태, 아래에는 단계별 내용을 그린다. */
@Composable
fun CampingScreen(
    state: CampingUiState,
    actions: CampingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "차박 모드 · ${state.stage.label()}", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "기어 ${state.gear.label()} · 배터리 ${state.batteryPercent?.let { "$it%" } ?: "알 수 없음"}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        state.message?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = actions.onDismissMessage) { Text("확인") }
        }

        Spacer(Modifier.height(8.dp))
        state.narration?.takeIf { it.belongsTo(state.stage) }?.let {
            NarrationCard(narration = it, voiceOn = state.voiceOn, onToggleVoice = actions.onToggleVoice)
        }
        StageContent(state = state, actions = actions)
    }
}

@Composable
private fun StageContent(state: CampingUiState, actions: CampingActions) {
    when (state.stage) {
        CampingStage.IDLE -> {
            state.lastEndReason?.let {
                Text(text = "지난 세션: ${it.label()}", style = MaterialTheme.typography.bodyLarge)
            }
            // 기어 이탈로 복구 후 바로 끝난 경우 등, 직접 확인할 설정이 남았으면 여기서도 알린다.
            state.restore?.takeIf { it.needsManualCheck }?.let { RestoreSummary(it) }
            Text(text = "주차(P) 상태에서 차박 모드를 시작할 수 있어요.", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = actions.onStart) { Text("차박 시작") }
                OutlinedButton(onClick = actions.onBack) { Text("모드 선택으로") }
            }
        }

        CampingStage.ARRIVAL_CHECK -> ArrivalContent(
            sites = state.sites,
            arrival = state.arrival,
            onSelectSite = actions.onSelectSite,
            onConfirm = actions.onConfirmArrival,
            sitesUnavailable = state.sitesUnavailable,
            onRetrySites = actions.onRetrySites,
        )

        CampingStage.BEDTIME_PREP -> BedtimeContent(bedtime = state.bedtime, actions = actions.bedtime)

        CampingStage.NIGHT -> NightContent(state = state, onToggleSound = actions.onToggleSound)

        CampingStage.MORNING -> MorningContent(
            morning = state.morning,
            reason = state.lastEndReason,
            restoring = state.restoring,
            restore = state.restore,
            actions = actions.morning,
        )
    }
}

private fun CampingStage.label() = when (this) {
    CampingStage.IDLE -> "대기"
    CampingStage.ARRIVAL_CHECK -> "도착"
    CampingStage.BEDTIME_PREP -> "취침 준비"
    CampingStage.NIGHT -> "밤"
    CampingStage.MORNING -> "기상"
}

private fun Gear.label() = when (this) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
    Gear.UNKNOWN -> "-"
}

private fun EndReason.label() = when (this) {
    EndReason.WAKE_TIME -> "기상 시각"
    EndReason.BATTERY_LOW -> "배터리 하한 도달"
    EndReason.GEAR_LEFT_PARK -> "기어가 P를 벗어나 종료됨"
    EndReason.COMPLETED -> "정상 종료"
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun CampingScreenPreview() {
    NaruTheme {
        CampingScreen(
            state = CampingUiState(stage = CampingStage.IDLE, gear = Gear.DRIVE, batteryPercent = 80, message = "주차(P) 상태에서만 차박 모드를 시작할 수 있습니다."),
            actions = CampingActions(),
        )
    }
}
