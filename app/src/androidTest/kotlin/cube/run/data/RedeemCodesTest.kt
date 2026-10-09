package cube.run.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class RedeemCodesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var saved: Map<String, *>
    @Before fun prepare() {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        saved = prefs.all
        prefs.edit().clear().commit()
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
        Settings.setDevMode(false); edit.commit(); Progress.init(context)
    }

    @Test fun coin500PaysOnceAcrossReloadsAndCaseVariants() {
        assertTrue(RedeemCodes.redeem("  CoIn500  ") is RedeemCodes.Result.Granted)
        assertEquals(500, Progress.coins)
        assertEquals("Bonus rewards are not earned gameplay coins", 0, Progress.totalCoins)
        Progress.init(context)
        assertEquals(500, Progress.coins)
        assertEquals(RedeemCodes.Result.AlreadyUsed, RedeemCodes.redeem("coin500"))
        assertEquals(RedeemCodes.Result.AlreadyUsed, RedeemCodes.redeem("COIN500"))
        assertEquals(500, Progress.coins)
    }

    @Test fun invalidCodesDoNotConsumeAnything() {
        for (code in listOf("", "  ", "coin 500", "coin500x", "unknown")) {
            assertEquals(RedeemCodes.Result.Invalid, RedeemCodes.redeem(code))
        }
        assertEquals(0, Progress.coins)
        assertTrue(RedeemCodes.redeem("coin500") is RedeemCodes.Result.Granted)
    }

    @Test fun simultaneousSubmissionsOnlyPayOnce() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            val requests = (0 until 24).map { pool.submit<RedeemCodes.Result> { RedeemCodes.redeem("coin500") } }
            val results = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is RedeemCodes.Result.Granted })
            assertEquals(23, results.count { it == RedeemCodes.Result.AlreadyUsed })
            assertEquals(500, Progress.coins)
            Progress.init(context); assertEquals(500, Progress.coins)
        } finally { pool.shutdownNow() }
    }

    @Test fun fullBankKeepsTheCodeAvailableUntilItCanPayInFull() {
        prefs.edit().putInt("coins", Int.MAX_VALUE - 200).commit(); Progress.init(context)
        assertEquals(RedeemCodes.Result.Unavailable, RedeemCodes.redeem("coin500"))
        assertEquals(Int.MAX_VALUE - 200, Progress.coins)
        prefs.edit().putInt("coins", 10).commit(); Progress.init(context)
        assertTrue(RedeemCodes.redeem("coin500") is RedeemCodes.Result.Granted)
        assertEquals(510, Progress.coins)
    }

    @Test fun developerBankDoesNotLoseThePermanentCodeReward() {
        prefs.edit().putInt("coins", 25).commit(); Progress.init(context)
        Progress.enterDev()
        assertTrue(RedeemCodes.redeem("coin500") is RedeemCodes.Result.Granted)
        Progress.leaveDev()
        assertEquals(525, Progress.coins)
        Progress.init(context)
        assertEquals(525, Progress.coins)
        assertEquals(RedeemCodes.Result.AlreadyUsed, RedeemCodes.redeem("coin500"))
    }

    @Test fun futureUnlockRewardsShareThePersistentOneTimeLedger() {
        val code = RedeemCodes.Definition("secret-example", RedeemCodes.Effect.Unlock("example-setting", "Example setting"))
        assertFalse(Progress.isCodeUnlocked("example-setting"))
        assertTrue(Progress.redeemCode(code) is RedeemCodes.Result.Granted)
        Progress.init(context)
        assertTrue(Progress.isCodeUnlocked("example-setting"))
        assertEquals(RedeemCodes.Result.AlreadyUsed, Progress.redeemCode(code))
        assertEquals(0, Progress.coins)
    }
}
