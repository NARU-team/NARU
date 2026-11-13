package ai.naru.app.ui.camping

import ai.naru.app.camping.AssistantState
import ai.naru.app.camping.BedtimeState
import ai.naru.app.camping.TempForecast
import ai.naru.app.camping.bedtime.BedtimeTexts
import ai.naru.app.camping.bedtime.FeedbackRules
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.intent.TurnRole
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.ui.components.BadgeTone
import ai.naru.app.ui.components.NaruBadge
import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruChip
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.components.NaruStepHeader
import ai.naru.app.ui.components.NaruSwitch
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 취침 준비 2/3에서 누를 수 있는 동작 */
data class BedtimeScreenActions(
    val onBack: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onSetTemperature: (Float) -> Unit = {},
    val onSetSunriseWake: (Boolean) -> Unit = {},
    val onSetWakeTime: (LocalTime) -> Unit = {},
    val onNext: () -> Unit = {},
    val assistant: AssistantActions = AssistantActions(),
    /** 음성 안내 켜기/끄기 (이슈 #44: 새 화면에서 빠졌던 스위치) */
    val onToggleVoice: () -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 나루 제안 패널의 요청 칩 (발화 해석으로 보낸다) */
private val SUGGESTION_CHIPS = listOf("더 따뜻하게", "배터리 아끼기", "7시에 깨워줘")

/** 피그마 v2 "취침 준비 2/3" (이슈 #37) */
@Composable
fun BedtimeScreen(
    bedtime: BedtimeState?,
    minTempC: Float?,
    batteryPercent: Int?,
    cabinTempC: Float?,
    assistant: AssistantState,
    voiceInputAvailable: Boolean,
    actions: BedtimeScreenActions,
    modifier: Modifier = Modifier,
    voiceOn: Boolean = true,
) {
    FigmaFrame(modifier = modifier.fillMaxSize().background(NaruTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NaruStepHeader(title = "취침 준비 2/3", onBack = actions.onBack, onClose = actions.onClose)
            if (bedtime == null) {
                Text(text = "오늘 밤 설정을 준비하고 있어요.", style = MaterialTheme.typography.bodyLarge, color = NaruTheme.colors.textSecondary)
                return@Column
            }
            SuggestionPanel(bedtime, minTempC, batteryPercent, assistant, voiceInputAvailable, actions.assistant, voiceOn, actions.onToggleVoice)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                TemperatureCard(bedtime, cabinTempC, actions.onSetTemperature, Modifier.weight(1.9f).fillMaxHeight())
                WakeCard(bedtime, actions, Modifier.weight(1f).fillMaxHeight())
            }
            NaruBottomBar(
                note = if (bedtime.forecast != null) "예상치는 실제와 다를 수 있어요. 이번 기록은 다음 예상에 반영돼요."
                else "이번 차박을 기록해 두면 다음부터 아침 배터리를 예상해 드려요.",
                noteIcon = NaruIcons.Check,
            ) {
                NaruPrimaryButton(text = "밤 분위기 고르기", onClick = actions.onNext)
            }
        }
    }
}

/**
 * 나루 제안: 규칙으로 만든 추천 문장 + 칩. 대화를 시작하면 나루의 마지막 답변과 승인 카드를 보여준다.
 * 칩과 [나루에게 말하기]는 발화 해석으로 이어지고, 바뀌는 값은 [바꾸기]를 눌러야 적용된다.
 */
@Composable
private fun SuggestionPanel(
    bedtime: BedtimeState,
    minTempC: Float?,
    batteryPercent: Int?,
    assistant: AssistantState,
    voiceInputAvailable: Boolean,
    actions: AssistantActions,
    voiceOn: Boolean,
    onToggleVoice: () -> Unit,
) {
    val colors = NaruTheme.colors
    val lastReply = assistant.turns.lastOrNull { it.role == TurnRole.ASSISTANT }?.text
    val message = when {
        assistant.listening -> assistant.partial?.let { "듣는 중: $it" } ?: "듣고 있어요. 말씀해 주세요."
        assistant.busy -> "생각하는 중…"
        assistant.notice != null -> assistant.notice
        lastReply != null -> lastReply
        else -> BedtimeTexts.suggestion(minTempC, bedtime.recommendation, bedtime.forecast, bedtime.feedbackHint)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(NaruShapes.card).background(colors.accentSurface).padding(NaruSpacing.card),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(NaruShapes.pill).background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(NaruIcons.Sparkle, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "나루 제안", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                Text(text = message, style = MaterialTheme.typography.titleMedium, color = if (assistant.notice != null) colors.warningIcon else colors.text)
            }
            Text(text = "음성 안내", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            NaruSwitch(checked = voiceOn, onCheckedChange = { onToggleVoice() })
        }
        if (assistant.pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    for (change in assistant.pending) Text(text = change.describe(), style = MaterialTheme.typography.titleMedium, color = colors.text)
                }
                NaruPrimaryButton(text = "바꾸기", onClick = actions.onApprove)
                NaruSecondaryButton(text = "취소", onClick = actions.onReject)
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            minTempC?.let { NaruChip("실외 최저 ${it.roundToInt()}°C", icon = NaruIcons.Moon) }
            bedtime.feedbackHint?.let { NaruChip(FeedbackRules.chipText(it), icon = NaruIcons.Check) }
            batteryPercent?.let { NaruChip("배터리 $it%", icon = NaruIcons.Battery) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (assistant.available) {
                for (chip in SUGGESTION_CHIPS) NaruSecondaryButton(text = chip, onClick = { actions.onAsk(chip) }, enabled = !assistant.busy)
            }
            Spacer(Modifier.weight(1f))
            if (assistant.available && voiceInputAvailable) {
                NaruSecondaryButton(text = if (assistant.listening) "그만 듣기" else "나루에게 말하기", onClick = actions.onToggleListening, enabled = !assistant.busy)
            }
        }
    }
}

