package ai.naru.app.ui.camping

import ai.naru.app.camping.AssistantState
import ai.naru.app.camping.CampingStage
import ai.naru.app.camping.intent.ProposedChange
import ai.naru.app.camping.intent.Turn
import ai.naru.app.camping.intent.TurnRole
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

/** 발화 해석에서 누를 수 있는 동작 */
data class AssistantActions(
    val onAsk: (String) -> Unit = {},
    val onToggleListening: () -> Unit = {},
    val onApprove: () -> Unit = {},
    val onReject: () -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 단계별 예시 요청 */
private fun examples(stage: CampingStage): List<String> = when (stage) {
    CampingStage.BEDTIME_PREP -> listOf("조금 더 따뜻하게", "7시 반에 깨워줘", "일출에 맞춰 깨워줘", "숲 테마로 바꿔줘")
    CampingStage.NIGHT -> listOf("좀 추워", "조금 시원하게", "소리 꺼줘", "음성 안내 꺼줘")
    else -> emptyList()
}

/** 나루에게 말하기: 입력칸·마이크·예시, 최근 대화, 승인 대기 중인 제안. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPanel(
    stage: CampingStage,
    assistant: AssistantState,
    voiceInputAvailable: Boolean,
    actions: AssistantActions,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(stage) { mutableStateOf("") }
    val send = {
        actions.onAsk(input)
        input = ""
    }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "나루에게 말하기", style = MaterialTheme.typography.titleLarge)

            for (turn in assistant.turns.takeLast(VISIBLE_TURNS)) TurnLine(turn)
            assistant.partial?.let { Text(text = "듣는 중: $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
            if (assistant.busy) Text(text = "생각하는 중…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            assistant.notice?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }

            if (assistant.pending.isNotEmpty()) PendingChanges(assistant.pending, actions)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(MAX_INPUT) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: 조금 더 따뜻하게") },
                    singleLine = true,
                    enabled = !assistant.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                Button(onClick = send, enabled = input.isNotBlank() && !assistant.busy) { Text("보내기") }
                if (voiceInputAvailable) {
                    FilledTonalButton(onClick = actions.onToggleListening, enabled = !assistant.busy) {
                        Text(if (assistant.listening) "그만 듣기" else "말하기")
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (example in examples(stage)) {
                    AssistChip(onClick = { actions.onAsk(example) }, enabled = !assistant.busy, label = { Text(example) })
                }
            }
        }
    }
}

@Composable
private fun TurnLine(turn: Turn) {
    val (who, color) = when (turn.role) {
        TurnRole.USER -> "나" to MaterialTheme.colorScheme.onSurface
        TurnRole.ASSISTANT -> "나루" to MaterialTheme.colorScheme.primary
    }
    Text(text = "$who: ${turn.text}", style = MaterialTheme.typography.bodyLarge, color = color)
}

@Composable
private fun PendingChanges(changes: List<ProposedChange>, actions: AssistantActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (change in changes) Text(text = change.describe(), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = actions.onApprove) { Text("바꾸기") }
                OutlinedButton(onClick = actions.onReject) { Text("취소") }
            }
        }
    }
}

/** 지금 값 → 바뀔 값 */
fun ProposedChange.describe(): String = when (this) {
    is ProposedChange.Temperature -> "설정 온도 ${from.formatTemp()}°C → ${to.formatTemp()}°C"
    is ProposedChange.WakeTime -> "기상 시각 ${from.format(hhmm)} → ${to.format(hhmm)}${if (sunrise) " (일출)" else ""}"
    is ProposedChange.Theme -> "밤 테마 ${from.themeLabel()} → ${to.themeLabel()}"
    is ProposedChange.Sound -> "테마 소리 ${if (on) "켜기" else "끄기"}"
    is ProposedChange.Voice -> "음성 안내 ${if (on) "켜기" else "끄기"}"
}

private const val VISIBLE_TURNS = 4
private const val MAX_INPUT = 100

@Preview(widthDp = 520)
@Composable
private fun AssistantPanelPreview() {
    NaruTheme {
        AssistantPanel(
            stage = CampingStage.BEDTIME_PREP,
            assistant = AssistantState(
                available = true,
                turns = listOf(Turn(TurnRole.USER, "조금 더 따뜻하게"), Turn(TurnRole.ASSISTANT, "설정 온도를 23도로 바꿀까요?")),
                pending = listOf(ProposedChange.Temperature(22f, 23f)),
            ),
            voiceInputAvailable = true,
            actions = AssistantActions(),
        )
    }
}
