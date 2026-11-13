package ai.naru.app.ui.home

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.morning.RestoreReport
import ai.naru.app.camping.record.SessionRecord
import ai.naru.app.ui.camping.RestoreSummary
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruChip
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** 홈 화면이 그릴 내용 */
data class HomeState(
    val batteryPercent: Int? = null,
    val cabinTempC: Float? = null,
    val outsideTempC: Float? = null,
    /** 가장 최근 차박 기록 (차박 카드 칩) */
    val lastRecord: SessionRecord? = null,
    /** 시작을 거절한 이유 등 (예: P가 아님) */
    val message: String? = null,
    /** 방금 끝난 세션의 이유. 정상 종료·그만둔 경우는 표시하지 않는다 */
    val lastEndReason: EndReason? = null,
    /** 직접 확인이 필요한 복구 결과 */
    val restore: RestoreReport? = null,
)

/** 피그마 v2 "홈": 목적(모드)을 고르면 온도·조명·소리·알람을 한 번에 맞춘다 (이슈 #33). */
@Composable
fun HomeScreen(
    state: HomeState,
    onStartCamping: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NaruTheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.background).padding(NaruSpacing.panel),
        verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
    ) {
        Text(text = "나루", style = MaterialTheme.typography.titleMedium, color = colors.text)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "어떻게 쉬어 갈까요?", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(
                text = "목적만 고르면 온도, 조명, 소리, 알람을 한 번에 맞춰 드려요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NaruSpacing.small)) {
            state.batteryPercent?.let { NaruChip("배터리 $it%", icon = NaruIcons.Battery) }
            state.cabinTempC?.let { NaruChip("실내 ${it.roundToInt()}°C", icon = NaruIcons.Thermometer) }
            state.outsideTempC?.let { NaruChip("실외 ${it.roundToInt()}°C", icon = NaruIcons.Wind) }
        }

        Notices(state, onDismissMessage)

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NaruSpacing.gap)) {
            ModeCard(
                illustration = { RestIllustration() },
                label = "쉼표 모드",
                title = "잠깐 쉬기",
                description = "15분에서 45분, 짧은 낮잠과 대기 시간",
                chip = "준비 중이에요",
                chipIcon = NaruIcons.Clock,
                startEnabled = false,
                onStart = {},
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                illustration = { NightIllustration() },
                label = "차박 모드",
                title = "오늘 여기서 자기",
                description = "장소 확인부터 일출 기상까지 하룻밤",
                chip = state.lastRecord?.let { "지난번 ${it.siteName.removeSuffix(" (시연)")}, ${it.targetTempC.formatTemp()}°C" },
                chipIcon = NaruIcons.Clock,
                startEnabled = true,
                onStart = onStartCamping,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 시작 거절 안내, 지난 세션 결과, 직접 확인할 복구 항목 */
@Composable
private fun Notices(state: HomeState, onDismissMessage: () -> Unit) {
    val colors = NaruTheme.colors
    state.message?.let { message ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NaruIcons.Car, contentDescription = null, tint = colors.warningIcon)
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            NaruSecondaryButton(text = "확인", onClick = onDismissMessage)
        }
    }
    val endNote = when (state.lastEndReason) {
        EndReason.GEAR_LEFT_PARK -> "지난 차박은 기어가 P를 벗어나 설정을 되돌리고 끝냈어요."
        EndReason.BATTERY_LOW -> "지난 차박은 배터리가 하한에 닿아 일찍 끝냈어요."
        else -> null
    }
    endNote?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary) }
    state.restore?.takeIf { it.needsManualCheck }?.let { RestoreSummary(it) }
}

@Composable
private fun ModeCard(
    illustration: @Composable () -> Unit,
    label: String,
    title: String,
    description: String,
    chip: String?,
    chipIcon: ImageVector,
    startEnabled: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NaruTheme.colors
    NaruCard(modifier = modifier.fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(ILLUSTRATION_HEIGHT)) { illustration() }
            Column(
                modifier = Modifier.weight(1f).padding(NaruSpacing.card),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(text = title, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    chip?.let { NaruChip(it, icon = chipIcon, modifier = Modifier.background(colors.background, NaruShapes.pill)) }
                    Spacer(Modifier.weight(1f))
                    NaruPrimaryButton(text = if (startEnabled) "시작" else "준비 중", onClick = onStart, enabled = startEnabled)
                }
            }
        }
    }
}

/** 쉼표: 복숭아색 바탕 + 큰 해 + 흰 원 안의 시계 */
@Composable
private fun RestIllustration() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PEACH, PEACH_LIGHT))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-20).dp).size(140.dp)
                .clip(NaruShapes.pill).background(SUN),
        )
        Box(modifier = Modifier.size(72.dp).clip(NaruShapes.pill).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(NaruIcons.Clock, contentDescription = null, tint = NaruTheme.colors.text, modifier = Modifier.size(40.dp))
        }
    }
}

/** 차박: 남색 밤하늘 + 별 + 달 + 텐트 */
@Composable
private fun NightIllustration() {
    val colors = NaruTheme.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.nightBrush), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for ((fx, fy) in STARS) {
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 2.2f, center = Offset(size.width * fx, size.height * fy))
            }
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 56.dp).size(48.dp)
                .clip(NaruShapes.pill).background(colors.moon),
        )
        Icon(NaruIcons.Tent, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
    }
}

private fun Float.formatTemp(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)

private val ILLUSTRATION_HEIGHT = 130.dp
private val PEACH = Color(0xFFFFE6CB)
private val PEACH_LIGHT = Color(0xFFFFF3E8)
private val SUN = Color(0xFFFFD2A6)

/** 별 위치 (그림 크기에 대한 비율) */
private val STARS = listOf(
    0.16f to 0.28f, 0.28f to 0.66f, 0.42f to 0.2f, 0.57f to 0.52f, 0.72f to 0.14f, 0.86f to 0.6f, 0.08f to 0.82f,
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    NaruTheme {
        HomeScreen(
            state = HomeState(
                batteryPercent = 72,
                cabinTempC = 26f,
                outsideTempC = 9f,
                lastRecord = SessionRecord(
                    id = "p",
                    nightStartedAt = LocalDateTime.of(2026, 11, 12, 22, 0),
                    nightEndedAt = LocalDateTime.of(2026, 11, 13, 7, 0),
                    siteName = "가평 숲 캠핑장 (시연)",
                    placeType = PlaceType.FOREST,
                    grade = null,
                    minTempC = 6f,
                    targetTempC = 22f,
                    theme = PlaceType.FOREST,
                    nightStartBattery = 80,
                    nightEndBattery = 70,
                    endReason = EndReason.WAKE_TIME,
                ),
            ),
            onStartCamping = {},
            onDismissMessage = {},
        )
    }
}
