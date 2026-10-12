package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.DnsPresetUnusableException
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.domain.GenerationProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun generate_resolvesDomainsConcurrentlyAndKeepsInputOrder() = runBlocking {
        val resolver = ConcurrencyTrackingFake()
        val generator = HostsGenerator(dnsResolver = resolver)
        val domains = (1..20).map { "d$it.example" }
        val progress = mutableListOf<GenerationProgress>()

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = domains,
                outputFileName = "hosts.txt",
                dedupEnabled = true,
                concurrency = 4,
            ),
            onProgress = { progress += it },
        )

        assertEquals(domains.joinToString("\n") { "10.0.0.1 $it" }, result.outputText)
        assertTrue(resolver.maxInFlight > 1, "expected parallel lookups, maxInFlight=${resolver.maxInFlight}")
        assertTrue(resolver.maxInFlight <= 4, "concurrency limit exceeded, maxInFlight=${resolver.maxInFlight}")
        assertEquals(20, progress.last().processedLines)
        assertEquals(20, progress.last().totalLines)
    }

    @Test
    fun generate_resolvesRepeatedDomainOnlyOnce() = runBlocking {
        val resolver = ConcurrencyTrackingFake()
        val generator = HostsGenerator(dnsResolver = resolver)

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("a.example", "  a.example  ", "a.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        // Без дедупликации все строки остаются в выводе, но DNS-запрос выполняется один раз.
        assertEquals(List(3) { "10.0.0.1 a.example" }.joinToString("\n"), result.outputText)
        assertEquals(2, resolver.totalCalls)
    }

    /**
     * 0.0.0.0/:: от DNS — это блокировка домена, а не разблокировка: в hosts такое писать нельзя.
     * Живой пример: пресет xbox-dns.ru обнуляет chatgpt.com/openai.com/gemini.google.com.
     */
    @Test
    fun generate_marksNullRoutedDomainAsBlockedAndKeepsOthers() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "83.220.169.155" to mapOf(
                        "blocked.example" to listOf("0.0.0.0"),
                        "ok.example" to listOf("10.0.0.1"),
                    ),
                    "8.8.8.8" to mapOf(
                        "blocked.example" to listOf("9.9.9.9"),
                        "ok.example" to listOf("10.0.0.2"),
                    ),
                ),
            ),
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "83.220.169.155",
                checkDns = "8.8.8.8",
                inputLines = listOf("blocked.example", "ok.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        assertEquals(1, result.stats.blockedCount)
        assertEquals(1, result.stats.resolvedCount)
        assertTrue(HostLine.Blocked("blocked.example") in result.lines)
        assertTrue("#blocked blocked.example" in result.outputText)
        assertFalse("0.0.0.0 blocked.example" in result.outputText)
        assertTrue("10.0.0.1 ok.example" in result.outputText)
    }

    @Test
    fun generate_cancelsPresetThatReturnsNullRouteForEverything() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "176.99.11.77" to mapOf(
                        "chatgpt.com" to listOf("0.0.0.0"),
                        "openai.com" to listOf("0.0.0.0"),
                    ),
                    "8.8.8.8" to mapOf(
                        "chatgpt.com" to listOf("1.2.3.4"),
                        "openai.com" to listOf("1.2.3.5"),
                    ),
                ),
            ),
        )

        val error = assertFailsWith<DnsPresetUnusableException> {
            generator.generate(
                presetTitle = "xbox-dns.ru",
                request = GenerateHostsRequest(
                    primaryDns = "176.99.11.77",
                    checkDns = "8.8.8.8",
                    inputLines = listOf("chatgpt.com", "openai.com"),
                    outputFileName = "hosts.txt",
                    dedupEnabled = false,
                ),
            )
        }

        assertEquals("xbox-dns.ru", error.presetTitle)
        assertEquals(2, error.blockedCount)
    }

    /** Полностью недоступный DNS — это не «пресет врёт», отменять генерацию не надо. */
    @Test
    fun generate_doesNotCancelWhenNothingResolvedAtAll() = runBlocking {
        val generator = HostsGenerator(dnsResolver = FakeDnsResolver(emptyMap()))

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "83.220.169.155",
                checkDns = "8.8.8.8",
                inputLines = listOf("none.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        assertEquals(1, result.stats.unresolvedCount)
        assertEquals(0, result.stats.blockedCount)
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

/** Фейк с задержкой, считающий максимальное число одновременных запросов. */
private class ConcurrencyTrackingFake : DnsResolver {
    var inFlight = 0
    var maxInFlight = 0
    var totalCalls = 0

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
    ): List<String> {
        totalCalls++
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        delay(10)
        inFlight--
        return if (dnsServer == "1.1.1.1") listOf("10.0.0.1") else listOf("10.0.0.2")
    }
}
