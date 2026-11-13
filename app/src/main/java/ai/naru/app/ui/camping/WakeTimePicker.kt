package ai.naru.app.ui.camping

import ai.naru.app.camping.bedtime.BedtimeRules
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.naru.app.ui.components.NaruDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 기상 시각 직접 설정 팝업 (피그마 v2 "기상 시각 직접 설정"): 시·분·오전/오후 휠.
 * 휠은 위아래로 끌거나 위·아래 숫자를 눌러 바꾼다.
 */
@Composable
fun WakeTimePickerDialog(initial: LocalTime, sunrise: LocalTime?, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val colors = NaruTheme.colors
    var hour12 by remember { mutableIntStateOf(((initial.hour + 11) % 12) + 1) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var pm by remember { mutableIntStateOf(if (initial.hour >= 12) 1 else 0) }

    NaruDialog(onDismiss = onDismiss, width = 600.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(NaruShapes.panel).background(colors.card).padding(NaruSpacing.panel),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NaruSpacing.gap),
        ) {
            Text(text = "몇 시에 깨워 드릴까요?", style = MaterialTheme.typography.titleLarge, color = colors.text)
            sunrise?.let {
                Text(
                    text = "일출이 ${it.format(hhmm)}라 ${BedtimeRules.sunriseWakeTime(it).format(hhmm)}을 권해요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Box(modifier = Modifier.clip(NaruShapes.card).background(colors.background).padding(horizontal = 12.dp)) {
                // 가운데 선택 줄 강조
                Box(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(ROW_HEIGHT.dp).clip(NaruShapes.button).background(colors.segment),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wheel(values = (1..12).map { it.toString() }, selected = hour12 - 1, onSelect = { hour12 = it + 1 })
                    Wheel(values = (0..59).map { "%02d".format(it) }, selected = minute, onSelect = { minute = it })
                    Wheel(values = listOf("AM", "PM"), selected = pm, onSelect = { pm = it }, wrap = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NaruPrimaryButton(
                    text = "이 시각에 깨우기",
                    onClick = { onConfirm(LocalTime.of((hour12 % 12) + if (pm == 1) 12 else 0, minute)) },
                    modifier = Modifier.weight(1f),
                )
                NaruSecondaryButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 값 5개(선택 ±2)를 보여주는 휠 */
@Composable
private fun Wheel(values: List<String>, selected: Int, onSelect: (Int) -> Unit, wrap: Boolean = true) {
    val colors = NaruTheme.colors
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    fun indexAt(offset: Int): Int? {
        val i = selected + offset
        return when {
            wrap -> ((i % values.size) + values.size) % values.size
            i in values.indices -> i
            else -> null
        }
    }
    Column(
        modifier = Modifier.width(64.dp).pointerInput(selected) {
            detectVerticalDragGestures(onDragEnd = { drag = 0f }) { _, dy ->
                drag += dy
                val steps = (drag / rowPx).toInt()
                if (steps != 0) {
                    indexAt(-steps)?.let(onSelect)
                    drag -= steps * rowPx
                }
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (offset in -2..2) {
            val index = indexAt(offset)
            Box(
                modifier = Modifier.height(ROW_HEIGHT.dp).fillMaxWidth().clickable(enabled = index != null && offset != 0) { index?.let(onSelect) },
                contentAlignment = Alignment.Center,
            ) {
                if (index != null) {
                    Text(
                        text = values[index],
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (offset == 0) FontWeight.Bold else FontWeight.Normal),
                        color = if (offset == 0) colors.text else colors.textTertiary.copy(alpha = if (kotlin.math.abs(offset) == 1) 0.8f else 0.4f),
                    )
                }
            }
        }
    }
}

private const val ROW_HEIGHT = 40
