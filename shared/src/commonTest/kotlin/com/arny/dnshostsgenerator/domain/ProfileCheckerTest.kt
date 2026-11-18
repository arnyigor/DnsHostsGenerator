package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.HttpProbeResult
import com.arny.dnshostsgenerator.resolver.HttpProber
import com.arny.dnshostsgenerator.resolver.HttpVerdict
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Подбор адреса для профиля: важен не «хоть один домен работает», а «все домены профиля
 * обслуживаются одним адресом». Ошибка здесь возвращает ровно ту ситуацию, которую
 * мы разбирали на телефоне: сайт открывается, приложение — нет.
 */
class ProfileCheckerTest {

    private val profile = AppProfile(
        id = "test",
        title = "Тестовый профиль",
        hosts = listOf("chatgpt.com", "android.chat.openai.com", "ws.chatgpt.com"),
    )

    private val preset = DnsProviderPreset(
        id = "p",
        title = "Пресет",
        primaryDns = "1.1.1.1",
        dotHost = "dns.example",
        outputFileName = "host.txt",
    )

    @Test
    fun check_picksIpThatServesEveryHost() = runBlocking {
        // Первый адрес проходит chatgpt.com, но получает отказ на android-хосте.
        val resolver = ProfileFakeResolver(
            profile.hosts.associateWith { listOf("1.1.1.1", "2.2.2.2") },
        )
        val validator = ProfileFakeValidator(serving = setOf("chatgpt.com", "android.chat.openai.com", "ws.chatgpt.com"))
        val prober = ProfileFakeProber(
            verdicts = mapOf(
                "chatgpt.com|1.1.1.1" to HttpVerdict.OK,
                "android.chat.openai.com|1.1.1.1" to HttpVerdict.REGION_BLOCKED,
                "ws.chatgpt.com|1.1.1.1" to HttpVerdict.OK,
                "chatgpt.com|2.2.2.2" to HttpVerdict.OK,
                "android.chat.openai.com|2.2.2.2" to HttpVerdict.OK,
                "ws.chatgpt.com|2.2.2.2" to HttpVerdict.OK,
            ),
        )
        val checker = ProfileChecker(resolver, validator, prober)

        val result = checker.check(profile, preset)

        assertEquals("2.2.2.2", result.ip, "должен выбраться адрес, годный для всех хостов")
        assertEquals(listOf("1.1.1.1", "2.2.2.2"), result.candidatesTried)
        assertTrue(result.hostChecks.all { it.isUsable })
    }

    @Test
    fun check_returnsNoIpWhenEveryCandidateIsRejected() = runBlocking {
        val resolver = ProfileFakeResolver(profile.hosts.associateWith { listOf("1.1.1.1") })
        val validator = ProfileFakeValidator(serving = setOf("chatgpt.com", "android.chat.openai.com", "ws.chatgpt.com"))
        val prober = ProfileFakeProber(
            verdicts = mapOf(
                "chatgpt.com|1.1.1.1" to HttpVerdict.OK,
                "android.chat.openai.com|1.1.1.1" to HttpVerdict.REGION_BLOCKED,
                "ws.chatgpt.com|1.1.1.1" to HttpVerdict.OK,
            ),
        )
        val checker = ProfileChecker(resolver, validator, prober)

        val result = checker.check(profile, preset)

        assertNull(result.ip, "региональный отказ нельзя считать рабочим адресом")
        assertFalse(result.isUsable)
        assertTrue(result.detail.orEmpty().contains("android.chat.openai.com=REGION_BLOCKED"))
    }

    @Test
    fun check_ignoresNullRoutesFromDns() = runBlocking {
        val resolver = ProfileFakeResolver(profile.hosts.associateWith { listOf("0.0.0.0", "1.1.1.1") })
        val validator = ProfileFakeValidator(serving = setOf("chatgpt.com", "android.chat.openai.com", "ws.chatgpt.com"))
        val prober = ProfileFakeProber(
            verdicts = mapOf(
                "chatgpt.com|1.1.1.1" to HttpVerdict.OK,
                "android.chat.openai.com|1.1.1.1" to HttpVerdict.OK,
                "ws.chatgpt.com|1.1.1.1" to HttpVerdict.OK,
            ),
        )

        val result = ProfileChecker(resolver, validator, prober).check(profile, preset)

        assertEquals("1.1.1.1", result.ip)
        assertEquals(listOf("1.1.1.1"), result.candidatesTried, "0.0.0.0 не должен попадать в кандидаты")
    }

