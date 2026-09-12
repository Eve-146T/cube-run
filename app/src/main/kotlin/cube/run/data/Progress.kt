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
    val perks = listOf(SAFESTART, COINVALUE, PORTALS, LUCKYBOX)

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
    /** A second wind: the next crash is not the end — you get back up, bubbled, and keep going. One per run. */
    const val REVIVE_PRICE = 260

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

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
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
        if (skin !in Skins.all.indices || !owns(Wardrobe.CUBE, skin)) skin = 0
        if (bubbleSkin !in BubbleSkins.all.indices || !owns(Wardrobe.BUBBLE, bubbleSkin)) bubbleSkin = 0
        if (trail !in Trails.all.indices || !owns(Wardrobe.TRAIL, trail)) trail = 0
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

    fun enterDev() {
        if (prefs.contains(DEV_BANK)) return
        prefs.edit().putInt(DEV_BANK, coins).putInt("coins", 9_999_999).apply()
        coins = 9_999_999
    }

    fun leaveDev() {
        if (!prefs.contains(DEV_BANK)) return
        coins = prefs.getInt(DEV_BANK, coins)
        prefs.edit().remove(DEV_BANK).putInt("coins", coins).apply()
    }

    fun addCoins(n: Int) {
        if (n <= 0) return
        coins += n
        totalCoins += n
        prefs.edit().putInt("coins", coins).putInt("total_coins", totalCoins).apply()
    }

    /** One more run finished (for the stats). */
    fun countRun() {
        runs += 1
        prefs.edit().putInt("runs", runs).apply()
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
            else -> perkLevels[u.key] = lvl
        }
        prefs.edit().putInt(u.key, lvl).apply()
        return true
    }

    fun buyRevive(): Boolean {
        if (!spend(REVIVE_PRICE)) return false
        revives += 1
        prefs.edit().putInt("revives", revives).apply()
        return true
    }

    /** Consume one second wind (GL thread, on a crash). Returns false when empty. */
    fun useRevive(): Boolean {
        if (revives <= 0) return false
        revives -= 1
        prefs.edit().putInt("revives", revives).apply()
        return true
    }

    fun buyBubble(): Boolean {
        if (!spend(BUBBLE_PRICE)) return false
        bubbles += 1
        prefs.edit().putInt("bubbles", bubbles).apply()
        return true
    }

    /** A bubble picked up on the track (GL thread): straight into the stash. */
    fun addBubble(n: Int) {
        bubbles += n
        prefs.edit().putInt("bubbles", bubbles).apply()
    }

    /** Consume one stocked bubble (GL thread, on activation). Returns false when empty. */
    fun useBubble(): Boolean {
        if (bubbles <= 0) return false
        bubbles -= 1
        prefs.edit().putInt("bubbles", bubbles).apply()
        return true
    }

    // ------------------------------------------------------------- shards

    fun shards(kind: Int): Int = shardCounts.getOrElse(kind) { 0 }

    private fun addShards(kind: Int, n: Int) {
        shardCounts[kind] += n
        prefs.edit().putInt("shards_$kind", shardCounts[kind]).apply()
    }

    /** Spend the shards a shard-only skin asks for and own it. False when it is not that kind of skin, or there are not enough. */
    fun unlockWithShards(id: Int): Boolean {
        val sk = Skins.get(id)
        if (!sk.shardOnly || owns(Wardrobe.CUBE, id)) return false
        if (shards(sk.shardType) < sk.shardsNeeded) return false
        shardCounts[sk.shardType] -= sk.shardsNeeded
        prefs.edit().putInt("shards_${sk.shardType}", shardCounts[sk.shardType]).apply()
        grant(Wardrobe.CUBE, id)
        return true
    }

    // ------------------------------------------------------------- wardrobe

    private fun ownedMask(cat: Int): Int = when (cat) { Wardrobe.CUBE -> ownedSkins; Wardrobe.BUBBLE -> ownedBubbleSkins; else -> ownedTrails }

    fun owns(cat: Int, id: Int): Boolean = ownedMask(cat) and (1 shl id) != 0

    fun equipped(cat: Int): Int = when (cat) { Wardrobe.CUBE -> skin; Wardrobe.BUBBLE -> bubbleSkin; else -> trail }

    /** Every item of [cat] not yet owned and buyable (shard-only skins are never handed out or sold). */
    fun unowned(cat: Int): List<Int> = (0 until Wardrobe.count(cat)).filter { !owns(cat, it) && !(cat == Wardrobe.CUBE && Skins.get(it).shardOnly) }

    private fun grant(cat: Int, id: Int) {
        when (cat) {
            Wardrobe.CUBE -> { ownedSkins = ownedSkins or (1 shl id); prefs.edit().putInt("owned_skins", ownedSkins).apply() }
            Wardrobe.BUBBLE -> { ownedBubbleSkins = ownedBubbleSkins or (1 shl id); prefs.edit().putInt("owned_bubble_skins", ownedBubbleSkins).apply() }
            else -> { ownedTrails = ownedTrails or (1 shl id); prefs.edit().putInt("owned_trails", ownedTrails).apply() }
        }
    }

    fun buy(cat: Int, id: Int): Boolean {
        if (id !in 0 until Wardrobe.count(cat)) return false
        if (owns(cat, id)) return true
        if (cat == Wardrobe.CUBE && Skins.get(id).shardOnly) return false // shards only
        if (!spend(Wardrobe.price(cat, id))) return false
        grant(cat, id)
        return true
    }

    fun equip(cat: Int, id: Int) {
        if (!owns(cat, id)) return
        when (cat) {
            Wardrobe.CUBE -> { skin = id; prefs.edit().putInt("skin", id).apply() }
            Wardrobe.BUBBLE -> { bubbleSkin = id; prefs.edit().putInt("bubble_skin", id).apply() }
            else -> { trail = id; prefs.edit().putInt("trail", id).apply() }
        }
    }

    // ------------------------------------------------------------- boxes

    /** Open a mystery box: roll a reward and bank it immediately. */
    fun openBox(random: Random = Random.Default): BoxReward {
        boxesOpened += 1
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
                val shard = shards.random(random); val n = random.nextInt(5, 31)
                addShards(shard.id, n); BoxReward(BoxReward.SHARDS, n, id = shard.id)
            }
            kind == BoxReward.COINS -> BoxReward(BoxReward.COINS,
                if (random.nextInt(5) == 0) random.nextInt(2000, 3001) else random.nextInt(500, 1001))
            else -> BoxReward(BoxReward.BUBBLE, random.nextInt(3, 6))
        }
        boxCoinStreak = if (reward.kind == BoxReward.COINS) boxCoinStreak + 1 else 0
        prefs.edit().putInt("boxes_opened", boxesOpened).putInt("box_coin_streak", boxCoinStreak).apply()
        if (reward.kind == BoxReward.BUBBLE) {
            bubbles += reward.amount
            prefs.edit().putInt("bubbles", bubbles).apply()
        } else if (reward.kind == BoxReward.COINS) {
            addCoins(reward.amount)
        }
        return reward
    }
}
