package ai.naru.app.comma

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private val previewActions = RestUiActions({}, {}, {}, {}, {}, {}, {})
private val previewState = RestUiState(
    vehicle = VehicleState(28f,32f,true,75),
    nowMillis = 1_794_700_800_000L
)

@Preview(name="Landscape", widthDp=1100, heightDp=780)
@Preview(name="Split", widthDp=560, heightDp=790)
@Preview(name="Phone", widthDp=360, heightDp=800)
@Composable
internal fun CommaSettingsPreview() {
    CommaSettingsScreen(previewState, previewActions, {})
}

@Preview(name="Custom minutes", widthDp=560, heightDp=790)
@Composable
internal fun CommaCustomSettingsPreview() {
    CommaSettingsScreen(previewState.copy(session=RestSession(selectedMinutes=20)), previewActions, {})
}

@Preview(widthDp=840, heightDp=800)
@Composable
internal fun CommaHomePreview() {
    CommaHomeScreen(previewState,{})
}
