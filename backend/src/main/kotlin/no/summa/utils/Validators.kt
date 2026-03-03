package no.summa.utils

object Validators {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_REGEX.matches(email) && email.length <= 255
    }

    fun isValidCategoryCode(code: String): Boolean {
        return code.matches(Regex("^\\d{4}$"))
    }

    fun isValidAmount(amount: String): Boolean {
        return try {
            val value = amount.toBigDecimal()
            value > java.math.BigDecimal.ZERO
        } catch (e: Exception) {
            false
        }
    }
}
