package cube.run.data

import kotlin.math.sin

/**
 * Cube skins. Every skin is the cube — what changes is its colour behaviour,
 * its glow and how much it sheds. The game samples [Skin.hueAt] /
 * [Skin.valueAt] every frame on the GL thread (pure functions of time).
 */
object Skins {
    // colour modes
    const val COMP = 0      // complementary to the world's base hue (the classic look)
    const val FIXED = 1     // one fixed hue
    const val RAINBOW = 2   // hue cycles continuously through the whole wheel
    const val PULSE = 3     // fixed hue, brightness throbs
    const val EMBER = 4     // hot hue that flickers like a coal
    const val WAVE = 5      // hue sways between [hue] and [hue2]
    const val STROBE = 6    // snaps between [hue] and [hue2] on a beat

    class Skin(
        val id: Int,
        val name: String,
        val price: Int,
        val mode: Int,
        val hue: Float = 0f,
        val hue2: Float = 0f,
        val sat: Float = 0.55f,
        val value: Float = 1f,
        /** Glow-shell opacity multiplier (1 = classic). */
        val glow: Float = 1f,
        /** Trail shard rate multiplier (1 = classic). */
        val trail: Float = 1f,
        /** The trail/burst colour is white sparkle instead of the body colour. */
        val sparkle: Boolean = false,
        /** Shard-only skins: the [Shards] type that unlocks it (-1 = bought with coins). */
        val shardType: Int = -1,
        /** How many of those shards it takes. */
        val shardsNeeded: Int = 100,
    ) {
        val shardOnly: Boolean get() = shardType >= 0
        /** Hue in degrees for the body at [t] seconds given the world's [baseHue]. */
        fun hueAt(t: Float, baseHue: Float): Float = when (mode) {
            COMP -> baseHue + 180f
            RAINBOW -> t * 90f
            EMBER -> hue + 14f * sin(t * 9f) + 6f * sin(t * 23f)
            WAVE -> hue + (hue2 - hue) * (0.5f + 0.5f * sin(t * 1.6f))
            STROBE -> if (sin(t * 6f) > 0f) hue else hue2
            else -> hue
        }

        fun valueAt(t: Float): Float = when (mode) {
            PULSE -> value * (0.72f + 0.28f * (0.5f + 0.5f * sin(t * 5f)))
            EMBER -> value * (0.85f + 0.15f * sin(t * 13f))
            else -> value
        }
    }

    val all: List<Skin> = listOf(
        Skin(0, "Classic", 0, COMP, sat = 0.55f),
        Skin(1, "Neon", 150, FIXED, hue = 0f, sat = 0.04f, value = 1f, glow = 1.8f, sparkle = true),
        Skin(2, "Lava", 250, EMBER, hue = 16f, sat = 0.95f, value = 1f, trail = 2f),
        Skin(3, "Ice", 250, FIXED, hue = 196f, sat = 0.32f, value = 1f, glow = 1.4f),
        Skin(4, "Void", 400, FIXED, hue = 275f, sat = 0.6f, value = 0.16f, glow = 2.2f),
        Skin(5, "Plasma", 500, PULSE, hue = 305f, sat = 0.85f, value = 1f, glow = 1.6f, trail = 1.6f),
        Skin(6, "Gold", 600, FIXED, hue = 46f, sat = 0.85f, value = 1f, sparkle = true, trail = 1.8f),
        Skin(7, "Rainbow", 800, RAINBOW, sat = 0.9f, value = 1f, glow = 1.5f, trail = 2f, sparkle = true),
        Skin(8, "Mint", 200, FIXED, hue = 150f, sat = 0.45f, value = 1f),
        Skin(9, "Rose", 200, FIXED, hue = 340f, sat = 0.5f, value = 1f, glow = 1.2f),
        Skin(10, "Ocean", 350, WAVE, hue = 195f, hue2 = 235f, sat = 0.8f, value = 0.95f, glow = 1.3f),
        Skin(11, "Toxic", 450, PULSE, hue = 95f, sat = 0.95f, value = 1f, glow = 1.9f, trail = 1.4f),
        Skin(12, "Sunset", 550, WAVE, hue = 20f, hue2 = 320f, sat = 0.85f, value = 1f, trail = 1.4f),
        Skin(13, "Ghost", 650, PULSE, hue = 220f, sat = 0.08f, value = 0.9f, glow = 2.6f, trail = 0.6f),
        Skin(14, "Strobe", 700, STROBE, hue = 55f, hue2 = 200f, sat = 0.9f, value = 1f, glow = 1.4f, trail = 1.6f, sparkle = true),
        Skin(15, "Coal", 300, EMBER, hue = 24f, sat = 0.9f, value = 0.35f, glow = 1.6f),
        Skin(16, "Bubblegum", 220, FIXED, hue = 328f, sat = 0.55f, value = 1f, glow = 1.3f),
        Skin(17, "Lemon", 220, FIXED, hue = 58f, sat = 0.8f, value = 1f),
        Skin(18, "Candy", 480, WAVE, hue = 325f, hue2 = 200f, sat = 0.6f, value = 1f, glow = 1.4f, trail = 1.3f),
        Skin(19, "Galaxy", 750, PULSE, hue = 262f, sat = 0.7f, value = 0.6f, glow = 2.4f, sparkle = true, trail = 1.5f),
        // shard-only: mystery boxes drop the shards, nothing else does
        Skin(20, "Inferno", 0, EMBER, hue = 12f, sat = 1f, value = 1f, glow = 2.4f, trail = 2.6f, sparkle = true, shardType = Shards.EMBER),
        Skin(21, "Glacier", 0, WAVE, hue = 185f, hue2 = 225f, sat = 0.55f, value = 1f, glow = 2.6f, trail = 1.8f, sparkle = true, shardType = Shards.FROST),
        Skin(22, "Eclipse", 0, STROBE, hue = 285f, hue2 = 325f, sat = 0.9f, value = 0.55f, glow = 3f, trail = 2.2f, sparkle = true, shardType = Shards.VOID),
    )

