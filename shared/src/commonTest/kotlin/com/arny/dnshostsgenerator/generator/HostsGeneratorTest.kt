package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.DnsPresetUnusableException
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.HttpProber
import com.arny.dnshostsgenerator.resolver.HttpProbeResult
import com.arny.dnshostsgenerator.resolver.HttpVerdict
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
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
                        "forwarded.example" to listOf("10.0.0.2"),
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

    /**
     * Частичное совпадение с check-DNS — рабочий случай, а не #forwarded: dns.comss.one на
     * chatgpt.com отвечает общим прокси провайдера и собственным адресом (замер 23.10.2026).
     */
    @Test
    fun generate_keepsProxyIpWhenOnlyPartOfAnswerCoincidesWithCheck() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "83.220.169.155" to mapOf("chatgpt.com" to listOf("80.74.30.162", "132.243.113.26")),
                    "8.8.8.8" to mapOf("chatgpt.com" to listOf("80.74.30.162")),
                ),
            ),
        )

        val result = generator.generate(
            presetTitle = "dns.comss.one",
            request = GenerateHostsRequest(
                primaryDns = "83.220.169.155",
                checkDns = "8.8.8.8",
                inputLines = listOf("chatgpt.com"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        // Адрес проверочного DNS отброшен, в hosts идёт собственный адрес пресета.
        assertEquals("132.243.113.26 chatgpt.com", result.outputText)
        assertEquals(0, result.stats.forwardedCount)
        assertEquals(1, result.stats.resolvedCount)
    }

    /** Адрес, подтверждённый TLS-проверкой, предпочитается первому адресу из ответа DNS. */
    @Test
    fun generate_prefersIpConfirmedByTlsValidator() = runBlocking {
        val validator = FakeIpValidator(confirmed = setOf("10.0.0.2"))
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "1.1.1.1" to mapOf("ai.example" to listOf("10.0.0.1", "10.0.0.2")),
                    "8.8.8.8" to mapOf("ai.example" to listOf("9.9.9.9")),
                ),
            ),
            ipValidator = validator,
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("ai.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        assertEquals("10.0.0.2 ai.example", result.outputText)
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), validator.checked)
    }

    /**
     * Медленный прокси: на быстрой проверке таймаут, на повторной — подтверждение.
     * Живой пример: 199.189.249.126 для gemini.google.com отдаёт TLS-хендшейк за ~10 с.
     */
    @Test
    fun generate_retriesSlowCandidateWithLongerTimeout() = runBlocking {
        val validator = FakeIpValidator(confirmed = setOf("10.0.0.9"))
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "1.1.1.1" to mapOf("gemini.example" to listOf("10.0.0.9")),
                    "8.8.8.8" to mapOf("gemini.example" to listOf("9.9.9.9")),
                ),
            ),
            ipValidator = validator,
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("gemini.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        assertEquals("10.0.0.9 gemini.example", result.outputText)
        assertEquals(1, result.stats.resolvedCount)
    }

    /**
     * Если ни один адрес не подтвердился, мёртвый адрес в hosts не пишется — домен уходит
     * в #unresolvedDomain: hosts с недостижимым IP хуже, чем честно пустой результат.
     * Живой пример: dns.comss.one отдаёт для gemini.google.com адрес, который не отвечает.
     */
    @Test
    fun generate_marksDomainUnresolvedWhenNoIpConfirmed() = runBlocking {
        val generator = HostsGenerator(
            dnsResolver = FakeDnsResolver(
                mapOf(
                    "1.1.1.1" to mapOf("ai.example" to listOf("10.0.0.1")),
                    "8.8.8.8" to mapOf("ai.example" to listOf("9.9.9.9")),
                ),
            ),
            ipValidator = FakeIpValidator(confirmed = emptySet()),
        )

        val result = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("ai.example"),
                outputFileName = "hosts.txt",
                dedupEnabled = false,
            ),
        )

        assertEquals("#unresolvedDomain ai.example", result.outputText)
        assertEquals(0, result.stats.resolvedCount)
        assertEquals(1, result.stats.unresolvedCount)
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

/**
 * Рабочий, но медленный адрес не должен попадать в hosts: скорость подтверждённого адреса
 * влияет на всё, что грузится с этого домена.
 */
class FastestIpSelectionTest {

