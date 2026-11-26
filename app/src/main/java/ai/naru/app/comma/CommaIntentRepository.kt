package ai.naru.app.comma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class CommaProposalSource { GLEO, LLM_SERVER, LOCAL_RULE, UNAVAILABLE }

data class CommaIntentContext(
    val phase: RestPhase,
    val sessionId: String?,
    val selectedMinutes: Int,
    val remainingMinutes: Int?,
    val currentTempC: Float?,
    val recommendedTempC: Float?,
    val temperatureRange: ClosedFloatingPointRange<Float>?,
    val climateSupported: Boolean,
    val ambientLightSupported: Boolean,
    val sound: RestSound,
    val screenBrightnessPercent: Int,
    val recentPreferenceSummary: String?,
)

data class CommaServerIntentResult(
    val requestId: String,
    val reply: String,
    val needsClarification: Boolean,
    val changes: List<CommaProposedChange>,
)

interface CommaIntentRepository {
    val available: Boolean
    suspend fun interpret(requestId: String, utterance: String, context: CommaIntentContext): CommaServerIntentResult?
}

object DisabledCommaIntentRepository : CommaIntentRepository {
    override val available = false
    override suspend fun interpret(requestId: String, utterance: String, context: CommaIntentContext) = null
}

class ServerCommaIntentRepository(private val baseUrl: String) : CommaIntentRepository {
    override val available = baseUrl.isNotBlank()

    override suspend fun interpret(
        requestId: String,
        utterance: String,
        context: CommaIntentContext,
    ): CommaServerIntentResult? {
        if (!available) return null
        return runCatching {
            CommaIntentJson.parse(post(CommaIntentJson.request(requestId, utterance, context)))
        }.getOrNull()?.takeIf { it.requestId == requestId }
    }

    private suspend fun post(body: String): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + "/api/comma/intent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

object CommaIntentJson {
    fun request(requestId: String, utterance: String, context: CommaIntentContext): String {
        val range = context.temperatureRange
        val body = JSONObject()
            .put("requestId", requestId)
            .put("utterance", utterance)
            .put("context", JSONObject()
                .put("stage", context.phase.name)
                .put("sessionId", context.sessionId ?: JSONObject.NULL)
                .put("selectedMinutes", context.selectedMinutes)
                .put("remainingMinutes", context.remainingMinutes ?: JSONObject.NULL)
                .put("currentTempC", context.currentTempC?.toDouble() ?: JSONObject.NULL)
                .put("recommendedTempC", context.recommendedTempC?.toDouble() ?: JSONObject.NULL)
                .put("tempMinC", range?.start?.toDouble() ?: JSONObject.NULL)
                .put("tempMaxC", range?.endInclusive?.toDouble() ?: JSONObject.NULL)
                .put("climateSupported", context.climateSupported)
                .put("ambientLightSupported", context.ambientLightSupported)
                .put("sound", context.sound.name)
                .put("screenBrightnessPercent", context.screenBrightnessPercent.coerceIn(5, 100))
                .put("recentPreferenceSummary", context.recentPreferenceSummary ?: JSONObject.NULL))
        return body.toString()
    }

    fun parse(json: String): CommaServerIntentResult {
        val root = JSONObject(json)
        val proposals = root.getJSONArray("proposals")
        return CommaServerIntentResult(
            requestId = root.getString("requestId"),
            reply = root.getString("reply"),
            needsClarification = root.optBoolean("needsClarification"),
            changes = (0 until proposals.length()).mapNotNull { parseChange(proposals.getJSONObject(it)) },
        )
    }

    private fun parseChange(value: JSONObject): CommaProposedChange? = when (value.optString("type")) {
        "SET_DURATION" -> value.intOrNull("minutes")?.let(CommaProposedChange::Duration)
        "SET_TEMPERATURE" -> value.floatOrNull("celsius")?.let(CommaProposedChange::TemperatureAbsolute)
        "ADJUST_TEMPERATURE" -> value.floatOrNull("deltaC")?.let(CommaProposedChange::TemperatureDelta)
        "SET_REST_SOUND" -> value.stringOrNull("sound")
            ?.let { runCatching { RestSound.valueOf(it) }.getOrNull() }
            ?.let(CommaProposedChange::Sound)
        "SET_SCREEN_BRIGHTNESS" -> value.intOrNull("percent")?.let(CommaProposedChange::Brightness)
        "SET_AMBIENT_LIGHT" -> value.intOrNull("percent")?.let(CommaProposedChange::AmbientLight)
        "EXTEND_REST" -> value.intOrNull("minutes")?.let(CommaProposedChange::Extension)
        else -> null
    }

    private fun JSONObject.intOrNull(key: String) = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.floatOrNull(key: String) = if (!has(key) || isNull(key)) null else getDouble(key).toFloat()
    private fun JSONObject.stringOrNull(key: String) = if (!has(key) || isNull(key)) null else getString(key)
}
