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
    val onConfirmArrival: () -> Unit = {},
    val onStartNight: () -> Unit = {},
    val onFinishRestore: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** 차박 진행 화면. 단계마다 내용은 자리만 잡아 두고, 다음 이슈에서 채운다. */
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
        )

        CampingStage.BEDTIME_PREP -> {
            // TODO(다음 이슈): 추천 온도, 기상 시각·일출 안내, 아침 예상 잔량, 테마 선택
            Placeholder("온도·기상 시각·테마 설정이 여기에 표시돼요.")
            Button(onClick = actions.onStartNight) { Text("차박 시작하기") }
        }

        CampingStage.NIGHT -> {
            // TODO(다음 이슈): 장소 테마(조명·음향), 기상 예정 시각, 배터리 하한 경고
            Placeholder("밤 테마가 적용되는 화면이에요. 기상 시각이나 배터리 하한에서 아침으로 넘어가요.")
        }

        CampingStage.MORNING -> {
            state.lastEndReason?.let {
                Text(text = "기상 이유: ${it.label()}", style = MaterialTheme.typography.bodyLarge)
            }
            // TODO(다음 이슈): 알람, 아침 날씨·차량 상태 브리핑, 설정 복구 실행
            Placeholder("아침 브리핑과 설정 복구가 여기에 표시돼요.")
            Button(onClick = actions.onFinishRestore) { Text("차박 종료") }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
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
