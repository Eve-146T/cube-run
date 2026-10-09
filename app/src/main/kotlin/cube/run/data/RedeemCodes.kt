package cube.run.data

import java.util.Locale

/** Local code catalogue. Stable ids retain one-time redemption when a code gets an alias. */
object RedeemCodes {
    sealed interface Effect {
        data class Coins(val amount: Int) : Effect
        data class Unlock(val key: String, val name: String) : Effect
    }
    internal data class Definition(val id: String, val effect: Effect)
    sealed interface Result {
        data class Granted(val effect: Effect) : Result
        data object Invalid : Result
        data object AlreadyUsed : Result
        data object Unavailable : Result
    }

    private val catalogue = mapOf(
        "coin500" to Definition("coin500", Effect.Coins(500)),
    )

    fun redeem(input: String): Result {
        val definition = catalogue[input.trim().lowercase(Locale.ROOT)] ?: return Result.Invalid
        return Progress.redeemCode(definition)
    }
}
