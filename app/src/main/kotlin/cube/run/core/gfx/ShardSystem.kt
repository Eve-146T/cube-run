package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/**
 * Cube-shard particles ("juice"): pooled, gravity-driven, tumbling, fading.
 * Allocation-free in steady state — [burst] only re-seeds pooled shards — and
 * rendered as ONE blended draw call with the Environment's lighting baked into
 * the vertex colours (see [BoxMeshKit]), so 240 live shards cost ~nothing.
 */
class ShardSystem(private val kit: BoxMeshKit, private val maxShards: Int = 240) : Disposable {

    private class Shard {
        val transform = Matrix4()     // translate * rotate * uniform-scale
        val color = Color(1f, 1f, 1f, 1f)
        val vel = Vector3()
        val rotAxis = Vector3()
        val pos = Vector3()
        var rotSpeed = 0f
        var life = 0f
        var maxLife = 0f
        var size = 0f
        var alpha = 1f
    }

    private val rnd = Random(System.nanoTime())
    private val live = ArrayList<Shard>(maxShards)      // active (updated + rendered)
    private val pool = ArrayList<Shard>(maxShards)      // free list — reused across bursts
    private val mesh = kit.newBatchMesh(maxShards)
    private val verts = FloatArray(maxShards * kit.vertsPerBox * 4)
    private val wc = FloatArray(24)     // scratch: 8 transformed corners (xyz)
    private val packed = FloatArray(6)  // scratch: per-face packed colour
    private val light = FloatArray(3)   // scratch: rgb light factors
    private val sN = Vector3()
    private val sV = Vector3()

    /** Live shard count (for the perf log). */
    val count: Int get() = live.size

    init { // build the whole pool up front (load time) so no burst ever allocates mid-run
        repeat(maxShards) { pool.add(Shard()) }
    }

    /** Take a free shard: from the pool, or a fresh one until the cap, else recycle oldest. */
    private fun obtain(): Shard = when {
        pool.isNotEmpty() -> pool.removeAt(pool.size - 1)
        live.size < maxShards -> Shard()
        else -> live.removeAt(0) // at cap: retire the oldest, re-seed it below
    }

    /** A shard explosion at a world position. */
    fun burst(at: Vector3, color: Color, n: Int, speed: Float, size: Float, life: Float) {
        repeat(n) {
            val s = obtain()
            s.color.set(color)
            s.alpha = 1f
            s.vel.set(
                rnd.nextFloat() * 2f - 1f,
                rnd.nextFloat() * 1.6f - 0.3f,
                rnd.nextFloat() * 2f - 1f,
            ).nor().scl(speed * (0.4f + rnd.nextFloat() * 0.9f))
            s.rotAxis.set(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()).nor()
            s.rotSpeed = (rnd.nextFloat() - 0.5f) * 720f
            val l = life * (0.5f + rnd.nextFloat() * 0.7f)
            s.life = l; s.maxLife = l
            s.size = size * (0.6f + rnd.nextFloat() * 0.9f)
            s.pos.set(at)
            live.add(s)
        }
    }

    fun update(dt: Float) {
        var i = live.size - 1
        while (i >= 0) {
            val s = live[i]
            s.life -= dt
            if (s.life <= 0f) {
                val last = live.size - 1
                live[i] = live[last]      // swap-remove: O(1), no array shift
                live.removeAt(last)
                pool.add(s)               // return to the pool for reuse
            } else {
                s.vel.y -= 14f * dt
                s.pos.mulAdd(s.vel, dt)
                val k = (s.life / s.maxLife).coerceIn(0f, 1f)
                s.alpha = k
                val sc = s.size * (0.4f + 0.6f * k)
                s.transform.idt()
                    .translate(s.pos)
                    .rotate(s.rotAxis, s.rotSpeed * (s.maxLife - s.life))
                    .scale(sc, sc, sc)
            }
            i--
        }
    }

    /** One blended draw call for every live shard. Run after the world (depth already written). */
    fun render(cam: Camera) {
        val n = live.size
        if (n == 0) return
        var w = 0
        val cl = kit.cornerLocal; val fn = kit.faceNrm
        for (i in 0 until n) {
            val s = live[i]; val m = s.transform
            val cr = s.color.r; val cg = s.color.g; val cb = s.color.b; val a = s.alpha
            for (c in 0 until 8) { // 8 shared cube corners -> world space (not 24 verts)
                val ci = c * 3
                sV.set(cl[ci], cl[ci + 1], cl[ci + 2]).mul(m)
                wc[ci] = sV.x; wc[ci + 1] = sV.y; wc[ci + 2] = sV.z
            }
            for (f in 0 until 6) { // 6 faces -> baked lit colour
                val fi = f * 3
                sN.set(fn[fi], fn[fi + 1], fn[fi + 2]).rot(m)
                kit.lightFace(sN.x, sN.y, sN.z, light, 0)
                packed[f] = Color.toFloatBits(
                    minOf(1f, cr * light[0]), minOf(1f, cg * light[1]), minOf(1f, cb * light[2]), a,
                )
            }
            for (v in 0 until kit.vertsPerBox) { // assemble the 24 verts from the corner+face lookups
                val ci = kit.cornerOf[v] * 3
                verts[w++] = wc[ci]; verts[w++] = wc[ci + 1]; verts[w++] = wc[ci + 2]
                verts[w++] = packed[kit.faceOf[v]]
            }
        }
        mesh.setVertices(verts, 0, w)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)                 // blended: test against scene, don't occlude each other
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, n * kit.idxPerBox)
        // Restore the state ModelBatch's RenderContext.end() used to leave behind,
        // so the following ShapeRenderer passes (flash, HUD) aren't affected.
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        mesh.dispose()
    }
}
