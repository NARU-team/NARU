package ai.naru.app.comma

sealed interface CommaProposedChange {
    data class Duration(val minutes: Int) : CommaProposedChange
    data class TemperatureAbsolute(val celsius: Float) : CommaProposedChange
    data class TemperatureDelta(val deltaCelsius: Float) : CommaProposedChange
    data class Sound(val sound: RestSound) : CommaProposedChange
    data class Brightness(val percent: Int) : CommaProposedChange
    data class AmbientLight(val percent: Int) : CommaProposedChange
    data class Extension(val minutes: Int) : CommaProposedChange
}

data class CommaIntentProposal(
    val utterance: String,
    val description: String,
    val change: CommaProposedChange,
    val additionalChanges: List<CommaProposedChange> = emptyList(),
    val source: CommaProposalSource = CommaProposalSource.LOCAL_RULE,
    val requestId: String? = null,
    val phaseAtCreation: RestPhase? = null,
) {
    val changes: List<CommaProposedChange> get() = listOf(change) + additionalChanges
}

object CommaIntentRules {
    fun interpretLocally(utterance: String, phase: RestPhase): CommaIntentProposal? {
        val normalized = utterance.trim().replace(" ", "")
        val changes = buildList {
            val extension = Regex("""(5|10)분.*더""").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (extension != null) {
                if (phase in setOf(RestPhase.RESTING, RestPhase.WAKING)) {
                    add(CommaProposedChange.Extension(extension))
                }
            } else {
                val duration = Regex("""(\d{1,3})분.*(쉬|쉴|휴식)""").find(normalized)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (duration != null && duration in 1..180 && phase == RestPhase.READY) {
                    add(CommaProposedChange.Duration(duration))
                }
            }

            val absoluteTemperature = Regex("""(\d{2}(?:\.5)?)도(?:로|에|맞)""").find(normalized)
                ?.groupValues?.getOrNull(1)?.toFloatOrNull()
            if (absoluteTemperature != null && phase in setOf(RestPhase.READY, RestPhase.RESTING)) {
                add(CommaProposedChange.TemperatureAbsolute(absoluteTemperature))
            } else if ("따뜻" in normalized && phase in setOf(RestPhase.READY, RestPhase.RESTING)) {
                add(CommaProposedChange.TemperatureDelta(1f))
            } else if (("시원" in normalized || "춥게" in normalized) &&
                phase in setOf(RestPhase.READY, RestPhase.RESTING)) {
                add(CommaProposedChange.TemperatureDelta(-1f))
            }

            if (phase in setOf(RestPhase.READY, RestPhase.RESTING)) {
                when {
                    "소리없이" in normalized || "무음" in normalized ->
                        add(CommaProposedChange.Sound(RestSound.SILENT))
                    "빗소리" in normalized || "비소리" in normalized ->
                        add(CommaProposedChange.Sound(RestSound.RAIN))
                    "파도" in normalized || "해변" in normalized ->
                        add(CommaProposedChange.Sound(RestSound.OCEAN))
                    "숲소리" in normalized ->
                        add(CommaProposedChange.Sound(RestSound.FOREST))
                    "호수" in normalized || "잔물결" in normalized ->
                        add(CommaProposedChange.Sound(RestSound.LAKE))
                }
                if ("화면" in normalized && "어둡" in normalized) add(CommaProposedChange.Brightness(15))
                if ("조명" in normalized && "어둡" in normalized) add(CommaProposedChange.AmbientLight(20))
            }
        }.distinct()

        if (changes.isEmpty()) return null
        return CommaIntentProposal(
            utterance = utterance,
            description = changes.joinToString(" · ", transform = ::description),
            change = changes.first(),
            additionalChanges = changes.drop(1),
            source = CommaProposalSource.LOCAL_RULE,
            phaseAtCreation = phase,
        )
    }

    fun fromServer(
        utterance: String,
        phase: RestPhase,
        result: CommaServerIntentResult,
    ): CommaIntentProposal? {
        val changes = result.changes.distinct()
        if (changes.isEmpty()) return null
        return CommaIntentProposal(
            utterance = utterance,
            description = changes.joinToString(" · ", transform = ::description),
            change = changes.first(),
            additionalChanges = changes.drop(1),
            source = CommaProposalSource.LLM_SERVER,
            requestId = result.requestId,
            phaseAtCreation = phase,
        )
    }

    fun validate(
        changes: List<CommaProposedChange>,
        phase: RestPhase,
        capabilities: VehicleCapabilities,
    ): Boolean = changes.isNotEmpty() && changes.all { change ->
        when (change) {
            is CommaProposedChange.Duration -> phase == RestPhase.READY && change.minutes in 1..180
            is CommaProposedChange.Extension ->
                phase in setOf(RestPhase.RESTING, RestPhase.WAKING) && change.minutes in setOf(5, 10)
            is CommaProposedChange.TemperatureAbsolute ->
                phase in setOf(RestPhase.READY, RestPhase.RESTING) &&
                    capabilities.climateTemperatureRange?.contains(change.celsius) == true
            is CommaProposedChange.TemperatureDelta ->
                phase in setOf(RestPhase.READY, RestPhase.RESTING) &&
                    capabilities.climateTemperatureRange != null &&
                    change.deltaCelsius != 0f && kotlin.math.abs(change.deltaCelsius) <= 5f
            is CommaProposedChange.Sound, is CommaProposedChange.Brightness ->
                phase in setOf(RestPhase.READY, RestPhase.RESTING)
            is CommaProposedChange.AmbientLight ->
                phase in setOf(RestPhase.READY, RestPhase.RESTING) &&
                    capabilities.lighting == ControlSupport.SUPPORTED &&
                    change.percent in 0..100
        }
    }

    private fun description(change: CommaProposedChange): String = when (change) {
        is CommaProposedChange.Duration -> "휴식 시간을 ${change.minutes}분으로 변경"
        is CommaProposedChange.TemperatureAbsolute -> "공조 목표를 ${change.celsius}°C로 변경"
        is CommaProposedChange.TemperatureDelta -> "공조 목표를 ${change.deltaCelsius}°C 변경"
        is CommaProposedChange.Sound -> "자연음을 ${soundLabel(change.sound)}로 변경"
        is CommaProposedChange.Brightness -> "화면 밝기를 ${change.percent}%로 변경"
        is CommaProposedChange.AmbientLight -> "차량 조명을 ${change.percent}%로 변경"
        is CommaProposedChange.Extension -> "현재 휴식을 ${change.minutes}분 연장"
    }
}