    /** Проверено на телефоне: безобидный отказ Cloudflare не делает адрес непригодным. */
    @Test
    fun check_toleratesCloudflareRejectionOnAuthenticationHosts() = runBlocking {
        val resolver = ProfileFakeResolver(profile.hosts.associateWith { listOf("45.13.237.213") })
        val validator = ProfileFakeValidator(serving = profile.hosts.toSet())
        val prober = ProfileFakeProber(
            verdicts = mapOf(
                "chatgpt.com|45.13.237.213" to HttpVerdict.OK,
                "android.chat.openai.com|45.13.237.213" to HttpVerdict.CLOUDFLARE_BLOCKED,
                "ws.chatgpt.com|45.13.237.213" to HttpVerdict.OK,
            ),
        )

        val result = ProfileChecker(resolver, validator, prober).check(profile, preset)

        assertEquals("45.13.237.213", result.ip)
    }

    /**
     * Вход через auth.openai.com не должен ломать весь набор: домен, который прокси
     * не обслуживает, просто выпадает, остальные получают адрес.
     */
    @Test
    fun resolvePerHost_picksIpPerDomain() = runBlocking {
        val profile = AppProfile(
            id = "p",
            title = "Профиль",
            hosts = listOf("chatgpt.com", "auth.openai.com", "cdn.auth0.com"),
        )
        val resolver = ProfileFakeResolver(
            mapOf(
                "chatgpt.com" to listOf("1.1.1.1"),
                "auth.openai.com" to listOf("2.2.2.2"),
                "cdn.auth0.com" to listOf("3.3.3.3"),
            ),
        )
        val validator = ProfileFakeValidator(serving = setOf("chatgpt.com", "auth.openai.com"))
        val prober = ProfileFakeProber(
            verdicts = mapOf(
                "chatgpt.com|1.1.1.1" to HttpVerdict.OK,
                "auth.openai.com|2.2.2.2" to HttpVerdict.OK,
            ),
        )

        val rules = ProfileChecker(resolver, validator, prober).resolvePerHost(profile, preset)

        assertEquals(mapOf("chatgpt.com" to "1.1.1.1", "auth.openai.com" to "2.2.2.2"), rules.entries)
        assertEquals(listOf("cdn.auth0.com"), rules.failed, "недоступный домен попадает в список отказов")
    }

