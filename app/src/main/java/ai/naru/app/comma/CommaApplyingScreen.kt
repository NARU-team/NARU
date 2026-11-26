package ai.naru.app.comma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun CommaApplyingScreen(ui: RestUiState) {
    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            val wide = maxWidth >= 900.dp
            Column(
                Modifier.fillMaxSize().padding(if (wide) 32.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("쉼표 설정", style = MaterialTheme.typography.headlineLarge)
                CommaPanel(Modifier.fillMaxWidth(), tint = CommaDesign.Suggestion) {
                    Text("바꾸는 중", color = CommaDesign.Blue,
                        style = MaterialTheme.typography.labelLarge)
                    Text("차량에 설정을 요청하고 실제 반영값을 확인하고 있어요.",
                        style = MaterialTheme.typography.titleLarge)
                    Text("응답이 끝나기 전에는 적용됐다고 표시하지 않습니다.",
                        color = CommaDesign.Muted)
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ApplyingSummary(ui, Modifier.weight(1f))
                        ApplyingItems(ui, Modifier.widthIn(min = 320.dp).weight(0.55f))
                    }
                } else {
                    ApplyingSummary(ui, Modifier.fillMaxWidth())
                    ApplyingItems(ui, Modifier.fillMaxWidth())
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("같은 요청은 완료 전 다시 실행할 수 없습니다. 화면을 나가도 저장된 차량 백업은 유지됩니다.",
                    color = CommaDesign.Muted)
            }
        }
    }
}

@Composable
private fun ApplyingSummary(ui: RestUiState, modifier: Modifier) {
    Surface(modifier.heightIn(min = 260.dp), shape = RoundedCornerShape(22.dp),
        color = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${ui.session.selectedMinutes}분 휴식",
                style = MaterialTheme.typography.titleLarge)
            Text("공조 변경 요청 중", style = MaterialTheme.typography.headlineLarge)
            Text(ui.selectedClimateCelsius?.let { "목표 $it°C" }
                ?: "목표 온도 확인 중")
            Text("요청 → SDK 응답 → 실제 값 재조회 순서로 확인합니다.",
                color = CommaDesign.Muted)
        }
    }
}

@Composable
private fun ApplyingItems(ui: RestUiState, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ApplyingRow("온도", ui.selectedClimateCelsius?.let { "$it°C" } ?: "확인 중", "변경 요청 중")
        ApplyingRow("화면", "${(ui.session.settings.brightness * 100).toInt()}%", "앱 설정 준비")
        ApplyingRow("소리", soundLabel(ui.session.settings.sound), "앱 설정 준비")
        ApplyingRow("조명", "${(ui.session.settings.lighting * 100).toInt()}%", "차량 지원 확인")
    }
}

@Composable
private fun ApplyingRow(label: String, value: String, status: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label · $value", style = MaterialTheme.typography.titleMedium)
                Text(status, color = CommaDesign.Blue)
            }
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

internal fun soundLabel(sound: RestSound): String = when (sound) {
    RestSound.SILENT -> "소리 없이"
    RestSound.SOFT_TONE -> "잔잔한 톤"
    RestSound.RAIN -> "잔잔한 빗소리"
    RestSound.OCEAN -> "해변"
    RestSound.FOREST -> "숲"
    RestSound.LAKE -> "호수"
}
