package cube.run.core.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/**
 * Cube-shard particles ("juice"): pooled, gravity-driven, tumbling, fading.
 * Allocation-free in steady state — [burst] only re-seeds pooled shards — and
 * rendered as one blended draw call, with GPU instancing on GLES 3 and a
 * CPU-lit vertex fallback on GLES 2 (see [BoxMeshKit]). Invisible shards and back faces are
 * rejected before color packing and vertex uploads; particle physics is unchanged.
 */
class ShardSystem(private val kit: BoxMeshKit, private val maxShards: Int = 240) : Disposable {

    private class Shard {
        @JvmField val transform = Matrix4()     // translate * rotate * uniform-scale
        @JvmField val color = Color(1f, 1f, 1f, 1f)
        @JvmField val vel = Vector3()
        @JvmField val rotAxis = Vector3()
        @JvmField val pos = Vector3()
        @JvmField var rotSpeed = 0f
        @JvmField var life = 0f
        @JvmField var maxLife = 0f
        @JvmField var size = 0f
        @JvmField var alpha = 1f
        @JvmField var gravity = 14f
    }

    private val rnd = Random(System.nanoTime())
    private val live = ArrayList<Shard>(maxShards)      // active (updated + rendered)
    private val pool = ArrayList<Shard>(maxShards)      // free list — reused across bursts
    private var recycle = 0
    private val instances = if (Gdx.gl30 != null) InstancedShards(kit, maxShards) else null
    private val mesh = kit.newBatchMesh(maxShards)
    private val verts = FloatArray(maxShards * kit.vertsPerBox * 4)
    private val wc = FloatArray(24)     // scratch: 8 transformed corners (xyz)
    private val visibility = BatchVisibility()
    private val light = FloatArray(3)   // scratch: rgb light factors
    private val sN = Vector3()
    private val sV = Vector3()

    /** Live shard count (for the perf log). */
    val count: Int get() = live.size

    init { // build the whole pool up front (load time) so no burst ever allocates mid-run
        repeat(maxShards) { pool.add(Shard()) }
    }

    /** Reuse a pooled shard, or rotate through the full live set without shifting the array. */
    private fun obtain(): Shard = when {
        pool.isNotEmpty() -> pool.removeAt(pool.size - 1)
        else -> {
            recycle %= live.size
            val s = live[recycle]
            val last = live.size - 1
            live[recycle] = live[last]
            live.removeAt(last)
            recycle++
            s
        }
    }

    /** A shard explosion at a world position. */
    fun burst(at: Vector3, color: Color, n: Int, speed: Float, size: Float, life: Float, gravity: Float = 14f, biasZ: Float = 0f) {
        // A huge burst cannot display more than the pool: don't seed shards only to overwrite them.
        repeat(n.coerceIn(0, maxShards)) {
            val s = obtain()
            s.color.set(color)
            s.gravity = gravity
            s.alpha = 1f
            s.vel.set(
                rnd.nextFloat() * 2f - 1f,
                rnd.nextFloat() * 1.6f - 0.3f,
                rnd.nextFloat() * 2f - 1f,
            ).nor().scl(speed * (0.4f + rnd.nextFloat() * 0.9f))
            s.vel.z += biasZ
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
                s.vel.y -= s.gravity * dt
                s.pos.mulAdd(s.vel, dt)
                val k = (s.life / s.maxLife).coerceIn(0f, 1f)
                s.alpha = k
            }
            i--
        }
    }

    /** One blended draw call for every live shard. Run after the world (depth already written). */
    fun render(cam: Camera, cull: Boolean = true, instanced: Boolean = true) {
        val n = live.size
        if (n == 0) return
        var w = 0
        val useInstances = instanced && instances != null
        instances?.begin()
        visibility.begin(if (cull) cam else null)
        val view = if (cull) cam as? PerspectiveCamera else null
        val cl = kit.cornerLocal; val fn = kit.faceNrm
        for (i in 0 until n) {
            val s = live[i]; val m = s.transform
            // Transform once per rendered frame, even if a hitch required several
            // simulation slices. Physics never needs the particle's draw matrix.
            val sc = s.size * (0.4f + 0.6f * s.alpha)
            val radius = sc * 0.866026f // circumscribed cube sphere, any rotation
            if (!visibility.visible(s.pos.x, s.pos.y, s.pos.z, radius, radius, radius)) continue
            m.idt().translate(s.pos).rotate(s.rotAxis, s.rotSpeed * (s.maxLife - s.life)).scale(sc, sc, sc)
            if (useInstances) { instances!!.add(m, s.color, s.alpha); continue }
            val cr = s.color.r; val cg = s.color.g; val cb = s.color.b; val a = s.alpha
            for (c in 0 until 8) { // 8 shared cube corners -> world space (not 24 verts)
                val ci = c * 3
                sV.set(cl[ci], cl[ci + 1], cl[ci + 2]).mul(m)
                wc[ci] = sV.x; wc[ci + 1] = sV.y; wc[ci + 2] = sV.z
            }
            var base = 0
            while (base < kit.vertsPerBox) {
                val first = base; base += 4
                val fi = kit.faceOf[first] * 3
                sN.set(fn[fi], fn[fi + 1], fn[fi + 2]).rot(m)
                val ci0 = kit.cornerOf[first] * 3
                if (view != null && sN.x * (view.position.x - wc[ci0]) +
                    sN.y * (view.position.y - wc[ci0 + 1]) +
                    sN.z * (view.position.z - wc[ci0 + 2]) < -.0001f) continue
                kit.lightFace(sN.x, sN.y, sN.z, light, 0)
                val color = Color.toFloatBits(
                    minOf(1f, cr * light[0]), minOf(1f, cg * light[1]), minOf(1f, cb * light[2]), a,
                )
                for (v in first until first + 4) {
                    val ci = kit.cornerOf[v] * 3
                    verts[w++] = wc[ci]; verts[w++] = wc[ci + 1]; verts[w++] = wc[ci + 2]
                    verts[w++] = color
                }
            }
        }
        if (useInstances) { instances!!.render(cam); return }
        if (w == 0) return
        mesh.setVertices(verts, 0, w)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)                 // blended: test against scene, don't occlude each other
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        kit.shader.bind()
        kit.shader.setUniformMatrix("u_projViewTrans", cam.combined)
        mesh.render(kit.shader, GL20.GL_TRIANGLES, 0, w / 16 * 6)
        // Restore the state ModelBatch's RenderContext.end() used to leave behind,
        // so the following ShapeRenderer passes (flash, HUD) aren't affected.
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    override fun dispose() {
        instances?.dispose()
        mesh.dispose()
    }
}
