package ai.naru.app.ui.camping

import ai.naru.app.camping.AssistantState
import ai.naru.app.camping.CampingUiState
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.intent.TurnRole
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruConfirmDialog
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 차박 중에서 누를 수 있는 동작 */
data class NightScreenActions(
    val onToggleSound: () -> Unit = {},
    val onEndNow: () -> Unit = {},
    val assistant: AssistantActions = AssistantActions(),
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 피그마 v2 "차박 밤 화면"과 "차박 종료 확인" (이슈 #39) */
@Composable
fun NightScreen(state: CampingUiState, voiceInputAvailable: Boolean, actions: NightScreenActions, modifier: Modifier = Modifier) {
    val colors = NaruTheme.colors
    var confirming by remember { mutableStateOf(false) }
    val settings = state.bedtime?.settings
    FigmaFrame(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel), verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            Text(text = "차박 중", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                NightCard(
                    wakeAt = state.wakeDateTime,
                    sunrise = state.bedtime?.sunrise?.format(hhmm),
                    theme = settings?.theme ?: PlaceType.BEACH,
                    modifier = Modifier.weight(1.9f).fillMaxHeight(),
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                    SideCard(
                        icon = NaruIcons.Thermometer,
                        label = "실내",
                        value = settings?.let { "${it.targetTempC.formatTemp()}°C 유지 중" } ?: "-",
                        note = state.temperatureApply?.problem("설정 온도"),
                        modifier = Modifier.weight(1f),
                    )
                    SideCard(
                        icon = NaruIcons.Tide,
                        label = "${settings?.theme?.themeLabel() ?: ""} 테마",
                        value = when {
                            !state.soundOn -> "소리 꺼져 있어요"
                            state.soundPlaying -> "소리 켜져 있어요"
                            else -> "다른 소리가 재생 중이에요"
                        },
                        note = state.lightApply?.problem("테마 조명"),
                        modifier = Modifier.weight(1f),
                    )
                    SideCard(
                        icon = NaruIcons.Battery,
                        label = "배터리",
                        value = "${state.batteryPercent?.let { "$it%" } ?: "알 수 없음"} (하한 ${NightThresholds.BATTERY_LIMIT_PERCENT}%)",
                        note = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AssistantStrip(state.assistant, actions.assistant)
            NaruBottomBar(
                note = if (state.restoring) "차량 설정을 원래대로 되돌리는 중이에요…" else "화면 밝기는 차량의 주간 또는 야간 모드를 따라요",
                noteIcon = NaruIcons.Moon,
            ) {
                if (state.assistant.available && voiceInputAvailable) {
                    NaruSecondaryButton(
                        text = if (state.assistant.listening) "그만 듣기" else "나루에게 말하기",
                        onClick = actions.assistant.onToggleListening,
                        enabled = !state.assistant.busy,
                    )
                }
                NaruSecondaryButton(text = if (state.soundOn) "소리 지금 끄기" else "소리 켜기", onClick = actions.onToggleSound)
                NaruPrimaryButton(text = "차박 종료", onClick = { confirming = true }, enabled = !state.restoring)
            }
        }
        // 팝업은 피그마 프레임 안에서 열어야 1dp = 1 피그마 단위 밀도가 팝업으로 이어진다
        if (confirming) {
            NaruConfirmDialog(
                title = "차박을 끝낼까요?",
                message = "공조, 조명, 소리를 원래대로 되돌리고 ${settings?.wakeAt?.format(hhmm) ?: ""} 알람을 꺼요.",
                confirmText = "차박 종료",
                dismissText = "계속 자기",
                onConfirm = {
                    confirming = false
                    actions.onEndNow()
                },
                onDismiss = { confirming = false },
            )
        }
    }
}

/** 큰 밤 카드: 기상 알람 시각, 남은 시간, 밤하늘·달·별, 테마 그림 */
@Composable
private fun NightCard(wakeAt: LocalDateTime?, sunrise: String?, theme: PlaceType, modifier: Modifier) {
    val colors = NaruTheme.colors
    // 남은 시간은 30초마다 다시 계산한다
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    Box(modifier = modifier.clip(NaruShapes.card).background(colors.nightBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for ((fx, fy) in listOf(0.55f to 0.15f, 0.62f to 0.4f, 0.48f to 0.5f, 0.75f to 0.38f, 0.38f to 0.62f)) {
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2f, center = Offset(size.width * fx, size.height * fy))
            }
            val ink = Color.White.copy(alpha = 0.8f)
            if (theme == PlaceType.FOREST) {
                for ((cx, h) in listOf(0.2f to 0.28f, 0.34f to 0.4f, 0.8f to 0.32f)) {
                    val w = size.width * 0.12f
                    val path = Path().apply {
                        moveTo(size.width * cx, size.height * (1f - h))
                        lineTo(size.width * cx - w / 2, size.height)
                        lineTo(size.width * cx + w / 2, size.height)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = 0.18f))
                }
            } else {
                for ((row, from, to) in listOf(Triple(0.76f, 0.08f, 0.32f), Triple(0.72f, 0.38f, 0.66f), Triple(0.84f, 0.38f, 0.66f), Triple(0.8f, 0.7f, 0.94f))) {
                    val y = size.height * row
                    val start = size.width * from
                    val step = (size.width * (to - from)) / 4
                    val path = Path().apply {
                        moveTo(start, y)
                        for (i in 0 until 4) quadraticTo(start + step * i + step / 2, y + if (i % 2 == 0) -8f else 8f, start + step * (i + 1), y)
                    }
                    drawPath(path, ink, style = Stroke(width = 3f))
                }
            }
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 70.dp).size(64.dp)
                .clip(NaruShapes.pill).background(colors.moon),
        )
        Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "기상 알람", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text(text = wakeAt?.format(hhmm) ?: "--:--", style = MaterialTheme.typography.displayMedium, color = Color.White)
            wakeAt?.let {
                val left = Duration.between(now, it).coerceAtLeast(Duration.ZERO)
                val hours = left.toHours()
                val minutes = left.toMinutes() % 60
                val leftText = if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
                Text(
                    text = "$leftText 뒤에 깨워 드려요${sunrise?.let { s -> " (일출 $s)" } ?: ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun SideCard(icon: ImageVector, label: String, value: String, note: String?, modifier: Modifier) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NaruIconCircle(icon)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                Text(text = value, style = MaterialTheme.typography.titleMedium, color = colors.text)
                note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.warningIcon) }
            }
        }
    }
}

