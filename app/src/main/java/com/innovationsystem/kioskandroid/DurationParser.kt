package com.innovationsystem.kioskandroid

/**
 * Replacement for the npm `parse-duration` module used by the Electron version.
 * Only the subset actually used by electron-kiosk is supported:
 *   - Suffixes: ms, s, m, h, d
 *   - Multiple segments: "1h30m", "2h45m10s"
 *   - Pure numbers are treated as milliseconds (matches parse-duration behaviour).
 * Returns null on malformed input so callers can fall back to a safe default.
 */
object DurationParser {

    private val SEGMENT = Regex("""(\d+)(ms|s|m|h|d)?""", RegexOption.IGNORE_CASE)

    fun parseMillis(input: String?): Long? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim().lowercase()

        // Reject anything that is not purely digits + suffix segments.
        val cleaned = trimmed.replace(" ", "")
        if (!cleaned.matches(Regex("""^(\d+(ms|s|m|h|d)?)+$"""))) return null

        var totalMs = 0L
        for (match in SEGMENT.findAll(cleaned)) {
            val value = match.groupValues[1].toLongOrNull() ?: return null
            val unit = match.groupValues[2]
            totalMs += when (unit) {
                "", "ms" -> value
                "s" -> value * 1_000
                "m" -> value * 60_000
                "h" -> value * 3_600_000
                "d" -> value * 86_400_000
                else -> return null
            }
        }
        return if (totalMs > 0) totalMs else null
    }
}
