package ai.naru.app.ui.camping

import ai.naru.app.camping.AssistantState
import ai.naru.app.camping.CampingUiState
import ai.naru.app.camping.ControlKind
import ai.naru.app.ui.components.CardTone
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.intent.TurnRole
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.camping.night.ThemeTimer
import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruConfirmDialog
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.components.NaruSwitch
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    /** 음성 안내 켜기/끄기 */
    val onToggleVoice: () -> Unit = {},
    val assistant: AssistantActions = AssistantActions(),
    /** 배터리 하한 경고 [확인] / [차박 종료] */
    val onDismissBatteryWarning: () -> Unit = {},
    val onEndForBattery: () -> Unit = {},
    /** 제어 실패 [한 번 더 시도] / [이대로 쉬기] */
    val onRetryControls: () -> Unit = {},
    val onDismissControlAlert: () -> Unit = {},
    /** 테마 끄기 / 다시 켜기 / 조명 칩 (피그마 v2 갱신 "테마 끄기와 종료 경로") */
    val onTurnThemeOff: () -> Unit = {},
    val onTurnThemeOn: () -> Unit = {},
    val onToggleLight: () -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 피그마 v2 "차박 밤 화면", "테마 끈 뒤", "차박 종료 확인" (이슈 #39) */
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
                    themeOn = state.themeOn,
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
                    ThemeSideCard(state, actions, Modifier.weight(1f))
                    SideCard(
                        icon = NaruIcons.Battery,
                        label = "배터리",
                        value = if (state.batteryWarned) {
                            "하한 ${NightThresholds.BATTERY_LIMIT_PERCENT}% 도달"
                        } else {
                            "${state.batteryPercent?.let { "$it%" } ?: "알 수 없음"} (하한 ${NightThresholds.BATTERY_LIMIT_PERCENT}%)"
                        },
                        note = null,
                        modifier = Modifier.weight(1f),
                        warning = state.batteryWarned,
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
                Text(text = "음성 안내", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                NaruSwitch(checked = state.voiceOn, onCheckedChange = { actions.onToggleVoice() }, modifier = Modifier.align(Alignment.CenterVertically))
                if (state.themeOn) NaruSecondaryButton(text = "테마 끄기", onClick = actions.onTurnThemeOff, enabled = !state.restoring)
                NaruPrimaryButton(text = "차박 종료", onClick = { confirming = true }, enabled = !state.restoring)
            }
        }
        // 팝업은 피그마 프레임 안에서 열어야 1dp = 1 피그마 단위 밀도가 팝업으로 이어진다
        // 한 번에 하나만: 종료 확인 > 배터리 하한 > 제어 실패
        if (!confirming && state.batteryWarning) {
            val temps = listOfNotNull(
                state.cabinTempC?.let { "실내 ${it.formatTemp()}°C" },
                state.outsideTempC?.let { "실외 ${it.formatTemp()}°C" },
            ).joinToString(", ")
            NaruConfirmDialog(
                title = "배터리가 ${NightThresholds.BATTERY_LIMIT_PERCENT}%까지 내려왔어요",
                message = "공조를 계속 켜 두면 아침에 쓸 수 있는 배터리가 더 줄어요." + if (temps.isNotEmpty()) " 지금 ${temps}예요." else "",
                confirmText = "차박 종료",
                dismissText = "확인",
                onConfirm = actions.onEndForBattery,
                onDismiss = actions.onDismissBatteryWarning,
            )
        } else if (!confirming && state.controlAlert.isNotEmpty()) {
            val subject = when {
                state.controlAlert.size > 1 -> "공조와 조명을"
                ControlKind.TEMPERATURE in state.controlAlert -> "공조를"
                else -> "조명을"
            }
            val where = if (ControlKind.TEMPERATURE in state.controlAlert) "하단 바의 공조에서" else "차량 설정에서"
            NaruConfirmDialog(
                title = "$subject 바꾸지 못했어요",
                message = "나루 화면, 소리, 알람은 적용됐어요. 한 번 더 시도해도 안 되면 $where 직접 맞춰 주세요.",
                confirmText = "한 번 더 시도",
                dismissText = "이대로 쉬기",
                onConfirm = actions.onRetryControls,
                onDismiss = actions.onDismissControlAlert,
            )
        }
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
private fun NightCard(wakeAt: LocalDateTime?, sunrise: String?, theme: PlaceType, themeOn: Boolean, modifier: Modifier) {
    val colors = NaruTheme.colors
    // 남은 시간은 30초마다 다시 계산한다
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    // 테마를 끄면 나루 화면을 어둡게 (밤하늘·달·물결 없이 시각만)
    val background = if (themeOn) Modifier.background(colors.nightBrush) else Modifier.background(Color(0xFF151618))
    Box(modifier = modifier.clip(NaruShapes.card).then(background)) {
        if (themeOn) Canvas(modifier = Modifier.fillMaxSize()) {
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
        if (themeOn) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 70.dp).size(64.dp)
                    .clip(NaruShapes.pill).background(colors.moon),
            )
        }
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
private fun SideCard(icon: ImageVector, label: String, value: String, note: String?, modifier: Modifier, warning: Boolean = false) {
    val colors = NaruTheme.colors
    val tone = if (warning) CardTone.WARNING else CardTone.DEFAULT
    NaruCard(modifier = modifier.fillMaxWidth(), tone = tone) {
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NaruIconCircle(icon, tone = tone)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                Text(text = value, style = MaterialTheme.typography.titleMedium, color = colors.text)
                note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.warningIcon) }
            }
        }
    }
}

/** 테마 카드: "해변 테마 · 42분 뒤 꺼져요 · [소리][조명]", 끄면 "꺼짐 · [다시 켜기]" */
@Composable
private fun ThemeSideCard(state: CampingUiState, actions: NightScreenActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(15_000)
            value = LocalDateTime.now()
        }
    }
    val settings = state.bedtime?.settings
    val value = when {
        !state.themeOn -> "꺼짐"
        state.themeEndsAt != null -> "${ThemeTimer.minutesLeft(now, state.themeEndsAt)}분 뒤 꺼져요"
        else -> "켜져 있어요"
    }
    val note = when {
        !state.themeOn -> null
        state.soundOn && !state.soundPlaying -> "다른 소리가 재생 중이라 테마 소리를 못 틀었어요"
        state.lightOn -> state.lightApply?.problem("테마 조명")
        else -> null
    }
    NaruCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NaruIconCircle(NaruIcons.Tide)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "${settings?.theme?.themeLabel() ?: ""} 테마", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                    Text(text = value, style = MaterialTheme.typography.titleMedium, color = colors.text)
                    note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.warningIcon) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.themeOn) {
                        ToggleChip("소리", state.soundOn, actions.onToggleSound)
                        if (state.bedtime?.lightSupported != false) ToggleChip("조명", state.lightOn, actions.onToggleLight)
                    } else {
                        ToggleChip("다시 켜기", on = false, onClick = actions.onTurnThemeOn, enabled = !state.restoring)
                    }
                }
            }
        }
    }
}

/** 켜짐/꺼짐 칩: 켜지면 흰 바탕 테두리, 꺼지면 회색 바탕에 흐린 글자 */
@Composable
private fun ToggleChip(text: String, on: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = NaruTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) colors.text else colors.textSecondary,
        modifier = Modifier.clip(NaruShapes.button)
            .background(if (on) colors.card else colors.secondaryButton)
            .border(1.dp, if (on) colors.segment else Color.Transparent, NaruShapes.button)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
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
