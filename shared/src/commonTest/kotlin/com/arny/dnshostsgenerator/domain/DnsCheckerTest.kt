package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверка DNS-серверов до генерации hosts: вердикт должен совпадать с тем, что потом
 * делает генератор — «работает» только если выданный адрес подтверждён TLS.
 */
class DnsCheckerTest {

    private val presets = listOf(
        preset("ok", "OK DNS", "1.1.1.1"),
        preset("null", "Null DNS", "2.2.2.2"),
        preset("dead", "Dead DNS", "3.3.3.3"),
        preset("tls", "TLS DNS", "4.4.4.4"),
    )

    @Test
    fun check_classifiesPresetsAndPutsWorkingFirst() = runBlocking {
        val checker = DnsChecker(
            dnsResolver = FakeResolver(
                answers = mapOf(
                    "1.1.1.1" to listOf("10.0.0.1"),
                    "2.2.2.2" to listOf("0.0.0.0"),
                    "4.4.4.4" to listOf("10.0.0.4"),
                    "8.8.8.8" to listOf("10.0.0.9"),
                ),
                failFor = setOf("3.3.3.3"),
            ),
            ipValidator = FakeValidator(serving = setOf("10.0.0.1")),
        )

        val results = checker.check(presets, domain = "gemini.google.com")

        assertEquals(DnsCheckStatus.Works, results.first().status)
        assertEquals("ok", results.first().presetId)
        assertEquals("10.0.0.1", results.first().ip)

        val byId = results.associateBy { it.presetId }
        assertEquals(DnsCheckStatus.Blocked, byId.getValue("null").status)
        assertEquals(DnsCheckStatus.Unreachable, byId.getValue("dead").status)
        assertEquals(DnsCheckStatus.TlsFailed, byId.getValue("tls").status)
        // Рабочие серверы идут перед нерабочими.
        assertTrue(results.takeWhile { it.status == DnsCheckStatus.Works }.isNotEmpty())
    }

    /** Сервер вернул те же адреса, что обычный DNS → он ничего не проксирует. */
    @Test
    fun check_marksSameAsProviderWhenAnswerMatchesReference() = runBlocking {
        val checker = DnsChecker(
            dnsResolver = FakeResolver(
                answers = mapOf(
                    "1.1.1.1" to listOf("142.251.150.2"),
                    "8.8.8.8" to listOf("142.251.150.2"),
                ),
            ),
            ipValidator = FakeValidator(serving = setOf("142.251.150.2")),
        )

        val results = checker.check(
            listOf(preset("same", "Same DNS", "1.1.1.1")),
            domain = "gemini.google.com",
        )

        assertEquals(DnsCheckStatus.SameAsProvider, results.single().status)
    }

    @Test
    fun check_treatsEmptyAnswerAsUnreachable() = runBlocking {
        val checker = DnsChecker(
            dnsResolver = FakeResolver(answers = mapOf("1.1.1.1" to emptyList())),
            ipValidator = FakeValidator(serving = emptySet()),
        )

        val results = checker.check(listOf(preset("ok", "OK DNS", "1.1.1.1")), "gemini.google.com")

        assertEquals(DnsCheckStatus.Unreachable, results.single().status)
        assertEquals("DoT недоступен или нет A-записей", results.single().detail)
    }

    @Test
    fun check_usesDotOptionsFromPreset() = runBlocking {
        val resolver = FakeResolver(answers = mapOf("5.5.5.5" to listOf("10.0.0.1")))
        val checker = DnsChecker(resolver, FakeValidator(serving = setOf("10.0.0.1")))
        val preset = DnsProviderPreset(
            id = "dot",
            title = "DoT DNS",
            primaryDns = "5.5.5.5",
            dotHost = "dns.example",
            allowInvalidTls = true,
            outputFileName = "hosts.txt",
        )

        checker.check(listOf(preset), "chatgpt.com")

        assertEquals(
            DnsQueryOptions(dotHost = "dns.example", allowInvalidTls = true, allowPlainFallback = false),
            resolver.lastOptions,
        )
        assertEquals("chatgpt.com", resolver.lastDomain)
    }

    private fun preset(id: String, title: String, dns: String) = DnsProviderPreset(
        id = id,
        title = title,
        primaryDns = dns,
        outputFileName = "$id.txt",
    )
}

private class FakeResolver(
    private val answers: Map<String, List<String>>,
    private val failFor: Set<String> = emptySet(),
) : DnsResolver {
    var lastOptions: DnsQueryOptions? = null
    var lastDomain: String? = null

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
        lastDomain = domain
        lastOptions = options
        if (dnsServer in failFor) throw IllegalStateException("timeout")
        return answers[dnsServer].orEmpty()
    }
}

private class FakeValidator(private val serving: Set<String>) : IpValidator {
    override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult =
        if (ip in serving) IpCheckResult.SERVES else IpCheckResult.REJECTED
}
