package ai.naru.app.ui.theme

import ai.naru.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/**
 * 나루 아이콘. 피그마 아이콘과 가장 비슷한 Material Symbols (Outlined, Apache 2.0)를 벡터 리소스로 넣었다.
 * 피그마 SVG를 받으면 res/drawable의 같은 이름 파일만 바꾸면 된다.
 */
object NaruIcons {
    val Back: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_back)
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_close)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_check)
    val Info: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_info)

    /** 나루 제안 */
    val Sparkle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome)
    val Mic: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_mic)

    /** 차량 상태 */
    val Battery: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_battery_horiz_050)
    val Thermometer: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_device_thermostat)
    val Car: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_directions_car)
    val Screen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_desktop_windows)

    /** 날씨·장소 */
    val Wind: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_air)
    val Moon: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_bedtime)
    val Sunny: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_sunny)
    val Sunrise: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_wb_twilight)
    val Tide: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_waves)
    val Location: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_location_on)
    val Warning: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_warning)

    /** 모드·테마 */
    val Tent: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_camping)
    val Clock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule)
    val Alarm: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_alarm)
    val Sound: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_graphic_eq)
    val Light: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lightbulb)
}
