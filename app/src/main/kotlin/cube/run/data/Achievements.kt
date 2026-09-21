package cube.run.data

import android.content.SharedPreferences

/** Calm, evolving cards. Earlier tiers stay earned; only the next goal is presented. */
object Achievements {
    data class Definition(val id: String, val title: String, val description: String, val thresholds: IntArray, val tiered: Boolean = true)
    data class Snapshot(val definition: Definition, val value: Int, val earnedTiers: Int, val claimedTiers: Int = 0) {
        val claimableTier: Int? get() = claimedTiers.takeIf { Progress.achievementsUnlocked && it < earnedTiers }
        val rewardAmount: Int? get() = claimableTier?.let { reward(definition, it) }
        val allClaimed: Boolean get() = claimedTiers >= definition.thresholds.size
        val nextTarget: Int? get() = definition.thresholds.getOrNull(earnedTiers)
        val fraction: Float get() = nextTarget?.let {
            val target = it + if (definition.id == "runner") 1 else 0
            (value.toFloat() / target).coerceIn(0f, 1f)
        } ?: 1f
    }
    data class Unlock(val definition: Definition, val tier: Int) {
        val tierName: String get() = if (!definition.tiered) "Challenge complete" else tierNames[tier.coerceIn(0, 3)]
    }
    val tierNames = listOf("Bronze", "Silver", "Gold", "Diamond")
    private val tierRewards = intArrayOf(250, 750, 2000, 5000)
    fun reward(definition: Definition, tier: Int): Int = when {
        tier !in definition.thresholds.indices -> 0
        definition.tiered -> tierRewards[tier]
        definition.id == "bubbles" -> 2000
        definition.id == "bounces" -> 1500
        definition.id == "center" -> 1500
        definition.id == "homeress" -> 1500
        definition.id == "gambliphobic" -> 1500
        definition.id == "cookie" -> 2000
        else -> 0
    }

    /** Claim the oldest unclaimed tier. The Progress lock owns the entire transaction. */
    fun claim(id: String): Int = Progress.claimAchievement(id)
    val all = listOf(
        Definition("runner", "Good Runner", "Your highest score in a single run.", intArrayOf(500, 1000, 2000, 5000)),
        Definition("coins", "Lifetime Coins", "Collect coins from runs and mystery boxes.", intArrayOf(5000, 25000, 100000, 500000)),
        Definition("cubes", "Unlocked Cubes", "Build your cube collection.", intArrayOf(5, 10, 15, 24)),
        Definition("powerups", "Power Collector", "Pick up power-ups on the track.", intArrayOf(100, 1000, 5000, 10000)),
        Definition("boxes", "Mystery Seeker", "Open mystery boxes from runs or the shop.", intArrayOf(10, 50, 100, 300)),
        Definition("bubbles", "Big Bubble", "Have 1,000 bubbles in your stash at once.", intArrayOf(1000), false),
        Definition("bounces", "Bouncer", "Bounce against the side wall 67 times in one run.", intArrayOf(67), false),
        Definition("center", "Stay Centered", "Reach 100 points without leaving the middle lane.", intArrayOf(100), false),
        Definition("homeress", "Homeress", "Reach 60 points without picking up a coin.", intArrayOf(60), false),
        Definition("gambliphobic", "Gambliphobic", "Miss 10 mystery boxes in a single run.", intArrayOf(10), false),
        Definition("cookie", "Cookie Clicker", "Toggle mute 1,000 times.", intArrayOf(1000), false),
    )
    private lateinit var prefs: SharedPreferences
    private val pending = LinkedHashMap<String, Unlock>()
    internal fun init(preferences: SharedPreferences) { prefs = preferences; synchronized(this) { pending.clear() }; evaluate(false) }
    fun snapshot(definition: Definition): Snapshot {
        val value = when (definition.id) {
            "runner" -> Progress.achievementScore
            "coins" -> Progress.achievementCoins
            "cubes" -> Integer.bitCount(Progress.ownedSkins)
            "powerups" -> Progress.totalPowerups
            "boxes" -> Progress.boxesOpened
            "bubbles" -> Progress.maxBubbles
            "center" -> Progress.bestCenteredScore
            "homeress" -> Progress.bestCoinlessScore
            "gambliphobic" -> Progress.maxRunMissedBoxes
            "cookie" -> Progress.totalMuteToggles
            else -> Progress.maxRunBounces
        }
        val earned = definition.thresholds.count { if (definition.id == "runner") value > it else value >= it }
        val awarded = if (::prefs.isInitialized && Progress.achievementsUnlocked)
            prefs.getInt("achievement_${definition.id}", 0).coerceIn(0, definition.thresholds.size) else 0
        val achieved = maxOf(earned, awarded)
        val claimed = if (::prefs.isInitialized) prefs.getInt("achievement_claimed_${definition.id}", 0).coerceIn(0, achieved) else 0
        return Snapshot(definition, value, achieved, claimed)
    }
    fun snapshot(): List<Snapshot> = all.map(::snapshot)
    @Synchronized internal fun evaluate(notify: Boolean = true) {
        if (!::prefs.isInitialized || !Progress.achievementsUnlocked) return
        val edit = prefs.edit()
        var changed = false
        for (definition in all) {
            val earned = snapshot(definition).earnedTiers
            val previous = prefs.getInt("achievement_${definition.id}", 0)
            if (earned > previous) {
                edit.putInt("achievement_${definition.id}", earned)
                changed = true
                // One toast per family, even when a single run crosses several tiers.
                if (notify) pending[definition.id] = Unlock(definition, earned - 1)
            }
        }
        if (changed) edit.apply()
    }
    @Synchronized fun drainUnlocks(): List<Unlock> = pending.values.toList().also { pending.clear() }
    @Synchronized fun clearUnlocks() { pending.clear() }
}
