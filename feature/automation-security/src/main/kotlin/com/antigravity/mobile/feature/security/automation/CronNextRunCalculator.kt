package com.antigravity.mobile.feature.security.automation

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Standard 5-field POSIX cron parser and next-trigger calculator:
 * `<minute> <hour> <day-of-month> <month> <day-of-week>`
 *
 * Supports wildcard, stepped wildcard (star-slash-step), single values, ranges `a-b`,
 * stepped ranges `a-b/step`, and comma lists.
 * Both `0` and `7` represent Sunday in the day-of-week field.
 */
object CronNextRunCalculator {

    private data class ParsedCron(
        val minutes: Set<Int>,
        val hours: Set<Int>,
        val daysOfMonth: Set<Int>,
        val months: Set<Int>,
        val daysOfWeek: Set<Int>, // 0 = Sunday, 1 = Monday ... 6 = Saturday
        val dayOfMonthWildcard: Boolean,
        val dayOfWeekWildcard: Boolean,
    )

    fun validate(cronExpression: String) {
        parse(cronExpression)
    }

    fun nextRunAtEpochMs(
        cronExpression: String,
        afterEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val cron = parse(cronExpression)
        var candidate = Instant.ofEpochMilli(afterEpochMs)
            .atZone(zoneId)
            .truncatedTo(ChronoUnit.MINUTES)
            .plusMinutes(1)

        val maxLimit = candidate.plusYears(4)

        while (candidate.isBefore(maxLimit)) {
            if (candidate.monthValue !in cron.months) {
                candidate = candidate
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .plusMonths(1)
                continue
            }

            if (!matchesDay(candidate, cron)) {
                candidate = candidate
                    .withHour(0)
                    .withMinute(0)
                    .plusDays(1)
                continue
            }

            if (candidate.hour !in cron.hours) {
                candidate = candidate
                    .withMinute(0)
                    .plusHours(1)
                continue
            }

            if (candidate.minute !in cron.minutes) {
                candidate = candidate.plusMinutes(1)
                continue
            }

            return candidate.toInstant().toEpochMilli()
        }

        throw IllegalArgumentException(
            "No matching execution time found within 4 years for cron expression: '$cronExpression'",
        )
    }

    private fun matchesDay(dt: ZonedDateTime, cron: ParsedCron): Boolean {
        val domMatch = dt.dayOfMonth in cron.daysOfMonth
        val cronDow = dt.dayOfWeek.value % 7 // Java Monday=1..Sunday=7 -> Cron Sunday=0, Monday=1..Saturday=6
        val dowMatch = cronDow in cron.daysOfWeek

        return when {
            cron.dayOfMonthWildcard && cron.dayOfWeekWildcard -> true
            cron.dayOfMonthWildcard -> dowMatch
            cron.dayOfWeekWildcard -> domMatch
            else -> domMatch || dowMatch
        }
    }

    private fun parse(expression: String): ParsedCron {
        val parts = expression.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(parts.size == 5) {
            "Cron expression must have exactly 5 fields (min hour dom month dow), got: '$expression'"
        }

        val minutes = parseField(parts[0], 0, 59, "minute")
        val hours = parseField(parts[1], 0, 23, "hour")
        val daysOfMonth = parseField(parts[2], 1, 31, "day-of-month")
        val months = parseField(parts[3], 1, 12, "month")
        val rawDow = parseField(parts[4], 0, 7, "day-of-week")
        val normalizedDow = rawDow.map { if (it == 7) 0 else it }.toSet()

        return ParsedCron(
            minutes = minutes,
            hours = hours,
            daysOfMonth = daysOfMonth,
            months = months,
            daysOfWeek = normalizedDow,
            dayOfMonthWildcard = parts[2] == "*",
            dayOfWeekWildcard = parts[4] == "*",
        )
    }

    private fun parseField(field: String, min: Int, max: Int, name: String): Set<Int> {
        require(field.isNotBlank()) { "Cron $name field must not be blank" }
        val result = linkedSetOf<Int>()

        for (segment in field.split(",")) {
            val trimmed = segment.trim()
            require(trimmed.isNotEmpty()) { "Empty segment in cron $name field: '$field'" }

            val slashParts = trimmed.split("/")
            require(slashParts.size <= 2) { "Invalid step syntax in cron $name field: '$trimmed'" }

            val step = if (slashParts.size == 2) {
                val parsedStep = slashParts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid step in cron $name field: '$trimmed'")
                require(parsedStep > 0) { "Step must be > 0 in cron $name field: '$trimmed'" }
                parsedStep
            } else {
                1
            }

            val rangePart = slashParts[0]
            val (start, end) = when {
                rangePart == "*" -> min to max
                rangePart.contains("-") -> {
                    val bounds = rangePart.split("-")
                    require(bounds.size == 2) { "Invalid range in cron $name field: '$trimmed'" }
                    val s = bounds[0].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid range start in cron $name field: '$trimmed'")
                    val e = bounds[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid range end in cron $name field: '$trimmed'")
                    require(s in min..max && e in min..max && s <= e) {
                        "Range out of bounds ($min..$max) in cron $name field: '$trimmed'"
                    }
                    s to e
                }
                else -> {
                    val value = rangePart.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid number in cron $name field: '$trimmed'")
                    require(value in min..max) {
                        "Value $value out of bounds ($min..$max) in cron $name field"
                    }
                    if (slashParts.size == 2) value to max else value to value
                }
            }

            var v = start
            while (v <= end) {
                result.add(v)
                v += step
            }
        }

        require(result.isNotEmpty()) { "Cron $name field produced no valid values: '$field'" }
        return result
    }
}
