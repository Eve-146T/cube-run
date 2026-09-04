package cube.run.data

/**
 * The worlds (biomes) the track runs through. Each is a palette + a kind of
 * roadside decoration; the game cross-fades between them every so often
 * (see game.world.WorldRunner). Obstacles keep their fixed hue offsets from
 * [hue], so every world reads the same way: pillars, walls and bars stay
 * recognisable while everything around them changes. Palettes are loud on
 * purpose: saturated skies, two-tone candy floors (kept deeper than any
 * cube skin so the cube always stands out), bright roadside.
 */
object Worlds {
    // roadside decoration styles (drawn by Scenery)
    const val LOLLIPOPS = 0   // candy: gumdrops, canes, cupcakes
    const val TOWERS = 1      // city: tall blocks with lit windows
    const val CRYSTALS = 2    // caves: shard clusters
    const val SPIKES = 3      // frost: tapering white-capped stacks
    const val CACTI = 4       // dunes: blocks with arms
    const val RINGS = 5       // space: floating spun frames

    class World(
        val id: Int,
        val name: String,
        /** The obstacle/base hue; obstacle kinds add their offsets to it. */
        val hue: Float,
        // sky gradient, HSV
        val skyTopH: Float, val skyTopS: Float, val skyTopV: Float,
        val skyBotH: Float, val skyBotS: Float, val skyBotV: Float,
        // floor checker
        val floorH: Float, val floorS: Float, val floorV: Float, val floorAltV: Float,
        // roadside
        val postH: Float, val postS: Float, val postV: Float,
        val deco: Int,
        /** The far haze mixes this much sky-top into the fog colour (0 = horizon, 1 = zenith). */
        val fogMix: Float = 0.62f,
    )

    val all: List<World> = listOf(
        World(0, "Candy Fields", 320f, 318f, 0.55f, 1f, 275f, 0.6f, 0.75f, 318f, 0.72f, 0.7f, 0.54f, 160f, 0.7f, 1f, LOLLIPOPS, 0.5f),
        World(1, "Neon City", 250f, 258f, 0.8f, 0.42f, 265f, 0.8f, 0.10f, 252f, 0.8f, 0.42f, 0.28f, 310f, 0.95f, 1f, TOWERS, 0.7f),
        World(2, "Lava Caves", 10f, 6f, 0.9f, 0.5f, 350f, 0.95f, 0.08f, 12f, 0.9f, 0.42f, 0.26f, 28f, 1f, 1f, CRYSTALS, 0.7f),
        World(3, "Frost Peaks", 205f, 200f, 0.5f, 1f, 225f, 0.6f, 0.75f, 208f, 0.66f, 0.7f, 0.54f, 195f, 0.25f, 1f, SPIKES, 0.45f),
        World(4, "Sunset Dunes", 30f, 26f, 0.9f, 1f, 300f, 0.7f, 0.5f, 34f, 0.8f, 0.72f, 0.55f, 305f, 0.8f, 1f, CACTI, 0.55f),
        World(5, "Deep Space", 230f, 238f, 0.9f, 0.22f, 255f, 0.95f, 0.05f, 232f, 0.85f, 0.26f, 0.12f, 185f, 1f, 1f, RINGS, 0.75f),
    )

    fun get(id: Int): World = all.getOrElse(id) { all[0] }
}
