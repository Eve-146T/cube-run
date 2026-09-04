package cube.run.core

import kotlin.math.sin

/**
 * Player skins. Every skin is the cube — what changes is its colour behaviour,
 * its glow and its trail. The game samples [Skin.hueAt] / [Skin.valueAt] every
 * frame on the GL thread (pure functions of time — no state).
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
    ) {
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
    )

    fun get(id: Int): Skin = all.getOrElse(id) { all[0] }
}
