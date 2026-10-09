package cube.run.game.world.biome

import cube.run.data.Worlds

/** Which look each world wears, by its roadside style. */
object BiomeLooks {
    fun of(world: Worlds.World): BiomeLook = when (world.deco) {
        Worlds.TOWERS -> CityLook(world)
        Worlds.CRYSTALS -> LavaLook(world)
        Worlds.SPIKES -> FrostLook(world)
        Worlds.CACTI -> DunesLook(world)
        Worlds.RINGS -> MoonLook(world)
        else -> CandyLook(world)
    }
}
