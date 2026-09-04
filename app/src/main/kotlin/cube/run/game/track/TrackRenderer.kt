package cube.run.game.track

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.core.hsvInto
import cube.run.game.world.Fog
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the track: every obstacle, platform, pickup and coin, all through
 * the engine's batched passes (boxes + coins), so the whole road costs two
 * draw calls. Rows stream in: everything scales by the row's [Row.pop], so
 * obstacles spring up out of the floor and coins bloom as they come into
 * view. Pure rendering — no state, no gameplay.
 */
class TrackRenderer(private val game: Gdx3DGame) {

    private val coinCol = Color()
    private val coinFace = Color()
    private val boxCol = Color()
    private val bandCol = Color()
    private val magnetCol = Color()
    private val multCol = Color()
    private val jetCol = Color()
    private val flameCol = Color()
    private val padTop = Color()
    private val rod = Color(0.32f, 0.28f, 0.4f, 1f)
    private val portalA = Color()
    private val portalB = Color()
    private val tmpCol = Color()

    init {
        hsvInto(coinCol, 44f, 0.9f, 1f)
        hsvInto(coinFace, 50f, 0.55f, 1f)
        hsvInto(boxCol, 282f, 0.72f, 0.95f)
        hsvInto(bandCol, 46f, 0.8f, 1f)
        hsvInto(magnetCol, 4f, 0.9f, 1f)
        hsvInto(multCol, 328f, 0.8f, 1f)
        hsvInto(jetCol, 215f, 0.75f, 1f)
        hsvInto(flameCol, 28f, 0.9f, 1f)
        hsvInto(padTop, 0f, 0f, 1f)
    }

    /** The coin colour (for bursts). */
    val gold: Color get() = coinCol

    fun render(track: Track, time: Float, kaleido: Float = 0f, kaleidoHue: Float = 0f) {
        val yaw = (time * 240f) % 360f
        // coins spin in lockstep, a little out of phase down the line, so every line glints in a wave
        val coinYaw = time * 190f
        for (r in track.rows) { // obstacles first: batch overflow drops from the back
            val p = r.pop
            if (p <= 0.001f) continue
            val fog = Fog.at(r.z)
            if (r.portal >= 0) renderPortal(r, time, fog, p)
            for (ob in r.obs) {
                when {
                    ob.type == ObType.PLAT -> renderPlatform(ob, r.z, fog, p)
                    ob.type == ObType.PAD -> renderPad(ob, r.z, fog, time, p)
                    ob.anim == ObAnim.PISTON && ob.sy < 0.03f -> {} // sunk into the floor
                    ob.anim == ObAnim.PENDULUM -> {
                        box(ob.x, ob.cy, r.z, ob.sx, ob.sy, ob.sz, ob.col, fog, p, false)
                        game.worldBox(ob.x * 0.5f, ob.top + 1.1f, r.z, abs(ob.x) + 0.16f, 0.16f, 0.16f, rod, fog) // the rod up to the beam
                        game.worldBox(0f, ob.top + 2.2f, r.z, 6.4f * p, 0.22f, 0.22f, rod, fog)                    // the beam it hangs from
                    }
                    else -> box(ob.x, ob.cy, r.z, ob.sx, ob.sy, ob.sz, if (kaleido > 0.01f) tmpCol.set(ob.col).lerp(hsvInto(portalB, kaleidoHue + r.z * 6f, 0.9f, 1f), kaleido) else ob.col, fog, p, ob.grounded)
                }
            }
            if (r.pickup != Pickup.NONE && r.pickup != Pickup.BUBBLE) renderPickup(r, yaw, time, p)
        }
        // coins: fat gold pieces with a raised, paler heart, spinning, bobbing
        for (r in track.rows) {
            val coins = r.coins ?: continue
            val p = r.pop
            if (p <= 0.001f) continue
            for (c in coins) {
                if (c.taken) continue
                val cz = r.z + c.dz
                val y = c.y + 0.06f * sin(time * 4f + c.dz * 0.9f)
                val fog = Fog.at(cz)
                val yaw = coinYaw + cz * 14f
                game.worldCoin(c.x, y, cz, 0.36f * p, 0.14f, yaw, coinCol, fog)
                game.worldCoin(c.x, y, cz, 0.23f * p, 0.2f, yaw, coinFace, fog)
            }
        }
    }