    @Test
    fun picksFastestConfirmedIp() = runBlocking {
        val resolver = object : DnsResolver {
            // Проверочный DNS отдаёт другой адрес, иначе все кандидаты считаются «форвардом».
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int) =
                if (dnsServer == "8.8.8.8") listOf("9.9.9.9") else listOf("1.1.1.1", "2.2.2.2")

            override suspend fun resolveA(
                domain: String,
                dnsServer: String,
                timeoutMillis: Int,
                options: com.arny.dnshostsgenerator.resolver.DnsQueryOptions,
            ) = resolveA(domain, dnsServer, timeoutMillis)
        }
        val generator = HostsGenerator(resolver, DelayIpValidator(slowIp = "1.1.1.1"))

        val result = generator.generate(
            presetTitle = "test",
            request = GenerateHostsRequest(
                primaryDns = "9.9.9.9",
                checkDns = "8.8.8.8",
                checkDotHost = "dns.google",
                inputLines = listOf("chatgpt.com"),
                outputFileName = "host.txt",
                dedupEnabled = false,
                timeoutMillis = 2_000,
                ipValidationTimeoutMillis = 2_000,
                ipValidationRetryTimeoutMillis = 2_000,
            ),
        )

        val text = result.outputText
        assertTrue(text.contains("2.2.2.2"), "должен выбираться быстрый адрес: $text")
        assertFalse(text.contains("1.1.1.1"), "медленный адрес не должен попадать в hosts: $text")
    }
}

/**
 * Быстрый TLS-хендшейк не значит быструю отдачу: выбор идёт по времени HTTP-ответа.
 */
class ResponseTimeSelectionTest {

    @Test
    fun picksByHttpResponseTimeNotByHandshake() = runBlocking {
        val resolver = object : DnsResolver {
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int) =
                if (dnsServer == "8.8.8.8") listOf("9.9.9.9") else listOf("1.1.1.1", "2.2.2.2")

            override suspend fun resolveA(
                domain: String,
                dnsServer: String,
                timeoutMillis: Int,
                options: com.arny.dnshostsgenerator.resolver.DnsQueryOptions,
            ) = resolveA(domain, dnsServer, timeoutMillis)
        }
        val generator = HostsGenerator(
            dnsResolver = resolver,
            ipValidator = object : IpValidator {
                override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult {
                    // 1.1.1.1 отвечает на TLS быстрее…
                    if (ip == "2.2.2.2") delay(200)
                    return IpCheckResult.SERVES
                }
            },
            // …но HTTP-ответ быстрее у 2.2.2.2.
            httpProber = object : HttpProber {
                override suspend fun probe(
                    host: String,
                    ip: String,
                    path: String,
                    timeoutMillis: Int,
                ): HttpProbeResult = HttpProbeResult(
                    verdict = HttpVerdict.OK,
                    statusCode = 200,
                    elapsedMs = if (ip == "1.1.1.1") 900 else 120,
                )
            },
        )

        val result = generator.generate(
            presetTitle = "test",
            request = GenerateHostsRequest(
                primaryDns = "9.9.9.9",
                checkDns = "8.8.8.8",
                checkDotHost = "dns.google",
                inputLines = listOf("chatgpt.com"),
                outputFileName = "host.txt",
                dedupEnabled = false,
                timeoutMillis = 2_000,
                ipValidationTimeoutMillis = 2_000,
                ipValidationRetryTimeoutMillis = 2_000,
            ),
        )

        assertTrue(result.outputText.contains("2.2.2.2"), "выбор должен идти по HTTP: ${result.outputText}")
    }
}

/** Фейк: все адреса обслуживают домен, но один отвечает медленно. */
private class DelayIpValidator(private val slowIp: String) : IpValidator {
    override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult {
        if (ip == slowIp) delay(300)
        return IpCheckResult.SERVES
    }
}

/** Фейк TLS-проверки: «обслуживают домен» только перечисленные адреса. */
private class FakeIpValidator(
    private val confirmed: Set<String>,
    private val unknown: Set<String> = emptySet(),
) : IpValidator {
    val checked = mutableListOf<String>()

    override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult {
        if (ip !in checked) checked += ip
        return when {
            ip in confirmed -> IpCheckResult.SERVES
            ip in unknown -> IpCheckResult.UNKNOWN
            else -> IpCheckResult.REJECTED
        }
    }
}
