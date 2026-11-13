package ai.naru.app.ui.camping

import ai.naru.app.camping.CampingStage
import ai.naru.app.camping.NarrationState
import ai.naru.app.camping.narration.NarrationMoment
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 지금 단계에 맞는 멘트가 있으면 보여준다 (도착 → 도착 단계, 잠들기 전 → 밤, 기상 → 아침). */
fun NarrationState.belongsTo(stage: CampingStage): Boolean = when (moment) {
    NarrationMoment.ARRIVAL -> stage == CampingStage.ARRIVAL_CHECK
    NarrationMoment.BEDTIME -> stage == CampingStage.NIGHT
    NarrationMoment.MORNING -> stage == CampingStage.MORNING
}

/** 나루의 멘트와 음성 안내 켜기/끄기. */
@Composable
fun NarrationCard(narration: NarrationState, voiceOn: Boolean, onToggleVoice: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val source = when {
                    narration.pending -> "나루 · 안내를 다듬는 중"
                    narration.fromAi -> "나루 · AI 안내"
                    else -> "나루 · 기본 안내"
                }
                Text(text = source, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "음성 안내", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = voiceOn, onCheckedChange = { onToggleVoice() })
                }
            }
            Text(text = narration.text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview(widthDp = 1280)
@Composable
private fun NarrationCardPreview() {
    NaruTheme {
        NarrationCard(
            narration = NarrationState(NarrationMoment.BEDTIME, "오늘 밤은 숲 테마로 쉬어요. 설정 온도는 22도이고, 07:00에 깨워 드릴게요. 편히 쉬세요.", fromAi = true, pending = false),
            voiceOn = true,
            onToggleVoice = {},
        )
    }
}
