package cube.run.core

import android.content.Context
import cube.run.data.Settings
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedurally synthesized sound effects, shared by every game.
 *
 * Available sound names (pitch-shift with `rate` 0.5..2.0 for variety):
 *  tap, blip, pop, place, perfect, combo, success, fail,
 *  whoosh, boom, coin, rise, slide, fanfare, drain, bell,
 *  and Outer Space's: moonjump, moonland, flyby, stardust, hum (a loop)
 */
object SoundFx {
    // Investigation hooks: inactive in ordinary runs and release builds.
    @Volatile var testMutedName: String? = null
    @Volatile var testObserver: ((String, Long, Long, Int) -> Unit)? = null
    private const val SR = 44100
    // Loop controls need the pool as well as the queued one-shot playback worker.
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    @Volatile private var ready = false
    private data class Playback(val name: String, val id: Int, val volume: Float, val rate: Float)
    private var playback: SoundPlaybackQueue<Playback>? = null

    private var initializing = false
    @Synchronized fun init(ctx: Context) {
        if (initializing) return
        initializing = true
        thread(name = "sfx-load") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val p = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build()
            pool = p
            playback = SoundPlaybackQueue { effect ->
                if (Settings.soundEnabled) {
                    val observer = if (cube.run.BuildConfig.DEBUG) testObserver else null
                    val before = if (observer != null) System.nanoTime() else 0L
                    val stream = p.play(effect.id, effect.volume, effect.volume, 1, 0, effect.rate)
                    observer?.invoke(effect.name, before, System.nanoTime(), stream)
                }
            }
            val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
            val names = listOf("tap", "blip", "pop", "place", "perfect", "combo", "success", "fail", "whoosh", "boom", "coin", "rise", "slide", "fanfare", "drain", "bell",
                "moonjump", "moonland", "flyby", "stardust", "hum")
            // A redesigned sound gets a new file name, so installed games synthesize it again.
            fun file(name: String) = File(dir, when (name) { "coin" -> "coin-chime-v2.wav"; "stardust" -> "stardust-v3.wav"; else -> "$name.wav" })
            // Installed games already have these WAVs. Do not synthesize all samples again.
            if (names.any { !file(it).exists() || file(it).length() == 0L }) {
                for ((name, pcm) in synthAll()) if (!file(name).exists() || file(name).length() == 0L) file(name).writeBytes(wav(pcm))
            }
            val loaded = java.util.concurrent.atomic.AtomicInteger(0)
            val submitted = java.util.concurrent.atomic.AtomicBoolean(false)
            fun publish() { if (submitted.get() && loaded.get() >= names.size) ready = true }
            p.setOnLoadCompleteListener { _, _, _ -> loaded.incrementAndGet(); publish() }
            for (name in names) ids[name] = p.load(file(name).path, 1)
            submitted.set(true); publish()
        }
    }

    fun play(name: String, rate: Float = 1f, vol: Float = 1f) {
        if (!ready || !Settings.soundEnabled) return
        if (cube.run.BuildConfig.DEBUG && name == testMutedName) return
        val id = ids[name] ?: return
        val v = vol.coerceIn(0f, 1f)
        playback?.offer(Playback(name, id, v, rate.coerceIn(0.5f, 2f)))
    }

    /** Start [name] looping at [vol]; returns its stream (0 if sound is off or not loaded yet — try again later). */
    fun loop(name: String, vol: Float): Int {
        if (!ready || !Settings.soundEnabled) return 0
        val id = ids[name] ?: return 0
        val v = vol.coerceIn(0f, 1f)
        return pool?.play(id, v, v, 2, -1, 1f) ?: 0
    }

    fun setVolume(stream: Int, vol: Float) {
        if (stream == 0) return
        val v = vol.coerceIn(0f, 1f)
        pool?.setVolume(stream, v, v)
    }

    fun stop(stream: Int) { if (stream != 0) pool?.stop(stream) }

    // ------------------------------------------------------------------ synth

    private fun synthAll(): List<Pair<String, ShortArray>> = listOf(
        "tap" to synth(40) { t, p -> sin(t * 1150.0 * TAU) * decay(p, 5.0) },
        "blip" to synth(70) { t, p -> square(t * (640.0 + 420.0 * p)) * 0.5 * decay(p, 3.0) },
        "pop" to synth(70) { t, p -> sin(t * (380.0 + 1500.0 * p * p) * TAU) * decay(p, 4.0) },
        "place" to synth(90) { t, p ->
            (tri(t * 145.0) * 0.8 + noise() * 0.35) * decay(p, 5.0)
        },
        "perfect" to synth(260) { t, p ->
            val f = 1318.0
            (sin(t * f * TAU) + 0.45 * sin(t * f * 2.0 * TAU) + 0.2 * sin(t * f * 3.0 * TAU)) /
                1.65 * decay(p, 4.5)
        },
        "combo" to synth(140) { t, p ->
            sin(t * 880.0 * TAU + 4.0 * sin(t * 26.0 * TAU)) * decay(p, 3.0)
        },
        "success" to arpeggio(doubleArrayOf(523.25, 659.25, 783.99, 1046.5), 120, 460),
        "fail" to synth(420) { t, p ->
            (saw(t * (520.0 - 360.0 * p)) * 0.7 + noise() * 0.12 * p) * decay(p, 2.2)
        },
        "whoosh" to lowpassed(190, 0.10) { _, p ->
            noise() * sin(p * PI).pow(1.4)
        },
        "boom" to lowpassed(380, 0.035) { t, p ->
            (noise() * 0.9 + sin(t * 64.0 * TAU) * 0.8) * decay(p, 3.2)
        },
        "coin" to synth(150) { t, p -> // a soft two-note chime: sine with a whisper of a second harmonic (a square wave here shreds on recordings)
            val f = if (t < 0.03) 987.77 else 1318.5
            (sin(t * f * TAU) * 0.85 + sin(t * f * 2 * TAU) * 0.15) * 0.5 * decay(p, 3.2)
        },
        "rise" to synth(300) { t, p ->
            sin(t * (280.0 + 1000.0 * p.pow(1.5)) * TAU) * (0.6 + 0.4 * sin(t * 30.0 * TAU)) *
                sin(p * PI).pow(0.5)
        },
        "slide" to lowpassed(130, 0.22) { _, p -> noise() * sin(p * PI).pow(0.8) * 0.8 },
        "fanfare" to fanfare(),
        "drain" to drain(),
        "moonjump" to moonJump(),
        "moonland" to moonLand(),
        "flyby" to flyby(),
        "stardust" to stardust(),
        "hum" to hum(),
        "bell" to synth(1400, vol = 0.8) { t, p -> // a singing bowl: a soft strike and inharmonic partials ringing out
            val f = 392.0
            (sin(t * f * TAU) + 0.5 * sin(t * f * 2.76 * TAU) * exp(-p * 3.0) + 0.25 * sin(t * f * 5.4 * TAU) * exp(-p * 6.0)) /
                1.75 * (0.85 + 0.15 * sin(t * 4.5 * TAU)) * decay(p, 3.4)
        },
    )

    private const val TAU = 2.0 * PI
    private val rng = Random(42)

    private fun decay(p: Double, k: Double) = exp(-p * k) * min(1.0, p * 60.0)
    private fun square(cycles: Double) = if (cycles - cycles.toLong() < 0.5) 1.0 else -1.0
    private fun saw(cycles: Double) = 2.0 * (cycles - cycles.toLong()) - 1.0
    private fun tri(cycles: Double) = 1.0 - 4.0 * abs((cycles - cycles.toLong()) - 0.5)
    private fun noise() = rng.nextDouble() * 2.0 - 1.0

    /** gen receives (t seconds, progress 0..1) and returns -1..1 */
    private fun synth(ms: Int, vol: Double = 1.0, gen: (Double, Double) -> Double): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val p = i.toDouble() / n
            val v = (gen(t, p) * vol).coerceIn(-1.0, 1.0)
            out[i] = (v * 30000).toInt().toShort()
        }
        return out
    }

    /** Like synth but runs the signal through a one-pole lowpass (alpha = cutoff feel). */
    private fun lowpassed(ms: Int, alpha: Double, gen: (Double, Double) -> Double): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        var acc = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val p = i.toDouble() / n
            acc += alpha * (gen(t, p) - acc)
            out[i] = (acc.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return out
    }

    /** Overlapping note arpeggio, soft square + sine blend. */
    private fun arpeggio(freqs: DoubleArray, noteMs: Int, totalMs: Int): ShortArray {
        val n = SR * totalMs / 1000
        val out = DoubleArray(n)
        val noteN = SR * noteMs / 1000
        for ((k, f) in freqs.withIndex()) {
            val start = k * (noteN * 3 / 4)
            for (i in 0 until (noteN * 2).coerceAtMost(n - start)) {
                val t = i.toDouble() / SR
                val p = i.toDouble() / (noteN * 2)
                out[start + i] += (sin(t * f * TAU) * 0.7 + square(t * f) * 0.18) * decay(p, 3.5) * 0.8
            }
        }
        return ShortArray(n) { (out[it].coerceIn(-1.0, 1.0) * 30000).toInt().toShort() }
    }

    /**
     * The jackpot: a bell chord struck note by note (C E G C E) that rings on
     * with a slow shimmer. Sines only, so it stays round on speakers and recordings.
     */
    private fun fanfare(): ShortArray {
        val n = SR * 1700 / 1000
        val out = DoubleArray(n)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5)
        for ((k, f) in notes.withIndex()) {
            val start = k * SR * 70 / 1000
            for (i in 0 until n - start) {
                val t = i.toDouble() / SR
                val p = i.toDouble() / (n - start)
                val shimmer = 1.0 + 0.004 * sin(t * 5.5 * TAU)
                val bell = sin(t * f * shimmer * TAU) + 0.3 * sin(t * f * 2.0 * TAU) * exp(-t * 6.0) + 0.12 * sin(t * f * 3.01 * TAU) * exp(-t * 9.0)
                out[start + i] += bell * decay(p, 2.6) * 0.23
            }
        }
        return ShortArray(n) { (out[it].coerceIn(-1.0, 1.0) * 30000).toInt().toShort() }
    }

    /**
     * The void swallowing the shop: a swirling tone that sinks from a whistle to a rumble
     * over 1.3 s and stops dead where the gulp lands.
     */
    private fun drain(): ShortArray {
        var phase = 0.0
        var swirl = 0.0
        return lowpassed(1300, 0.2) { _, p ->
            val f = 55.0 + 520.0 * (1.0 - p).pow(2.2)
            phase += f / SR
            swirl += (4.0 + 16.0 * p) / SR
            val env = p.pow(0.45) * (1.0 - ((p - 0.93) / 0.07).coerceIn(0.0, 1.0))
            (sin(phase * TAU) * 0.75 * (0.65 + 0.35 * sin(swirl * TAU)) + noise() * 0.3 * p) * env
        }
    }

    // ------------------------------------------------------------ outer space

    /** A low-gravity take-off: a soft, airy sine that rises and floats away. */
    private fun moonJump(): ShortArray {
        var phase = 0.0
        var acc = 0.0
        return synth(340) { t, p ->
            phase += (240.0 + 320.0 * p.pow(0.6)) / SR
            acc += 0.06 * (noise() - acc)
            val env = (p / 0.06).coerceAtMost(1.0) * (1.0 - p).pow(1.4)
            (sin(phase * TAU + 0.15 * sin(t * 9.0 * TAU)) * 0.55 + acc * 0.5) * env
        }
    }

    /** A low-gravity landing: a cushioned thump, no crack. */
    private fun moonLand(): ShortArray {
        var phase = 0.0
        var acc = 0.0
        return synth(260) { _, p ->
            phase += (130.0 - 60.0 * p) / SR
            acc += 0.05 * (noise() - acc)
            (sin(phase * TAU) * 0.8 + acc * 0.7) * decay(p, 5.5)
        }
    }

    /** An asteroid tumbling past: a swell of filtered air that drops in pitch as it goes by. */
    private fun flyby(): ShortArray {
        var phase = 0.0
        var acc = 0.0
        return synth(760) { _, p ->
            acc += (0.2 - 0.16 * p) * (noise() - acc)
            phase += (95.0 - 40.0 * p) / SR
            val env = sin(p * PI).pow(1.6)
            (acc * 1.3 + sin(phase * TAU) * 0.25) * env
        }
    }

    /**
     * A coin in space: one soft, round note (A5) with a slow chorus shimmer
     * from a twin a few hertz off, a breath of octave on the onset and a short
     * tail. No bright strike and no high sparkle: a long line of coins should
     * stay pleasant, never shrill.
     */
    private fun stardust(): ShortArray {
        var phase = 0.0
        var twin = 0.0
        return synth(260, vol = 0.55) { t, p ->
            val f = 880.0 * (0.985 + 0.015 * (1.0 - exp(-t * 70.0)))     // settles up into the note
            phase += f / SR; twin += (f + 4.0) / SR
            val tone = sin(phase * TAU) + 0.35 * sin(twin * TAU) + 0.16 * sin(phase * 2.0 * TAU) * exp(-t * 40.0)
            tone / 1.5 * exp(-t * 13.0) * min(1.0, t * 250.0) * min(1.0, (1.0 - p) * 10.0)
        }
    }

    /**
     * Space's hum: a calm pad (A, C#, E, with a slowly beating twin and a high
     * shimmer) and a breath of solar wind. Every partial and wobble completes
     * whole cycles in its 4 seconds, and the wind is cross-faded over its own
     * seam, so it loops without a click.
     */
    private fun hum(): ShortArray {
        val seconds = 4.0
        val n = (SR * seconds).toInt()
        val fade = SR / 2
        val wind = DoubleArray(n + fade)
        var acc = 0.0; var acc2 = 0.0
        for (i in wind.indices) { acc += 0.02 * (noise() - acc); acc2 += 0.02 * (acc - acc2); wind[i] = acc2 }
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val swell = 0.8 + 0.2 * sin(t * 0.25 * TAU)
            val pad = sin(t * 220.0 * TAU) + sin(t * 220.5 * TAU) * 0.8 + sin(t * 277.25 * TAU) * 0.55 +
                sin(t * 329.75 * TAU) * 0.6 + sin(t * 440.0 * TAU) * 0.25 * (0.5 + 0.5 * sin(t * 0.5 * TAU)) +
                sin(t * 659.25 * TAU) * 0.08 * (0.5 + 0.5 * sin(t * 0.75 * TAU + 1.0))
            // the wind's tail is blended into its head, so the loop point is seamless
            val w = if (i < fade) wind[i] * i / fade + wind[n + i] * (1.0 - i.toDouble() / fade) else wind[i]
            val v = pad * 0.16 * swell + w * 3.2
            out[i] = (v.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return out
    }

    private fun max0(v: Double) = if (v > 0.0) v else 0.0

    // ------------------------------------------------------------------- wav

    private fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val o = ByteArrayOutputStream(44 + dataLen)
        fun s(s: String) = o.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) {
            o.write(v and 0xFF); o.write((v shr 8) and 0xFF)
            o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF)
        }
        fun i16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
        s("RIFF"); i32(36 + dataLen); s("WAVE")
        s("fmt "); i32(16); i16(1); i16(1); i32(SR); i32(SR * 2); i16(2); i16(16)
        s("data"); i32(dataLen)
        for (v in pcm) { o.write(v.toInt() and 0xFF); o.write((v.toInt() shr 8) and 0xFF) }
        return o.toByteArray()
    }
}