@Composable
private fun TemperatureCard(bedtime: BedtimeState, cabinTempC: Float?, onSetTemperature: (Float) -> Unit, modifier: Modifier) {
    val colors = NaruTheme.colors
    val range = bedtime.vehicleTempRange ?: (15f..30f)
    NaruCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NaruIconCircle(NaruIcons.Thermometer)
                Text(text = "밤새 유지할 온도", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                cabinTempC?.let { NaruBadge("지금 실내 ${it.roundToInt()}°C", BadgeTone.NEUTRAL) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(12.dp))
                    NaruBadge("나루 추천 ${bedtime.recommendation.celsius.formatTemp()}°C", BadgeTone.ACCENT)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(NaruShapes.button).border(1.5.dp, colors.segment, NaruShapes.button)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val value = bedtime.settings.targetTempC
                        Text(
                            text = buildAnnotatedString {
                                append(value.toInt().toString())
                                withStyle(SpanStyle(fontSize = 16.sp)) { append(".${((value % 1f) * 10).roundToInt()}") }
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.text,
                            modifier = Modifier.width(64.dp),
                        )
                        Slider(
                            value = value,
                            onValueChange = onSetTemperature,
                            valueRange = range,
                            steps = (((range.endInclusive - range.start) / 0.5f).roundToInt() - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.text,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.warningIcon.copy(alpha = 0.6f),
                                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row {
                        Text(text = "${range.start.formatTemp()}°C", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                        Spacer(Modifier.weight(1f))
                        Text(text = "${range.endInclusive.formatTemp()}°C", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                    }
                }
                VerticalDivider(color = colors.segment, modifier = Modifier.height(150.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(NaruIcons.Battery, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        Text(text = "온도별 아침 예상 배터리", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    }
                    for (row in bedtime.forecastRows) ForecastRow(row, selected = row.celsius == bedtime.settings.targetTempC)
                    Text(
                        text = "${NightThresholds.BATTERY_LIMIT_PERCENT}%에 닿으면 경고해요",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** 온도 한 줄: 온도 / 막대(예상 잔량 위쪽 값) / 범위 또는 "기록 부족" */
@Composable
private fun ForecastRow(row: TempForecast, selected: Boolean) {
    val colors = NaruTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(NaruShapes.button).background(if (selected) colors.accentContainer else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "${row.celsius.formatTemp()}°C", style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.width(44.dp))
        Box(modifier = Modifier.weight(1f).height(6.dp).clip(NaruShapes.pill).background(colors.track)) {
            row.forecast?.let { f ->
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(f.highPercent / 100f).clip(NaruShapes.pill)
                        .background(if (selected) colors.accent else colors.textTertiary.copy(alpha = 0.5f)),
                )
            }
        }
        Text(
            text = row.forecast?.let { "${it.lowPercent}~${it.highPercent}%" } ?: "기록 부족",
            style = MaterialTheme.typography.titleMedium,
            color = if (row.forecast?.belowLimit == true) colors.warningIcon else colors.text,
        )
    }
}

@Composable
private fun WakeCard(bedtime: BedtimeState, actions: BedtimeScreenActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    var picking by remember { mutableStateOf(false) }
    val settings = bedtime.settings
    NaruCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NaruIconCircle(NaruIcons.Sunrise)
                Text(text = "기상 시각", style = MaterialTheme.typography.titleMedium, color = colors.text)
            }
            Text(text = settings.wakeAt.format(hhmm), style = MaterialTheme.typography.displaySmall, color = colors.text)
            bedtime.sunrise?.let { sunrise ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "일출에 맞추기", style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                    NaruSwitch(checked = settings.wakeBasis == WakeBasis.SUNRISE, onCheckedChange = actions.onSetSunriseWake)
                }
                Text(
                    text = "일출 ${sunrise.format(hhmm)}\n" +
                        if (settings.wakeBasis == WakeBasis.SUNRISE) "해 뜨기 ${ai.naru.app.camping.bedtime.BedtimeThresholds.SUNRISE_LEAD_MINUTES}분 전에 깨워 드려요" else "직접 정한 시각에 깨워 드려요",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            NaruSecondaryButton(text = "직접 설정", onClick = { picking = true })
        }
    }
    if (picking) {
        WakeTimePickerDialog(
            initial = settings.wakeAt,
            sunrise = bedtime.sunrise,
            onConfirm = {
                actions.onSetWakeTime(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
