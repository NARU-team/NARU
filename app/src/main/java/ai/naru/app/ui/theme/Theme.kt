package ai.naru.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

// 차량 화면은 밤에 쓰는 경우가 많아 어두운 색상을 기본으로 둔다.
private val NaruColorScheme = darkColorScheme()

/**
 * 차량 화면용 글씨 배율.
 * Pleos 에뮬레이터(2560×1440, 160dpi)에서 기본 글씨가 작아 한눈에 읽기 어려웠다 (2026-11-13 확인).
 * 화면마다 키우지 않고 테마에서 한 번에 키운다.
 */
private const val CAR_TEXT_SCALE = 1.35f

private fun TextStyle.scaled() = copy(fontSize = fontSize * CAR_TEXT_SCALE, lineHeight = lineHeight * CAR_TEXT_SCALE)

private val NaruTypography = Typography().run {
    copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}

@Composable
fun NaruTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaruColorScheme,
        typography = NaruTypography,
        content = content,
    )
}
