package cube.run.core.gfx

/** Primitive float signature: sampling every box/coin must not box two Floats per call. */
fun interface TerrainHeight {
    operator fun invoke(z: Float): Float
}