    /** A portal: a big turning ring of candy beads across the road, breathing, with a second counter-turning ring inside. */
    private fun renderPortal(r: Row, time: Float, fog: Float, p: Float) {
        val hue = if (r.portalExit) 200f else cube.run.data.Bonus.get(r.portal).hue
        hsvInto(portalA, hue, 0.85f, 1f)
        hsvInto(portalB, hue + 40f, 0.6f, 1f)
        val cx = 0f; val cy = 2.3f
        val radius = (2.4f + 0.08f * sin(time * 3f)) * p
        val n = 16
        for (i in 0 until n) {
            val a = time * 1.4f + i * (6.2832f / n)
            val bx = cx + cos(a) * radius; val by = cy + sin(a) * radius
            val s = 0.34f + 0.1f * sin(time * 6f + i)
            game.worldBoxSpin(bx, by, r.z, s, s, s, time * 200f + i * 30f, if (i % 2 == 0) portalA else portalB, fog)
        }
        val n2 = 10
        for (i in 0 until n2) {
            val a = -time * 2.2f + i * (6.2832f / n2)
            val rr = radius * 0.62f
            val s = 0.2f
            game.worldBoxSpin(cx + cos(a) * rr, cy + sin(a) * rr, r.z + 0.3f, s, s, s, -time * 300f + i * 40f, Color.WHITE, fog)
        }
        // the frame's feet
        game.worldBox(-radius - 0.3f, 0.5f, r.z, 0.5f, 1f, 0.5f, portalB, fog)
        game.worldBox(radius + 0.3f, 0.5f, r.z, 0.5f, 1f, 0.5f, portalB, fog)
    }

    /** A box scaled by the stream-in [p]: grounded ones grow up out of the floor, hanging ones swell in place. */
    private fun box(x: Float, cy: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Color, fog: Float, p: Float, grounded: Boolean) {
        if (p >= 0.999f) { game.worldBox(x, cy, z, sx, sy, sz, col, fog); return }
        val h = sy * p
        val y = if (grounded) cy - sy / 2f + h / 2f else cy
        game.worldBox(x, y, z, sx * (0.4f + 0.6f * p), h, sz, col, fog)
    }

    private fun renderPlatform(ob: Ob, front: Float, fog: Float, p: Float) {
        val top = ob.clear * p
        val bodyLen = ob.sz - ob.ramp
        game.worldBox(ob.x, top / 2f, front - ob.ramp - bodyLen / 2f, ob.sx, top, bodyLen, ob.col, fog)
        if (ob.ramp > 0f) { // the ramp as 4 rising steps
            val n = 4
            val d = ob.ramp / n
            for (i in 0 until n) {
                val h = top * (i + 1) / n
                game.worldBox(ob.x, h / 2f, front - d * (i + 0.5f), ob.sx, h, d, ob.col, fog)
            }
        }
    }

    /** A springy slab: a coloured base with a pale top that breathes. */
    private fun renderPad(ob: Ob, z: Float, fog: Float, time: Float, p: Float) {
        val breath = 0.03f * sin(time * 6f)
        val s = 0.5f + 0.5f * p
        game.worldBox(ob.x, 0.05f, z, ob.sx * s, 0.1f, ob.sz * s, ob.col, fog)
        game.worldBox(ob.x, 0.13f + breath, z, ob.sx * 0.8f * s, 0.08f, ob.sz * 0.8f * s, padTop, fog)
        game.worldBox(ob.x, 0.11f + breath * 2f, z, ob.sx * 0.35f * s, 0.16f, ob.sz * 0.35f * s, ob.col, fog)
    }

