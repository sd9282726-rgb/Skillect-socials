package com.example.core.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API 24-safe ISO-8601 UTC helpers (java.time requires API 26 without desugaring).
 */
object TimeCompat {
    private val isoFormats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    fun nowUtcIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    /** Epoch millis, or null if unparseable. */
    fun parseToEpochMillis(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        for (pattern in isoFormats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.isLenient = true
                return fmt.parse(value)?.time
            } catch (_: ParseException) {
                // try next
            }
        }
        return null
    }

    fun isAfter(laterIso: String?, earlierIso: String?): Boolean {
        val later = parseToEpochMillis(laterIso) ?: return false
        val earlier = parseToEpochMillis(earlierIso) ?: return false
        return later > earlier
    }
}
