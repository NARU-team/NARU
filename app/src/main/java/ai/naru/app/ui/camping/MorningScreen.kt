package ai.naru.app.ui.camping

import ai.naru.app.camping.CampingUiState
import ai.naru.app.camping.EndReason
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.TempFeedback
import ai.naru.app.camping.morning.MorningPresenter
import ai.naru.app.camping.narration.NarrationMoment
import ai.naru.app.camping.night.NightTempSummary
import ai.naru.app.camping.night.NightTemps
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class MorningScreenActions(
    /** [알람 끄고 아침 브리핑 보기]: 알람 끄기 + 설정 복구 */
    val onWakeUp: () -> Unit = {},
    val onFeedback: (TempFeedback) -> Unit = {},
    /** [차박 종료] */
    val onFinish: () -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 피그마 v2 "차박 일출 기상 알람" → "아침 브리핑" (이슈 #41).
 * 설정 복구를 시작하기 전(알람을 끄기 전)에는 기상 화면, 그 뒤로는 아침 브리핑.
 */
@Composable
fun MorningScreen(state: CampingUiState, actions: MorningScreenActions, modifier: Modifier = Modifier) {
    FigmaFrame(modifier = modifier.fillMaxSize().background(NaruTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel), verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            val woke = state.restore != null || state.restoring
            Text(
                text = if (woke) "아침 브리핑" else "기상",
                style = MaterialTheme.typography.titleLarge,
                color = NaruTheme.colors.text,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            if (woke) Briefing(state, actions, Modifier.weight(1f)) else Wake(state, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Wake(state: CampingUiState, actions: MorningScreenActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    val now = LocalDateTime.now()
    val sunrise = state.bedtime?.sunrise
    val batteryLow = state.lastEndReason == EndReason.BATTERY_LOW
    val morning = state.morning
    // 나루 화면을 밤 색에서 흰색으로 천천히 밝힌다 (피그마 v2 갱신 "천천히 밝히는 중")
    val brightness = remember { Animatable(0f) }
    LaunchedEffect(Unit) { brightness.animateTo(1f, tween(durationMillis = WAKE_BRIGHTEN_MS, easing = LinearEasing)) }
    val dawn = lerp(colors.nightTop, colors.card, brightness.value)
    val ink = lerp(Color.White, colors.text, brightness.value)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            Box(modifier = Modifier.weight(1.9f).fillMaxHeight().clip(NaruShapes.card).background(dawn)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(260.dp)) {
                        drawCircle(colors.accent, style = Stroke(width = 14.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = state.wakeDateTime?.format(hhmm) ?: now.format(hhmm),
                            style = MaterialTheme.typography.displayMedium,
                            color = ink,
                        )
                        Text(
                            text = if (batteryLow) "배터리가 ${state.batteryPercent ?: NightThresholds.BATTERY_LIMIT_PERCENT}%라 일찍 깨웠어요" else "일어날 시간이에요",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (batteryLow) colors.warningIcon else ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                InfoSide(
                    icon = NaruIcons.Sunrise,
                    label = "일출",
                    value = MorningPresenter.sunriseCard(now.toLocalTime(), sunrise) ?: "알 수 없음",
                    modifier = Modifier.weight(1f),
                )
                InfoSide(
                    icon = NaruIcons.Screen,
                    label = "나루 화면",
                    value = if (brightness.value < 1f) "천천히 밝히는 중" else "밝혀 뒀어요",
                    // 실내 조명은 나루 화면과 함께 밝힌다. 반영이 안 됐을 때만 알린다
                    note = when (morning?.lightApply) {
                        is ControlResult.Failed, ControlResult.Unverified -> "실내 조명 반영을 확인하지 못했어요"
                        else -> null
                    },
                    modifier = Modifier.weight(1f),
                )
                InfoSide(
                    icon = NaruIcons.Alarm,
                    label = "알람",
                    value = when {
                        morning?.alarmRinging == true -> "울리는 중"
                        morning?.alarmUnavailable == true -> "울리지 못했어요"
                        else -> "꺼져 있어요"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        NaruBottomBar(
            note = if (batteryLow) "배터리를 아끼려면 공조를 줄이고 충전 계획을 세워 주세요." else MorningPresenter.sunriseSentence(now.toLocalTime(), sunrise),
            noteIcon = NaruIcons.Sunrise,
        ) {
            NaruPrimaryButton(text = "알람 끄고 아침 브리핑 보기", onClick = actions.onWakeUp)
        }
    }
}

@Composable
private fun Briefing(state: CampingUiState, actions: MorningScreenActions, modifier: Modifier) {
    val colors = NaruTheme.colors
    val now = LocalDateTime.now()
    val summary = NightTemps.summarize(state.nightTemps)
    val weather = state.arrival?.conditions?.morningWeather
    val batteryLow = state.lastEndReason == EndReason.BATTERY_LOW
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 배너
        Row(
            modifier = Modifier.fillMaxWidth().clip(NaruShapes.card).background(colors.morningBrush).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (batteryLow) "배터리가 하한에 닿아 일찍 깨웠어요" else "좋은 아침이에요",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                )
                Text(
                    text = listOfNotNull(MorningPresenter.sunriseSentence(now.toLocalTime(), state.bedtime?.sunrise), MorningPresenter.staySentence(state.nightStartedAt, state.nightEndedAt ?: now)).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Box(modifier = Modifier.size(64.dp).clip(NaruShapes.pill).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(NaruIcons.Sunrise, contentDescription = null, tint = Color(0xFFB4612A), modifier = Modifier.size(34.dp))
            }
        }
        state.narration?.takeIf { it.moment == NarrationMoment.MORNING }?.let { n ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(NaruIcons.Sparkle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Text(text = if (n.pending) "나루가 안내를 다듬고 있어요…" else n.text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        state.restore?.takeIf { it.needsManualCheck }?.let {
            Text(text = "일부 차량 설정은 원래대로 되돌렸는지 확인하지 못했어요. 차량에서 직접 확인해 주세요.", style = MaterialTheme.typography.bodyMedium, color = colors.warningIcon)
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            CabinCard(summary, state.bedtime?.settings?.targetTempC, state.arrival?.conditions?.weather?.minTempC, Modifier.weight(1f).fillMaxHeight())
            BatteryCard(state, Modifier.weight(1f).fillMaxHeight())
            WeatherCard(MorningPresenter.weatherValue(weather), MorningPresenter.weatherDetail(weather), Modifier.weight(1f).fillMaxHeight())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "온도는 어땠나요?", style = MaterialTheme.typography.titleMedium, color = colors.text)
            FeedbackSegments(state.feedback, actions.onFeedback, Modifier.width(380.dp))
            Spacer(Modifier.weight(1f))
            NaruPrimaryButton(text = if (state.restoring) "설정을 되돌리는 중" else "차박 종료", onClick = actions.onFinish, enabled = state.restore != null)
        }
    }
}

/** 밤새 실내 온도: 범위 + 선 그래프(목표 온도 점선) + 실외 최저 */
@Composable
private fun CabinCard(summary: NightTempSummary, targetC: Float?, forecastMinC: Float?, modifier: Modifier) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(NaruIcons.Thermometer, "밤새 실내 온도")
            val range = MorningPresenter.cabinRange(summary.cabinRange)
            Text(text = range ?: "기록이 부족해요", style = if (range != null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium, color = colors.text)
            if (range != null) {
                TempChart(summary, targetC, Modifier.fillMaxWidth().height(70.dp))
                Row {
                    Text(text = summary.cabinSamples.first().at.format(hhmm), style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                    Spacer(Modifier.weight(1f))
                    targetC?.let { Text(text = "목표 ${it.formatTemp()}°C", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary) }
                    Spacer(Modifier.weight(1f))
                    Text(text = summary.cabinSamples.last().at.format(hhmm), style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                }
            } else {
                Text(text = "밤 동안 10분마다 온도를 기록해요", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            MorningPresenter.outsideLine(summary.outsideMinC, forecastMinC)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TempChart(summary: NightTempSummary, targetC: Float?, modifier: Modifier) {
    val colors = NaruTheme.colors
    val values = summary.cabinSamples.mapNotNull { it.cabinC }
    val low = (listOfNotNull(values.minOrNull(), targetC).min()) - 1f
    val high = (listOfNotNull(values.maxOrNull(), targetC).max()) + 1f
    Canvas(modifier = modifier) {
        fun y(v: Float) = size.height * (1f - (v - low) / (high - low))
        targetC?.let {
            drawLine(colors.textTertiary.copy(alpha = 0.5f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        if (values.size >= 2) {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (values.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, colors.accent, style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun BatteryCard(state: CampingUiState, modifier: Modifier) {
    val colors = NaruTheme.colors
    val now = state.batteryPercent
    val forecast = state.bedtime?.forecast
    NaruCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(NaruIcons.Battery, "배터리")
            Text(text = now?.let { "$it%" } ?: "알 수 없음", style = MaterialTheme.typography.headlineLarge, color = colors.text)
            now?.let {
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(NaruShapes.pill).background(colors.track)) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(it / 100f).clip(NaruShapes.pill).background(if (it <= NightThresholds.BATTERY_LIMIT_PERCENT) colors.warningIcon else colors.accent))
                }
            }
            MorningPresenter.batteryDetail(now, state.nightStartBattery, forecast?.lowPercent, forecast?.highPercent)?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text(text = "이번 기록은 다음 예상에 반영돼요", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun WeatherCard(value: String?, detail: String?, modifier: Modifier) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(NaruIcons.Sunny, "아침 날씨")
            Text(text = value ?: "불러오지 못했어요", style = if (value != null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(Modifier.weight(1f))
            detail?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            HorizontalDivider(color = colors.segment)
            Text(text = "기상청 단기예보", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
    }
}

/** 추웠어요 / 딱 좋았어요 / 더웠어요 (처음에는 아무것도 고르지 않음) */
@Composable
private fun FeedbackSegments(selected: TempFeedback?, onSelect: (TempFeedback) -> Unit, modifier: Modifier) {
    val colors = NaruTheme.colors
    Row(modifier = modifier.clip(NaruShapes.button).background(colors.segment).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((feedback, label) in listOf(TempFeedback.COLD to "추웠어요", TempFeedback.GOOD to "딱 좋았어요", TempFeedback.HOT to "더웠어요")) {
            val isSelected = feedback == selected
            Box(
                modifier = Modifier.weight(1f).clip(NaruShapes.button).background(if (isSelected) colors.card else Color.Transparent)
                    .clickable { onSelect(feedback) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = if (isSelected) colors.text else colors.textSecondary)
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NaruIconCircle(icon)
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = NaruTheme.colors.text)
    }
}

@Composable
private fun InfoSide(icon: ImageVector, label: String, value: String, modifier: Modifier, note: String? = null) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NaruIconCircle(icon)
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                Text(text = value, style = MaterialTheme.typography.titleMedium, color = colors.text)
                note?.let { Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.warningIcon) }
            }
        }
    }
}

/** 기상 화면을 밤 색에서 흰색으로 밝히는 시간 */
private const val WAKE_BRIGHTEN_MS = 60_000