    @Test
    fun perHost_rejectsHttpFailureAndReportsProgress() = runBlocking {
        val checker = ProfileChecker(
            ProfileFakeResolver(profile.hosts.associateWith { listOf("1.1.1.1") }),
            ProfileFakeValidator(profile.hosts.toSet()),
            ProfileFakeProber(emptyMap()),
        )
        val progress = mutableListOf<Int>()
        val result = checker.resolvePerHost(profile, preset, onProgress = { done, total ->
            assertEquals(profile.hosts.size, total)
            progress += done
        })
        assertTrue(result.entries.isEmpty())
        assertEquals(profile.hosts, result.failed)
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun perHost_doesNotSwallowCancellation() = runBlocking {
        val resolver = object : DnsResolver {
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int): List<String> =
                throw kotlinx.coroutines.CancellationException("cancelled")
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int, options: DnsQueryOptions): List<String> =
                resolveA(domain, dnsServer, timeoutMillis)
        }
        var cancelled = false
        try {
            ProfileChecker(resolver, ProfileFakeValidator(emptySet()), ProfileFakeProber(emptyMap()))
                .resolvePerHost(profile, preset)
        } catch (error: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    /**
     * Регрессия: раньше пресет отбрасывался целиком, если не обслужил первые два домена,
     * и домены, которые он всё-таки обслуживает, оставались без адреса.
     */
    @Test
    fun merged_keepsPresetThatServedOnlyLaterHosts() = runBlocking {
        val profile = AppProfile(
            id = "m",
            title = "Профиль",
            hosts = listOf("first.example", "second.example", "third.example"),
        )
        // Отвечает только пресет с адресом 2.2.2.2; TLS проходит только для third.example.
        val resolver = object : DnsResolver {
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int): List<String> =
                if (dnsServer == "2.2.2.2") listOf("5.5.5.5") else emptyList()

            override suspend fun resolveA(
                domain: String,
                dnsServer: String,
                timeoutMillis: Int,
                options: DnsQueryOptions,
            ): List<String> = resolveA(domain, dnsServer, timeoutMillis)
        }
        val checker = ProfileChecker(
            resolver,
            ProfileFakeValidator(serving = setOf("third.example")),
            // Без HTTP-вердикта OK проба по умолчанию возвращает FAILED и адрес не годится.
            ProfileFakeProber(mapOf("third.example|5.5.5.5" to HttpVerdict.OK)),
        )

        val merged = checker.resolvePerHostMerged(
            profile = profile,
            presets = listOf(preset.copy(id = "b", primaryDns = "1.1.1.1"), preset.copy(id = "a", primaryDns = "2.2.2.2")),
            deadlineMillis = 30_000,
        )

        assertEquals(setOf("third.example"), merged.entries.keys)
        assertEquals(listOf("first.example", "second.example"), merged.failed)
        assertTrue(merged.unfinished.isEmpty(), "все домены успели проверить: ${merged.unfinished}")
    }

    /** Дедлайн не должен выдавать «не проверили» за «провайдер отказал». */
    @Test
    fun merged_reportsUnfinishedSeparatelyOnDeadline() = runBlocking {
        val profile = AppProfile(id = "d", title = "Профиль", hosts = listOf("slow.example"))
        val slow = object : DnsResolver {
            override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int): List<String> {
                kotlinx.coroutines.delay(2_000)
                return listOf("1.1.1.1")
            }

            override suspend fun resolveA(
                domain: String,
                dnsServer: String,
                timeoutMillis: Int,
                options: DnsQueryOptions,
            ): List<String> = resolveA(domain, dnsServer, timeoutMillis)
        }

        val merged = ProfileChecker(slow, ProfileFakeValidator(emptySet()), ProfileFakeProber(emptyMap()))
            .resolvePerHostMerged(profile, listOf(preset), deadlineMillis = 200)

        assertTrue(merged.entries.isEmpty())
        assertTrue(merged.failed.isEmpty(), "домен не проверен до конца, значит не отказ: ${merged.failed}")
        assertEquals(listOf("slow.example"), merged.unfinished)
        assertEquals(1, merged.totalHosts)
    }

    @Test
    fun rules_haveMappingAndBlockingLineForEveryHost() {
        val rules = buildPersonalDnsFilterRules(profile, "9.9.9.9")

        profile.hosts.forEach { host ->
            assertTrue(rules.contains(">$host 9.9.9.9"), "нет подмены для $host")
            assertTrue(
                rules.lines().any { it.trim() == host },
                "нет строки блокировки для $host — поддомены уйдут на настоящий адрес",
            )
        }
    }
}

private class ProfileFakeResolver(private val answers: Map<String, List<String>>) : DnsResolver {
    override suspend fun resolveA(domain: String, dnsServer: String, timeoutMillis: Int): List<String> =
        answers[domain].orEmpty()

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
        options: DnsQueryOptions,
    ): List<String> = resolveA(domain, dnsServer, timeoutMillis)
}

private class ProfileFakeValidator(private val serving: Set<String>) : IpValidator {
    override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult =
        if (domain in serving) IpCheckResult.SERVES else IpCheckResult.REJECTED
}

private class ProfileFakeProber(private val verdicts: Map<String, HttpVerdict>) : HttpProber {
    override suspend fun probe(host: String, ip: String, path: String, timeoutMillis: Int): HttpProbeResult =
        HttpProbeResult(verdicts["$host|$ip"] ?: HttpVerdict.FAILED)
}
