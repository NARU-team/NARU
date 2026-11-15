package ai.naru.app.ui.camping

import ai.naru.app.camping.EndReason
import ai.naru.app.camping.record.SessionRecord
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/** 대기 화면: 최근 차박 기록 몇 건 (최신 순). */
@Composable
fun RecordList(records: List<SessionRecord>, limit: Int = 3) {
    if (records.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "최근 차박 기록", style = MaterialTheme.typography.titleMedium)
        for (record in records.sortedByDescending { it.nightStartedAt }.take(limit)) {
            Text(text = record.summary(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun SessionRecord.summary(): String {
    val used = batteryUsed?.let { if (it >= 0) "밤사이 $it% 사용" else "충전됨" } ?: "배터리 기록 없음"
    val hours = (nightHours * 10).roundToInt() / 10.0
    val ended = when (endReason) {
        EndReason.BATTERY_LOW -> " · 배터리 하한으로 종료"
        EndReason.GEAR_LEFT_PARK -> " · 기어 변경으로 종료"
        else -> ""
    }
    return "${nightStartedAt.format(dateFormat)} · $siteName · $used (${hours}시간)$ended"
}
