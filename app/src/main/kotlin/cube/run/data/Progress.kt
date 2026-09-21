package cube.run.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Persistent meta-progression: the coin bank, permanent upgrade levels, the
 * bubble-shield stock, the owned/equipped wardrobe (cube skin, bubble skin,
 * trail) and a few lifetime stats. Written from the UI thread (shop,
 * wardrobe) and the session (banking a run's coins); read from the GL thread
 * at run start, so every field is @Volatile.
 */
object Progress {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    /**
     * A permanent upgrade: the duration of one power-up, bought one level at a
     * time up to [MAX_LEVEL]. Each level just adds [step] seconds to [base].
     */
    class Upgrade(val key: String, val name: String, val base: Float, val step: Float, val max: Int = MAX_LEVEL, val basePrice: Int = 750) {
        fun duration(level: Int): Float = base + step * level.coerceIn(0, max)
        /** Long-term progression: late levels take sustained play, rounded to 50 coins. */
        fun price(level: Int): Int = (basePrice * (level.coerceIn(0, max - 1) + 1.0).pow(1.65) / 50).roundToInt() * 50
    }

    const val MAX_LEVEL = 10
    // ---- durations
    val BUBBLE = Upgrade("bubble_time", "Bubble", 8f, 1.2f)
    val MAGNET = Upgrade("magnet_time", "Magnet", 8f, 1.0f)
    val MULT = Upgrade("mult_time", "2× score", 10f, 1.2f)
    val JET = Upgrade("jet_time", "Jetpack", 5f, 0.6f)
    val upgrades = listOf(BUBBLE, MAGNET, MULT, JET)
    // ---- perks: each level changes a rule of the run (see CubeRun)
    /** Every run starts under a bubble for a few seconds (3 s + 1.5 s per level; 0 = none). */
    val SAFESTART = Upgrade("perk_safestart", "Safe start", 0f, 1f, max = 5, basePrice = 1500)
    /** Coins are worth +20% per level. */
    val COINVALUE = Upgrade("perk_coinvalue", "Rich coins", 1f, 0.2f, max = 10, basePrice = 2400)
    /** Portals open more often. */
    val PORTALS = Upgrade("perk_portals", "Portal luck", 0f, 1f, max = 5, basePrice = 1500)
    /** Mystery boxes turn up more often. */
    val LUCKYBOX = Upgrade("perk_luckybox", "Lucky boxes", 0f, 1f, max = 5, basePrice = 1500)
    val FASTERSTART = Upgrade("perk_fasterstart", "Even faster starts", 5f, 1f, max = 5, basePrice = 1500)
    val perks = listOf(SAFESTART, COINVALUE, PORTALS, LUCKYBOX, FASTERSTART)
    val maxStartPresses: Int get() = 5 + level(FASTERSTART).coerceIn(0, 5)

    /** Seconds of free bubble at the start of a run. */
    val safeStartSeconds: Float get() = level(SAFESTART).let { if (it == 0) 0f else 3f + 1.5f * it }

    /**
     * What a mystery box held. [amount] is coins for [COINS], count for
     * [BUBBLE] and [SHARDS] (with [id] = the shard kind); for [SKIN] it is
     * the wardrobe [cat] + [id] of the new item.
     */
    class BoxReward(val kind: Int, val amount: Int, val cat: Int = -1, val id: Int = -1) {
        companion object {
            const val COINS = 0
            const val BUBBLE = 1
            const val SKIN = 2
            const val SHARDS = 3
        }
        /** Big coin hauls, skins and big shard drops are the "rare" pulls. */
        val rare: Boolean get() = kind == SKIN || (kind == COINS && amount >= 2000) || (kind == SHARDS && amount >= 20)
    }

    /** A bubble shield: activate in-run with a double tap, absorbs one crash. */
    const val BUBBLE_PRICE = 120
    /** A second wind: the next crash is not the end — you get back up, bubbled, and keep going. Stock capped at three. */
    const val REVIVE_PRICE = 999
    const val MAX_REVIVES = 3

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
    @Volatile var revives: Int = 0
        private set
    private val perkLevels = HashMap<String, Int>()
    private val shardCounts = IntArray(Shards.all.size)

    // ---- wardrobe: equipped ids + owned bitmasks (item 0 of each is always owned)
    @Volatile var skin: Int = 0
        private set
    @Volatile var ownedSkins: Int = 1
        private set
    @Volatile var bubbleSkin: Int = 0
        private set
    @Volatile var ownedBubbleSkins: Int = 1
        private set
    @Volatile var trail: Int = 0
        private set
    @Volatile var ownedTrails: Int = 1
        private set

    // ---- lifetime stats
    @Volatile var totalCoins: Int = 0
        private set
    @Volatile var runs: Int = 0
        private set
    @Volatile var boxesOpened: Int = 0
        private set
    private var boxCoinStreak = 0
    const val ACHIEVEMENTS_PRICE = 3000
    const val VOID_LIFETIME_GATE = 100000
    @Volatile var achievementsUnlocked = false
        private set
    @Volatile var bestRunScore = 0
        private set
    /** Read both histories so an already-open activity cannot hide an older saved record. */
    val achievementScore: Int get() = maxOf(bestRunScore,
        if (::appContext.isInitialized) appContext.getSharedPreferences("scores", Context.MODE_PRIVATE)
            .getInt("best_cuberun", 0) else 0)
    @Volatile var totalPowerups = 0
        private set
    @Volatile var maxBubbles = 0
        private set
    @Volatile var maxRunBounces = 0
        private set
    @Volatile var bestCenteredScore = 0
        private set
    @Volatile var bestCoinlessScore = 0
        private set
    @Volatile var maxRunMissedBoxes = 0
        private set
    @Volatile var totalMuteToggles = 0
        private set
    @Volatile var voidPurchases = 0
        private set
    val voidAvailable: Boolean get() = Settings.devMode || totalCoins >= VOID_LIFETIME_GATE
    /** Steadily rising offerings, rounded to 500 and capped safely below the bank limit. */
    val voidPrice: Int get() = voidPurchases.coerceIn(0, 10000).toLong().let { n -> (5000L + n * 2500L + n * n * 500L).coerceAtMost(1_000_000_000L).toInt() }
    val voidLine: String get() = voidLines.getOrElse(voidPurchases) { voidEchoes[(voidPurchases - voidLines.size).mod(voidEchoes.size)] }
    private val voidLines = listOf(
        "This upgrade does nothing.", "What did you think was going to happen?", "Do you never learn?",
        "Still nothing.", "You could have bought something useful.", "The silence is getting expensive.",
        "There is no refund in the dark.", "You are very persistent.", "One more will change nothing.", "Are you sure?",
        "Fine.", "There was more.", "Don't look so pleased.", "The dark remembers you.", "Something follows.",
        "You cannot see it yet.", "Keep walking.", "Even nothing leaves a trace.", "Almost a shadow.", "Look behind you.",
        "A trail. For your trouble.", "You are still here.", "The silence has a shape.", "It is getting closer.",
        "Something wants to keep you safe.", "Or keep you here.", "A little more darkness.", "You feel it now.",
        "One thin veil.", "Breathe.", "The dark surrounds you."
    )
    private val voidEchoes = listOf("Nothing more. Probably.", "The void appreciates your donation.", "We have been here before.", "Still listening?", "The silence deepens.")

    @Synchronized fun buyAchievements(): Boolean {
        if (achievementsUnlocked || !spend(ACHIEVEMENTS_PRICE)) return false
        achievementsUnlocked = true
        prefs.edit().putBoolean("achievements_unlocked", true).apply()
        Achievements.evaluate(false) // Existing progress is awarded quietly, never a popup avalanche.
        return true
    }

    @Synchronized fun buyVoid(): Boolean {
        if (!voidAvailable || !spend(voidPrice)) return false
        voidPurchases = (voidPurchases.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        prefs.edit().putInt("void_purchases", voidPurchases).apply()
        return true
    }

    fun secretAvailable(cat: Int, id: Int): Boolean = when {
        cat == Wardrobe.CUBE && id == Skins.VOID_ID -> voidPurchases >= 10
        cat == Wardrobe.TRAIL && id == Trails.VOID_ID -> voidPurchases >= 20
        cat == Wardrobe.BUBBLE && id == BubbleSkins.VOID_ID -> voidPurchases >= 30
        else -> true
    }
    private fun secret(cat: Int, id: Int): Boolean = when (cat) {
        Wardrobe.CUBE -> id == Skins.VOID_ID
        Wardrobe.TRAIL -> id == Trails.VOID_ID
        else -> id == BubbleSkins.VOID_ID
    }

    @Volatile private var unbankedRunCoins = 0
    val achievementCoins: Int get() = saturatedAdd(totalCoins, unbankedRunCoins)
    /** Current run's unbanked earnings; clear immediately before addCoins banks them. */
    @Synchronized fun recordRunCoins(unbanked: Int) {
        val current = unbanked.coerceAtLeast(0)
        if (unbankedRunCoins == current) return
        unbankedRunCoins = current
        Achievements.evaluate()
    }
    @Synchronized fun clearRunCoins() { unbankedRunCoins = 0 }

    /** May be called during a run, allowing milestones to surface while playing. */
    @Synchronized fun recordRunProgress(score: Int, sideBounces: Int) {
        val best = maxOf(bestRunScore, score)
        val bounces = maxOf(maxRunBounces, sideBounces)
        if (best == bestRunScore && bounces == maxRunBounces) return
        // Avoid a disk write each frame. Tier crossings persist immediately; the
        // final exact score is saved by countRun, with periodic crash recovery.
        val persist = bounces != maxRunBounces || best / 100 != bestRunScore / 100 ||
            intArrayOf(500, 1000, 2000, 5000).any { bestRunScore <= it && best > it }
        bestRunScore = best; maxRunBounces = bounces
        if (persist) prefs.edit().putInt("achievement_best_score", best).putInt("max_run_bounces", bounces).apply()
        Achievements.evaluate()
    }
    @Synchronized fun recordPowerup() {
        totalPowerups = saturatedAdd(totalPowerups, 1)
        prefs.edit().putInt("total_powerups", totalPowerups).apply()
        Achievements.evaluate()
    }
    /** Only the live session, with an unbroken middle-lane history, may call this. */
    @Synchronized fun recordCenteredScore(score: Int) {
        val progress = score.coerceIn(0, 100)
        if (progress <= bestCenteredScore) return
        bestCenteredScore = progress
        prefs.edit().putInt("best_centered_score", progress).apply()
        Achievements.evaluate()
    }
    @Synchronized fun recordCoinlessScore(score: Int) {
        val progress = score.coerceIn(0, 60)
        if (progress <= bestCoinlessScore) return
        bestCoinlessScore = progress
        prefs.edit().putInt("best_coinless_score", progress).apply()
        Achievements.evaluate()
    }
    @Synchronized fun recordMissedBoxes(count: Int) {
        val progress = count.coerceIn(0, 10)
        if (progress <= maxRunMissedBoxes) return
        maxRunMissedBoxes = progress
        prefs.edit().putInt("max_run_missed_boxes", progress).apply()
        Achievements.evaluate()
    }
    /** Call only for a user changing the mute toggle, never settings initialization. */
    @Synchronized fun recordMuteToggle() {
        if (totalMuteToggles == Int.MAX_VALUE) return
        totalMuteToggles++
        prefs.edit().putInt("total_mute_toggles", totalMuteToggles).apply()
        Achievements.evaluate()
    }
    private fun recordBubbles() {
        if (bubbles > maxBubbles) {
            maxBubbles = bubbles
            prefs.edit().putInt("max_bubbles", maxBubbles).apply()
        }
        Achievements.evaluate()
    }
    private fun saturatedAdd(a: Int, b: Int): Int = (a.toLong() + b).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()


    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        prefs = appContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
        coins = prefs.getInt("coins", 0)
        if (prefs.contains(DEV_BANK)) leaveDev() // dev mode never survives a launch; neither does its bank
        bubbles = prefs.getInt("bubbles", 0)
        bubbleLevel = prefs.getInt(BUBBLE.key, 0)
        magnetLevel = prefs.getInt(MAGNET.key, 0)
        multLevel = prefs.getInt(MULT.key, 0)
        jetLevel = prefs.getInt(JET.key, 0)
        revives = prefs.getInt("revives", 0)
        for (u in perks) perkLevels[u.key] = prefs.getInt(u.key, 0)
        for (k in Shards.all) shardCounts[k.id] = prefs.getInt("shards_${k.id}", 0)
        skin = prefs.getInt("skin", 0)
        ownedSkins = prefs.getInt("owned_skins", 1) or 1
        bubbleSkin = prefs.getInt("bubble_skin", 0)
        ownedBubbleSkins = prefs.getInt("owned_bubble_skins", 1) or 1
        trail = prefs.getInt("trail", 0)
        ownedTrails = prefs.getInt("owned_trails", 1) or 1
        totalCoins = prefs.getInt("total_coins", 0)
        runs = prefs.getInt("runs", 0)
        boxesOpened = prefs.getInt("boxes_opened", 0)
        boxCoinStreak = prefs.getInt("box_coin_streak", 0).coerceIn(0, 2)
        achievementsUnlocked = prefs.getBoolean("achievements_unlocked", false)
        bestRunScore = maxOf(prefs.getInt("achievement_best_score", 0), ctx.applicationContext.getSharedPreferences("scores", Context.MODE_PRIVATE).getInt("best_cuberun", 0))
        totalPowerups = prefs.getInt("total_powerups", 0)
        maxBubbles = maxOf(bubbles, prefs.getInt("max_bubbles", 0))
        maxRunBounces = prefs.getInt("max_run_bounces", 0)
        bestCenteredScore = prefs.getInt("best_centered_score", 0).coerceIn(0, 100)
        bestCoinlessScore = prefs.getInt("best_coinless_score", 0).coerceIn(0, 60)
        maxRunMissedBoxes = prefs.getInt("max_run_missed_boxes", 0).coerceIn(0, 10)
        totalMuteToggles = prefs.getInt("total_mute_toggles", 0).coerceAtLeast(0)
        voidPurchases = prefs.getInt("void_purchases", 0).coerceAtLeast(0)
        unbankedRunCoins = 0
        prefs.edit().putInt("achievement_best_score", bestRunScore).putInt("max_bubbles", maxBubbles).apply()
        Achievements.init(prefs)
        if (skin !in Skins.all.indices || !owns(Wardrobe.CUBE, skin)) skin = 0
        if (bubbleSkin !in BubbleSkins.all.indices || !owns(Wardrobe.BUBBLE, bubbleSkin)) bubbleSkin = 0
        if (trail !in Trails.all.indices || !owns(Wardrobe.TRAIL, trail)) trail = 0
    }

    /** Explicit developer reset: clear every saved game value while preserving app options. */
    @Synchronized fun resetForDeveloper(): Boolean {
        if (!Settings.devMode || !::prefs.isInitialized) return false
        val progressSaved = prefs.edit().clear().commit()
        val scoresSaved = appContext.getSharedPreferences("scores", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Reload every cached counter, equipped item and achievement queue from fresh defaults.
        // The old pre-dev bank is intentionally gone too; toggling dev off must not restore it.
        init(appContext)
        return progressSaved && scoresSaved
    }

    fun level(u: Upgrade): Int = when (u) {
        BUBBLE -> bubbleLevel
        MAGNET -> magnetLevel
        MULT -> multLevel
        JET -> jetLevel
        else -> perkLevels[u.key] ?: 0
    }

    /** Price of the next level, or null when maxed. */
    fun nextPrice(u: Upgrade): Int? = if (level(u) >= u.max) null else u.price(level(u))

    /** Coin worth per pickup with the Rich coins perk. */
    val coinValue: Float get() = COINVALUE.duration(level(COINVALUE))

    // ---- dev mode: an unlimited bank while it is on, the real one back when it is off (or on the next launch)
    private const val DEV_BANK = "bank_before_dev"

    @Synchronized fun enterDev() {
        if (prefs.contains(DEV_BANK)) return
        prefs.edit().putInt(DEV_BANK, coins).putInt("coins", 9_999_999).apply()
        coins = 9_999_999
    }

    @Synchronized fun leaveDev() {
        if (!prefs.contains(DEV_BANK)) return
        coins = prefs.getInt(DEV_BANK, coins)
        prefs.edit().remove(DEV_BANK).putInt("coins", coins).apply()
    }

    /**
     * The claim marker and bank change share one preferences transaction, under
     * the same lock as purchases and run banking. Bonus coins intentionally do
     * not increase totalCoins: claiming a milestone cannot earn another one.
     */
    @Synchronized internal fun claimAchievement(id: String): Int {
        if (!achievementsUnlocked || !::prefs.isInitialized) return 0
        val definition = Achievements.all.firstOrNull { it.id == id } ?: return 0
        val state = Achievements.snapshot(definition)
        val tier = state.claimableTier ?: return 0
        val amount = state.rewardAmount ?: return 0
        // Keep the reward available if the bank cannot fit the complete payout.
        if (amount <= 0 || coins.toLong() + amount > Int.MAX_VALUE) return 0
        // Dev mode replaces the spendable bank, but claims are permanent. Preserve
        // their payout in the real bank too, so leaving dev cannot erase a reward.
        val realBank = if (prefs.contains(DEV_BANK)) prefs.getInt(DEV_BANK, 0) else null
        if (realBank != null && realBank.toLong() + amount > Int.MAX_VALUE) return 0
        val balance = coins + amount
        prefs.edit()
            .putInt("coins", balance)
            .putInt("achievement_$id", state.earnedTiers)
            .putInt("achievement_claimed_$id", tier + 1)
            .also { if (realBank != null) it.putInt(DEV_BANK, realBank + amount) }
            .apply()
        coins = balance
        return amount
    }

    @Synchronized fun addCoins(n: Int) {
        if (n <= 0) return
        coins = saturatedAdd(coins, n)
        totalCoins = saturatedAdd(totalCoins, n)
        prefs.edit().putInt("coins", coins).putInt("total_coins", totalCoins).apply()
        Achievements.evaluate()
    }

    /** One more run finished (for the stats). */
    @Synchronized fun countRun() {
        runs += 1
        prefs.edit().putInt("runs", runs).putInt("achievement_best_score", bestRunScore).putInt("max_run_bounces", maxRunBounces).apply()
    }

    private fun spend(n: Int): Boolean {
        if (n < 0 || n > coins) return false
        coins -= n
        prefs.edit().putInt("coins", coins).apply()
        return true
    }

    /** Buy the next level of [u]. Returns false when maxed or unaffordable. */
    @Synchronized fun buyUpgrade(u: Upgrade): Boolean {
        val price = nextPrice(u) ?: return false
        if (!spend(price)) return false
        val lvl = level(u) + 1
        when (u) {
            BUBBLE -> bubbleLevel = lvl
            MAGNET -> magnetLevel = lvl
            MULT -> multLevel = lvl
            JET -> jetLevel = lvl
            else -> perkLevels[u.key] = lvl
        }
        prefs.edit().putInt(u.key, lvl).apply()
        return true
    }

    @Synchronized fun buyRevive(): Boolean {
        if (revives >= MAX_REVIVES || !spend(REVIVE_PRICE)) return false
        revives += 1
        prefs.edit().putInt("revives", revives).apply()
        return true
    }

    /** Consume one second wind (GL thread, on a crash). Returns false when empty. */
    @Synchronized fun useRevive(): Boolean {
        if (revives <= 0) return false
        revives -= 1
        prefs.edit().putInt("revives", revives).apply()
        return true
    }

    @Synchronized fun buyBubble(): Boolean {
        if (!spend(BUBBLE_PRICE)) return false
        bubbles = saturatedAdd(bubbles, 1)
        prefs.edit().putInt("bubbles", bubbles).apply()
        recordBubbles()
        return true
    }

    /** A bubble picked up on the track (GL thread): straight into the stash. */
    @Synchronized fun addBubble(n: Int) {
        if (n <= 0) return
        bubbles = saturatedAdd(bubbles, n)
        prefs.edit().putInt("bubbles", bubbles).apply()
        recordBubbles()
    }

    /** Consume one stocked bubble (GL thread, on activation). Returns false when empty. */
    @Synchronized fun useBubble(saveChance: Float = 0f, random: Random = Random.Default): Boolean {
        if (bubbles <= 0) return false
        if (saveChance > 0f && random.nextFloat() < saveChance) return true
        bubbles -= 1
        prefs.edit().putInt("bubbles", bubbles).apply()
        recordBubbles()
        return true
    }

    // ------------------------------------------------------------- shards

    /** Developer stock is virtual; real shard counts remain available when dev mode ends. */
    fun shards(kind: Int): Int = when {
        kind !in shardCounts.indices -> 0
        Settings.devMode -> Int.MAX_VALUE
        else -> shardCounts[kind]
    }

    fun addShards(kind: Int, n: Int) {
        if (kind !in shardCounts.indices || n <= 0) return
        shardCounts[kind] += n
        prefs.edit().putInt("shards_$kind", shardCounts[kind]).apply()
    }

    /** Spend the shards a shard-only skin asks for and own it. False when it is not that kind of skin, or there are not enough. */
    @Synchronized fun unlockWithShards(id: Int): Boolean {
        val sk = Skins.get(id)
        if (!sk.shardOnly || owns(Wardrobe.CUBE, id)) return false
        if (shards(sk.shardType) < sk.shardsNeeded) return false
        if (!Settings.devMode) {
            shardCounts[sk.shardType] -= sk.shardsNeeded
            prefs.edit().putInt("shards_${sk.shardType}", shardCounts[sk.shardType]).apply()
        }
        grant(Wardrobe.CUBE, id)
        return true
    }

    // ------------------------------------------------------------- wardrobe

    private fun ownedMask(cat: Int): Int = when (cat) { Wardrobe.CUBE -> ownedSkins; Wardrobe.BUBBLE -> ownedBubbleSkins; else -> ownedTrails }

    fun owns(cat: Int, id: Int): Boolean = ownedMask(cat) and (1 shl id) != 0

    fun equipped(cat: Int): Int = when (cat) { Wardrobe.CUBE -> skin; Wardrobe.BUBBLE -> bubbleSkin; else -> trail }

    /** Every item of [cat] not yet owned and buyable (shard-only skins are never handed out or sold). */
    fun unowned(cat: Int): List<Int> = (0 until Wardrobe.count(cat)).filter { !owns(cat, it) && !secret(cat, it) && !(cat == Wardrobe.CUBE && Skins.get(it).shardOnly) }

    private fun grant(cat: Int, id: Int) {
        when (cat) {
            Wardrobe.CUBE -> { ownedSkins = ownedSkins or (1 shl id); prefs.edit().putInt("owned_skins", ownedSkins).apply() }
            Wardrobe.BUBBLE -> { ownedBubbleSkins = ownedBubbleSkins or (1 shl id); prefs.edit().putInt("owned_bubble_skins", ownedBubbleSkins).apply() }
            else -> { ownedTrails = ownedTrails or (1 shl id); prefs.edit().putInt("owned_trails", ownedTrails).apply() }
        }
        Achievements.evaluate()
    }

    @Synchronized fun buy(cat: Int, id: Int): Boolean {
        if (!secretAvailable(cat, id)) return false
        if (id !in 0 until Wardrobe.count(cat)) return false
        if (owns(cat, id)) return true
        if (cat == Wardrobe.CUBE && Skins.get(id).shardOnly) return false // shards only
        if (!spend(Wardrobe.price(cat, id))) return false
        grant(cat, id)
        return true
    }

    @Synchronized fun equip(cat: Int, id: Int) {
        if (!owns(cat, id)) return
        when (cat) {
            Wardrobe.CUBE -> { skin = id; prefs.edit().putInt("skin", id).apply() }
            Wardrobe.BUBBLE -> { bubbleSkin = id; prefs.edit().putInt("bubble_skin", id).apply() }
            else -> { trail = id; prefs.edit().putInt("trail", id).apply() }
        }
    }

    // ------------------------------------------------------------- boxes

    val mysteryBoxPrice: Int get() = BoxLoot.purchasePrice()
    @Synchronized fun buyMysteryBox(random: Random = Random.Default): BoxReward? {
        if (!spend(mysteryBoxPrice)) return null
        return openBox(random)
    }

    /** Open a mystery box: roll a reward and bank it immediately. */
    @Synchronized fun openBox(random: Random = Random.Default): BoxReward {
        boxesOpened = saturatedAdd(boxesOpened, 1)
        // Disjoint rolls: the old shard branch swallowed the entire bubble range.
        // After two coin boxes the next pull is guaranteed to be something else.
        val kind = BoxLoot.kind(random.nextFloat(), boxCoinStreak)
        val cats = if (kind == BoxReward.SKIN) Wardrobe.cats.filter { unowned(it).isNotEmpty() } else emptyList()
        val shards = if (kind == BoxReward.SHARDS || kind == BoxReward.SKIN && cats.isEmpty())
            Shards.all.filter { k -> Skins.forShard(k.id)?.let { !owns(Wardrobe.CUBE, it.id) } ?: false } else emptyList()
        val reward = when {
            kind == BoxReward.SKIN && cats.isNotEmpty() -> {
                val cat = cats.random(random); val id = unowned(cat).random(random)
                grant(cat, id); BoxReward(BoxReward.SKIN, 1, cat, id)
            }
            shards.isNotEmpty() -> {
                val shard = shards.random(random); val n = random.nextInt(2, 5)
                addShards(shard.id, n); BoxReward(BoxReward.SHARDS, n, id = shard.id)
            }
            kind == BoxReward.COINS -> BoxReward(BoxReward.COINS,
                if (random.nextInt(5) == 0) random.nextInt(2000, 3001) else random.nextInt(500, 1001))
            else -> BoxReward(BoxReward.BUBBLE, random.nextInt(3, 6))
        }
        boxCoinStreak = if (reward.kind == BoxReward.COINS) boxCoinStreak + 1 else 0
        prefs.edit().putInt("boxes_opened", boxesOpened).putInt("box_coin_streak", boxCoinStreak).apply()
        if (reward.kind == BoxReward.BUBBLE) {
            addBubble(reward.amount)
        } else if (reward.kind == BoxReward.COINS) {
            addCoins(reward.amount)
        }
        Achievements.evaluate()
        return reward
    }
}
