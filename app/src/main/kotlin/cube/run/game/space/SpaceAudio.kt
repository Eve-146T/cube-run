package cube.run.game.space

import cube.run.core.SoundFx
import cube.run.data.Settings
import kotlin.math.abs
import kotlin.math.min

/** Space's quiet hum: fades in with the picture, out with it (or with the run), silent while paused or muted. */
class SpaceAudio {
    private var stream = 0
    private var volume = 0f
    private var retry = 0f

    fun tick(level: Float, dt: Float) {
        val target = if (Settings.soundEnabled) HUM_VOLUME * level else 0f
        volume += (target - volume) * min(1f, dt * 2f)
        if (target <= 0f && volume < 0.004f) { stop(); return }
        if (stream == 0) {
            retry -= dt
            if (retry > 0f) return
            stream = SoundFx.loop("hum", volume)
            if (stream == 0) { retry = 0.5f; return }
        }
        if (abs(volume - applied) > 0.003f) { SoundFx.setVolume(stream, volume); applied = volume }
    }

    private var applied = -1f

    fun stop() {
        SoundFx.stop(stream)
        stream = 0; applied = -1f; volume = 0f; retry = 0f
    }

    private companion object { const val HUM_VOLUME = 0.32f }
}
