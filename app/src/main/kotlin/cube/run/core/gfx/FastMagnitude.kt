package cube.run.core.gfx

/** Avoid ART's non-intrinsic Math.abs/float-bit conversion path in hot bounds calculations. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun fastMagnitude(value: Float): Float = if (value < 0f) -value else value
