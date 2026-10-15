package cube.run.game

import android.content.SharedPreferences
import com.badlogic.gdx.graphics.Color
import cube.run.data.Skins
import cube.run.data.Wardrobe
import cube.run.game.track.*
import org.junit.Assert.*
import org.junit.Test

/** Smol (#50): passes under bars standing up, but walls and pillars still stop it. */
class SmolCubeTest {
    private fun cube(id: Int): (SharedPreferences.Editor) -> Unit = { it.putInt("owned_skins", 1 or (1 shl id)).putInt("skin", id) }
    private fun bar() = listOf(Row(-12f, arrayListOf(Ob(Color.BLUE, 0f, 1.14f, 3f, ObType.SOLID, 6f, .72f, .7f))))
    private fun pillar() = listOf(Row(-12f, arrayListOf(Ob(Color.RED, 0f, .31f, 3f, ObType.SOLID, 6f, .62f, .7f))))

    @Test fun smolIsSoldInTheWardrobe() {
        assertTrue(Skins.Ability.SMOL in Skins.get(Skins.SMOL_ID).abilities)
        assertTrue(Wardrobe.shopItems(Wardrobe.CUBE).contains(Skins.SMOL_ID))
    }

    @Test fun smolPassesUnderAnOverheadBarWithoutDucking() = playingRun(cube(Skins.SMOL_ID)) { run ->
        run.rows(bar())
        run.frames(60)
        assertTrue(run.read<Player>("player").smol)
        assertFalse("Standing upright under the bar", run.read<Boolean>("dead"))
    }

    @Test fun anOrdinaryCubeMustDuckUnderThatBar() = playingRun(cube(3)) { run ->
        run.rows(bar())
        run.frames(60)
        assertTrue(run.read<Boolean>("dead"))
    }

    @Test fun smolStillHitsPillarsAndWalls() = playingRun(cube(Skins.SMOL_ID)) { run ->
        run.rows(pillar())
        run.frames(60)
        assertTrue(run.read<Boolean>("dead"))
    }
}
