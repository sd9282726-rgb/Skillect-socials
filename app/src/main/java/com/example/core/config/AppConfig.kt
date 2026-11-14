package com.example.core.config

import com.example.BuildConfig
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class CountryOption(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val defaultTimezone: String,
)

object AppConfig {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("YOUR_PROJECT_REF") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.contains("YOUR_SUPABASE_ANON_KEY")

    const val MAX_POST_LENGTH = 5000
    const val MAX_COMMENT_LENGTH = 1500
    const val MAX_MESSAGE_LENGTH = 2000
    const val MAX_BIO_LENGTH = 500

    fun isValidHttpUrl(rawUrl: String?): Boolean {
        val candidate = rawUrl?.trim().orEmpty()
        if (candidate.isBlank()) return false
        val uri = try {
            android.net.Uri.parse(candidate)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host.orEmpty()
        return (scheme == "https" || scheme == "http") && host.isNotBlank() && host.contains('.')
    }

    val supportedCountries: List<CountryOption> = listOf(
        CountryOption("US", "United States", "+1", "America/New_York"),
        CountryOption("GB", "United Kingdom", "+44", "Europe/London"),
        CountryOption("CA", "Canada", "+1", "America/Toronto"),
        CountryOption("DE", "Germany", "+49", "Europe/Berlin"),
        CountryOption("FR", "France", "+33", "Europe/Paris"),
        CountryOption("JP", "Japan", "+81", "Asia/Tokyo"),
        CountryOption("KR", "South Korea", "+82", "Asia/Seoul"),
        CountryOption("AU", "Australia", "+61", "Australia/Sydney"),
        CountryOption("BR", "Brazil", "+55", "America/Sao_Paulo"),
        CountryOption("IN", "India", "+91", "Asia/Kolkata"),
        CountryOption("NG", "Nigeria", "+234", "Africa/Lagos"),
        CountryOption("ZA", "South Africa", "+27", "Africa/Johannesburg"),
        CountryOption("SG", "Singapore", "+65", "Asia/Singapore"),
        CountryOption("AE", "United Arab Emirates", "+971", "Asia/Dubai"),
        CountryOption("MX", "Mexico", "+52", "America/Mexico_City"),
        CountryOption("ES", "Spain", "+34", "Europe/Madrid"),
        CountryOption("IT", "Italy", "+39", "Europe/Rome"),
        CountryOption("NL", "Netherlands", "+31", "Europe/Amsterdam"),
        CountryOption("SE", "Sweden", "+46", "Europe/Stockholm"),
        CountryOption("ID", "Indonesia", "+62", "Asia/Jakarta"),
    ).let { featured ->
        // Keep common regions first, then include every remaining ISO-3166 country.
        // Unknown calling codes/time zones intentionally fall back to blank/UTC rather than
        // silently assigning an incorrect regional value.
        val featuredCodes = featured.map { it.isoCode }.toSet()
        val remaining = Locale.getISOCountries()
            .asSequence()
            .filterNot { it in featuredCodes }
            .mapNotNull { isoCode ->
                val countryName = Locale("", isoCode).getDisplayCountry(Locale.ENGLISH).trim()
                if (countryName.isBlank()) null else CountryOption(
                    isoCode = isoCode,
                    name = countryName,
                    dialCode = "",
                    defaultTimezone = "UTC",
                )
            }
            .sortedBy { it.name }
            .toList()
        featured + remaining
    }

    val supportedLanguages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "hi" to "हिन्दी",
    )

    val communityCategories: List<String> = listOf(
        "Technology",
        "Entrepreneurship",
        "Design",
        "Business",
        "Programming",
        "Photography",
        "Entertainment",
        "Education",
        "Sports",
        "Science",
        "Creative Arts",
        "General Discussions",
    )

    /**
     * Validates that the provided ISO date (YYYY-MM-DD) meets Skillect's minimum 16+ age policy.
     */
    fun calculateAgeYears(birthDateIso: String): Int? {
        val parts = birthDateIso.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || year < 1900) return null

        val today = Calendar.getInstance()
        val dob = Calendar.getInstance().apply {
            isLenient = false
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return try {
            val dobTime = dob.timeInMillis
            if (dobTime > today.timeInMillis) return null
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age -= 1
            }
            age
        } catch (_: Exception) {
            null
        }
    }

    fun isEligibleAge16Plus(birthDateIso: String): Boolean {
        val age = calculateAgeYears(birthDateIso) ?: return false
        return age >= 16
    }

    fun isMinor16To17(birthDateIso: String): Boolean {
        val age = calculateAgeYears(birthDateIso) ?: return false
        return age in 16..17
    }

    fun formatCompactCount(count: Int, locale: Locale = Locale.getDefault()): String {
        val safe = maxOf(0, count)
        val nf = NumberFormat.getNumberInstance(locale)
        return when {
            safe >= 1_000_000 -> String.format(locale, "%.1fM", safe / 1_000_000.0)
            safe >= 10_000 -> String.format(locale, "%.1fK", safe / 1_000.0)
            else -> nf.format(safe)
        }
    }

    fun formatRelativeTimestamp(isoTimestamp: String, locale: Locale = Locale.getDefault()): String {
        val parsed = parseIsoTimestamp(isoTimestamp) ?: return isoTimestamp
        val now = System.currentTimeMillis()
        val diffSeconds = abs(now - parsed.time) / 1000L
        return when {
            diffSeconds < 60 -> "Just now"
            diffSeconds < 3600 -> "${diffSeconds / 60}m"
            diffSeconds < 86400 -> "${diffSeconds / 3600}h"
            diffSeconds < 604800 -> "${diffSeconds / 86400}d"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(parsed)
        }
    }

    fun parseIsoTimestamp(raw: String): Date? {
        if (raw.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(raw)
            } catch (_: Exception) {
                // Try next format
            }
        }
        return null
    }
}
