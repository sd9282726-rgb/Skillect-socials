package com.example.ui.auth

import com.example.core.config.AppConfig
import java.util.Calendar

/**
 * Helpers for Create Account / onboarding validation.
 * Day, Month, Year parts compose to ISO YYYY-MM-DD for age checks.
 */
object OnboardingValidation {
    fun composeBirthDateIso(day: String, month: String, year: String): String? {
        val d = day.trim().toIntOrNull() ?: return null
        val m = month.trim().toIntOrNull() ?: return null
        val y = year.trim().toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31 || y < 1900) return null
        val cal = Calendar.getInstance().apply {
            isLenient = false
            try {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, d)
                time // force validation
            } catch (_: Exception) {
                return null
            }
        }
        if (cal.get(Calendar.YEAR) != y || cal.get(Calendar.MONTH) != m - 1 || cal.get(Calendar.DAY_OF_MONTH) != d) {
            return null
        }
        return "%04d-%02d-%02d".format(y, m, d)
    }

    fun birthDateError(day: String, month: String, year: String): String? {
        if (day.isBlank() || month.isBlank() || year.isBlank()) {
            return "Select day, month and year of birth."
        }
        val iso = composeBirthDateIso(day, month, year) ?: return "Enter a valid date of birth."
        if (!AppConfig.isEligibleAge16Plus(iso)) {
            return "Skillect requires users to be aged 16 or older."
        }
        return null
    }

    fun countryFlagEmoji(isoCode: String): String {
        if (isoCode.length != 2) return "🌐"
        val base = 0x1F1E6
        val a = isoCode[0].uppercaseChar() - 'A'
        val b = isoCode[1].uppercaseChar() - 'A'
        if (a !in 0..25 || b !in 0..25) return "🌐"
        return String(Character.toChars(base + a)) + String(Character.toChars(base + b))
    }

    /** Interest categories required by product. */
    val interestCategories: List<String> = listOf(
        "Technology",
        "Entrepreneurship",
        "Programming",
        "Design",
        "Marketing",
        "Business",
        "Education",
        "Art",
    )

    /** Goal options required by product. */
    val goalOptions: List<String> = listOf(
        "Learn",
        "Share knowledge",
        "Showcase projects",
        "Connect with people",
        "Network",
        "Discover communities",
    )
}
