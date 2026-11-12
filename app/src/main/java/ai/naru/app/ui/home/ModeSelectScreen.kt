package ai.naru.app.ui.home

import ai.naru.app.ui.theme.NaruTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 모드 선택 화면. 차박 모드와 쉼표 모드 중 하나를 고른다. */
@Composable
fun ModeSelectScreen(
    onCampingSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "나루", style = MaterialTheme.typography.displayMedium)
        Text(text = "차에서 머무는 모든 순간을 나만의 쉼으로.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ModeCard(
                title = "차박 모드",
                description = "오늘 여기서 밤을 보내요",
                enabled = true,
                onClick = onCampingSelected,
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                title = "쉼표 모드",
                description = "준비 중이에요",
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(text = description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ModeSelectScreenPreview() {
    NaruTheme { ModeSelectScreen(onCampingSelected = {}) }
}
