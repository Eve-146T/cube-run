package cube.run.game

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cube.run.GameActivity
import cube.run.bot.LiveBotDriver.Companion.gl
import cube.run.core.Stage
import cube.run.data.Progress
import cube.run.data.Settings
import cube.run.game.track.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ShardPickupTest {
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun shardsAreAsFrequentAsJetsInNormalAndDeveloperRuns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings.init(context); Progress.init(context)
        val dev = Settings.devMode; val pill = Settings.testPillWorld; val section = Settings.testSection
        try {
            Settings.testPillWorld = false; Settings.testSection = -1
            for (mode in listOf(false,true)) {
                Settings.setDevMode(mode); Lanes.reset()
                val track = Track(Random(771),ObstacleFactory(Random(89)))
                track.reset(.2f,250f); track.rows.clear()
                var jets = 0; val shards = IntArray(3)
                repeat(30000) {
                    track.spawn(20f,250f,10000,.5f)
                    for (row in track.rows) {
                        if(row.pickup == Pickup.JET) jets++
                        val type = Pickup.shardType(row.pickup)
                        if(type >= 0) shards[type]++
                    }
                    track.rows.clear()
                }
                assertTrue(shards.all { it > 20 })
                assertTrue("1 shard offer per jet: dev=$mode shards=${shards.sum()} jets=$jets",kotlin.math.abs(jets - shards.sum()) <= 2)
                android.util.Log.i("SHARD_RARITY","dev=$mode jets=$jets shards=${shards.joinToString()}")
            }
        } finally { Settings.setDevMode(dev); Settings.testPillWorld = pill; Settings.testSection = section; Lanes.reset() }
    }

    @Test fun boxesAwardTwoToFourShardsAndBankThatExactAmount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Progress.init(context)
        val prefs = context.getSharedPreferences("progress",0)
        val keys = listOf("shards_0","shards_1","shards_2","boxes_opened","box_coin_streak")
        val saved = keys.associateWith { if (prefs.contains(it)) prefs.getInt(it,0) else null }
        val before = IntArray(3) { Progress.shards(it) }
        val source = Random(719)
        val random = object : Random() {
            override fun nextBits(bitCount: Int) = source.nextBits(bitCount)
            override fun nextFloat() = .2f // The normal shard reward band.
        }
        try {
            field(Progress,"ownedSkins").setInt(Progress,1)
            field(Progress,"boxCoinStreak").setInt(Progress,0)
            val amounts = mutableSetOf<Int>()
            val totals = IntArray(3)
            repeat(90) {
                val reward = Progress.openBox(random)
                assertEquals(Progress.BoxReward.SHARDS,reward.kind)
                assertTrue("Shard reward must be 2–4",reward.amount in 2..4)
                amounts.add(reward.amount); totals[reward.id] += reward.amount
            }
            assertEquals(setOf(2,3,4),amounts)
            Progress.init(context)
            assertArrayEquals(IntArray(3) { before[it]+totals[it] },IntArray(3) { Progress.shards(it) })
        } finally {
            val edit = prefs.edit()
            saved.forEach { (key,value) -> if(value==null) edit.remove(key) else edit.putInt(key,value) }
            edit.commit(); Progress.init(context)
        }
    }

    @Test fun crystalsRenderOnAllThreeLanes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(GameActivity::class.java).use { scenario ->
            scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
            gl { game ->
                Stage.mode = Stage.NONE; Stage.paused = false; game.onTap(360f,760f); Stage.paused = true
                val track = field(game,"track").get(game) as Track
                track.rows.clear()
                for(type in 0..2) track.rows.add(Row(-4f,arrayListOf()).apply {
                    pickup = Pickup.SHARD_EMBER+type; pickupX = Lanes.x(type); pop = 1f
                })
            }
            SystemClock.sleep(800)
            if (InstrumentationRegistry.getArguments().getString("captureShards") == "true") {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val out = java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"shard-review/track-crystals.png")
                out.parentFile?.mkdirs()
                out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
        }
        Stage.paused = false; Stage.mode = Stage.NONE
    }

    @Test fun trackPickupsCreditOneShardEachAndBankOnceAtRunEnd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Settings.init(context); Progress.init(context)
        val before = IntArray(3) { Progress.shards(it) }
        val dev = Settings.devMode
        try {
            Settings.setDevMode(false)
            ActivityScenario.launch(GameActivity::class.java).use { scenario ->
                scenario.onActivity { it.setShowWhenLocked(true); it.setTurnScreenOn(true) }
                gl { game ->
                    Stage.mode = Stage.NONE; Stage.paused = false; game.onTap(360f,760f); Stage.paused = true
                    val track = field(game,"track").get(game) as Track
                    val player = field(game,"player").get(game) as Player
                    track.rows.clear(); field(track,"spawnAcc").setFloat(track,-10000f)
                    for(type in listOf(0,1,1,2)) {
                        player.forceGround(0f)
                        val row = Row(-Row.PICKUP_DZ,arrayListOf()).apply { pickup = Pickup.SHARD_EMBER+type; pickupX = player.px }
                        track.rows.add(row)
                        game.tick(.001f)
                        assertEquals(Pickup.NONE,row.pickup)
                        game.tick(.001f) // Collected rows cannot pay twice.
                    }
                    assertArrayEquals("Rewards wait for the end of the run",before,IntArray(3) { Progress.shards(it) })
                    game.session.gameOver(); game.session.gameOver(); game.session.addShard(1)
                    assertArrayEquals(intArrayOf(before[0]+1,before[1]+2,before[2]+1),IntArray(3) { Progress.shards(it) })
                }
                SystemClock.sleep(1900)
                if (InstrumentationRegistry.getArguments().getString("captureShards") == "true") {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val out = java.io.File(context.getExternalFilesDir(null),"shard-review/run-shards.png")
                    out.parentFile?.mkdirs()
                    out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                }
                scenario.onActivity { activity ->
                    fun descriptions(v: android.view.View): List<String> = listOf(v.contentDescription?.toString() ?: "") +
                        if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { descriptions(v.getChildAt(it)) } else emptyList()
                    val all = descriptions(activity.findViewById(android.R.id.content))
                    assertTrue(all.contains("1 Ember shards collected")); assertTrue(all.contains("2 Frost shards collected")); assertTrue(all.contains("1 Void shards collected"))
                }
            }
        } finally {
            // Preserve the device's inventory even when an assertion fails.
            val prefs = context.getSharedPreferences("progress",0).edit()
            before.forEachIndexed { i,n -> prefs.putInt("shards_$i",n) }; prefs.commit(); Progress.init(context)
            Settings.setDevMode(dev); Stage.paused = false; Stage.mode = Stage.NONE
        }
    }
}
