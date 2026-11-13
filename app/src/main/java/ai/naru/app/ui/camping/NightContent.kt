package ai.naru.app.ui.camping

import ai.naru.app.camping.CampingUiState
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.WakeBasis
import ai.naru.app.camping.night.NightThresholds
import ai.naru.app.vehicle.FailureReason
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 밤 단계: 오늘 밤 설정, 차량 제어 결과, 테마 소리 켜기/끄기. */
@Composable
fun NightContent(state: CampingUiState, onToggleSound: () -> Unit) {
    val settings = state.bedtime?.settings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        settings?.let {
            Text(text = "${it.theme.themeLabel()} 테마로 쉬는 중", style = MaterialTheme.typography.headlineMedium)
            val basis = if (it.wakeBasis == WakeBasis.SUNRISE) " (일출)" else ""
            Text(text = "기상 ${it.wakeAt.format(timeFormat)}$basis", style = MaterialTheme.typography.titleLarge)
        }

        state.temperatureApply?.let {
            Text(text = it.notice(subject = "설정 온도", applied = "설정 온도를 ${settings?.targetTempC?.formatTemp() ?: "-"}°C로 맞췄어요."), style = MaterialTheme.typography.bodyLarge)
        }
        state.lightApply?.let {
            Text(text = it.notice(subject = "테마 조명", applied = "테마 조명을 켰어요."), style = MaterialTheme.typography.bodyLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "테마 소리", style = MaterialTheme.typography.titleLarge)
            Switch(checked = state.soundOn, onCheckedChange = { onToggleSound() })
            if (state.soundOn && !state.soundPlaying) {
                Text(text = "다른 소리가 재생 중이라 지금은 들려드릴 수 없어요.", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text(
            text = "배터리가 ${NightThresholds.BATTERY_LIMIT_PERCENT}%가 되면 알려드리고 아침 단계로 넘어가요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 차량 제어 결과 안내. 실패하면 자동으로 다시 시도하지 않고 직접 조작을 안내한다. */
internal fun ControlResult.notice(subject: String, applied: String): String = when (this) {
    ControlResult.Applied -> applied
    ControlResult.Unverified -> "$subject 변경을 요청했지만 반영을 확인하지 못했어요. 차량 화면에서 확인해 주세요."
    ControlResult.NotSupported -> "이 차량은 $subject 제어를 지원하지 않아요. 필요하면 직접 조절해 주세요."
    is ControlResult.Failed -> when (reason) {
        FailureReason.PERMISSION -> "$subject 을(를) 바꿀 권한이 없어요. 차량에서 직접 조절해 주세요."
        else -> "$subject 을(를) 바꾸지 못했어요. 차량에서 직접 조절해 주세요."
    }
}
