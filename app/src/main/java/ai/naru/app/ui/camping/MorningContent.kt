package ai.naru.app.ui.camping

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.MorningState
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.morning.MorningBriefing
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 기상 단계에서 누를 수 있는 동작. */
data class MorningActions(
    /** "일어났어요": 알람 끄기 + 설정 복구 */
    val onWakeUp: () -> Unit = {},
    /** "차박 마치기": 복구가 끝난 뒤 세션 종료 */
    val onFinish: () -> Unit = {},
)

/** 기상 단계: 알람 끄기, 아침 브리핑, 설정 복구 결과. */
@Composable
fun MorningContent(
    morning: MorningState?,
    reason: EndReason?,
    restoring: Boolean,
    restore: RestoreReport?,
    actions: MorningActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "좋은 아침이에요", style = MaterialTheme.typography.headlineMedium)

        val briefing = morning?.briefing
        when {
            briefing != null -> BriefingCard(briefing)
            reason == EndReason.BATTERY_LOW ->
                Text(text = "배터리가 하한에 닿아 일찍 깨웠어요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
            else -> Text(text = "아침 소식을 준비하고 있어요.", style = MaterialTheme.typography.bodyLarge)
        }

        morning?.lightApply?.let {
            if (it != ControlResult.Applied) {
                Text(text = it.notice(subject = "아침 조명", applied = ""), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (morning?.alarmUnavailable == true && restore == null) {
            Text(text = "다른 소리가 재생 중이거나 알람음이 없어 알람을 울리지 못했어요.", style = MaterialTheme.typography.bodyLarge)
        }

        when {
            morning == null -> Unit
            restoring -> Text(text = "차량 설정을 원래대로 되돌리는 중이에요…", style = MaterialTheme.typography.titleLarge)
            restore != null -> {
                RestoreSummary(restore)
                Button(onClick = actions.onFinish) {
                    Text("차박 마치기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                }
            }
            else -> Button(onClick = actions.onWakeUp) {
                val label = if (morning.alarmRinging) "알람 끄고 일어났어요" else "일어났어요"
                Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun BriefingCard(briefing: MorningBriefing) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        briefing.warning?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
        }
        briefing.lines.forEach { Text(text = it, style = MaterialTheme.typography.titleLarge) }
    }
}

/** 복구 결과. 모두 되돌렸으면 한 줄, 아니면 항목마다 직접 확인을 안내한다. */
@Composable
fun RestoreSummary(report: RestoreReport) {
    val items = listOfNotNull(
        report.temperature?.let { "설정 온도" to it },
        report.ambient?.let { "앰비언트 조명" to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            items.isEmpty() -> Text(text = "되돌릴 차량 설정이 없어요.", style = MaterialTheme.typography.bodyLarge)
            !report.needsManualCheck -> Text(text = "차량 설정을 차박 전으로 되돌렸어요.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                Text(text = "일부 설정은 직접 확인이 필요해요.", style = MaterialTheme.typography.titleMedium)
                items.filter { it.second != ControlResult.Applied }.forEach { (subject, result) ->
                    Text(text = "· " + result.restoreNotice(subject), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun ControlResult.restoreNotice(subject: String): String = when (this) {
    ControlResult.Applied -> "$subject 복구 완료"
    ControlResult.Unverified -> "$subject: 되돌리기를 요청했지만 반영을 확인하지 못했어요."
    ControlResult.NotSupported -> "$subject: 이 차량은 제어를 지원하지 않아요. 직접 확인해 주세요."
    is ControlResult.Failed -> "$subject: 되돌리지 못했어요. 차량에서 직접 조절해 주세요."
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun MorningContentPreview() {
    NaruTheme {
        MorningContent(
            morning = MorningState(
                alarmRinging = true,
                lightApply = ControlResult.Applied,
                briefing = MorningBriefing(
                    warning = null,
                    lines = listOf("지금 바깥은 맑음, 8°C예요.", "배터리는 68% 남았어요. 밤사이 12%를 썼어요.", "차 안은 21.5°C예요."),
                    batteryUsedPercent = 12,
                ),
            ),
            reason = EndReason.WAKE_TIME,
            restoring = false,
            restore = null,
            actions = MorningActions(),
        )
    }
}
