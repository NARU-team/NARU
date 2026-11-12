package ai.naru.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// 차량 화면은 밤에 쓰는 경우가 많아 어두운 색상을 기본으로 둔다.
private val NaruColorScheme = darkColorScheme()

@Composable
fun NaruTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NaruColorScheme,
        content = content,
    )
}
