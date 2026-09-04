package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.Haptics
import cube.run.core.SoundFx
import cube.run.core.hsvInto
import cube.run.game.track.ObType
import cube.run.game.track.Row
import kotlin.random.Random

/**
 * The run's feedback vocabulary: every event's sound + haptic + flash +
 * shards in one place, so the conductor reads as rules and the feel is
 * tuned here. Never text on screen — only light, sound and shards.
 */
class RunFx(private val game: Gdx3DGame, private val rnd: Random) {

    private val tmp = Vector3()
    private val tmpCol = Color()

    fun runStart(baseHue: Float) {
        SoundFx.play("rise")
        Haptics.click()
        game.flash(hsvInto(tmpCol, baseHue + 180f, 0.4f, 1f), 0.12f)
    }

    /** The launch: the cube kicks off with a spray of dust behind it. */
    fun launch(px: Float, py: Float, col: Color) {
        game.burst3d(tmp.set(px, py - 0.3f, 0.4f), col, n = 16, speed = 4f, size = 0.1f, life = 0.5f, biasZ = 6f)
        game.burst3d(tmp, Color.WHITE, n = 8, speed = 6f, size = 0.07f, life = 0.35f, biasZ = 8f)
        SoundFx.play("whoosh", rate = 1.1f)
    }

    /** Through the start gate: a bang, a flash, candy raining off the beam. */
    fun startGate(col: Color) {
        SoundFx.play("boom", rate = 1.5f, vol = 0.6f)
        SoundFx.play("success", rate = 1.2f)
        Haptics.success()
        game.flash(Color.WHITE, 0.45f)
        for (i in 0 until 5) {
            hsvInto(tmpCol, 40f + i * 60f, 0.75f, 1f)
            game.burst3d(tmp.set(-2.4f + i * 1.2f, 5.0f, -1f), tmpCol, n = 8, speed = 5f, size = 0.13f, life = 1.0f, biasZ = 5f)
        }
        game.burst3d(tmp.set(0f, 4.8f, -1f), col, n = 14, speed = 7f, size = 0.12f, life = 0.8f, biasZ = 6f)
    }

    fun crash(px: Float, py: Float, col: Color) {
        game.burst3d(tmp.set(px, py, 0f), col, n = 40, speed = 9f, size = 0.2f, life = 1.1f)
        game.burst3d(tmp, Color.WHITE, n = 12, speed = 13f, size = 0.11f, life = 0.6f)
        SoundFx.play("boom")
        Haptics.heavy()
        game.slowMo(0.35f, 0.4f)
        game.shake(0.5f)
        game.flash(Color.RED, 0.45f)
    }

    /** The bubble hoisted you onto a platform you ran into. */
    fun hoist() {
        SoundFx.play("perfect", rate = 1.2f)
        Haptics.heavy()
        game.flash(Color.WHITE, 0.2f)
    }

    /** The bubble smashed [row] and a breather zone ahead ([zoneRows]). */
    fun smash(row: Row, zoneRows: List<Row>) {
        for (ob in row.obs) {
            if (ob.type != ObType.SOLID) continue
            game.burst3d(tmp.set(ob.x, ob.cy, row.z), ob.col, n = 14, speed = 9f, size = 0.24f, life = 0.9f)
        }
        for (r in zoneRows) for (ob in r.obs) {
            if (ob.type != ObType.SOLID) continue
            game.burst3d(tmp.set(ob.x, ob.cy, r.z), ob.col, n = 4, speed = 3f, size = 0.14f, life = 0.6f)
        }
        SoundFx.play("boom", rate = 1.4f, vol = 0.7f)
        SoundFx.play("perfect", rate = 1.2f)
        Haptics.heavy()
        game.shake(0.22f)
        game.flash(Color.WHITE, 0.2f)
    }

    fun rowPassed(rowsPassed: Int) {
        SoundFx.play("tick", rate = 1f + (rowsPassed % 15) * 0.025f, vol = 0.8f)
        Haptics.tick()
    }

    /** Shaved an obstacle: an air-rush. */
    fun nearMiss(px: Float, py: Float) {
        SoundFx.play("whoosh", rate = 1.55f + rnd.nextFloat() * 0.2f, vol = 0.7f)
        Haptics.click()
        game.flash(Color.WHITE, 0.07f)
        game.burst3d(tmp.set(px, py + 0.4f, 0.2f), Color.WHITE, n = 10, speed = 4f, size = 0.1f, life = 0.5f)
    }

