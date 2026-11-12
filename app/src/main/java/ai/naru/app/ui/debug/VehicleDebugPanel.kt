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
 * Fake 차량의 기어·배터리를 바꾸고, 아직 구현 전인 알람·배터리 감시 대신 사건을 손으로 발생시킨다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleDebugPanel(
    fake: FakeVehicleGateway,
    onSimulateWakeTime: () -> Unit,
    onSimulateBatteryLow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "개발용 차량 조작 (Fake)", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (gear in listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE)) {
                    OutlinedButton(onClick = { fake.setGear(gear) }) { Text("기어 ${gear.name.first()}") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (percent in listOf(80, 30, 10)) {
                    OutlinedButton(onClick = { fake.setBatteryPercent(percent) }) { Text("배터리 $percent%") }
                }
                OutlinedButton(onClick = onSimulateWakeTime) { Text("기상 시각 도달") }
                OutlinedButton(onClick = onSimulateBatteryLow) { Text("배터리 하한 도달") }
            }
        }
    }
}