    /** A box that orbits a pickup's centre as the whole thing spins (offset [dx] along the spun x axis). */
    private fun spinPart(cx: Float, cz: Float, dx: Float, y: Float, sx: Float, sy: Float, sz: Float, yaw: Float, col: Color, fog: Float) {
        val rad = Math.toRadians(yaw.toDouble())
        game.worldBoxSpin(cx + dx * cos(rad).toFloat(), y, cz - dx * sin(rad).toFloat(), sx, sy, sz, yaw, col, fog)
    }

    private fun renderPickup(r: Row, yaw: Float, time: Float, p: Float) {
        val cz = r.z + Row.PICKUP_DZ
        val x = r.pickupX
        val y = 0.85f + 0.1f * sin(time * 3f + cz)
        val fog = Fog.at(cz)
        val s = p
        when (r.pickup) {
            Pickup.BOX -> { // a spinning gift: purple cube with a gold ribbon
                game.worldBoxSpin(x, y, cz, 0.62f * s, 0.62f * s, 0.62f * s, yaw * 0.5f, boxCol, fog)
                game.worldBoxSpin(x, y, cz, 0.66f * s, 0.16f * s, 0.66f * s, yaw * 0.5f, bandCol, fog)
                game.worldBoxSpin(x, y, cz, 0.16f * s, 0.66f * s, 0.66f * s, yaw * 0.5f, bandCol, fog)
            }
            Pickup.MAGNET -> { // a red horseshoe with white tips
                spinPart(x, cz, -0.26f * s, y + 0.1f, 0.2f * s, 0.62f * s, 0.2f * s, yaw, magnetCol, fog)
                spinPart(x, cz, 0.26f * s, y + 0.1f, 0.2f * s, 0.62f * s, 0.2f * s, yaw, magnetCol, fog)
                game.worldBoxSpin(x, y - 0.2f, cz, 0.72f * s, 0.2f * s, 0.2f * s, yaw, magnetCol, fog)
                spinPart(x, cz, -0.26f * s, y + 0.45f, 0.2f * s, 0.12f * s, 0.2f * s, yaw, Color.WHITE, fog)
                spinPart(x, cz, 0.26f * s, y + 0.45f, 0.2f * s, 0.12f * s, 0.2f * s, yaw, Color.WHITE, fog)
            }
            Pickup.MULT -> { // two stacked pink slabs: "×2"
                game.worldBoxSpin(x, y - 0.18f, cz, 0.7f * s, 0.22f * s, 0.7f * s, yaw, multCol, fog)
                game.worldBoxSpin(x, y + 0.18f, cz, 0.7f * s, 0.22f * s, 0.7f * s, yaw + 30f, multCol, fog)
                game.worldBoxSpin(x, y + 0.45f, cz, 0.2f * s, 0.2f * s, 0.2f * s, yaw, Color.WHITE, fog)
            }
            Pickup.JET -> { // twin blue tanks with flickering flames
                val flick = 0.18f + 0.1f * abs(sin(time * 21f + cz))
                spinPart(x, cz, -0.2f * s, y + 0.1f, 0.3f * s, 0.8f * s, 0.3f * s, yaw, jetCol, fog)
                spinPart(x, cz, 0.2f * s, y + 0.1f, 0.3f * s, 0.8f * s, 0.3f * s, yaw, jetCol, fog)
                spinPart(x, cz, -0.2f * s, y - 0.42f, 0.2f * s, flick, 0.2f * s, yaw, flameCol, fog)
                spinPart(x, cz, 0.2f * s, y - 0.42f, 0.2f * s, flick, 0.2f * s, yaw, flameCol, fog)
            }
        }
    }

    /** Colours for the pickup bursts. */
    fun colorOf(kind: Int): Color = when (kind) {
        Pickup.BOX -> boxCol
        Pickup.MAGNET -> magnetCol
        Pickup.MULT -> multCol
        Pickup.JET -> jetCol
        else -> bandCol
    }
}
