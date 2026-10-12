package com.arny.dnshostsgenerator.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HostsExportersTest {

    private val result = GenerationResult(
        presetTitle = "dns.comss.one (рекомендуемый)",
        outputFileName = "host_out_dns.comss.one.txt",
        lines = listOf(
            HostLine.Comment("# --- OpenAI ChatGPT ---"),
            HostLine.Resolved("chatgpt.com", "103.137.248.179", listOf("103.137.248.179")),
            HostLine.Resolved("ab.chatgpt.com", "103.137.248.179", listOf("103.137.248.179")),
            HostLine.Forwarded("forwarded.example", listOf("1.1.1.1"), listOf("1.1.1.1")),
            HostLine.Unresolved("unresolved.example"),
            HostLine.Duplicate("chatgpt.com"),
        ),
        stats = GenerationStats(
            lineCount = 6,
            resolvedCount = 2,
            forwardedCount = 1,
            unresolvedCount = 1,
            duplicateCount = 1,
        ),
    )

    @Test
    fun hostsFormatIsTheNativeOutput() {
        assertEquals(result.outputText, result.exportText(ExportFormat.HOSTS))
        assertEquals("host_out_dns.comss.one.txt", result.exportFileName(ExportFormat.HOSTS))
    }

    /** Строка-эталон — aihosts.conf из пака dnscomssone-10-09-2026.zip (тема 4PDA 1117490). */
    @Test
    fun dnsmasqFormatMatchesCommunityPacks() {
        assertEquals(
            "# DnsHostsGenerator: dns.comss.one (рекомендуемый) — " +
                "resolved 2, forwarded 1, unresolved 1\n" +
                "server=/chatgpt.com/103.137.248.179\n" +
                "server=/ab.chatgpt.com/103.137.248.179",
            result.exportText(ExportFormat.DNSMASQ),
        )
        assertEquals("host_out_dns.comss.one.conf", result.exportFileName(ExportFormat.DNSMASQ))
    }

    /** Строка-эталон — adguard.txt из того же пака. */
    @Test
    fun adguardFormatMatchesCommunityPacks() {
        assertEquals(
            "||chatgpt.com^\$dnsrewrite=103.137.248.179\n" +
                "||ab.chatgpt.com^\$dnsrewrite=103.137.248.179",
            result.exportText(ExportFormat.ADGUARD).lines().drop(1).joinToString("\n"),
        )
        assertEquals("host_out_dns.comss.one.adguard.txt", result.exportFileName(ExportFormat.ADGUARD))
    }

    /** Строка-эталон — nextdns.txt: `ip domain`, только resolved. */
    @Test
    fun nextDnsFormatContainsOnlyResolvedLines() {
        val text = result.exportText(ExportFormat.NEXTDNS)
        assertEquals(
            listOf("103.137.248.179 chatgpt.com", "103.137.248.179 ab.chatgpt.com"),
            text.lines().drop(1),
        )
        assertEquals("host_out_dns.comss.one.nextdns.txt", result.exportFileName(ExportFormat.NEXTDNS))
    }
}
