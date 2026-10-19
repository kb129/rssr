package com.kb129.rssr.core.parser

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** RSS の RFC 822 形式と Atom / Dublin Core の ISO 8601 形式を読む。 */
object DateParser {
    private val rfc822Patterns = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss z",
        "EEE, d MMM yyyy HH:mm Z",
        "EEE, d MMM yyyy HH:mm z",
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss z",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    /** "JST" などの略称は環境によって解決できないので自前で補う。 */
    private val zoneAbbreviations = mapOf(
        "JST" to "+0900", "KST" to "+0900", "UT" to "+0000", "GMT" to "+0000", "UTC" to "+0000",
        "EST" to "-0500", "EDT" to "-0400", "CST" to "-0600", "CDT" to "-0500",
        "MST" to "-0700", "MDT" to "-0600", "PST" to "-0800", "PDT" to "-0700",
    )

    fun parse(raw: String): Long? {
        val s = raw.trim().replace(Regex("""\s+"""), " ")
        if (s.isEmpty()) return null
        parseIso(s)?.let { return it }
        val withOffset = zoneAbbreviations.entries.fold(s) { acc, (abbr, offset) ->
            acc.replace(Regex("""\b$abbr$"""), offset)
        }
        for (f in rfc822Patterns) {
            try {
                return ZonedDateTime.parse(withOffset, f).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun parseIso(s: String): Long? {
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            // タイムゾーン無しは UTC とみなす
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            // "2026-10-19" のような日付のみ
            return java.time.LocalDate.parse(s).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        return null
    }
}
