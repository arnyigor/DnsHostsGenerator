package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.generator.HostsGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Системные домены не должны попадать в hosts даже если они есть в группе.
 *
 * Проверено живым DoT-запросом 14.11.2026: пресет отдаёт прокси-адрес для
 * `clientservices.googleapis.com` и `o.pki.goog`, а это ломает push и проверку сертификатов.
 */
class ReservedDomainsTest {

    private class FakeResolver(
        private val records: Map<String, Map<String, List<String>>>,
    ) : com.arny.dnshostsgenerator.resolver.DnsResolver {
        override suspend fun resolveA(
            domain: String,
            dnsServer: String,
            timeoutMillis: Int,
        ): List<String> = records[dnsServer]?.get(domain).orEmpty()
    }

    @Test
    fun systemEndpointsAreReserved() {
        listOf(
            "clientservices.googleapis.com",
            "android.googleapis.com",
            "mtalk.google.com",
            "o.pki.goog",
            "connectivitycheck.gstatic.com",
            "www.msftconnecttest.com",
        ).forEach { domain ->
            assertTrue(ReservedDomains.isReserved(domain), "$domain должен быть защищён")
        }
    }

    @Test
    fun matchingIgnoresCaseAndTrailingDot() {
        assertTrue(ReservedDomains.isReserved("CLIENTSERVICES.googleapis.com"))
        assertTrue(ReservedDomains.isReserved("o.pki.goog."))
        assertTrue(ReservedDomains.isReserved("  o.pki.goog  "))
    }

    @Test
    fun aiEndpointsAreNotReserved() {
        // Иначе фильтр вырежет то, ради чего приложение существует.
        listOf(
            "gemini.google.com",
            "aistudio.google.com",
            "generativelanguage.googleapis.com",
            "alkalicore-pa.clients6.google.com",
            "api.openai.com",
            "claude.ai",
        ).forEach { domain ->
            assertFalse(ReservedDomains.isReserved(domain), "$domain не должен блокироваться")
        }
    }

    @Test
    fun geminiGroupHasNoSystemEndpoints() {
        val gemini = DomainSeed.groups().first { it.name.contains("Gemini", ignoreCase = true) }
        val offending = gemini.domains.filter { ReservedDomains.isReserved(it) }
        assertTrue(offending.isEmpty(), "в группе остались системные домены: $offending")
    }

    @Test
    fun generatorSkipsSystemDomainAndKeepsOutput() = runTest {
        val generator = HostsGenerator(
            dnsResolver = FakeResolver(
                records = mapOf(
                    "1.1.1.1" to mapOf(
                        "gemini.google.com" to listOf("10.0.0.5"),
                        "clientservices.googleapis.com" to listOf("10.0.0.6"),
                    ),
                    "8.8.8.8" to mapOf(
                        "gemini.google.com" to listOf("8.8.8.8"),
                        "clientservices.googleapis.com" to listOf("8.8.8.8"),
                    ),
                ),
            ),
        )

        val result: GenerationResult = generator.generate(
            presetTitle = "Test DNS",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                checkDns = "8.8.8.8",
                inputLines = listOf("gemini.google.com", "clientservices.googleapis.com"),
                outputFileName = "hosts_test.txt",
                dedupEnabled = true,
            ),
        )

        val text = result.outputText
        assertTrue(text.contains("10.0.0.5 gemini.google.com"), "рабочий домен должен остаться: $text")
        assertFalse(text.contains("10.0.0.6"), "системный домен не должен получить адрес: $text")
        assertTrue(text.contains("# system: clientservices.googleapis.com"), "строка должна быть помечена: $text")
        assertEquals(1, result.stats.skippedCount)
    }
}
