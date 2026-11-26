package ai.naru.app.comma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SoundChoice(
    val sound: RestSound,
    val title: String,
    val detail: String,
    val previewable: Boolean,
)

private val figmaSoundChoices = listOf(
    SoundChoice(RestSound.RAIN, "잔잔한 빗소리", "차분하게 고른 소리", true),
    SoundChoice(RestSound.OCEAN, "해변", "낮은 파도 소리", true),
    SoundChoice(RestSound.FOREST, "숲", "풀벌레와 바람 소리", true),
    SoundChoice(RestSound.LAKE, "호수", "잔물결 소리", true),
    SoundChoice(RestSound.SILENT, "소리 없이", "알람만 울려요", false),
)

@Composable
internal fun CommaSoundScreen(
    ui: RestUiState,
    actions: RestUiActions,
    onBack: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ui.session.settings.sound) }
    var previewing by remember { mutableStateOf<RestSound?>(null) }
    var previewFailed by remember { mutableStateOf(false) }
    val previous = ui.history.records.firstOrNull { it.source == RecordSource.REAL }?.sound

    DisposableEffect(previewing) {
        previewFailed = previewing?.let { !RestAudioCoordinator.playNature(it) } ?: false
        onDispose { RestAudioCoordinator.stopNature() }
    }

    CommaDesignTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            val wide = maxWidth >= 900.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 36.dp else 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, modifier = Modifier.sizeIn(48.dp, 48.dp)) {
                        Text("‹", fontSize = 32.sp, modifier = Modifier.semantics {
                            contentDescription = "소리 선택 취소"
                        })
                    }
                    Text("소리 고르기", style = MaterialTheme.typography.headlineLarge)
                }
                Text("어떤 소리와 쉴까요?", style = MaterialTheme.typography.headlineLarge)
                figmaSoundChoices.forEach { choice ->
                    val isSelected = selected == choice.sound
                    Surface(
                        onClick = {
                            selected = choice.sound
                            if (!choice.previewable) previewing = null
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(2.dp, CommaDesign.Blue)
                        } else null,
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(choice.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (previous == choice.sound) "지난번에 고른 소리" else choice.detail,
                                    color = CommaDesign.Muted,
                                )
                            }
                            if (choice.previewable) {
                                CommaButton(
                                    if (previewing == choice.sound) "미리 듣기 중" else "미리 듣기",
                                    onClick = {
                                        previewing = if (previewing == choice.sound) null else choice.sound
                                    },
                                )
                            }
                        }
                    }
                }
                if (previewFailed) {
                    Text("소리를 재생하지 못했습니다. 선택은 적용할 수 있습니다.",
                        color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    CommaButton("취소", onBack)
                    CommaButton("이 소리로 정하기", onClick = {
                        previewing = null
                        actions.configure(ui.session.settings.copy(sound = selected))
                        onBack()
                    }, filled = true)
                }
                Text("다음 휴식에는 실제 저장된 최근 소리만 우선 표시합니다.",
                    color = CommaDesign.Muted)
            }
        }
    }
}
