package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostsGeneratorTest {

    @Test
    fun generate_matchesPowerShellScriptOutputSemantics() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                records = mapOf(
                    "1.1.1.1" to mapOf(
                        "forwarded.example" to listOf("10.0.0.1", "10.0.0.2"),
                        "resolved.example" to listOf("10.0.0.3"),
                        "unresolved.example" to emptyList(),
                        "duplicate.example" to listOf("10.0.0.4"),
                    ),
                    "8.8.8.8" to mapOf(
                        "forwarded.example" to listOf("10.0.0.2"),
                        "resolved.example" to listOf("8.8.8.8"),
                        "duplicate.example" to listOf("8.8.4.4"),
                    ),
                ),
            ),
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf(
                    "",
                    "# comment",
                    "forwarded.example",
                    "resolved.example",
                    "unresolved.example",
                    "duplicate.example",
                    "duplicate.example",
                ),
                outputFileName = "hosts.txt",
                dedupEnabled = true,
            ),
        )

        assertEquals(
            listOf(
                "",
                "# comment",
                "#forwarded forwarded.example",
                "10.0.0.3 resolved.example",
                "#unresolvedDomain unresolved.example",
                "10.0.0.4 duplicate.example",
                "#duplicate duplicate.example",
            ).joinToString("\n"),
            result.outputText,
        )
        assertEquals(7, result.stats.lineCount)
        assertEquals(2, result.stats.resolvedCount)
        assertEquals(1, result.stats.forwardedCount)
        assertEquals(1, result.stats.unresolvedCount)
        assertEquals(1, result.stats.duplicateCount)
        assertEquals(2, result.stats.activeHostsCount)
    }

    @Test
    fun generate_doesNotMarkUnresolvedDomainAsProcessedForDedup() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                records = mapOf(
                    "1.1.1.1" to mapOf("missing.example" to emptyList()),
                ),
            ),
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("missing.example", "missing.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = true,
            ),
        )

        assertEquals(
            listOf(
                "#unresolvedDomain missing.example",
                "#unresolvedDomain missing.example",
            ).joinToString("\n"),
            result.outputText,
        )
        assertEquals(0, result.stats.duplicateCount)
        assertEquals(2, result.stats.unresolvedCount)
    }

    @Test
    fun generate_forwardsDotOptionsToResolver() = runBlocking {
        val resolver = OptionsCapturingFake()
        val generator = HostsGenerator(dnsResolver = resolver)

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "83.220.169.155",
                primaryDotHost = "dns.comss.one",
                primaryAllowInvalidTls = false,
                checkDns = "8.8.8.8",
                checkDotHost = "dns.google",
                inputLines = listOf("example.com"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        // DoT-настройки пресета должны дойти до резолвера в виде options.
        assertEquals(DnsQueryOptions(dotHost = "dns.comss.one", allowInvalidTls = false), resolver.primaryOptions)
        assertEquals(DnsQueryOptions(dotHost = "dns.google", allowInvalidTls = false), resolver.checkOptions)
        // Разные IP у primary/check -> домен резолвится в hosts.
        assertEquals("10.0.0.1 example.com", result.outputText)
        assertEquals(1, result.stats.resolvedCount)
    }

    @Test
    fun generationStats_marksSuspiciousForwarding() {
        val intercepted = GenerationStats(
            lineCount = 10,
            resolvedCount = 1,
            forwardedCount = 9,
            unresolvedCount = 0,
            duplicateCount = 0,
        )
        assertTrue(intercepted.suspiciousForwarding)

        val normal = GenerationStats(
            lineCount = 10,
            resolvedCount = 9,
            forwardedCount = 1,
            unresolvedCount = 0,
            duplicateCount = 0,
        )
        assertFalse(normal.suspiciousForwarding)

        val noDomains = GenerationStats(
            lineCount = 0,
            resolvedCount = 0,
            forwardedCount = 0,
            unresolvedCount = 0,
            duplicateCount = 0,
        )
        assertFalse(noDomains.suspiciousForwarding)
    }
}

private class FakeDnsResolver(
    private val records: Map<String, Map<String, List<String>>>,
) : DnsResolver {
    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
    ): List<String> = records[dnsServer]?.get(domain).orEmpty()
}

/**
 * Фейк, перехватывающий options: проверяет, что DoT-настройки (dotHost/allowInvalidTls)
 * доходят от генератора до резолвера.
 */
private class OptionsCapturingFake : DnsResolver {
    var primaryOptions: DnsQueryOptions? = null
    var checkOptions: DnsQueryOptions? = null

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
    ): List<String> = emptyList()

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
        options: DnsQueryOptions,
    ): List<String> {
        if (dnsServer == "83.220.169.155") {
            primaryOptions = options
            return listOf("10.0.0.1")
        }
        checkOptions = options
        return listOf("10.0.0.2")
    }
}
