package ai.naru.app.comma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RestSound { SILENT, SOFT_TONE, RAIN, OCEAN, FOREST, LAKE }
data class CabinSettings(
    val brightness: Float = 0.2f,
    val lighting: Float = 0.15f,
    val sound: RestSound = RestSound.SILENT
)

/** App-local state. Never changes vehicle lighting, system brightness or system volume. */
interface CabinController {
    val settings: StateFlow<CabinSettings>
    fun apply(settings: CabinSettings)
}

class AppCabinController : CabinController {
    private val mutable = MutableStateFlow(CabinSettings(0.7f, 0f))
    override val settings: StateFlow<CabinSettings> = mutable
    override fun apply(settings: CabinSettings) { mutable.value = settings }
}
