package cube.run.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import cube.run.core.Gdx3DGame
import cube.run.core.GameSession
import cube.run.core.Haptics
import cube.run.core.SoundFx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cube Run — 3-lane endless runner. The world rushes toward the camera;
 * swipe LEFT/RIGHT to snap lanes, UP to jump, DOWN to slam mid-air.
 * Low walls = jump, pillars/wide bars = dodge, sliders drift into the open
 * lane as they approach. +1 per row passed, +2 for shaving it close.
 */
class CubeRun(session: GameSession) : Gdx3DGame(session) {

    private class Ob(
        val inst: ModelInstance, var x: Float, val halfW: Float, val h: Float,
        val low: Boolean, val sx: Float, val sy: Float, val sz: Float,
        val sliding: Boolean = false, val slideTo: Float = 0f,
    )

    private class Row(var z: Float, val obs: ArrayList<Ob>) {
        var scored = false
        var minClear = 99f // tightest clearance seen while crossing (near-miss detect)
    }

    private class Tile(val inst: ModelInstance, val col: Color, var z: Float, val x: Float, val dim: Boolean)
    private class Post(val inst: ModelInstance, val col: Color, var z: Float, val x: Float)

    private lateinit var unit: Model
    private lateinit var playerInst: ModelInstance
    private lateinit var playerCol: Color
    private lateinit var shellInst: ModelInstance
    private lateinit var shellBlend: BlendingAttribute
    private lateinit var shadowInst: ModelInstance
    private lateinit var shadowBlend: BlendingAttribute

    private val rows = ArrayList<Row>()
    private val tiles = ArrayList<Tile>()
    private val posts = ArrayList<Post>()
    private val rnd = Random(System.nanoTime())
    private val tmp = Vector3()

    private val laneW = 1.7f
    private val ground = 0.45f      // resting cube center (cube = 0.9 across)
    private val spawnZ = -64f
    private val tileD = 3f
    private val tileRows = 26
    private val postGap = 6.6f
    private val postPairs = 12

    private var baseHue = 0f
    private var started = false
    private var dead = false
    private var lane = 1
    private var px = 0f
    private var py = ground
    private var vy = 0f
    private var air = false
    private var roll = 0f           // forward tumble angle
    private var squash = 0f         // landing squash timer
    private var nudge = 0f          // edge-bonk offset
    private var trailT = 0f
    private var spd = 4.5f
    private var speedBonus = 0f
    private var dist = 0f
    private var runT = 0f
    private var spawnAcc = 0f
    private var rowsSpawned = 0
    private var rowsPassed = 0
    private var freeLane = 1        // guaranteed-survivable lane of the last spawned row
    private var deathT = 0f

    private fun laneX(l: Int) = (l - 1) * laneW

    private fun diffuse(inst: ModelInstance): Color =
        (inst.materials.first().get(ColorAttribute.Diffuse) as ColorAttribute).color

    /** Allocation-free HSV write into an existing Color. */
    private fun setHsv(c: Color, h: Float, s: Float, v: Float) {
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
        when (i % 6) {
            0 -> c.set(v, t, p, 1f); 1 -> c.set(q, v, p, 1f); 2 -> c.set(p, v, t, 1f)
            3 -> c.set(p, q, v, 1f); 4 -> c.set(t, p, v, 1f); else -> c.set(v, p, q, 1f)
        }
    }

    private fun tileHue(t: Tile) {
        // floor hue drifts with total distance; checker brightness reads as a grid
        setHsv(t.col, baseHue + dist * 1.6f, 0.7f, if (t.dim) 0.30f else 0.46f)
    }

    override fun init() {
        unit = box(1f, 1f, 1f, Color.WHITE)
        baseHue = (System.currentTimeMillis() % 360L).toFloat()
        bgTop = gdxHsv(baseHue + 30f, 0.6f, 0.4f)
        bgBottom = gdxHsv(baseHue + 70f, 0.65f, 0.1f)

        for (r in 0 until tileRows) for (l in 0..2) {
            val inst = ModelInstance(unit)
            val t = Tile(inst, diffuse(inst), 8f - r * tileD, laneX(l), (r + l) % 2 == 0)
            tileHue(t)
            tiles.add(t)
        }
        var pz = 6f
        repeat(postPairs) {
            for (side in intArrayOf(-1, 1)) {
                val inst = ModelInstance(unit)
                val p = Post(inst, diffuse(inst), pz, side * (laneW * 1.5f + 1.0f))
                setHsv(p.col, baseHue + 50f, 0.85f, 1f)
                posts.add(p)
            }
            pz -= postGap
        }

        playerInst = ModelInstance(unit)
        playerCol = diffuse(playerInst)
        setHsv(playerCol, baseHue + 180f, 0.55f, 1f)

        shellInst = ModelInstance(unit) // pulsing translucent "glow" shell
        shellBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shellInst.materials.first().set(ColorAttribute.createDiffuse(gdxHsv(baseHue + 180f, 0.5f, 1f)), shellBlend)

        shadowInst = ModelInstance(unit)
        shadowBlend = BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.3f)
        shadowInst.materials.first().set(ColorAttribute.createDiffuse(Color.BLACK), shadowBlend)

