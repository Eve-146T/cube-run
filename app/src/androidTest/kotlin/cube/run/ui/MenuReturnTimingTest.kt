package cube.run.ui

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.badlogic.gdx.Gdx
import cube.run.GameActivity
import cube.run.core.Gdx3DGame
import cube.run.data.Progress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Measures painted page reuse and live results resets, including fresh GL frames. */
class MenuReturnTimingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(action: () -> Unit) = instrumentation.runOnMainSync(action)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        error("Missing $name")
    }
    private fun launch(): GameActivity = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Hud.EXTRA_AUTOSTART, false)
    ) as GameActivity
    private fun awaitMenu(activity: GameActivity, timeout: Long = 5000) {
        val deadline = SystemClock.uptimeMillis() + timeout
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            ui {
                val menu = field<MainMenu>(field<Hud>(activity, "hud"), "menu")
                ready = menu.isShown && listOf("top", "middle", "rightChips", "bank").all { name ->
                    val view = field<View>(menu, name)
                    view.alpha >= .99f && kotlin.math.abs(view.translationY) < .5f
                }
            }
            if (!ready) SystemClock.sleep(10)
        }
        assertTrue("Menu controls became visible and settled", ready)
    }
    private fun awaitScene() {
        val drawn = CountDownLatch(1)
        (Gdx.app.applicationListener as Gdx3DGame).afterFreshSceneFrame { drawn.countDown() }
        assertTrue("Fresh game scene rendered", drawn.await(5, TimeUnit.SECONDS))
    }

    private fun awaitCaches(activity: GameActivity): Map<String, Page> {
        val deadline = SystemClock.uptimeMillis() + 8000
        var pages = emptyMap<String, Page>()
        var ready = false
        var diagnostic = ""
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            ui {
                val hud = field<Hud>(activity, "hud")
                val current = hud.javaClass.getDeclaredMethod("pageCacheKey").apply { isAccessible = true }.invoke(hud)
                val entries = field<Map<String, Any>>(hud, "cachedPages")
                ready = entries.size == 3 && entries.values.all { field<Boolean>(it, "painted") && field<List<Any>>(it, "key") == current }
                diagnostic = entries.entries.joinToString { (name, entry) ->
                    val view = field<Page>(entry, "view")
                    "$name: painted=${field<Boolean>(entry, "painted")}, key=${field<List<Any>>(entry, "key") == current}, size=${view.width}x${view.height}, layout=${view.isLayoutRequested}, attached=${view.isAttachedToWindow}, ready=${(view as? AchievementsView)?.contentReady}"
                }
                if (ready) pages = entries.mapValues { field<Page>(it.value, "view") }
            }
            if (!ready) SystemClock.sleep(20)
        }
        assertTrue("All pages were laid out and painted before navigation: $diagnostic", ready)
        return pages
    }

    @Test fun cachedPagesOpenOnTheNextFrameAndReuseTheirViews() {
        val activity = launch()
        val unlocked = Progress.achievementsUnlocked
        val unlockField = Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }
        try {
            ui {
                unlockField.setBoolean(null, true)
                field<Hud>(activity, "hud").javaClass.getDeclaredMethod("schedulePagePreparation").apply { isAccessible = true }
                    .invoke(field<Hud>(activity, "hud"))
            }
            awaitMenu(activity); awaitScene()
            val cached = awaitCaches(activity)
            assertEquals("Preparing wardrobe must not change the live GL stage", cube.run.core.Stage.NONE, cube.run.core.Stage.mode)
            repeat(4) { cycle ->
                for ((name, page) in cached) {
                    val drawn = CountDownLatch(1)
                    var startedAt = 0L
                    var elapsed = 0L
                    ui {
                        val hud = field<Hud>(activity, "hud")
                        val method = when (name) {
                            "achievements" -> "openAchievements"
                            "wardrobe" -> "openWardrobe"
                            else -> "openSections"
                        }
                        startedAt = SystemClock.uptimeMillis()
                        hud.javaClass.getDeclaredMethod(method).apply { isAccessible = true }.invoke(hud)
                        assertSame("Opening $name must reuse its prebuilt page", page, field<Page>(hud, "page"))
                        assertEquals(1f, page.alpha, .001f)
                        assertEquals(1f, field<View>(page, "content").alpha, .001f)
                        if (Build.VERSION.SDK_INT >= 29) page.viewTreeObserver.registerFrameCommitCallback {
                            elapsed = SystemClock.uptimeMillis() - startedAt
                            drawn.countDown()
                        } else page.postOnAnimation {
                            elapsed = SystemClock.uptimeMillis() - startedAt
                            drawn.countDown()
                        }
                        page.rootView.invalidate()
                    }
                    assertTrue("$name drew a complete frame", drawn.await(2, TimeUnit.SECONDS))
                    if (cycle == 0 && InstrumentationRegistry.getArguments().getString("captureMenus") == "true") {
                        // Submission precedes SurfaceFlinger presentation; capture the latched screen.
                        SystemClock.sleep(100)
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        java.io.File(activity.getExternalFilesDir(null), "cached-$name.png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                    android.util.Log.i("InstantMenu", "$name cycle $cycle: submitted frame in $elapsed ms")
                    assertTrue("$name opening took $elapsed ms", elapsed < 80)
                    ui {
                        page.navigateBack()
                        assertNull(field<Page?>(field<Hud>(activity, "hud"), "page"))
                        assertEquals(View.INVISIBLE, page.visibility)
                    }
                    awaitMenu(activity)
                }
            }
            assertEquals(3, field<Map<String, Any>>(field<Hud>(activity, "hud"), "cachedPages").size)
        } finally { ui { unlockField.setBoolean(null, unlocked); activity.finish() } }
    }

    @Test fun changedBalanceInvalidatesHiddenPagesBeforeTheNextTap() {
        val activity = launch()
        val unlocked = Progress.achievementsUnlocked
        val unlockField = Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }
        try {
            ui {
                unlockField.setBoolean(null, true)
                field<Hud>(activity, "hud").javaClass.getDeclaredMethod("schedulePagePreparation").apply { isAccessible = true }
                    .invoke(field<Hud>(activity, "hud"))
            }
            awaitMenu(activity); awaitScene()
            val before = awaitCaches(activity)
            ui { Progress.addCoins(5) }
            // SharedPreferences delivers changes on the main thread; let invalidation run first.
            instrumentation.waitForIdleSync()
            val after = awaitCaches(activity)
            assertNotSame(before["achievements"], after["achievements"])
            assertNotSame(before["wardrobe"], after["wardrobe"])
            ui {
                val hud = field<Hud>(activity, "hud")
                hud.javaClass.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud)
                val page = field<AchievementsView>(hud, "page")
                val bank = field<android.widget.LinearLayout>(page, "bank")
                assertEquals(Progress.coins.toString(),
                    UiKit(activity).labelOf(bank).text.toString())
                val menuBank = field<View>(field<MainMenu>(hud, "menu"), "bank")
                val expected = IntArray(2); val actual = IntArray(2)
                menuBank.getLocationOnScreen(expected); bank.getLocationOnScreen(actual)
                assertEquals("Achievements bank keeps the menu's top corner", expected[1], actual[1])
                assertEquals(expected[0] + menuBank.width, actual[0] + bank.width)
                val titleBounds = android.graphics.Rect(); val bankBounds = android.graphics.Rect()
                field<View>(page, "titleView").getGlobalVisibleRect(titleBounds)
                bank.getGlobalVisibleRect(bankBounds)
                assertFalse("Heading must never overlap the coin pill", android.graphics.Rect.intersects(titleBounds, bankBounds))
            }
        } finally { ui { unlockField.setBoolean(null, unlocked); activity.finish() } }
    }

    @Test fun resultsReturnMakesMenuAndSceneReadyWithoutLaunchStagger() {
        val activity = launch()
        try {
            awaitMenu(activity); awaitScene()
            val game = Gdx.app.applicationListener as cube.run.game.CubeRun
            val surface = field<View>(activity, "gameSurface")
            val ownedCount = field<List<Any>>(game, "owned").size
            repeat(3) { cycle ->
                val started = CountDownLatch(1)
                Gdx.app.postRunnable {
                    game.onTap(.5f, .5f)
                    game.session.addScore(12)
                    game.session.setCoins(7)
                    cube.run.core.Stage.endRun = true
                    started.countDown()
                }
                assertTrue(started.await(2, TimeUnit.SECONDS))
                val resultBy = SystemClock.uptimeMillis() + 5000
                var resultsVisible = false
                while (!resultsVisible && SystemClock.uptimeMillis() < resultBy) {
                    ui { resultsVisible = field<RunOverFlow?>(field<Hud>(activity, "hud"), "runOver") != null }
                    if (!resultsVisible) SystemClock.sleep(20)
                }
                assertTrue("The real run reached results", resultsVisible)
                val banked = Progress.coins
                SystemClock.sleep(350)
                val startedAt = SystemClock.uptimeMillis()
                ui {
                    val flow = field<RunOverFlow>(field<Hud>(activity, "hud"), "runOver")
                    field<View>(flow, "page").performClick()
                    field<View>(flow, "page").performClick()
                }
                awaitMenu(activity); awaitScene()
                val elapsed = SystemClock.uptimeMillis() - startedAt
                android.util.Log.i("MenuReturn", "Results cycle $cycle: menu and GL scene ready in $elapsed ms")
                assertTrue("Results return took $elapsed ms", elapsed < 120)
                assertSame(game, Gdx.app.applicationListener)
                assertSame(surface, field<View>(activity, "gameSurface"))
                assertEquals("Returning must not allocate more GL models", ownedCount, field<List<Any>>(game, "owned").size)
                assertEquals("Run score was reset", 0, game.session.score)
                assertFalse(game.session.isOver)
                assertEquals("Rewards must not be banked twice", banked, Progress.coins)
            }
        } finally { ui { activity.finish() } }
    }

    @Test fun achievementsBackRestoresMenuControlsQuickly() {
        val activity = launch()
        val unlocked = Progress.achievementsUnlocked
        val unlockField = Progress::class.java.getDeclaredField("achievementsUnlocked").apply { isAccessible = true }
        try {
            awaitMenu(activity); awaitScene()
            awaitCaches(activity)
            repeat(3) { cycle ->
                ui {
                    unlockField.setBoolean(null, true)
                    val hud = field<Hud>(activity, "hud")
                    hud.javaClass.getDeclaredMethod("openAchievements").apply { isAccessible = true }.invoke(hud)
                }
                SystemClock.sleep(300)
                val startedAt = SystemClock.uptimeMillis()
                ui { field<Page>(field<Hud>(activity, "hud"), "page").navigateBack() }
                awaitMenu(activity)
                val elapsed = SystemClock.uptimeMillis() - startedAt
                android.util.Log.i("MenuReturn", "Achievements cycle $cycle: controls ready in $elapsed ms")
                assertTrue("Achievements Back took $elapsed ms", elapsed < 70)
                ui { assertNull(field<Page?>(field<Hud>(activity, "hud"), "page")) }
            }
        } finally { ui { unlockField.setBoolean(null, unlocked); activity.finish() } }
    }

    @Test fun pauseReturnClearsLiveAbilitiesAndCanStartAnotherRun() {
        val activity = launch()
        try {
            awaitMenu(activity); awaitScene(); awaitCaches(activity)
            val game = Gdx.app.applicationListener as cube.run.game.CubeRun
            val ready = CountDownLatch(1)
            Gdx.app.postRunnable {
                game.onTap(.5f, .5f)
                val powers = field<cube.run.game.PowerUps>(game, "powerUps")
                powers.jet.start(20f); powers.magnet.start(20f); powers.mult.start(20f)
                field<cube.run.game.Player>(game, "player").setFlying(true)
                game.session.addScore(21)
                ready.countDown()
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            ui {
                val hud = field<Hud>(activity, "hud")
                hud.pause(animate = false)
                val sheet = field<PauseSheet>(hud, "pauseSheet")
                // Actual MENU button, after header, RESUME and RESTART.
                field<android.widget.LinearLayout>(sheet, "card").getChildAt(3).performClick()
            }
            awaitMenu(activity); awaitScene()
            assertSame(game, Gdx.app.applicationListener)
            val verified = CountDownLatch(1)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            Gdx.app.postRunnable {
                try {
                    val powers = field<cube.run.game.PowerUps>(game, "powerUps")
                    assertFalse(powers.jet.active || powers.magnet.active || powers.mult.active)
                    assertFalse(field<cube.run.game.Player>(game, "player").flying)
                    assertFalse(cube.run.core.Stage.paused)
                    assertEquals(0, game.session.score)
                    assertFalse(field<Boolean>(game, "started"))
                    game.onTap(.5f, .5f)
                    assertTrue(field<Boolean>(game, "started"))
                    assertFalse(game.session.isOver)
                } catch (error: Throwable) { failure.set(error) }
                finally { verified.countDown() }
            }
            assertTrue(verified.await(2, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            awaitScene()
        } finally { ui { activity.finish() } }
    }

    @Test fun wardrobeBackRestoresTheSettledCameraWithoutAnotherLaunchZoom() {
        val activity = launch()
        try {
            awaitMenu(activity); awaitScene(); awaitCaches(activity)
            ui {
                val hud = field<Hud>(activity, "hud")
                hud.javaClass.getDeclaredMethod("openWardrobe").apply { isAccessible = true }.invoke(hud)
            }
            awaitScene(); SystemClock.sleep(350)
            ui { field<Page>(field<Hud>(activity, "hud"), "page").navigateBack() }
            awaitMenu(activity); awaitScene()
            repeat(3) {
                val checked = CountDownLatch(1)
                val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
                Gdx.app.postRunnable {
                    try {
                        val game = Gdx.app.applicationListener as cube.run.game.CubeRun
                        val rig = field<cube.run.game.RunCamera>(game, "rig")
                        assertEquals("Back must restore the settled camera immediately", 0f, rig.intro, .001f)
                        assertTrue(field<Float>(game, "introT") >= 1.8f)
                        assertEquals(cube.run.intro.OpeningPose.MENU_CAMERA_Z, game.cam.position.z, .01f)
                    } catch (t: Throwable) { failure.set(t) }
                    finally { checked.countDown() }
                }
                assertTrue(checked.await(2, TimeUnit.SECONDS)); failure.get()?.let { throw it }
                SystemClock.sleep(150)
            }
        } finally { ui { activity.finish() } }
    }
}
