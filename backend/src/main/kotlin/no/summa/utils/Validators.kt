package no.summa.utils

/**
 * App-spesifikke validatorer for summa-summarum.
 * Felles validering (e-post, telefon, URL etc.) ligger i grunnmur: no.grunnmur.Validators
 */
object Validators {

    fun isValidAmount(amount: String): Boolean {
        return try {
            val value = amount.toBigDecimal()
            value > java.math.BigDecimal.ZERO
        } catch (e: Exception) {
            false
        }
    }
}
