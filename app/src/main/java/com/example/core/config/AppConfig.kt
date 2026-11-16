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

data class CountryOption(
    val isoCode: String,
    val displayName: String,
    val callingCode: String,
    val defaultTimeZone: String,
)

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim()
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            supabaseUrl.contains(".supabase.co") &&
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
        val featuredCodes = featured.map { it.isoCode }.toSet()
        val remaining = Locale.getISOCountries()
            .asSequence()
            .filterNot { it in featuredCodes }
            .map { code ->
                val display = Locale("", code).displayCountry.ifBlank { code }
                CountryOption(code, display, "", "UTC")
            }
            .sortedBy { it.displayName }
            .toList()
        featured + remaining
    }

    val supportedLanguages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "hi" to "हिन्दी",
        "ar" to "العربية",
        "ja" to "日本語",
        "ko" to "한국어",
    )

    fun formatCount(value: Int, locale: Locale = Locale.getDefault()): String {
        val safe = value.coerceAtLeast(0)
        val nf = NumberFormat.getIntegerInstance(locale)
        return when {
            safe < 1000 -> nf.format(safe)
            safe < 1_000_000 -> String.format(locale, "%.1fK", safe / 1000.0).replace(".0K", "K")
            else -> String.format(locale, "%.1fM", safe / 1_000_000.0).replace(".0M", "M")
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
        // Normalize microsecond/nanosecond fractions to millis so SimpleDateFormat can parse
        // e.g. 2026-11-16T06:20:03.618962+00:00 → 2026-11-16T06:20:03.618+00:00
        val normalized = raw.trim().replace(Regex("(\\.\\d{3})\\d+"), "$1")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(normalized)
                if (parsed != null) return parsed
            } catch (_: Exception) {
                // Try next format
            }
        }
        // Last-resort: Instant.parse for strict ISO-8601
        return try {
            Date.from(java.time.Instant.parse(raw.trim()))
        } catch (_: Exception) {
            try {
                Date.from(java.time.Instant.parse(normalized))
            } catch (_: Exception) {
                null
            }
        }
    }
}
