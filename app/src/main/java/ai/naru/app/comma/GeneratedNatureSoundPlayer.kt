package ai.naru.app.comma

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 외부 파일 없이 짧은 PCM 루프를 만들어 재생한다.
 * 이는 자연음 선택/미리듣기의 오프라인 fallback이며 시스템 음량은 변경하지 않는다.
 */
internal object RestAudioCoordinator {
    private var player: GeneratedNatureSoundPlayer? = null

    @Synchronized
    fun playNature(sound: RestSound): Boolean {
        stopNature()
        return GeneratedNatureSoundPlayer().also { player = it }.play(sound)
    }

    @Synchronized
    fun stopNature() {
        player?.stop()
        player = null
    }
}

internal class GeneratedNatureSoundPlayer {
    private var track: AudioTrack? = null

    fun play(sound: RestSound): Boolean {
        stop()
        if (sound !in NATURE_SOUNDS) return false
        val samples = generateSamples(sound)
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, samples.size * Short.SIZE_BYTES))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also { audioTrack ->
                    audioTrack.write(samples, 0, samples.size)
                    audioTrack.setLoopPoints(0, samples.size, -1)
                    audioTrack.setVolume(OUTPUT_VOLUME)
                    audioTrack.play()
                    track = audioTrack
                }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        track?.let { audioTrack ->
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
        track = null
    }

    private fun generateSamples(sound: RestSound): ShortArray {
        val random = Random(sound.ordinal * 7919)
        var smoothNoise = 0.0
        return ShortArray(SAMPLE_COUNT) { index ->
            val seconds = index.toDouble() / SAMPLE_RATE
            val white = random.nextDouble(-1.0, 1.0)
            smoothNoise = smoothNoise * 0.94 + white * 0.06
            val value = when (sound) {
                RestSound.RAIN -> smoothNoise * 0.65 + white * 0.12
                RestSound.OCEAN -> {
                    val swell = 0.25 + 0.75 * (sin(2 * PI * seconds / 4.0) + 1.0) / 2.0
                    smoothNoise * swell
                }
                RestSound.FOREST -> {
                    val breeze = smoothNoise * 0.35
                    val birdWindow = if ((seconds % 2.7) < 0.22) 1.0 else 0.0
                    breeze + birdWindow * sin(2 * PI * (1200.0 + 500.0 * seconds) * seconds) * 0.18
                }
                RestSound.LAKE -> {
                    val ripple = sin(2 * PI * seconds / 1.8) * 0.08
                    smoothNoise * 0.24 + ripple
                }
                else -> 0.0
            }
            (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * SOURCE_GAIN).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val LOOP_SECONDS = 6
        const val SAMPLE_COUNT = SAMPLE_RATE * LOOP_SECONDS
        const val SOURCE_GAIN = 0.35
        const val OUTPUT_VOLUME = 0.45f
        val NATURE_SOUNDS = setOf(RestSound.RAIN, RestSound.OCEAN, RestSound.FOREST, RestSound.LAKE)
    }
}
