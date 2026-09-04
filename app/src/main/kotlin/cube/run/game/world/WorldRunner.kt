package cube.run.game.world

import com.badlogic.gdx.graphics.Color
import cube.run.core.Gdx3DGame
import cube.run.core.hsvInto
import cube.run.data.Worlds
import kotlin.math.min
import kotlin.random.Random

/**
 * Which world the run is in and the cross-fade between them. Every
 * [rowsPerWorld] rows a gate is dropped at the horizon in the next world's
 * colour; everything born behind it (tiles, roadside, obstacles) is already
 * in the new style, and when the gate reaches the player the sky and haze
 * cross-fade over a couple of seconds. The run always starts in the first
 * world; after that the order is shuffled so no two runs feel the same.
 */
class WorldRunner(private val game: Gdx3DGame, private val scenery: Scenery, private val rnd: Random) {

    private val rowsPerWorld = 140

    /** The world the player is *in* (for the results). */
    var world: Worlds.World = Worlds.get(0)
        private set
    /** The world new rows are spawned for (switches at the gate's spawn, ahead of [world]). */
    var spawnWorld: Worlds.World = world
        private set
    /** The base hue obstacles spawn with. */
    val hue: Float get() = spawnWorld.hue
    /** How many worlds this run has passed through. */
    var visited = 1
        private set

    private var lastSwitchRow = 0
    private var fade = 1f          // 0 → 1 while the sky cross-fades
    private val fromTop = Color(); private val fromBot = Color()
    private val toTop = Color(); private val toBot = Color()
    private val gateCol = Color()
    private var fromFog = 0.6f
    private var toFog = 0.6f
    private var pendingWorld: Worlds.World? = null
    private var order = ArrayList<Int>()

    /** Sky colours the game should be showing right now. */
    val skyTop = Color()
    val skyBottom = Color()
    /** The fog's blend toward the sky top (world-dependent). */
    var fogMix = 0.62f
        private set

    fun reset() {
        order = ArrayList(Worlds.all.indices.shuffled(rnd)) // a different first world every launch
        if (cube.run.data.Settings.testWorld >= 0) { order.remove(cube.run.data.Settings.testWorld); order.add(0, cube.run.data.Settings.testWorld) }
        world = Worlds.get(order.removeAt(0))
        spawnWorld = world
        visited = 1
        lastSwitchRow = 0
        fade = 1f
        pendingWorld = null
        scenery.setWorld(world)
        paint(world, skyTop, skyBottom)
        fogMix = world.fogMix
        fromFog = fogMix; toFog = fogMix
    }

    private fun paint(w: Worlds.World, top: Color, bot: Color) {
        hsvInto(top, w.skyTopH, w.skyTopS, w.skyTopV)
        hsvInto(bot, w.skyBotH, w.skyBotS, w.skyBotV)
    }

    /** Called with the running row count; drops the next gate when it is time. */
    fun onRow(rowsPassed: Int) {
        if (pendingWorld != null || rowsPassed - lastSwitchRow < rowsPerWorld) return
        lastSwitchRow = rowsPassed
        if (order.isEmpty()) order = ArrayList((0 until Worlds.all.size).filter { it != world.id }.shuffled(rnd))
        val next = Worlds.get(order.removeAt(0))
        pendingWorld = next
        spawnWorld = next
        scenery.setWorld(next)
        hsvInto(gateCol, next.postH, next.postS, next.postV)
        scenery.spawnGate(gateCol)
    }

    /** The gate just passed the player: start the sky cross-fade. Returns the world entered. */
    fun gatePassed(): Worlds.World? {
        val next = pendingWorld ?: return null
        pendingWorld = null
        fromTop.set(skyTop); fromBot.set(skyBottom); fromFog = fogMix
        paint(next, toTop, toBot); toFog = next.fogMix
        fade = 0f
        world = next
        visited++
        return next
    }

    fun tick(dt: Float) {
        if (fade < 1f) {
            fade = min(1f, fade + dt / 2.4f)
            val k = fade * fade * (3f - 2f * fade)
            skyTop.set(fromTop).lerp(toTop, k)
            skyBottom.set(fromBot).lerp(toBot, k)
            fogMix = fromFog + (toFog - fromFog) * k
        }
    }

    /** The gate colour of the world being entered (for the flash). */
    fun gateColor(): Color = gateCol
}