/** 나루에게 말하기 결과: 대화 중일 때만 하단 위에 나타나는 줄 (답변 + 승인 카드) */
@Composable
private fun AssistantStrip(assistant: AssistantState, actions: AssistantActions) {
    val colors = NaruTheme.colors
    val lastReply = assistant.turns.lastOrNull { it.role == TurnRole.ASSISTANT }?.text
    val message = when {
        assistant.listening -> assistant.partial?.let { "듣는 중: $it" } ?: "듣고 있어요. 말씀해 주세요."
        assistant.busy -> "생각하는 중…"
        assistant.notice != null -> assistant.notice
        lastReply != null && assistant.pending.isNotEmpty() -> lastReply
        else -> null
    } ?: return
    Row(
        modifier = Modifier.fillMaxWidth().clip(NaruShapes.card).background(colors.accentSurface).padding(horizontal = NaruSpacing.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = message, style = MaterialTheme.typography.titleMedium, color = if (assistant.notice != null) colors.warningIcon else colors.text)
            for (change in assistant.pending) Text(text = change.describe(), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        if (assistant.pending.isNotEmpty()) {
            NaruPrimaryButton(text = "바꾸기", onClick = actions.onApprove)
            NaruSecondaryButton(text = "취소", onClick = actions.onReject)
        }
    }
}

/** 차량 제어 결과 중 사용자에게 알려야 할 문제 (정상 반영이면 null) */
private fun ControlResult.problem(subject: String): String? = when (this) {
    ControlResult.Applied -> null
    ControlResult.Unverified -> "$subject 반영을 확인하지 못했어요"
    ControlResult.NotSupported -> "$subject 제어를 지원하지 않는 차량이에요"
    is ControlResult.Failed -> "$subject 변경에 실패했어요"
}
