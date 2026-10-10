package hu.galvill.frekvencia

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Mode { BINAURAL, ISO, PURE }

fun fmt(f: Double): String =
    String.format(Locale.US, "%.2f", f).trimEnd('0').trimEnd('.')

/**
 * Hangszintézis saját szálon, AudioTrack streamelésével.
 * A paraméterek menet közben is állíthatók; a fázisok folytonosak, így nincs kattanás.
 */
object Engine {
    private const val SR = 44100
    private const val FRAMES = 1024
    private const val SCOPE_DECIMATE = 16

    @Volatile var freq = 7.83
    @Volatile var carrier = 200.0
    @Volatile var mode = Mode.BINAURAL
    @Volatile var volume = 0.3f
    @Volatile var name = "Schumann alapfrekvencia"
    /** System.currentTimeMillis() szerinti leállási idő, 0 = nincs időzítő */
    @Volatile var endAtMs = 0L

    @Volatile var running = false
        private set
    @Volatile private var stopRequested = false

    /** Lejátszik és nincs leállítás alatt */
    val active: Boolean get() = running && !stopRequested

    /** Oszcilloszkóp-puffer (körkörös) */
    val scope = FloatArray(512)
    @Volatile var scopePos = 0
        private set

    var onFinished: (() -> Unit)? = null

    @Synchronized
    fun start() {
        if (running) {           // leállítás közben újraindítva: visszaúsztatás
            stopRequested = false
            return
        }
        stopRequested = false
        running = true
        Thread({ loop() }, "frekvencia-audio").start()
    }

    fun requestStop() {
        stopRequested = true
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, FRAMES * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buf = ShortArray(FRAMES * 2)
        val twoPi = 2 * PI
        var pL = 0.0
        var pR = 0.0
        var pM = 0.0
        var gain = 0.0
        val rampUp = 1.0 / (SR * 1.5)     // 1,5 s beúsztatás
        val rampDownFast = 1.0 / (SR * 0.1)
        val rampDownStop = 1.0 / (SR * 1.2) // 1,2 s kiúsztatás
        var dec = 0
        var sp = scopePos

        try {
            track.play()
            while (true) {
                if (!stopRequested && endAtMs > 0 && System.currentTimeMillis() >= endAtMs) {
                    stopRequested = true
                }
                val stopping = stopRequested
                val target = if (stopping) 0.0 else volume.toDouble()
                val m = mode
                val f = freq
                val c = carrier
                val norm = 1.0 / maxOf(volume.toDouble(), 0.05)

                for (i in 0 until FRAMES) {
                    if (gain < target) gain = minOf(target, gain + rampUp)
                    else if (gain > target) gain = maxOf(target, gain - if (stopping) rampDownStop else rampDownFast)

                    val l: Double
                    val r: Double
                    when (m) {
                        Mode.BINAURAL -> {
                            l = sin(pL); r = sin(pR)
                            pL += twoPi * c / SR; pR += twoPi * (c + f) / SR
                        }
                        Mode.ISO -> {
                            val env = 0.5 - 0.5 * cos(pM)
                            val s = sin(pL) * env
                            l = s; r = s
                            pL += twoPi * c / SR; pM += twoPi * f / SR
                        }
                        Mode.PURE -> {
                            l = sin(pL); r = l
                            pL += twoPi * f / SR
                        }
                    }
                    if (pL > twoPi) pL -= twoPi
                    if (pR > twoPi) pR -= twoPi
                    if (pM > twoPi) pM -= twoPi

                    buf[2 * i] = (l * gain * 29000).toInt().toShort()
                    buf[2 * i + 1] = (r * gain * 29000).toInt().toShort()

                    if (++dec >= SCOPE_DECIMATE) {
                        dec = 0
                        scope[sp] = ((l + r) * 0.5 * gain * norm).toFloat()
                        sp = (sp + 1) % scope.size
                        scopePos = sp
                    }
                }
                track.write(buf, 0, buf.size)

                if (stopping && gain <= 0.0) {
                    synchronized(this) {
                        if (stopRequested) running = false
                    }
                    if (!running) break
                }
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
            running = false
            scope.fill(0f)
            onFinished?.invoke()
        }
    }
}
