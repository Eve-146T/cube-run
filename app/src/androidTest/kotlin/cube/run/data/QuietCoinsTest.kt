package cube.run.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class QuietCoinsTest {
    @Test fun quietCoinsDefaultsOffPreservesOldChoicesAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = listOf("quiet_coins", "road_coins").associateWith { key ->
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }
        try {
            prefs.edit().remove("quiet_coins").remove("road_coins").commit(); Settings.init(context)
            assertFalse(Settings.quietCoins); assertTrue(Settings.roadCoins)
            assertEquals(1f, Settings.roadCoinOpacity, 0f)
            for (oldVisible in listOf(false, true)) {
                prefs.edit().remove("quiet_coins").putBoolean("road_coins", oldVisible).commit(); Settings.init(context)
                assertEquals(!oldVisible, Settings.quietCoins)
                assertEquals(oldVisible, Settings.roadCoins)
            }
            Settings.setQuietCoins(true); Settings.init(context)
            assertTrue(Settings.quietCoins); assertFalse(Settings.roadCoins)
            assertEquals(.12f, Settings.roadCoinOpacity, 0f)
            Settings.setQuietCoins(false); Settings.init(context)
            assertFalse(Settings.quietCoins); assertTrue(Settings.roadCoins)
        } finally {
            val edit = prefs.edit()
            for ((key, value) in saved) if (value == null) edit.remove(key) else edit.putBoolean(key, value)
            edit.commit(); Settings.init(context)
        }
    }
}