        cam.position.set(0f, 3.7f, 6.4f)
        cam.lookAt(0f, 1.0f, -8f)
        cam.update()
        session.banner("SWIPE · JUMP")
    }

    // ------------------------------------------------------------- spawning

    private fun colored(c: Color): ModelInstance {
        val inst = ModelInstance(unit)
        diffuse(inst).set(c)
        return inst
    }

    private fun pillar(x: Float, hue: Float, sliding: Boolean = false, slideTo: Float = 0f): Ob {
        val h = 2.0f + rnd.nextFloat() * 0.6f
        val c = if (sliding) gdxHsv(hue + 230f, 0.95f, 1f) else gdxHsv(hue + 185f, 0.85f, 1f)
        return Ob(colored(c), x, 0.75f, h, false, 1.5f, h, 0.9f, sliding, slideTo)
    }

    private fun spawnRowAt(z: Float) {
        val obs = ArrayList<Ob>(3)
        val hue = baseHue + dist * 1.6f // obstacles pop against current floor hue
        val r = rnd.nextFloat()
        when {
            rowsSpawned < 3 -> { // warmup: single side pillar, center always free
                freeLane = 1
                obs.add(pillar(laneX(if (rnd.nextBoolean()) 0 else 2), hue))
            }
            r < 0.24f -> { // low wall across all lanes: JUMP
                obs.add(Ob(colored(gdxHsv(hue + 140f, 0.9f, 1f)),
                    0f, laneW * 1.5f + 0.3f, 0.62f, true, laneW * 3f + 0.6f, 0.62f, 0.7f))
            }
            rowsPassed >= 12 && r < 0.42f -> { // slider drifts INTO the open lane — fake-out
                val t = freeLane
                val s = if (t == 1) (if (rnd.nextBoolean()) 0 else 2) else 1
                obs.add(pillar(laneX(s), hue, sliding = true, slideTo = laneX(t)))
                freeLane = s // its start lane opens up as it leaves
            }
            r < 0.72f -> { // wide bar / twin pillars: exactly one survivable lane
                freeLane = (freeLane + rnd.nextInt(3) - 1).coerceIn(0, 2)
                if (freeLane == 1) {
                    obs.add(pillar(laneX(0), hue)); obs.add(pillar(laneX(2), hue))
                } else {
                    val a = if (freeLane == 0) 1 else 0 // blocked adjacent lane pair
                    val cx = (laneX(a) + laneX(a + 1)) / 2f
                    val w = laneW + 1.5f
                    obs.add(Ob(colored(gdxHsv(hue + 185f, 0.85f, 1f)),
                        cx, w / 2f, 2.3f, false, w, 2.3f, 0.9f))
                }
            }
            else -> { // single pillar in a random non-free lane
                var b = rnd.nextInt(3)
                if (b == freeLane) b = (b + 1 + rnd.nextInt(2)) % 3
                obs.add(pillar(laneX(b), hue))
            }
        }
        rows.add(Row(z, obs))
        rowsSpawned++
    }

    // --------------------------------------------------------------- events

    private fun start() {
        if (started || session.isOver) return
        started = true
        runT = 0f
        // prefill so the action arrives within seconds
        spawnRowAt(-28f); spawnRowAt(-40f); spawnRowAt(-52f); spawnRowAt(spawnZ)
        spawnAcc = 0f
        session.banner("GO!")
        SoundFx.play("rise")
        Haptics.click()
        flash(gdxHsv(baseHue + 180f, 0.4f, 1f), 0.12f)
    }

    private fun crash() {
        if (dead) return
        dead = true
        burst3d(tmp.set(px, py, 0f), playerCol, n = 40, speed = 9f, size = 0.2f, life = 1.1f)
        burst3d(tmp, Color.WHITE, n = 12, speed = 13f, size = 0.11f, life = 0.6f)
        SoundFx.play("boom")
        Haptics.heavy()
        shake(1.0f)
        flash(Color.RED, 0.45f)
        session.gameOver()
    }

    private fun scoreRow(row: Row) {
        rowsPassed++
        session.addScore(1)
        SoundFx.play("tick", rate = 1f + (rowsPassed % 15) * 0.025f, vol = 0.8f)
        Haptics.tick()
        if (row.minClear < 0.34f) { // shaved it
            session.addScore(2)
            SoundFx.play("coin", rate = 1.1f + rnd.nextFloat() * 0.1f)
            Haptics.click()
            flash(Color.WHITE, 0.07f)
            burst3d(tmp.set(px, py + 0.4f, 0.2f), Color.WHITE, n = 10, speed = 4f, size = 0.1f, life = 0.5f)
        }
        if (rowsPassed % 15 == 0) {
            speedBonus += 0.9f
            session.banner("SPEED UP")
            SoundFx.play("rise")
            Haptics.buzz(40, 160)
            flash(gdxHsv(baseHue + dist * 1.6f + 180f, 0.5f, 1f), 0.12f)
            shake(0.18f)
        }
        // sky drifts as you survive
        bgTop = gdxHsv(baseHue + 30f + rowsPassed * 2f, 0.6f, 0.4f)
        bgBottom = gdxHsv(baseHue + 70f + rowsPassed * 2f, 0.65f, 0.1f)
    }

    // ---------------------------------------------------------------- input

    override fun onDown(x: Float, y: Float) {
        start()
    }

    override fun onSwipe(dir: Int) {
        if (session.isOver || dead) return
        if (!started) start()
        when (dir) {
            Gdx3DGame.LEFT, Gdx3DGame.RIGHT -> {
                val d = if (dir == Gdx3DGame.LEFT) -1 else 1
                val nl = lane + d
                if (nl in 0..2) {
                    lane = nl
                    SoundFx.play("whoosh", rate = 0.95f + rnd.nextFloat() * 0.15f)
                    SoundFx.play("tick", rate = 1.4f, vol = 0.5f)
                    Haptics.tick()
                } else { // bonk the invisible wall
                    nudge = d * 0.4f
                    SoundFx.play("tap", rate = 0.7f)
                    Haptics.tick()
                }
            }
            Gdx3DGame.UP -> if (!air) {
                air = true; vy = 8.4f
                SoundFx.play("whoosh", rate = 1.3f)
                Haptics.click()
                burst3d(tmp.set(px, 0.1f, 0.3f), playerCol, n = 6, speed = 2.5f, size = 0.08f, life = 0.35f)
            }
            Gdx3DGame.DOWN -> if (air && vy > -12f) { // slam back down
                vy = -19f
                SoundFx.play("slide", rate = 1.3f)
                Haptics.tick()
            }
        }
    }

    // ----------------------------------------------------------------- loop

    override fun tick(dt: Float) {
        if (started && !dead) {
            runT += dt
            // relentless ramp: per-row + per-second + milestone bonuses
            spd = min(26f, 9.6f + rowsPassed * 0.28f + runT * 0.10f + speedBonus)
        } else if (!started) {
            spd = 4.5f // ambient pre-start scroll
        } else {
            spd = max(0f, spd - spd * 2.4f * dt) // death: world glides to a stop
            deathT = min(deathT + dt, 2.5f)
        }
        val mv = spd * dt
        dist += mv

        // floor tiles + neon side posts scroll and wrap, recoloring on wrap
        for (t in tiles) {
            t.z += mv
            if (t.z > 8f) { t.z -= tileRows * tileD; tileHue(t) }
            t.inst.transform.setToTranslation(t.x, -0.14f, t.z).scale(laneW * 0.92f, 0.26f, tileD * 0.9f)
        }
        for (p in posts) {
            p.z += mv
            if (p.z > 8f) { p.z -= postPairs * postGap; setHsv(p.col, baseHue + dist * 1.6f + 50f, 0.85f, 1f) }
            p.inst.transform.setToTranslation(p.x, 0.7f, p.z).scale(0.26f, 1.4f, 0.26f)
        }

        if (started && !dead && !session.isOver) {
            spawnAcc += mv
            val gap = max(5.4f, 8.8f - rowsPassed * 0.07f)
            if (spawnAcc >= gap) { spawnAcc -= gap; spawnRowAt(spawnZ) }
        }

        // ---- player physics + transforms
        if (!dead) {
            px += (laneX(lane) - px) * min(1f, dt * 13f) // eased lane snap
            nudge *= max(0f, 1f - 10f * dt)
            if (air) {
                vy -= 26f * dt
                py += vy * dt
                if (py <= ground) {
                    py = ground; air = false; vy = 0f
                    SoundFx.play("pop", rate = 0.95f + rnd.nextFloat() * 0.12f)
                    Haptics.click()
                    burst3d(tmp.set(px, 0.06f, 0.4f), playerCol, n = 8, speed = 3.2f, size = 0.09f, life = 0.4f)
                    squash = 1f
                    shake(0.06f)
                }
            }
            squash = max(0f, squash - dt * 5f)
            roll += mv * 90f + (if (air) 160f * dt else 0f) // tumble, extra flip mid-air
            if (roll > 360f) roll -= 360f
            val tilt = ((laneX(lane) - px) * -22f).coerceIn(-32f, 32f)
            val sq = squash * 0.3f
            playerInst.transform.setToTranslation(px + nudge, py - squash * 0.08f, 0f)
                .rotate(Vector3.Z, tilt)
                .rotate(Vector3.X, -roll)
                .scale(0.9f * (1f + sq), 0.9f * (1f - sq), 0.9f * (1f + sq))
            val pulse = 0.9f * (1.18f + 0.06f * sin(time * 8f))
            shellBlend.opacity = 0.22f + 0.08f * sin(time * 6f)
            shellInst.transform.setToTranslation(px + nudge, py, 0f)
                .rotate(Vector3.Z, tilt).rotate(Vector3.X, -roll)
                .scale(pulse, pulse, pulse)
            shadowBlend.opacity = (0.36f * (1f - (py - ground) / 1.6f)).coerceIn(0.06f, 0.36f)
            shadowInst.transform.setToTranslation(px + nudge, 0.04f, 0f).scale(1.0f, 0.02f, 1.0f)

            if (started) { // glow trail
                trailT += dt
                if (trailT > 0.08f) {
                    trailT = 0f
                    burst3d(tmp.set(px, py, 0.55f), playerCol, n = 1, speed = 1.4f, size = 0.08f, life = 0.35f)
                }
            }
        }

        // ---- obstacle rows: move, slide, collide, score
        var i = rows.size - 1
        while (i >= 0) {
            val row = rows[i]
            row.z += mv
            for (ob in row.obs) {
                if (ob.sliding && row.z > -26f) ob.x += (ob.slideTo - ob.x) * min(1f, dt * 2.0f)
                ob.inst.transform.setToTranslation(ob.x, ob.h / 2f, row.z).scale(ob.sx, ob.sy, ob.sz)
            }
            if (started && !dead && abs(row.z) < 0.95f) {
                for (ob in row.obs) {
                    val lat = abs(px - ob.x) - (ob.halfW + 0.36f)
                    val clear = if (ob.low) (py - 0.45f) - ob.h else lat
                    row.minClear = min(row.minClear, clear)
                    if (abs(row.z) < 0.82f && lat < 0f && (!ob.low || clear < -0.02f)) {
                        crash()
                        break
                    }
                }
            }
            if (!row.scored && row.z > 1.2f) {
                row.scored = true
                if (started && !dead) scoreRow(row)
            }
            if (row.z > 12f) rows.removeAt(i)
            i--
        }

        // ---- chase camera: above-behind, leans with the player, pulls back on death
        val cy = 3.6f + (py - ground) * 0.22f + deathT * 1.6f
        cam.position.set(px * 0.45f, cy, 6.4f + deathT * 2.2f)
        cam.lookAt(px * 0.55f, 1.0f, -8f)
        cam.up.set(0f, 1f, 0f)
    }

    override fun renderWorld(batch: ModelBatch, env: Environment) {
        for (t in tiles) batch.render(t.inst, env)
        for (p in posts) batch.render(p.inst, env)
        for (r in rows) for (ob in r.obs) batch.render(ob.inst, env)
        if (!dead) {
            batch.render(shadowInst, env)
            batch.render(playerInst, env)
            batch.render(shellInst, env)
        }
    }
}
