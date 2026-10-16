package cube.run.game.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.hsvInto
import cube.run.data.BubbleSkins
import cube.run.data.Progress
import cube.run.data.Shards
import cube.run.data.Skins
import cube.run.data.Trails
import cube.run.data.Wardrobe
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * How a mystery box and its prizes look, wherever one is opened: the run-over
 * [GiftStage] and the shop's showroom ([ShopGift]) draw the same chunky gift
 * and the same prizes — a coin, a bubble, a new cube shown as itself, a little
 * cube shedding a new trail, a cluster of shard crystals.
 */
class GiftArt(private val game: Gdx3DGame) {
    val body = Color()
    val band = Color()
    val gold = Color()
    private val coinFace = Color()
    private val tmpCol = Color()
    private val tmp = Vector3()
    private var trailEmission = 0f

    init {
        hsvInto(body, 282f, 0.72f, 0.95f)
        hsvInto(band, 46f, 0.8f, 1f)
        hsvInto(gold, 46f, 0.85f, 1f)
        hsvInto(coinFace, 50f, 0.55f, 1f)
    }

    /** The gift: body, ribbon, lid and bow, [s] wide, standing at ([x], [y], [z]). */
    fun box(x: Float, y: Float, z: Float, s: Float, yaw: Float) {
        game.worldBoxSpin(x, y, z, s, s * 0.82f, s, yaw, body)
        game.worldBoxSpin(x, y, z, s + 0.06f * s / 1.2f, s * 0.24f, s * 0.26f, yaw, band)
        game.worldBoxSpin(x, y, z, s * 0.26f, s * 0.24f, s + 0.06f * s / 1.2f, yaw, band)
        game.worldBoxSpin(x, y + s * 0.52f, z, s + 0.12f * s / 1.2f, s * 0.22f, s + 0.12f * s / 1.2f, yaw, body)
        game.worldBoxSpin(x, y + s * 0.52f, z, s + 0.18f * s / 1.2f, s * 0.26f, s * 0.26f, yaw, band)
        game.worldBoxSpin(x, y + s * 0.52f, z, s * 0.26f, s * 0.26f, s + 0.18f * s / 1.2f, yaw, band)
        game.worldBoxSpin(x, y + s * 0.72f, z, s * 0.32f, s * 0.18f, s * 0.32f, yaw + 45f, band) // the bow
    }

    /** The box becomes the shards: its body, its ribbon and a few white sparks. */
    fun shatter(x: Float, y: Float, z: Float, scale: Float = 1f) {
        game.burst3d(tmp.set(x, y, z), body, n = (34 * scale).toInt(), speed = 5.5f * scale, size = 0.2f * scale, life = 1.2f)
        game.burst3d(tmp, band, n = (14 * scale).toInt(), speed = 6f * scale, size = 0.14f * scale, life = 1.0f)
        game.burst3d(tmp, Color.WHITE, n = (10 * scale).toInt(), speed = 7f * scale, size = 0.09f * scale, life = 0.5f)
    }

    /** The sunburst's colour once [r] is out: a step away from the prize's own colour, so the prize stands out. */
    fun rayColor(r: Progress.BoxReward, out: Color): Color = when (r.kind) {
        Progress.BoxReward.SKIN -> hsvInto(out, 300f, 0.5f, 0.85f)
        Progress.BoxReward.BUBBLE -> hsvInto(out, 190f, 0.5f, 0.8f)
        Progress.BoxReward.SHARDS -> hsvInto(out, Shards.get(r.id).hue + 30f, 0.55f, 0.7f)
        else -> hsvInto(out, 30f, 0.55f, 0.75f) // deeper than the gold of the coin
    }