    /** The skin a shard type unlocks. */
    fun forShard(type: Int): Skin? = all.firstOrNull { it.shardType == type }

    fun get(id: Int): Skin = all.getOrElse(id) { all[0] }
}

/**
 * Bubble-shield skins: how the sphere around the cube is coloured and lit.
 * Rendered by the fresnel bubble shader (see core.gfx.BubbleRenderer): a
 * soap-film rim whose hue slides from [hue] to [hue2] around the sphere.
 */
object BubbleSkins {
    const val IRIS = 0      // hue sweeps [hue]→[hue2] around the sphere, drifting with time (soap film)
    const val SOLID = 1     // one hue
    const val RAINBOW = 2   // the whole wheel, slowly turning
    const val ELECTRIC = 3  // flickering rim with a hard pulse

    class BubbleSkin(
        val id: Int,
        val name: String,
        val price: Int,
        val style: Int,
        val hue: Float = 190f,
        /** The far end of the film's hue sweep (solid skins leave it at [hue]). */
        val hue2: Float = hue,
        val sat: Float = 0.6f,
        /** Rim sharpness: higher = thinner, glassier rim. */
        val rim: Float = 2.6f,
        /** Extra fill inside the rim (0 = only the rim glows). */
        val fill: Float = 0.08f,
        /** The pop / activation shards sparkle white. */
        val sparkle: Boolean = false,
    )

    val all: List<BubbleSkin> = listOf(
        BubbleSkin(0, "Soap", 0, IRIS, hue = 170f, hue2 = 270f),
        BubbleSkin(1, "Rose", 180, SOLID, hue = 335f, sat = 0.7f),
        BubbleSkin(2, "Mint", 180, SOLID, hue = 150f, sat = 0.65f),
        BubbleSkin(3, "Gold", 350, SOLID, hue = 46f, sat = 0.85f, fill = 0.14f, sparkle = true),
        BubbleSkin(4, "Plasma", 420, ELECTRIC, hue = 300f, hue2 = 330f, sat = 0.9f, rim = 2.2f),
        BubbleSkin(5, "Rainbow", 700, RAINBOW, sat = 0.9f, rim = 2.4f, sparkle = true),
        BubbleSkin(6, "Ice", 260, IRIS, hue = 190f, hue2 = 215f, sat = 0.35f, rim = 3.4f, fill = 0.05f),
        BubbleSkin(7, "Lava", 480, ELECTRIC, hue = 12f, hue2 = 42f, sat = 0.95f, rim = 2f, fill = 0.12f),
        BubbleSkin(8, "Neon", 320, SOLID, hue = 118f, sat = 0.95f, rim = 2f),
        BubbleSkin(9, "Galaxy", 600, IRIS, hue = 250f, hue2 = 340f, sat = 0.85f, rim = 2.8f, fill = 0.1f, sparkle = true),
    )

    fun get(id: Int): BubbleSkin = all.getOrElse(id) { all[0] }
}

/**
 * Trail effects: what the cube sheds as it runs. Each is a recipe for the
 * shard system — colour rule, size, life, rate, drift — sampled per emission.
 */
object Trails {
    const val BODY = 0      // the cube's own colour
    const val FIXED = 1     // one hue
    const val RAINBOW = 2   // hue cycles over time
    const val DUO = 3       // alternates [hue] / [hue2]
    const val CONFETTI = 4  // random candy hue per shard

