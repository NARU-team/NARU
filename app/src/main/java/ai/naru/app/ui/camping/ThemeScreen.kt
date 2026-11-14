package ai.naru.app.ui.camping

import ai.naru.app.camping.BedtimeState
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.ui.components.BadgeTone
import ai.naru.app.ui.components.CardTone
import ai.naru.app.ui.components.NaruBadge
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruStepHeader
import ai.naru.app.ui.components.ThemeIllustration
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.height
import ai.naru.app.camping.night.ThemeTimer
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

data class ThemeScreenActions(
    val onBack: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onSelectTheme: (PlaceType) -> Unit = {},
    val onStartNight: () -> Unit = {},
    /** 테마 종료 시간 화살표 (-1 / +1) */
    val onMoveThemeMinutes: (Int) -> Unit = {},
)

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 피그마 v2 "밤 분위기 3/3" (이슈 #37), 갱신본의 "테마 종료" 줄(30/60/90분)과 "제어 미지원 차량" 문구.
 * 소리·조명 설명은 실제 동작(NightThemes 색, 테마 소리)과 맞춘다.
 */
@Composable
fun ThemeScreen(bedtime: BedtimeState?, recommended: PlaceType?, actions: ThemeScreenActions, modifier: Modifier = Modifier) {
    val colors = NaruTheme.colors
    FigmaFrame(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel), verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            NaruStepHeader(title = "밤 분위기 3/3", onBack = actions.onBack, onClose = actions.onClose)
            if (bedtime == null) return@Column
            Text(text = "이곳에 어울리는 밤을 골라 보세요", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
                for (theme in PlaceType.entries) {
                    ThemeCard(
                        theme = theme,
                        selected = bedtime.settings.theme == theme,
                        recommended = theme == recommended,
                        lightSupported = bedtime.lightSupported,
                        onClick = { actions.onSelectTheme(theme) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            ThemeTimerRow(bedtime.settings.themeMinutes, actions.onMoveThemeMinutes)
            SummaryBar(bedtime, actions.onStartNight)
        }
    }
}

@Composable
private fun ThemeCard(theme: PlaceType, selected: Boolean, recommended: Boolean, lightSupported: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier, tone = if (selected) CardTone.SELECTED else CardTone.DEFAULT, onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(0.45f)) { ThemeIllustration(theme) }
            Column(modifier = Modifier.weight(0.55f).padding(NaruSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Radio(selected)
                    Text(text = theme.title(), style = MaterialTheme.typography.titleLarge, color = colors.text)
                    if (recommended) NaruBadge("${theme.title()} 위치라 추천", BadgeTone.ACCENT)
                }
                Line(NaruIcons.Sound, theme.sound())
                Line(NaruIcons.Light, if (lightSupported) theme.light() else theme.screen())
            }
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    val colors = NaruTheme.colors
    Box(
        modifier = Modifier.size(22.dp).clip(NaruShapes.pill).border(2.dp, if (selected) colors.toggleOn else colors.segment, NaruShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(modifier = Modifier.size(12.dp).clip(NaruShapes.pill).background(colors.toggleOn))
    }
}

@Composable
private fun Line(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = NaruTheme.colors.textTertiary, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = NaruTheme.colors.textSecondary)
    }
}

/** 요약 줄: 테마 · 설정 온도 · 기상 알람 + [이 설정으로 차박 시작] */
@Composable
private fun SummaryBar(bedtime: BedtimeState, onStartNight: () -> Unit) {
    val colors = NaruTheme.colors
    val s = bedtime.settings
    Row(
        modifier = Modifier.fillMaxWidth().clip(NaruShapes.card).background(colors.card).padding(horizontal = NaruSpacing.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SummaryItem(NaruIcons.Tide, "${s.theme.title()} 테마")
                SummaryItem(NaruIcons.Thermometer, "설정 온도 ${s.targetTempC.formatTemp()}°C")
                SummaryItem(NaruIcons.Alarm, "기상 알람 ${s.wakeAt.format(hhmm)}")
            }
            val note = when {
                !bedtime.lightSupported && bedtime.vehicleTempRange == null -> "이 차는 공조와 조명 제어를 지원하지 않아요. 온도는 차량에서 직접 맞춰 주세요."
                !bedtime.lightSupported -> "이 차는 조명 제어를 지원하지 않아 나루 화면 색으로 보여 드려요."
                else -> "소리와 조명은 차박을 시작하면 켜지고 테마 종료 시각에 꺼져요"
            }
            Text(text = note, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
        NaruPrimaryButton(
            text = if (bedtime.preparing) "차량 설정을 준비하고 있어요" else "이 설정으로 차박 시작",
            onClick = onStartNight,
            enabled = !bedtime.preparing,
        )
    }
}

/** "테마 종료" 줄: [<] 진행 바 [>]  60분  00:20에 꺼져요 */
@Composable
private fun ThemeTimerRow(minutes: Int, onMove: (Int) -> Unit) {
    val colors = NaruTheme.colors
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val options = ThemeTimer.OPTIONS
    val fraction = (options.indexOf(minutes) + 1).toFloat() / options.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(NaruIcons.Clock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(text = "테마 종료", style = MaterialTheme.typography.titleMedium, color = colors.text)
        Row(
            modifier = Modifier.weight(1f).clip(NaruShapes.button).background(colors.secondaryButton).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMove(-1) }, enabled = minutes != options.first()) {
                Icon(NaruIcons.ChevronLeft, contentDescription = "줄이기", tint = colors.text)
            }
            Box(modifier = Modifier.weight(1f).height(28.dp).clip(NaruShapes.button).background(colors.track)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(NaruShapes.button).background(colors.card))
            }
            IconButton(onClick = { onMove(1) }, enabled = minutes != options.last()) {
                Icon(NaruIcons.ChevronRight, contentDescription = "늘리기", tint = colors.text)
            }
        }
        Text(text = "${minutes}분", style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(text = "${ThemeTimer.endsAt(now, minutes).format(hhmm)}에 꺼져요", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
    }
}

@Composable
private fun SummaryItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = NaruTheme.colors.accent, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = NaruTheme.colors.text)
    }
}

private fun PlaceType.title() = when (this) {
    PlaceType.BEACH -> "해변"
    PlaceType.FOREST -> "숲"
    PlaceType.LAKE -> "호수"
}

private fun PlaceType.sound() = when (this) {
    PlaceType.BEACH -> "파도 소리"
    PlaceType.FOREST -> "풀벌레와 바람 소리"
    PlaceType.LAKE -> "잔물결 소리"
}

/** 조명 미지원 차량: 나루 화면 색으로만 보여 준다 */
private fun PlaceType.screen() = when (this) {
    PlaceType.BEACH -> "푸른빛 화면"
    PlaceType.FOREST -> "초록빛 화면"
    PlaceType.LAKE -> "청록빛 화면"
}

/** NightThemes의 실제 조명 색과 맞춘다 (해변 파랑, 숲 초록, 호수 청록) */
private fun PlaceType.light() = when (this) {
    PlaceType.BEACH -> "푸른빛 조명"
    PlaceType.FOREST -> "초록빛 조명"
    PlaceType.LAKE -> "청록빛 조명"
}
