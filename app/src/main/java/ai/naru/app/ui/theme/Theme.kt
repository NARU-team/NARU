package ai.naru.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ai.naru.app.R
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 피그마 v2의 밝은 테마 (이슈 #29). 야간 모드(어두운 테마)는 후속 이슈.
 * Material 컴포넌트가 디자인 색을 쓰도록 토큰을 Material 색 역할에 연결한다.
 * - primary = 주요 버튼(진회색): 기본 Button이 디자인의 주요 버튼 색이 된다
 * - 강조 파랑은 Material 역할이 맞지 않아 [NaruTheme.colors].accent로 쓴다
 */
private fun colorScheme(c: NaruColors) = lightColorScheme(
    primary = c.primaryButton,
    onPrimary = c.onPrimaryButton,
    primaryContainer = c.accentContainer,
    onPrimaryContainer = c.text,
    secondary = c.accent,
    onSecondary = Color.White,
    secondaryContainer = c.secondaryButton,
    onSecondaryContainer = c.text,
    tertiary = c.toggleOn,
    background = c.background,
    onBackground = c.text,
    surface = c.background,
    onSurface = c.text,
    surfaceVariant = c.secondaryButton,
    onSurfaceVariant = c.textSecondary,
    surfaceContainerLowest = c.card,
    surfaceContainerLow = c.card,
    surfaceContainer = c.card,
    surfaceContainerHigh = c.card,
    surfaceContainerHighest = c.secondaryButton,
    outline = c.textTertiary,
    outlineVariant = c.segment,
    error = Color(0xFFD93A2B),
    errorContainer = c.warningCard,
    onErrorContainer = c.text,
)

/**
 * 차량 화면용 글씨 배율.
 * Pleos 에뮬레이터(2560×1440, 160dpi)에서 기본 글씨가 작아 한눈에 읽기 어려웠다 (2026-11-13 확인).
 * 화면마다 키우지 않고 테마에서 한 번에 키운다.
 */
private const val CAR_TEXT_SCALE = 1.35f

/**
 * Asta Sans (42dot, SIL Open Font License 1.1). 피그마 디자인 글꼴.
 * 가변 글꼴 하나(res/font/asta_sans.ttf)에서 굵기별로 꺼내 쓴다. 정적 굵기 3개(7.7MB)보다 작다(5.8MB).
 */
@OptIn(ExperimentalTextApi::class) // FontVariation 설정 (minSdk 28이라 가변 글꼴 지원)
private fun astaSans(weight: FontWeight) = Font(
    R.font.asta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val AstaSans = FontFamily(
    astaSans(FontWeight.Normal),
    astaSans(FontWeight.Medium),
    astaSans(FontWeight.SemiBold),
    astaSans(FontWeight.Bold),
)

private fun TextStyle.scaled() = copy(
    fontFamily = AstaSans,
    fontSize = fontSize * CAR_TEXT_SCALE,
    lineHeight = lineHeight * CAR_TEXT_SCALE,
)

/**
 * 글자 계층 (피그마 기준, 배율 적용 전 값).
 * - headlineLarge: 카드 큰 값 (06:50, 만조 02:14)
 * - headlineMedium: 화면 제목 ("오늘 밤은 다른 곳을 권해요")
 * - titleLarge: 단계 헤더 ("취침 준비 2/3"), 카드 강조 문장
 * - titleMedium: 카드 이름, 버튼
 * - bodyLarge/bodyMedium: 본문·설명
 * - labelMedium: 출처, 하단 안내, 칩
 */
private val NaruTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold).scaled(),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold).scaled(),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold).scaled(),
        headlineLarge = headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold).scaled(),
        headlineMedium = headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold).scaled(),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold).scaled(),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold).scaled(),
        titleMedium = titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold).scaled(),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold).scaled(),
        bodyLarge = bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp).scaled(),
        bodyMedium = bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp).scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold).scaled(),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal).scaled(),
        labelSmall = labelSmall.scaled(),
    )
}

private val LocalNaruColors = staticCompositionLocalOf { NaruColors() }

object NaruTheme {
    /** 디자인 토큰 색 (강조 파랑, 주의 카드 등 Material 역할에 없는 색) */
    val colors: NaruColors
        @Composable @ReadOnlyComposable get() = LocalNaruColors.current
}

@Composable
fun NaruTheme(content: @Composable () -> Unit) {
    val colors = NaruColors()
    CompositionLocalProvider(LocalNaruColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme(colors),
            typography = NaruTypography,
            content = content,
        )
    }
}