    class Trail(
        val id: Int,
        val name: String,
        val price: Int,
        val mode: Int,
        val hue: Float = 0f,
        val hue2: Float = 0f,
        val sat: Float = 0.8f,
        val value: Float = 1f,
        /** Shard size (world units). */
        val size: Float = 0.08f,
        val life: Float = 0.35f,
        /** Emissions per second at rest (the cube's skin can multiply it). */
        val rate: Float = 12f,
        /** Shards per emission. */
        val count: Int = 1,
        val speed: Float = 1.4f,
        /** Downward pull; negative drifts up (flames, smoke). */
        val gravity: Float = 14f,
    ) {
        /** Hue for the [k]-th emission at [t]. */
        fun hueAt(t: Float, k: Int): Float = when (mode) {
            RAINBOW -> t * 160f
            DUO -> if (k % 2 == 0) hue else hue2
            CONFETTI -> (k * 137) % 360f
            else -> hue
        }
    }

    val all: List<Trail> = listOf(
        Trail(0, "Classic", 0, BODY),
        Trail(1, "Sparks", 150, FIXED, hue = 0f, sat = 0f, size = 0.06f, life = 0.3f, rate = 22f, speed = 3f, gravity = 22f),
        Trail(2, "Flame", 300, DUO, hue = 18f, hue2 = 44f, sat = 0.95f, size = 0.13f, life = 0.5f, rate = 20f, count = 2, speed = 1.2f, gravity = -7f),
        Trail(3, "Rainbow", 500, RAINBOW, sat = 0.9f, size = 0.09f, life = 0.5f, rate = 24f, speed = 1.6f, gravity = 6f),
        Trail(4, "Frost", 260, FIXED, hue = 196f, sat = 0.3f, size = 0.12f, life = 0.8f, rate = 9f, speed = 0.8f, gravity = 2f),
        Trail(5, "Smoke", 240, FIXED, hue = 262f, sat = 0.25f, value = 0.55f, size = 0.18f, life = 0.9f, rate = 10f, speed = 0.7f, gravity = -3f),
        Trail(6, "Confetti", 450, CONFETTI, sat = 0.85f, size = 0.09f, life = 0.7f, rate = 16f, count = 2, speed = 2.4f, gravity = 9f),
        Trail(7, "Stardust", 400, FIXED, hue = 48f, sat = 0.75f, size = 0.06f, life = 1.0f, rate = 18f, speed = 1.0f, gravity = 0f),
        Trail(8, "Bubbles", 350, FIXED, hue = 185f, sat = 0.35f, size = 0.11f, life = 0.9f, rate = 10f, speed = 1.0f, gravity = -4f),
        Trail(9, "Pixie", 550, DUO, hue = 300f, hue2 = 180f, sat = 0.7f, size = 0.07f, life = 0.6f, rate = 26f, speed = 2f, gravity = -2f),
    )

    fun get(id: Int): Trail = all.getOrElse(id) { all[0] }
}

/** The three wardrobe categories, so menus and rewards can talk about "a skin of kind X". */
/**
 * Shards: three kinds of crystal that only mystery boxes drop (1 to 30 at a
 * time). Collect enough of one kind and its skin unlocks in the wardrobe.
 */
object Shards {
    const val EMBER = 0
    const val FROST = 1
    const val VOID = 2
    class Kind(val id: Int, val name: String, val hue: Float)
    val all: List<Kind> = listOf(Kind(EMBER, "Ember shards", 18f), Kind(FROST, "Frost shards", 195f), Kind(VOID, "Void shards", 282f))
    fun get(id: Int): Kind = all.getOrElse(id) { all[0] }
}

object Wardrobe {
    const val CUBE = 0
    const val BUBBLE = 1
    const val TRAIL = 2
    val cats = intArrayOf(CUBE, BUBBLE, TRAIL)

    fun count(cat: Int): Int = when (cat) { CUBE -> Skins.all.size; BUBBLE -> BubbleSkins.all.size; else -> Trails.all.size }
    fun name(cat: Int, id: Int): String = when (cat) { CUBE -> Skins.get(id).name; BUBBLE -> BubbleSkins.get(id).name; else -> Trails.get(id).name }
    fun price(cat: Int, id: Int): Int = when (cat) { CUBE -> Skins.get(id).price; BUBBLE -> BubbleSkins.get(id).price; else -> Trails.get(id).price }
    fun label(cat: Int): String = when (cat) { CUBE -> "CUBE"; BUBBLE -> "BUBBLE"; else -> "TRAIL" }
}
