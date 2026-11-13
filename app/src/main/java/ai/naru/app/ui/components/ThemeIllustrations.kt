package ai.naru.app.ui.components

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.ui.theme.NaruShapes
import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 밤 테마 그림 (피그마 v2 "밤 분위기 3/3"). 이미지 파일 없이 도형으로 그린다.
 * - 해변: 하늘색→살구색 하늘, 해, 물결
 * - 숲: 연두 하늘, 나무 세 그루
 * - 호수: 밤하늘, 달, 별, 잔물결
 */
@Composable
fun ThemeIllustration(theme: PlaceType, modifier: Modifier = Modifier) {
    when (theme) {
        PlaceType.BEACH -> BeachScene(modifier)
        PlaceType.FOREST -> ForestScene(modifier)
        PlaceType.LAKE -> LakeScene(modifier)
    }
}

@Composable
private fun BeachScene(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDDEBFA), Color(0xFFFCE7CF))))) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 30.dp).size(54.dp)
                .clip(NaruShapes.pill).background(Color(0xFFFFD39A)),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            waves(Color.White.copy(alpha = 0.9f), rows = listOf(0.62f, 0.78f), amplitude = 5f)
        }
    }
}

@Composable
private fun ForestScene(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDCEFD8), Color(0xFFB9DDB2))))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trees = listOf(0.28f to 0.55f, 0.48f to 0.75f, 0.78f to 0.5f)
            for ((cx, h) in trees) {
                val width = size.width * 0.16f
                val top = size.height * (1f - h)
                val path = Path().apply {
                    moveTo(size.width * cx, top)
                    lineTo(size.width * cx - width / 2, size.height)
                    lineTo(size.width * cx + width / 2, size.height)
                    close()
                }
                drawPath(path, Color(0xFF3F8A55))
            }
        }
    }
}

@Composable
private fun LakeScene(modifier: Modifier) {
    val colors = NaruTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.nightBrush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for ((fx, fy) in listOf(0.12f to 0.25f, 0.3f to 0.5f, 0.45f to 0.18f, 0.6f to 0.42f, 0.88f to 0.6f)) {
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.6f, center = Offset(size.width * fx, size.height * fy))
            }
            waves(Color.White.copy(alpha = 0.85f), rows = listOf(0.8f), amplitude = 4f, from = 0.38f, to = 0.62f)
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 16.dp).size(40.dp)
                .clip(NaruShapes.pill).background(colors.moon),
        )
    }
}

/** 가로 물결 선 */
private fun DrawScope.waves(color: Color, rows: List<Float>, amplitude: Float, from: Float = 0.12f, to: Float = 0.88f) {
    for (row in rows) {
        val y = size.height * row
        val path = Path()
        val start = size.width * from
        val end = size.width * to
        val step = (end - start) / 6
        path.moveTo(start, y)
        var x = start
        var up = true
        while (x < end - 1) {
            path.quadraticTo(x + step / 2, y + if (up) -amplitude * 2 else amplitude * 2, x + step, y)
            x += step
            up = !up
        }
        drawPath(path, color, style = Stroke(width = 2.5f))
    }
}
