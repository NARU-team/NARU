package ai.naru.app.ui.home

import ai.naru.app.ui.components.NaruBottomBar
import ai.naru.app.ui.components.NaruCard
import ai.naru.app.ui.components.NaruIconCircle
import ai.naru.app.ui.components.NaruPrimaryButton
import ai.naru.app.ui.components.NaruSecondaryButton
import ai.naru.app.ui.components.NaruStepHeader
import ai.naru.app.ui.theme.FigmaFrame
import ai.naru.app.ui.theme.NaruIcons
import ai.naru.app.ui.theme.NaruSpacing
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 피그마 v2 "처음 시작하기 – 권한 동의" (이슈 #44). 처음 한 번만 보여준다.
 * 위치만 실행 중 승인이 필요한 권한이고(FUSED_LOCATION), 공조·조명·배터리는 앱 설치 때 허용된다.
 * 그래서 위치 줄에만 지금 상태를 보여 주고, 나머지는 무엇에 쓰는지 설명만 한다.
 */
@Composable
fun OnboardingScreen(locationGranted: Boolean, onLater: () -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NaruTheme.colors
    FigmaFrame(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(NaruSpacing.panel), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NaruStepHeader(title = "처음 시작하기")
            Text(text = "쉬는 동안 차를 맞춰 드리려면 권한이 필요해요", style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionRow(NaruIcons.Location, "위치", "도착한 곳의 날씨, 물때, 시설 정보를 찾아요", if (locationGranted) "허용됨" else "허용 필요")
                PermissionRow(NaruIcons.Thermometer, "공조", "쉬는 동안 실내 온도를 맞추고 끝나면 되돌려요", "앱 설치 때 허용")
                PermissionRow(NaruIcons.Light, "실내 조명", "테마에 맞는 조명을 켜고 끝나면 되돌려요", "앱 설치 때 허용")
                PermissionRow(NaruIcons.Battery, "배터리 정보", "잔량을 확인하고 하한에 닿으면 알려 드려요", "앱 설치 때 허용")
            }
            NaruBottomBar(note = "허용하지 않은 기능은 빼고 화면, 소리, 알람만으로도 쉴 수 있어요", noteIcon = NaruIcons.Check) {
                NaruSecondaryButton(text = "나중에", onClick = onLater)
                NaruPrimaryButton(text = "계속", onClick = onContinue)
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, description: String, status: String) {
    val colors = NaruTheme.colors
    NaruCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NaruIconCircle(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = when (status) {
                    "허용됨" -> colors.toggleOn
                    "허용 필요" -> colors.accent
                    else -> colors.textTertiary
                },
            )
        }
    }
}
