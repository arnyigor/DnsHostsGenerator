package com.arny.dnshostsgenerator.domain

/**
 * Дата окончания сертификата в читаемом виде и остаток срока.
 *
 * Считается вручную, без kotlinx-datetime: нужна одна дата в UTC, а лишняя зависимость
 * тянет обновления и вес. Алгоритм — гражданская дата из числа дней от эпохи
 * (Howard Hinnant, civil_from_days), поэтому он одинаков на Android и desktop.
 */
object CertDates {

    private const val MILLIS_PER_DAY = 86_400_000L

    /** `дд.мм.гггг` в UTC. */
    fun formatDateUtc(epochMillis: Long): String {
        val days = floorDiv(epochMillis, MILLIS_PER_DAY)
        val z = days + 719_468
        val era = floorDiv(z, 146_097)
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthPrime = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
        val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
        val realYear = if (month <= 2) year + 1 else year
        return "${pad2(day)}.${pad2(month)}.$realYear"
    }

    /** Сколько целых дней осталось до [notAfterEpochMillis]; отрицательное значение — просрочен. */
    fun daysLeft(notAfterEpochMillis: Long, nowEpochMillis: Long): Long =
        floorDiv(notAfterEpochMillis - nowEpochMillis, MILLIS_PER_DAY)

    private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()

    private fun floorDiv(value: Long, divisor: Long): Long {
        val quotient = value / divisor
        return if (value % divisor != 0L && (value xor divisor) < 0) quotient - 1 else quotient
    }
}
