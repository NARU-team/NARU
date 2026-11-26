package ai.naru.app.comma

import android.content.Context
import android.content.Intent
import org.json.JSONObject

object GleoCommaContract {
    const val MAX_ACTION_PARAM_LENGTH = 4_096
    const val ACTION_PROPOSE_SETTINGS = "NARU_COMMA_PROPOSE_SETTINGS"
    const val ACTION_EXTEND_REST = "NARU_COMMA_EXTEND_REST"
    const val ACTION_GET_STATUS = "NARU_COMMA_GET_STATUS"
    const val ACTION_PARAM_EXTRA = "ACTION_PARAM"
    const val CALLBACK_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    fun isValidEnvelope(functionName: String, parameters: Map<String, Any?>): Boolean {
        val requestId = parameters["requestId"]
        if (requestId != null && (requestId !is String || requestId.length !in 1..100)) return false
        val allowedKeys = when (functionName) {
            ACTION_PROPOSE_SETTINGS -> SETTINGS_KEYS
            ACTION_EXTEND_REST -> setOf("requestId", "minutes")
            ACTION_GET_STATUS -> setOf("requestId")
            else -> return false
        }
        if (!allowedKeys.containsAll(parameters.keys)) return false
        return when (functionName) {
            ACTION_PROPOSE_SETTINGS -> {
                val changeKeys = parameters.keys - "requestId"
                changeKeys.isNotEmpty() &&
                    numberIn(parameters, "durationMinutes", 1.0, 180.0) &&
                    numberIn(parameters, "targetTemperatureCelsius", -50.0, 80.0) &&
                    numberIn(parameters, "temperatureDeltaCelsius", -5.0, 5.0, rejectZero = true) &&
                    numberIn(parameters, "screenBrightnessPercent", 0.0, 100.0) &&
                    numberIn(parameters, "ambientLightPercent", 0.0, 100.0) &&
                    (parameters["restSound"] == null || parameters["restSound"] in SOUND_NAMES)
            }
            ACTION_EXTEND_REST -> (parameters["minutes"] as? Number)?.toInt() in setOf(5, 10)
            ACTION_GET_STATUS -> true
            else -> false
        }
    }

    fun proposal(
        functionName: String,
        parameters: Map<String, Any?>,
        phase: RestPhase,
        capabilities: VehicleCapabilities,
    ): CommaIntentProposal? {
        val changes = when (functionName) {
            ACTION_PROPOSE_SETTINGS -> settingsChanges(parameters)
            ACTION_EXTEND_REST -> listOfNotNull(
                (parameters["minutes"] as? Number)?.toInt()?.let(CommaProposedChange::Extension),
            )
            else -> emptyList()
        }
        if (!CommaIntentRules.validate(changes, phase, capabilities)) return null
        return CommaIntentProposal(
            utterance = "Gleo Action: $functionName",
            description = "Gleo가 설정 변경을 제안했습니다.",
            change = changes.first(),
            additionalChanges = changes.drop(1),
            source = CommaProposalSource.GLEO,
            requestId = parameters["requestId"] as? String,
            phaseAtCreation = phase,
        )
    }

    private fun numberIn(
        parameters: Map<String, Any?>,
        key: String,
        min: Double,
        max: Double,
        rejectZero: Boolean = false,
    ): Boolean {
        val raw = parameters[key] ?: return true
        val value = (raw as? Number)?.toDouble() ?: return false
        return value.isFinite() && value in min..max && (!rejectZero || value != 0.0)
    }

    private val SETTINGS_KEYS = setOf(
        "requestId",
        "durationMinutes",
        "targetTemperatureCelsius",
        "temperatureDeltaCelsius",
        "screenBrightnessPercent",
        "ambientLightPercent",
        "restSound",
    )
    private val SOUND_NAMES = RestSound.entries.mapTo(mutableSetOf()) { it.name }

    private fun settingsChanges(parameters: Map<String, Any?>): List<CommaProposedChange> = buildList {
        (parameters["durationMinutes"] as? Number)?.toInt()?.let {
            add(CommaProposedChange.Duration(it))
        }
        (parameters["targetTemperatureCelsius"] as? Number)?.toFloat()?.let {
            add(CommaProposedChange.TemperatureAbsolute(it))
        }
        (parameters["temperatureDeltaCelsius"] as? Number)?.toFloat()?.let {
            add(CommaProposedChange.TemperatureDelta(it))
        }
        (parameters["screenBrightnessPercent"] as? Number)?.toInt()?.let {
            add(CommaProposedChange.Brightness(it))
        }
        (parameters["ambientLightPercent"] as? Number)?.toInt()?.let {
            add(CommaProposedChange.AmbientLight(it))
        }
        (parameters["restSound"] as? String)?.let { raw ->
            runCatching { RestSound.valueOf(raw) }.getOrNull()?.let {
                add(CommaProposedChange.Sound(it))
            }
        }
    }
}

enum class GleoCallbackStatus(val wireValue: String) {
    SUCCESS("success"),
    ASKING("asking"),
    FAIL("fail"),
}

data class GleoCallback(
    val functionName: String,
    val status: GleoCallbackStatus,
    val message: String,
    val data: String? = null,
    val mode: String = "voice",
) {
    fun toJson(): String = JSONObject().apply {
        put("functionName", functionName)
        put("status", status.wireValue)
        put("message", message)
        put("mode", mode)
        data?.let { put("data", it) }
    }.toString()
}

class GleoCallbackSender(private val context: Context) {
    fun send(callback: GleoCallback) {
        val intent = Intent(GleoCommaContract.CALLBACK_ACTION).apply {
            putExtra("packageName", context.packageName)
            putExtra("response", callback.toJson())
        }
        context.sendBroadcast(intent)
    }
}
