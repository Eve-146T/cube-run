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
        definition.id in setOf("greedy", "coal_miner", "full_kit", "insomniac", "bankrupt", "exactly_67", "nervous_tic", "silent_treatment", "stage_fright") -> 1500
        definition.id in setOf("scenic_route") -> 2500
        definition.id in setOf("just_browsing", "two_ez", "untouchable", "house_loses", "voidwalker", "magpie", "shard_hunter", "long_con") -> 2000
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
        Definition("globetrotter", "Globetrotter", "Visit every bonus world across your runs.", intArrayOf(1, 2, 3, 4)),
        Definition("long_hauler", "Long Hauler", "Travel metres across all runs.", intArrayOf(10000, 100000, 500000, 2000000)),
        Definition("shardsmith", "Shardsmith", "Collect shards of any kind.", intArrayOf(25, 100, 250, 750)),
        Definition("regular", "Regular", "Start runs.", intArrayOf(10, 100, 500, 2000)),
        Definition("bubble_popper", "Bubble Popper", "Spend bubbles from your stash.", intArrayOf(10, 100, 500, 2000)),
        Definition("near_miss", "Near Miss", "Earn near-miss bonuses.", intArrayOf(50, 500, 2500, 10000)),
        Definition("untouchable", "Untouchable", "Reach 150 without picking up a power-up.", intArrayOf(150), false),
        Definition("house_loses", "House Always Loses", "Win a Gambler jackpot.", intArrayOf(1), false),
        Definition("voidwalker", "Voidwalker", "Make five offerings to the void.", intArrayOf(5), false),
        Definition("greedy", "Greedy", "Collect 13 mystery boxes in one run.", intArrayOf(13), false),
        Definition("scenic_route", "Scenic Route", "Visit all four unique bonus worlds in one run.", intArrayOf(4), false),
        Definition("coal_miner", "Coal Miner", "Collect 5,000 worthless coal coins.", intArrayOf(5000), false),
        Definition("magpie", "Magpie", "Collect 10,000 coins in one run.", intArrayOf(10000), false),
        Definition("shard_hunter", "Shard Hunter", "Collect all three shard kinds in one run.", intArrayOf(3), false),
        Definition("full_kit", "Full Kit", "Hold a bubble, magnet, 2× and jetpack at once.", intArrayOf(1), false),
        Definition("long_con", "Long Con", "Stay alive for 10 minutes in one run.", intArrayOf(600), false),
        Definition("insomniac", "Insomniac", "Finish a run between 3 and 4 am.", intArrayOf(1), false),
        Definition("bankrupt", "Bankrupt", "Spend your coin balance to exactly zero.", intArrayOf(1), false),
        Definition("exactly_67", "Exactly Sixty-Seven", "Finish a run with exactly 67 points.", intArrayOf(1), false),
        Definition("just_browsing", "Just Browsing", "Visit the shop 100 times without buying.", intArrayOf(100), false),
        Definition("two_ez", "2EZ", "Make 50 pointless lane swipes and swipe straight back in one run.", intArrayOf(50), false),
        Definition("nervous_tic", "Nervous Tic", "Pause and resume 50 times in one run.", intArrayOf(50), false),
        Definition("silent_treatment", "Silent Treatment", "Finish a 100-point run with sound and haptics off using the free cube.", intArrayOf(1), false),
        Definition("stage_fright", "Stage Fright", "Crash within two seconds of the start gate in 25 runs.", intArrayOf(25), false),
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
            "bounces" -> Progress.maxRunBounces
            "regular" -> Progress.metric("regular")
            "shardsmith" -> Progress.metric("shardsmith")
            "voidwalker" -> Progress.voidPurchases
            "globetrotter", "scenic_route", "shard_hunter" -> Integer.bitCount(Progress.metric(definition.id))
            else -> Progress.metric(definition.id)
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
