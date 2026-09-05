package cube.run.game

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import cube.run.GameActivity
import cube.run.core.gfx.BoxMeshKit
import cube.run.core.gfx.WorldBoxBatch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerrainMeshTest {
    @Test fun groundEdgesMeetAcrossHillsWhileObstaclesStayRigid() {
        val scenario = ActivityScenario.launch(GameActivity::class.java)
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Gdx.app.postRunnable {
            try {
                val kit = BoxMeshKit(ModelBuilder())
                val batch = WorldBoxBatch(kit, 3)
                try {
                    for (amplitude in listOf(0f, 0.2f, 1.25f)) for (phase in 0..20) {
                        batch.terrain = { z -> amplitude * sin(z * 0.24f + phase * 0.3f) }
                        batch.begin()
                        batch.box(0f, -0.14f, 0f, 1.7f, 0.26f, 3f, Color.WHITE, followTerrain = true)
                        batch.box(0f, -0.14f, 3f, 1.7f, 0.26f, 3f, Color.WHITE, followTerrain = true)
                        batch.box(0f, 1f, 0f, 1.5f, 2f, 0.9f, Color.WHITE)
                        val verts = WorldBoxBatch::class.java.getDeclaredField("verts").apply { isAccessible = true }.get(batch) as FloatArray
                        fun edge(box: Int, z: Float) = (0 until kit.vertsPerBox).map { (box * kit.vertsPerBox + it) * 4 }
                            .filter { abs(verts[it + 2] - z) < 0.0001f }.map { verts[it + 1] }.sorted()
                        val first = edge(0, 1.5f); val second = edge(1, 1.5f)
                        assertEquals(first.size, second.size)
                        assertTrue(first.isNotEmpty())
                        for (i in first.indices) assertEquals(first[i], second[i], 0.00001f)
                        assertEquals(-0.01f + batch.terrain!!(1.5f), first.last(), 0.00001f)
                        val heights = (0 until kit.vertsPerBox).map { verts[(2 * kit.vertsPerBox + it) * 4 + 1] }
                        assertEquals(2f, heights.max() - heights.min(), 0.00001f)
                        assertEquals(2, heights.distinct().size)
                    }
                } finally { batch.dispose(); kit.dispose() }
            } catch (t: Throwable) { failure = t } finally { done.countDown() }
        }
        try {
            assertTrue("GL mesh test timed out", done.await(15, TimeUnit.SECONDS))
            failure?.let { throw it }
        } finally { scenario.close() }
    }
}
