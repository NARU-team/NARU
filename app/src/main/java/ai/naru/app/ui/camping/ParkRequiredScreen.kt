package ai.naru.app.ui.camping

import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruStepHeader
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import ai.naru.app.vehicle.Gear
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 피그마 v2 예외 "주차(P) 상태가 아닐 때" (기획서: 차량이 주차(P) 상태일 때만 휴식 세션 시작).
 * 이 화면에서 기어가 P가 되면 ViewModel이 바로 이어서 시작한다.
 */
@Composable
fun ParkRequiredScreen(gear: Gear, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NaruTheme.colors
    FigmaFrame(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel)) {
            NaruStepHeader(title = "나루", onBack = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NaruIconCircle(NaruIcons.Car, size = 80)
                    Text(
                        text = "현재 기어  ${gear.letter()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.clip(NaruShapes.pill).background(colors.segment).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Text(text = "주차(P) 상태에서만 쉴 수 있어요", style = MaterialTheme.typography.headlineMedium, color = colors.text)
                    Text(
                        text = "차를 세우고 기어를 P에 두면 여기서 바로 이어서 시작해요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Gear.letter() = when (this) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
    Gear.UNKNOWN -> "-"
}

@Preview(widthDp = 850, heightDp = 590)
@Composable
private fun ParkRequiredPreview() {
    NaruTheme { ParkRequiredScreen(gear = Gear.DRIVE, onBack = {}) }
}
