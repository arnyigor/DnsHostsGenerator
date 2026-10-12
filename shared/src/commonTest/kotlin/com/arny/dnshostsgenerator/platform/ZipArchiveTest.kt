package com.arny.dnshostsgenerator.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipArchiveTest {

    /** Набор как в паках темы: hosts, dnsmasq, AdGuard, NextDNS. */
    private val entries = listOf(
        ZipTextEntry("aihosts.txt", "103.137.248.179 chatgpt.com\n"),
        ZipTextEntry("aihosts.conf", "server=/chatgpt.com/83.220.169.155\n"),
        ZipTextEntry("adguard.txt", "||chatgpt.com^\$dnsrewrite=103.137.248.179\n"),
        ZipTextEntry("nextdns.txt", "103.137.248.179 chatgpt.com\n"),
    )

    @Test
    fun zipRoundTripKeepsTextEntries() {
        val restored = unzipTextEntries(zipTextEntries(entries))

        assertEquals(entries.map { it.name }, restored.map { it.name })
        assertEquals(entries.map { it.content }, restored.map { it.content })
    }

    @Test
    fun binaryAndDotEntriesAreSkipped() {
        val packed = zipTextEntries(
            entries + listOf(
                ZipTextEntry("shot.png", "not a list"),
                ZipTextEntry(".DS_Store", "junk"),
            ),
        )

        assertEquals(
            listOf("aihosts.txt", "aihosts.conf", "adguard.txt", "nextdns.txt"),
            unzipTextEntries(packed).map { it.name },
        )
    }

    @Test
    fun totalBytesLimitStopsReading() {
        val packed = zipTextEntries(
            listOf(
                ZipTextEntry("big.txt", "a".repeat(3000)),
                ZipTextEntry("small.txt", "b".repeat(100)),
            ),
        )

        val restored = unzipTextEntries(packed, maxTotalBytes = 1000)
        assertEquals(listOf("small.txt"), restored.map { it.name })
    }

    @Test
    fun textEntryFilterAcceptsListsWithoutExtension() {
        assertTrue(isTextEntryName("hosts"))
        assertTrue(isTextEntryName("folder/aihosts.conf"))
        assertEquals(false, isTextEntryName("icon.jpg"))
    }
}
