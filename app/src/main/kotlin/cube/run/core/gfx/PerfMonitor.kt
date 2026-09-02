package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.profiling.GLProfiler

/**
 * Frame-time instrumentation: a ring buffer of CPU frame-build times (NOT
 * vsync-capped — the real headroom signal) split into sim vs draw, logged once
 * a second to logcat (tag PERF) when [perfLog] is on, plus a minimalist
 * 7-segment FPS readout ([drawFps]) when [showFps] is on.
 */
class PerfMonitor(private val showFps: Boolean, private val perfLog: Boolean) {

    private val glProfiler: GLProfiler? = if (perfLog) GLProfiler(Gdx.graphics).also { it.enable() } else null
    private val cpuMs = FloatArray(512)
    private var ringIdx = 0
    private var ringCount = 0
    private val sortBuf = FloatArray(512)        // reused for percentile sort (no per-log alloc)
    private var frameStartNs = 0L
    private var curSlot = 0
    private var logAccum = 0f
    private var logFrames = 0
    private var winMaxDraws = 0
    private var simAccNs = 0L
    private var drawAccNs = 0L
    // 7-segment masks for 0..9 (bit a=0x01 b=0x02 c=0x04 d=0x08 e=0x10 f=0x20 g=0x40)
    private val segMasks = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

    fun beginFrame() {
        frameStartNs = System.nanoTime()
        glProfiler?.reset()
        curSlot = ringIdx
        ringIdx = (ringIdx + 1) % cpuMs.size
        if (ringCount < cpuMs.size) ringCount++
    }

    fun addSim(ns: Long) { simAccNs += ns }
    fun addDraw(ns: Long) { drawAccNs += ns }

    /** Close the frame; aggregates + logs once per second. */
    fun endFrame(shards: Int) {
        cpuMs[curSlot] = (System.nanoTime() - frameStartNs) / 1_000_000f
        glProfiler?.let { p -> if (p.drawCalls > winMaxDraws) winMaxDraws = p.drawCalls }
        logAccum += Gdx.graphics.rawDeltaTime
        logFrames++
        if (logAccum < 1f) return
        if (perfLog) {
            val n = ringCount
            System.arraycopy(cpuMs, 0, sortBuf, 0, n)
            java.util.Arrays.sort(sortBuf, 0, n)
            val cp50 = sortBuf[n / 2]; val cp95 = sortBuf[(n * 95 / 100).coerceIn(0, n - 1)]
            val cMax = sortBuf[n - 1]
            Gdx.app.log(
                "PERF",
                "fps=%.1f  cpu[p50/p95/max]=%.1f/%.1f/%.1f  sim=%.2f draw=%.2f  draws=%d shards=%d".format(
                    logFrames / logAccum, cp50, cp95, cMax,
                    simAccNs / logFrames / 1e6, drawAccNs / logFrames / 1e6,
                    winMaxDraws, shards,
                ),
            )
        }
        logAccum = 0f; logFrames = 0; winMaxDraws = 0
        simAccNs = 0L; drawAccNs = 0L
    }

    /** FPS readout, top-left (only when [showFps]). Green ≥55, amber ≥40, red below. */
    fun drawFps(shapes: ShapeRenderer, w: Float, h: Float) {
        if (!showFps) return
        val fps = Gdx.graphics.framesPerSecond.coerceIn(0, 999)
        when {
            fps >= 55 -> shapes.setColor(0.30f, 1f, 0.45f, 0.9f)
            fps >= 40 -> shapes.setColor(1f, 0.80f, 0.20f, 0.9f)
            else -> shapes.setColor(1f, 0.30f, 0.25f, 0.95f)
        }
        val dh = h * 0.030f
        val dw = dh * 0.62f
        val t = dh * 0.16f
        val gap = dw * 0.40f
        val pad = w * 0.035f
        var x = pad
        val y = h - pad - dh
        for (ch in fps.toString()) { drawDigit(shapes, ch - '0', x, y, dw, dh, t); x += dw + gap }
    }

    private fun drawDigit(shapes: ShapeRenderer, d: Int, x: Float, y: Float, dw: Float, dh: Float, t: Float) {
        val seg = segMasks[d]
        val half = (dh - t) * 0.5f
        if (seg and 0x01 != 0) shapes.rect(x, y + dh - t, dw, t)              // a  top
        if (seg and 0x02 != 0) shapes.rect(x + dw - t, y + half, t, half + t) // b  top-right
        if (seg and 0x04 != 0) shapes.rect(x + dw - t, y, t, half + t)        // c  bottom-right
        if (seg and 0x08 != 0) shapes.rect(x, y, dw, t)                       // d  bottom
        if (seg and 0x10 != 0) shapes.rect(x, y, t, half + t)                 // e  bottom-left
        if (seg and 0x20 != 0) shapes.rect(x, y + half, t, half + t)          // f  top-left
        if (seg and 0x40 != 0) shapes.rect(x, y + half, dw, t)                // g  middle
    }
}
