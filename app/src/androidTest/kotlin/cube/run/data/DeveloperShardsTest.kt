package cube.run.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeveloperShardsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var saved: Map<String, *>
    private var previousDevMode = false

    @Before fun prepare() {
        previousDevMode = Settings.devMode
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        saved = prefs.all
        prefs.edit().clear().putInt("shards_0", 7).putInt("shards_1", 19).putInt("shards_2", 0).commit()
        Settings.init(context); Settings.setDevMode(false); Progress.init(context)
    }

    @After fun restore() {
        val edit = prefs.edit().clear()
        for ((key, value) in saved) when (value) {
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        edit.commit(); Settings.setDevMode(previousDevMode); Progress.init(context)
    }

    @Test fun developerUnlocksAllShardCubesWithoutSpendingOrPersistingVirtualStock() {
        val original = mapOf(Shards.EMBER to 7, Shards.FROST to 19, Shards.VOID to 0)
        Settings.setDevMode(true)
        assertEquals(0, Progress.shards(-1)); assertEquals(0, Progress.shards(Int.MAX_VALUE))
        for ((kind, actual) in original) {
            val skin = Skins.forShard(kind)!!
            assertEquals(Int.MAX_VALUE, Progress.shards(kind))
            assertTrue(Progress.unlockWithShards(skin.id))
            assertFalse("An owned cosmetic cannot be unlocked twice", Progress.unlockWithShards(skin.id))
            assertTrue(Progress.owns(Wardrobe.CUBE, skin.id))
            assertEquals(actual, prefs.getInt("shards_$kind", -1))
        }
        Settings.setDevMode(false); Progress.init(context)
        for ((kind, actual) in original) {
            assertEquals(actual, Progress.shards(kind))
            assertTrue(Progress.owns(Wardrobe.CUBE, Skins.forShard(kind)!!.id))
        }
    }

    @Test fun disablingDeveloperModeRestoresTheNormalShardCostAndEarnedCounts() {
        val skin = Skins.forShard(Shards.EMBER)!!
        assertFalse(Progress.unlockWithShards(skin.id))
        Settings.setDevMode(true)
        Progress.addShards(Shards.EMBER, 3)
        assertEquals(Int.MAX_VALUE, Progress.shards(Shards.EMBER))
        Settings.setDevMode(false)
        assertEquals(10, Progress.shards(Shards.EMBER))
        assertFalse(Progress.unlockWithShards(skin.id))
        Progress.addShards(Shards.EMBER, skin.shardsNeeded - 10)
        assertTrue(Progress.unlockWithShards(skin.id))
        assertEquals(0, Progress.shards(Shards.EMBER))
        Progress.init(context)
        assertEquals(0, Progress.shards(Shards.EMBER))
        assertTrue(Progress.owns(Wardrobe.CUBE, skin.id))
    }
}
