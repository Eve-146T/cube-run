package cube.run.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent meta-progression: the coin bank, permanent upgrade levels, the
 * bubble-shield stock and the owned/equipped skins. Written from the UI thread
 * (shop) and the session (banking a run's coins); read from the GL thread at
 * run start, so every field is @Volatile.
 */
object Progress {
    private lateinit var prefs: SharedPreferences

    /**
     * A permanent upgrade: the duration of one power-up, bought one level at a
     * time up to [MAX_LEVEL]. Each level just adds [step] seconds to [base].
     */
    class Upgrade(val key: String, val name: String, val base: Float, val step: Float) {
        fun duration(level: Int): Float = base + step * level.coerceIn(0, MAX_LEVEL)
        /** Price of buying [level] + 1. Escalates gently. */
        fun price(level: Int): Int = 80 + 45 * level + 8 * level * level
    }

    const val MAX_LEVEL = 10
    val BUBBLE = Upgrade("bubble_time", "Bubble", 8f, 1.2f)
    val MAGNET = Upgrade("magnet_time", "Magnet", 8f, 1.0f)
    val MULT = Upgrade("mult_time", "2× score", 10f, 1.2f)
    val JET = Upgrade("jet_time", "Jetpack", 5f, 0.6f)
    val upgrades = listOf(BUBBLE, MAGNET, MULT, JET)

    /** What a mystery box held. [amount] is coins for [COINS], count for [BUBBLE]. */
    class BoxReward(val kind: Int, val amount: Int) {
        companion object {
            const val COINS = 0
            const val BUBBLE = 1
        }
    }

    /** A bubble shield: activate in-run with a double tap, absorbs one crash. */
    const val BUBBLE_PRICE = 120

    @Volatile var coins: Int = 0
        private set
    @Volatile var bubbles: Int = 0
        private set
    @Volatile var bubbleLevel: Int = 0
        private set
    @Volatile var magnetLevel: Int = 0
        private set
    @Volatile var multLevel: Int = 0
        private set
    @Volatile var jetLevel: Int = 0
        private set
    /** Equipped skin id (see [Skins]). */
    @Volatile var skin: Int = 0
        private set
    /** Bitmask of owned skin ids. Skin 0 is always owned. */
    @Volatile var ownedSkins: Int = 1
        private set

    /** Lifetime stats, for the game-over card and skin unlock flavour. */
    @Volatile var totalCoins: Int = 0
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
        coins = prefs.getInt("coins", 0)
        bubbles = prefs.getInt("bubbles", 0)
        bubbleLevel = prefs.getInt(BUBBLE.key, 0)
        magnetLevel = prefs.getInt(MAGNET.key, 0)
        multLevel = prefs.getInt(MULT.key, 0)
        jetLevel = prefs.getInt(JET.key, 0)
        skin = prefs.getInt("skin", 0)
        ownedSkins = prefs.getInt("owned_skins", 1) or 1
        totalCoins = prefs.getInt("total_coins", 0)
        if (skin !in Skins.all.indices || ownedSkins and (1 shl skin) == 0) skin = 0
    }

    fun level(u: Upgrade): Int = when (u) {
        BUBBLE -> bubbleLevel
        MAGNET -> magnetLevel
        MULT -> multLevel
        JET -> jetLevel
        else -> 0
    }

    /** Price of the next level, or null when maxed. */
    fun nextPrice(u: Upgrade): Int? = if (level(u) >= MAX_LEVEL) null else u.price(level(u))

    /** The dev toggle's perk: an effectively unlimited bank. */
    fun giveDevCoins() {
        coins = 999_999
        prefs.edit().putInt("coins", coins).apply()
    }

    fun addCoins(n: Int) {
        if (n <= 0) return
        coins += n
        totalCoins += n
        prefs.edit().putInt("coins", coins).putInt("total_coins", totalCoins).apply()
    }

    private fun spend(n: Int): Boolean {
        if (n > coins) return false
        coins -= n
        prefs.edit().putInt("coins", coins).apply()
        return true
    }

    /** Buy the next level of [u]. Returns false when maxed or unaffordable. */
    fun buyUpgrade(u: Upgrade): Boolean {
        val price = nextPrice(u) ?: return false
        if (!spend(price)) return false
        val lvl = level(u) + 1
        when (u) {
            BUBBLE -> bubbleLevel = lvl
            MAGNET -> magnetLevel = lvl
            MULT -> multLevel = lvl
            JET -> jetLevel = lvl
        }
        prefs.edit().putInt(u.key, lvl).apply()
        return true
    }

    fun buyBubble(): Boolean {
        if (!spend(BUBBLE_PRICE)) return false
        bubbles += 1
        prefs.edit().putInt("bubbles", bubbles).apply()
        return true
    }

    /** Consume one stocked bubble (GL thread, on activation). Returns false when empty. */
    fun useBubble(): Boolean {
        if (bubbles <= 0) return false
        bubbles -= 1
        prefs.edit().putInt("bubbles", bubbles).apply()
        return true
    }

    /** Open a mystery box: roll a reward and bank it immediately. */
    fun openBox(): BoxReward {
        val r = Math.random()
        val reward = when {
            r < 0.15 -> BoxReward(BoxReward.BUBBLE, 1)
            r < 0.40 -> BoxReward(BoxReward.COINS, 100 + (Math.random() * 101).toInt())   // 100..200
            else -> BoxReward(BoxReward.COINS, 25 + (Math.random() * 51).toInt())         // 25..75
        }
        if (reward.kind == BoxReward.BUBBLE) {
            bubbles += reward.amount
            prefs.edit().putInt("bubbles", bubbles).apply()
        } else {
            addCoins(reward.amount)
        }
        return reward
    }

    fun ownsSkin(id: Int): Boolean = ownedSkins and (1 shl id) != 0

    fun buySkin(id: Int): Boolean {
        val s = Skins.all.getOrNull(id) ?: return false
        if (ownsSkin(id)) return true
        if (!spend(s.price)) return false
        ownedSkins = ownedSkins or (1 shl id)
        prefs.edit().putInt("owned_skins", ownedSkins).apply()
        return true
    }

    fun selectSkin(id: Int) {
        if (!ownsSkin(id)) return
        skin = id
        prefs.edit().putInt("skin", id).apply()
    }
}
