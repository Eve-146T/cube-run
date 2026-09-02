package cube.run.game

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.core.hsvInto
import kotlin.math.max
import kotlin.random.Random

/** Distance haze: crisp until [start], fully dissolved into the sky by [end]. Rows spawn beyond [end]. */
object Fog {
    const val start = 58f
    const val end = 96f

    /** Fog factor for world geometry at [z] (negative = ahead of the player). */
    fun at(z: Float) = ((-z - start) / (end - start)).coerceIn(0f, 1f)
}

/**
 * The scrolling backdrop: checkered floor tiles, neon side posts, and wind
 * streaks that fly past faster than the world at speed. All plain data drawn
 * through the batched world-box pass; wrapping recolours to the current hue.
 */
class Scenery(private val game: Gdx3DGame, private val laneW: Float, private val rnd: Random) {

    private class Tile(val col: Color, var z: Float, val x: Float, val dim: Boolean)
    private class Post(val col: Color, var z: Float, val x: Float)
    private class Streak(var x: Float, var y: Float, var z: Float, var len: Float)

    private val tileD = 3f
    private val tileRows = 40       // floor reaches past the fog wall (8 - 39*3 = -109)
    private val postGap = 6.6f
    private val postPairs = 17      // last pair ≈ -100, born inside the fog
    private val streakCount = 22

    private val tiles = ArrayList<Tile>()
    private val posts = ArrayList<Post>()
    private val streaks = ArrayList<Streak>()
    private val streakCol = Color(1f, 1f, 1f, 1f)
    private var hue = 0f

    private fun laneX(l: Int) = (l - 1) * laneW

    private fun tileHue(t: Tile) {
        // floor hue drifts with total distance; checker brightness reads as a grid
        hsvInto(t.col, hue, 0.7f, if (t.dim) 0.30f else 0.46f)
    }

    private fun seedStreak(s: Streak) {
        val side = if (rnd.nextBoolean()) -1f else 1f
        s.x = side * (laneW * 1.5f + 2.2f + rnd.nextFloat() * 4f)
        s.y = 0.4f + rnd.nextFloat() * 4.5f
        s.len = 3f + rnd.nextFloat() * 5f
    }

    fun init(hue: Float) {
        this.hue = hue
        for (r in 0 until tileRows) for (l in 0..2) {
            val t = Tile(Color(), 8f - r * tileD, laneX(l), (r + l) % 2 == 0)
            tileHue(t)
            tiles.add(t)
        }
        var pz = 6f
        repeat(postPairs) {
            for (side in intArrayOf(-1, 1)) {
                val p = Post(Color(), pz, side * (laneW * 1.5f + 1.0f))
                hsvInto(p.col, hue + 50f, 0.85f, 1f)
                posts.add(p)
            }
            pz -= postGap
        }
        repeat(streakCount) { streaks.add(Streak(0f, 0f, -rnd.nextFloat() * 100f, 0f).also { seedStreak(it) }) }
    }

    /** Scroll by [mv]; pieces that wrap are reborn in the current [hue]. */
    fun scroll(mv: Float, hue: Float) {
        this.hue = hue
        for (t in tiles) {
            t.z += mv
            if (t.z > 8f) { t.z -= tileRows * tileD; tileHue(t) }
        }
        for (p in posts) {
            p.z += mv
            if (p.z > 8f) { p.z -= postPairs * postGap; hsvInto(p.col, hue + 50f, 0.85f, 1f) }
        }
        for (s in streaks) { // wind streaks fly past faster than the world (parallax sells the speed)
            s.z += mv * 1.6f
            if (s.z > 10f) { s.z = -90f - rnd.nextFloat() * 30f; seedStreak(s) }
        }
    }

    /** Queue everything into the batched pass. [wind] 0..1 = how vivid the speed streaks are. */
    fun render(wind: Float) {
        for (p in posts) game.worldBox(p.x, 0.7f, p.z, 0.26f, 1.4f, 0.26f, p.col, Fog.at(p.z))
        for (t in tiles) game.worldBox(t.x, -0.14f, t.z, laneW * 0.92f, 0.26f, tileD * 0.9f, t.col, Fog.at(t.z))
        if (wind > 0f) { // faint at cruising speed, vivid near the ceiling (fog doubles as fade)
            for (s in streaks) {
                val fade = max(Fog.at(s.z), 1f - wind * 0.75f)
                game.worldBox(s.x, s.y, s.z, 0.05f, 0.05f, s.len * (0.6f + wind * 0.8f), streakCol, fade)
            }
        }
    }
}