    /** The solid prize at ([x], [y], [z]); [grow] 0..1 as it appears, [s] its overall scale. */
    fun prize(r: Progress.BoxReward, x: Float, y: Float, z: Float, grow: Float, time: Float, s: Float = 1f) {
        val k = grow * s
        when {
            r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.CUBE -> { // the new cube, as itself
                val sk = Skins.get(r.id)
                hsvInto(tmpCol, sk.hueAt(time, 0f), sk.sat, sk.valueAt(time))
                game.worldBoxSpin(x, y, z, 0.95f * k, 0.95f * k, 0.95f * k, time * 90f, tmpCol)
            }
            r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.TRAIL -> { // a little cube orbiting, shedding the new trail
                val a = time * 3f
                hsvInto(tmpCol, 0f, 0f, 1f)
                game.worldBoxSpin(x + cos(a) * 1.1f * s, y, z + sin(a) * 1.1f * s, 0.45f * k, 0.45f * k, 0.45f * k, time * 200f, tmpCol)
            }
            r.kind == Progress.BoxReward.COINS -> { // one big spinning coin with a raised heart (two for a big win)
                game.worldCoin(x, y, z, 0.6f * k, 0.16f * s, time * 120f, gold)
                game.worldCoin(x, y, z, 0.4f * k, 0.22f * s, time * 120f, coinFace)
                if (r.amount >= 100) {
                    game.worldCoin(x + 0.75f * s, y - 0.3f * s, z + 0.3f * s, 0.38f * k, 0.12f * s, time * 120f + 60f, gold)
                    game.worldCoin(x + 0.75f * s, y - 0.3f * s, z + 0.3f * s, 0.25f * k, 0.17f * s, time * 120f + 60f, coinFace)
                }
            }
            r.kind == Progress.BoxReward.SHARDS -> { // a cluster of crystals turning together, more of them for a bigger drop
                val kind = Shards.get(r.id)
                val n = 3 + min(4, r.amount / 8)
                for (i in 0 until n) {
                    val a = time * 1.6f + i * (6.2832f / n)
                    val rr = if (i == 0) 0f else 0.55f * s
                    val h = (if (i == 0) 0.9f else 0.5f + 0.1f * (i % 3)) * k
                    hsvInto(tmpCol, kind.hue + (i % 2) * 14f, 0.75f, 1f)
                    val dy = (if (i == 0) 0.1f else -0.2f + 0.12f * sin(time * 3f + i)) * s
                    game.worldBoxSpin(x + cos(a) * rr, y + dy, z + sin(a) * rr, 0.26f * k, h, 0.26f * k, time * 120f + i * 40f, tmpCol)
                }
            }
        }
    }

    /** A trail prize sheds its trail at 30 Hz of simulation time (one burst at most per slice). */
    fun emitTrail(r: Progress.BoxReward, x: Float, y: Float, z: Float, dt: Float, time: Float, s: Float = 1f) {
        if (r.kind != Progress.BoxReward.SKIN || r.cat != Wardrobe.TRAIL) { trailEmission = 0f; return }
        trailEmission += dt
        val interval = 1f / 30f
        if (trailEmission < interval) return
        trailEmission %= interval
        val tr = Trails.get(r.id)
        val a = time * 3f
        val c = hsvInto(tmpCol, tr.hueAt(time, (time * 30f).toInt()), tr.sat, tr.value)
        game.burst3d(tmp.set(x + cos(a) * 1.1f * s, y, z + sin(a) * 1.1f * s), c,
            n = tr.count, speed = tr.speed * s, size = tr.size * s, life = tr.life * 1.5f, gravity = tr.gravity)
    }

    /** The see-through prizes (a bubble, a bubble skin), drawn in the blended pass. */
    fun prizeBlended(cam: PerspectiveCamera, r: Progress.BoxReward, x: Float, y: Float, z: Float, grow: Float, time: Float, s: Float = 1f) {
        val d = 1.5f * grow * s
        when {
            r.kind == Progress.BoxReward.BUBBLE ->
                game.bubbles.draw(cam, x, y, z, d, d, d, time * 40f, time, 170f, 270f, 0.6f, 2.6f, 0.1f, 1f, BubbleSkins.IRIS)
            r.kind == Progress.BoxReward.SKIN && r.cat == Wardrobe.BUBBLE -> {
                val b = BubbleSkins.get(r.id)
                game.bubbles.draw(cam, x, y, z, d, d, d, time * 40f, time, b.hue, b.hue2, b.sat, b.rim, b.fill, 1f, b.style)
            }
        }
    }
}
