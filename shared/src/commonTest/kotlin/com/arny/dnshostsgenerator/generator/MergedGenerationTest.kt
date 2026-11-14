package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.resolver.DnsResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Мерж в генерации: домен обслуживает не тот же пресет, что соседний, и hosts должен
 * собираться из обоих, а не оставаться с пустыми строками.
 */
class MergedGenerationTest {

    private class FakeResolver(
        private val records: Map<String, Map<String, List<String>>>,
    ) : DnsResolver {
        override suspend fun resolveA(
            domain: String,
            dnsServer: String,
            timeoutMillis: Int,
        ): List<String> = records[dnsServer]?.get(domain).orEmpty()
    }

    private fun preset(id: String, address: String, check: String = "8.8.8.8") = DnsProviderPreset(
        id = id,
        title = id,
        primaryDns = address,
        checkDns = check,
        outputFileName = "hosts_$id.txt",
    )

    @Test
    fun takesFirstWorkingAddressForEachDomain() = runTest {
        val resolver = FakeResolver(
            mapOf(
                // Первый пресет обслуживает только chatgpt.com, второй — только Gemini.
                "10.0.0.1" to mapOf(
                    "chatgpt.com" to listOf("1.1.1.1"),
                    "gemini.google.com" to emptyList(),
                ),
                "10.0.0.2" to mapOf(
                    "chatgpt.com" to listOf("2.2.2.2"),
                    "gemini.google.com" to listOf("3.3.3.3"),
                ),
                "8.8.8.8" to mapOf(
                    "chatgpt.com" to listOf("8.8.8.8"),
                    "gemini.google.com" to listOf("8.8.8.8"),
                ),
            ),
        )
        val generator = HostsGenerator(dnsResolver = resolver)

        val result = generator.generate(
            presetTitle = "merged",
            request = GenerateHostsRequest(
                primaryDns = "10.0.0.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("chatgpt.com", "gemini.google.com"),
                outputFileName = "hosts_merged.txt",
                dedupEnabled = true,
                presetChain = listOf(preset("first", "10.0.0.1"), preset("second", "10.0.0.2")),
            ),
        )

        val text = result.outputText
        // chatgpt.com отдан первым пресетом, Gemini — вторым: файл один, домены не потеряны.
        assertTrue(text.contains("1.1.1.1 chatgpt.com"), text)
        assertTrue(text.contains("3.3.3.3 gemini.google.com"), text)
        assertEquals(2, result.stats.resolvedCount)
        assertEquals(0, result.stats.unresolvedCount)
    }

    @Test
    fun fallsBackWhenFirstPresetReturnsNullRoute() = runTest {
        val resolver = FakeResolver(
            mapOf(
                "10.0.0.1" to mapOf("gemini.google.com" to listOf("0.0.0.0")),
                "10.0.0.2" to mapOf("gemini.google.com" to listOf("3.3.3.3")),
                "8.8.8.8" to mapOf("gemini.google.com" to listOf("8.8.8.8")),
            ),
        )
        val generator = HostsGenerator(dnsResolver = resolver)

        val result = generator.generate(
            presetTitle = "merged",
            request = GenerateHostsRequest(
                primaryDns = "10.0.0.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("gemini.google.com"),
                outputFileName = "hosts_merged.txt",
                dedupEnabled = true,
                presetChain = listOf(preset("first", "10.0.0.1"), preset("second", "10.0.0.2")),
            ),
        )

        // 0.0.0.0 от первого пресета — не приговор: второй дал рабочий адрес.
        assertTrue(result.outputText.contains("3.3.3.3 gemini.google.com"), result.outputText)
        assertEquals(0, result.stats.blockedCount)
    }

    @Test
    fun singlePresetPathIsUnchanged() = runTest {
        val resolver = FakeResolver(
            mapOf(
                "10.0.0.1" to mapOf("chatgpt.com" to listOf("1.1.1.1")),
                "8.8.8.8" to mapOf("chatgpt.com" to listOf("8.8.8.8")),
            ),
        )
        val generator = HostsGenerator(dnsResolver = resolver)

        val result = generator.generate(
            presetTitle = "single",
            request = GenerateHostsRequest(
                primaryDns = "10.0.0.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("chatgpt.com"),
                outputFileName = "hosts_single.txt",
                dedupEnabled = true,
            ),
        )

        assertTrue(result.outputText.contains("1.1.1.1 chatgpt.com"), result.outputText)
    }
}
