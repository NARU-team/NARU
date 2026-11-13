package ai.naru.app.ui.debug

import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.Gear
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 개발용 차량 조작 패널. 디버그 빌드에서만 보인다.
 *
 * 기상 시각을 기다리지 않고 아침으로 넘기는 버튼을 둔다. 배터리 하한은 실제 배터리 감시로 동작한다.
 * Fake 차량일 때는 기어·배터리도 바꾼다. 실제 차량일 때는 Virtual Console에서 바꾼다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleDebugPanel(
    fake: FakeVehicleGateway?,
    onSimulateWakeTime: () -> Unit,
    modifier: Modifier = Modifier,
    onAddSampleRecords: () -> Unit = {},
    onClearRecords: () -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val source = if (fake != null) "Fake 차량" else "실제 차량 · 기어·배터리는 Virtual Console에서"
            Text(text = "개발용 조작 ($source)", style = MaterialTheme.typography.titleMedium)
            if (fake != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (gear in listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE)) {
                        OutlinedButton(onClick = { fake.setGear(gear) }) { Text("기어 ${gear.name.first()}") }
                    }
                    for (percent in listOf(80, 30, 10)) {
                        OutlinedButton(onClick = { fake.setBatteryPercent(percent) }) { Text("배터리 $percent%") }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSimulateWakeTime) { Text("기상 시각 도달") }
                // 시연용: 에뮬레이터의 밤은 짧아 실제 기록으로는 예상 잔량이 나오지 않는다
                OutlinedButton(onClick = onAddSampleRecords) { Text("샘플 기록 추가") }
                OutlinedButton(onClick = onClearRecords) { Text("기록 지우기") }
            }
        }
    }
}