    fun coin(x: Float, y: Float, z: Float, pitch: Int, gold: Color) {
        SoundFx.play("coin", rate = (1f + 0.05f * minOf(pitch, 14)).coerceAtMost(1.9f), vol = 0.65f)
        Haptics.tick()
        game.burst3d(tmp.set(x, y, z), gold, n = 6, speed = 3.2f, size = 0.09f, life = 0.4f)
        game.burst3d(tmp, Color.WHITE, n = 2, speed = 4f, size = 0.06f, life = 0.25f)
    }

    /** Coin streak milestone: a chime + a gold flash, no text. */
    fun coinMilestone(gold: Color) {
        SoundFx.play("perfect", rate = 1.1f)
        Haptics.success()
        game.flash(gold, 0.15f)
    }

    /** Pickup feedback: a chime, a tinted flash and a burst. */
    fun pickup(col: Color, x: Float, cz: Float) {
        SoundFx.play("success", rate = 1.3f, vol = 0.8f)
        Haptics.success()
        game.flash(col, 0.18f)
        game.burst3d(tmp.set(x, 0.8f, cz), col, n = 18, speed = 5f, size = 0.13f, life = 0.7f)
    }

    fun boxPickup(col: Color, band: Color, x: Float, cz: Float) {
        pickup(col, x, cz)
        game.burst3d(tmp.set(x, 0.8f, cz), band, n = 10, speed = 6f, size = 0.09f, life = 0.5f)
    }

    /** A bounce pad launched you. */
    fun bounce(px: Float, py: Float, col: Color) {
        SoundFx.play("pop", rate = 0.6f)
        SoundFx.play("rise", rate = 1.6f, vol = 0.6f)
        Haptics.click()
        game.burst3d(tmp.set(px, py - 0.4f, 0.3f), col, n = 16, speed = 5f, size = 0.12f, life = 0.5f)
        game.burst3d(tmp, Color.WHITE, n = 6, speed = 6f, size = 0.08f, life = 0.35f)
    }

    fun jetEnd() { SoundFx.play("slide", rate = 0.8f) }

    fun emptyStock() { SoundFx.play("tap", rate = 0.6f) }

    /** A new section tier unlocked. */
    fun tierUp(tier: Int, baseHue: Float) {
        SoundFx.play("rise", rate = 1.1f + tier * 0.1f)
        Haptics.success()
        game.flash(hsvInto(tmpCol, baseHue + 180f, 0.4f, 1f), 0.16f)
    }

    /** Through the gate into a new world. */
    fun worldGate(col: Color) {
        SoundFx.play("success", rate = 0.9f)
        SoundFx.play("rise", rate = 0.8f, vol = 0.7f)
        Haptics.success()
        game.flash(col, 0.35f)
        game.burst3d(tmp.set(0f, 3f, 0f), col, n = 30, speed = 7f, size = 0.14f, life = 0.9f)
        game.burst3d(tmp, Color.WHITE, n = 12, speed = 9f, size = 0.1f, life = 0.5f)
    }

    /** A mid-air tap: bump the combo with an ascending pitch + a spark burst. */
    fun styleTap(combo: Int, px: Float, py: Float) {
        val rate = (0.85f + 0.16f * combo).coerceAtMost(2f)
        SoundFx.play("pop", rate = rate)
        SoundFx.play("tick", rate = (1f + 0.1f * combo).coerceAtMost(1.6f), vol = 0.3f)
        Haptics.tick()
        val hue = 290f + combo * 16f
        game.flash(hsvInto(tmpCol, hue, 0.5f, 1f), 0.05f)
        game.burst3d(tmp.set(px, py + 0.3f, 0.2f), hsvInto(tmpCol, hue, 0.9f, 1f), n = 10 + combo * 3, speed = 5f + combo, size = 0.11f, life = 0.55f)
        game.burst3d(tmp, Color.WHITE, n = 4, speed = 6f, size = 0.07f, life = 0.3f)
    }

    /** Touchdown after an air combo: a burst of flair. */
    fun styleLand(combo: Int, px: Float, py: Float) {
        SoundFx.play("perfect", rate = (1f + 0.06f * combo).coerceAtMost(1.7f))
        Haptics.success()
        val hue = 300f + combo * 10f
        game.flash(hsvInto(tmpCol, hue, 0.4f, 1f), 0.12f)
        game.burst3d(tmp.set(px, py + 0.2f, 0.2f), hsvInto(tmpCol, hue, 0.85f, 1f), n = 14 + combo * 3, speed = 7f, size = 0.13f, life = 0.7f)
        game.burst3d(tmp, Color.WHITE, n = 6, speed = 5f, size = 0.09f, life = 0.4f)
    }
}
