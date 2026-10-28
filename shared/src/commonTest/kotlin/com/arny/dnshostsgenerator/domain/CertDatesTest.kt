package com.arny.dnshostsgenerator.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Даты сертификатов считаются без kotlinx-datetime, поэтому проверяем границы алгоритма
 * на известных значениях эпохи (все даты — UTC).
 */
class CertDatesTest {

    @Test
    fun formatDateUtc_knownEpochValues() {
        assertEquals("01.01.1970", CertDates.formatDateUtc(0))
        assertEquals("14.11.2023", CertDates.formatDateUtc(1_700_000_000_000))
        assertEquals("01.03.2024", CertDates.formatDateUtc(1_709_251_200_000)) // leap year, 1 Mar
        assertEquals("29.02.2024", CertDates.formatDateUtc(1_709_164_800_000)) // 29 Feb
        assertEquals("01.01.2038", CertDates.formatDateUtc(2_145_916_800_000)) // end of 32-bit time_t
    }

    @Test
    fun formatDateUtc_isStableAroundMidnightUtc() {
        // 2023-12-31 23:59:59.999 UTC и 2024-01-01 00:00:00 UTC
        assertEquals("31.12.2023", CertDates.formatDateUtc(1_704_067_199_999))
        assertEquals("01.01.2024", CertDates.formatDateUtc(1_704_067_200_000))
    }

    @Test
    fun daysLeft_countsWholeDaysAndGoesNegativeForExpired() {
        val now = 1_700_000_000_000
        assertEquals(30, CertDates.daysLeft(now + 30L * 86_400_000, now))
        assertEquals(0, CertDates.daysLeft(now + 86_399_999, now))
        assertEquals(-1, CertDates.daysLeft(now - 1, now))
        assertEquals(-5, CertDates.daysLeft(now - 5L * 86_400_000, now))
    }
}
