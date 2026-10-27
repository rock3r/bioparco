package dev.sebastiano.departureboard

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)

/** [city]'s row at [instant]: its local time and its name. */
internal fun cityRow(city: City, instant: Instant): String {
    val local = ZonedDateTime.ofInstant(instant, ZoneId.of(city.zone))
    return rowText(clockText(local.hour, local.minute), city.name)
}

internal fun gmtLabel(city: City, instant: Instant): String =
    gmtLabel(ZoneId.of(city.zone).rules.getOffset(instant).totalSeconds)

/** `Tue, 27 Oct`, in the viewer's own time zone. */
internal fun dateLabel(instant: Instant): String =
    dateFormat.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()))

/** Milliseconds from [instant] to the next whole minute. */
internal fun untilNextMinute(instant: Instant): Long = 60_000L - instant.toEpochMilli() % 60_000L
