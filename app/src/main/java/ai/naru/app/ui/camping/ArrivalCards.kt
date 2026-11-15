package ai.naru.app.ui.camping

import ai.naru.app.camping.arrival.ArrivalCard
import ai.naru.app.camping.arrival.ArrivalCardDetail
import ai.naru.app.camping.arrival.ArrivalCardKind
import ai.naru.app.camping.arrival.Bar
import ai.naru.app.camping.arrival.TideEvent
import ai.naru.app.ui.components.CardTone
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 시간별 막대의 최대 높이 */
private val BAR_MAX_HEIGHT = 48.dp

/**
 * 도착 안내 카드 (피그마 v2 갱신): 이름 → 큰 값 → 짧은 설명 → 그림(시간별 막대 / 물때 시간표 / 시설 목록) → 출처.
 * 그림이 없으면(예보 부족, 조회 실패) 설명 글만 보여 준다.
 */
@Composable
fun ArrivalInfoCard(card: ArrivalCard, modifier: Modifier = Modifier) {
    val colors = NaruTheme.colors
    val tone = if (card.warning) CardTone.WARNING else CardTone.DEFAULT
    NaruCard(modifier = modifier, tone = tone) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
                NaruIconCircle(card.kind.icon(card.warning), tone = tone)
                Text(text = card.label, style = MaterialTheme.typography.titleMedium, color = if (card.warning) colors.danger else colors.textSecondary)
            }
            // 이용 정보는 글이 길어 한 단계 작게 (피그마도 작게 씀)
            Text(
                text = card.value,
                style = if (card.kind == ArrivalCardKind.USAGE) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                color = colors.text,
            )
            when (val detail = card.detail) {
                is ArrivalCardDetail.Bars -> {
                    if (card.description.isNotEmpty()) Text(text = card.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Bars(detail, Modifier.weight(1f).fillMaxWidth())
                }
                is ArrivalCardDetail.Timeline -> Timeline(detail.events, Modifier.weight(1f))
                is ArrivalCardDetail.Checklist -> Checklist(detail, Modifier.weight(1f))
                null -> {
                    if (card.description.isNotEmpty()) Text(text = card.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = colors.segment)
            Text(text = card.source, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
    }
}

/** 시간별 막대: 위 값, 막대(높이 비율), 아래 시각 */
@Composable
private fun Bars(detail: ArrivalCardDetail.Bars, modifier: Modifier) {
    val colors = NaruTheme.colors
    val strong = if (detail.highlightWarning) colors.danger else colors.accent
    val weak = if (detail.highlightWarning) colors.danger.copy(alpha = 0.22f) else colors.accent.copy(alpha = 0.15f)
    Row(modifier = modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        for (bar in detail.bars) BarColumn(bar, if (bar.highlight) strong else weak, if (bar.highlight) strong else colors.text, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun BarColumn(bar: Bar, barColor: androidx.compose.ui.graphics.Color, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(text = bar.valueText, style = MaterialTheme.typography.labelLarge, color = valueColor)
        Spacer(Modifier.height(4.dp))
        // 막대 높이는 고정 최대값에 비례 (카드가 낮아도 납작해지지 않게)
        Box(
            modifier = Modifier.width(12.dp).height(BAR_MAX_HEIGHT * bar.fraction.coerceIn(0.1f, 1f))
                .clip(NaruShapes.pill).background(barColor),
        )
        Spacer(Modifier.height(6.dp))
        Text(text = bar.label, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
    }
}

/** 물때 시간표: 점과 세로선, 시각, 이름, (자는 동안) 배지 */
@Composable
private fun Timeline(events: List<TideEvent>, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier.padding(top = 4.dp)) {
        events.forEachIndexed { index, event ->
            if (index > 0) {
                Box(modifier = Modifier.padding(start = 5.dp).width(1.dp).height(14.dp).background(colors.track))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(if (event.highlight) 11.dp else 8.dp).clip(NaruShapes.pill)
                        .background(if (event.highlight) colors.accent else colors.track),
                )
                Text(
                    text = event.time.format(hhmm),
                    style = if (event.highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (event.highlight) colors.accent else colors.text,
                )
                Text(text = event.label, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                event.tag?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.clip(NaruShapes.pill).background(colors.accentContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** 시설 목록(체크)과 확인 안내 알약 */
@Composable
private fun Checklist(detail: ArrivalCardDetail.Checklist, modifier: Modifier) {
    val colors = NaruTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in detail.items) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(NaruIcons.Check, contentDescription = null, tint = colors.text, modifier = Modifier.size(16.dp))
                Text(text = item, style = MaterialTheme.typography.bodyMedium, color = colors.text)
            }
        }
        detail.pill?.let {
            Row(
                modifier = Modifier.padding(top = 2.dp).clip(NaruShapes.pill).background(colors.background).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(NaruIcons.Location, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ArrivalCardKind.icon(warning: Boolean): ImageVector = when (this) {
    ArrivalCardKind.WEATHER -> if (warning) NaruIcons.Wind else NaruIcons.Moon
    ArrivalCardKind.TIDE -> NaruIcons.Tide
    ArrivalCardKind.SUNRISE -> NaruIcons.Sunrise
    ArrivalCardKind.USAGE -> NaruIcons.Warning
}
