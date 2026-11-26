package ai.naru.app.comma

import ai.naru.app.NaruApplication
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Official Gleo action entry point. It only validates and creates a proposal.
 * Vehicle and app settings are changed by RestViewModel only after explicit approval.
 */
class CommaGleoActivity : ComponentActivity() {
    private val callback by lazy { GleoCallbackSender(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val action = intent?.action
        if (action !in SUPPORTED_ACTIONS) {
            finishWithFailure(action ?: "UNKNOWN", "The requested function is not supported.")
            return
        }
        val functionName = requireNotNull(action)
        if (functionName == GleoCommaContract.ACTION_GET_STATUS) {
            lifecycleScope.launch { respondWithStatus() }
            return
        }
        val raw = intent.getStringExtra(GleoCommaContract.ACTION_PARAM_EXTRA)
        if (raw != null && raw.length > GleoCommaContract.MAX_ACTION_PARAM_LENGTH) {
            finishWithFailure(functionName, "The action parameters are too large.")
            return
        }
        val parameters = parseParameters(raw)
        if (parameters == null || !GleoCommaContract.isValidEnvelope(functionName, parameters)) {
            finishWithFailure(functionName, "The action parameters are invalid.")
            return
        }
        val requestId = (parameters["requestId"] as? String)
            ?.takeIf { it.length in 1..100 }
            ?: (functionName + ":" + raw.orEmpty().hashCode())
        val accepted = GleoCommaActionStore.accept(
            GleoCommaAction(functionName, requestId, parameters),
        )
        callback.send(
            GleoCallback(
                functionName = functionName,
                status = if (accepted) GleoCallbackStatus.ASKING else GleoCallbackStatus.FAIL,
                message = if (accepted) {
                    "User confirmation is required in the NARU app."
                } else {
                    "This action request was already received."
                },
            ),
        )
        if (accepted) {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }?.let(::startActivity)
        }
        finish()
    }

    private suspend fun respondWithStatus() {
        val stored = RestApplicationServices.activeSession(applicationContext).load()
        val charging = (application as NaruApplication).commaChargingSource.state.value
        val data = JSONObject().apply {
            put("active", stored != null)
            put("phase", stored?.phase?.name ?: RestPhase.READY.name)
            put("chargingState", charging.state.name)
            put("chargePortConnected", charging.portConnected)
            put("chargingAvailability", charging.availability.name)
            stored?.let {
                put("remainingMillis", (it.endAtWallMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            }
        }.toString()
        callback.send(
            GleoCallback(
                functionName = GleoCommaContract.ACTION_GET_STATUS,
                status = GleoCallbackStatus.SUCCESS,
                message = if (stored == null) {
                    "There is no active rest session."
                } else {
                    "The current rest session status was retrieved."
                },
                data = "[$data]",
            ),
        )
        finish()
    }

    private fun finishWithFailure(functionName: String, message: String) {
        callback.send(GleoCallback(functionName, GleoCallbackStatus.FAIL, message))
        finish()
    }

    private fun parseParameters(raw: String?): Map<String, Any?>? = runCatching {
        val first = JSONTokener(raw ?: "{}").nextValue()
        val json = when (first) {
            is JSONObject -> first
            is String -> JSONObject(first)
            else -> return null
        }
        json.keys().asSequence().associateWith { key ->
            json.opt(key).takeUnless { it === JSONObject.NULL }
        }
    }.getOrNull()

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            GleoCommaContract.ACTION_PROPOSE_SETTINGS,
            GleoCommaContract.ACTION_EXTEND_REST,
            GleoCommaContract.ACTION_GET_STATUS,
        )
    }
}
