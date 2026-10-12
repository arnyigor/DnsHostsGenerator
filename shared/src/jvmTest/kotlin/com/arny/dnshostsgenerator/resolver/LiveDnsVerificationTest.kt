package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.domain.DnsPresetUnusableException
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.generator.HostsGenerator
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Живая проверка DNS-логики: то, что не проверить фейками — реальный TLS/DoT, fallback на
 * plain DNS и соответствие пресетов заявленному поведению (`allowInvalidTls`, `dotHost`).
 *
 * Обычные прогоны не трогает: запуск только явно.
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :shared:jvmTest --tests "*LiveDnsVerificationTest*" --rerun-tasks
 *
 * Отчёт (включая замеры времени) пишется в `build/live-dns-report.txt`.
 */
class LiveDnsVerificationTest {

    private val report = StringBuilder()
    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    private fun line(text: String) {
        report.appendLine(text)
    }

    private fun finish() {
        val file = File("build/live-dns-report.txt")
        file.parentFile?.mkdirs()
        file.writeText(report.toString())
        println(report.toString())
    }

    @Test
    fun liveDnsVerification() = runBlocking {
        if (!enabled) {
            println("LiveDnsVerificationTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        val resolver = createDnsResolver()
        val failures = mutableListOf<String>()

        line("== 1. Пресеты: резолв через приложение (DoT + fallback) ==")
        line(String.format("%-34s %-7s %-18s %-26s %s", "пресет", "мс", "IP", "ошибка", "strict-TLS к dotHost:853"))
        BuiltInDnsPresets.defaults.forEach { preset ->
            val started = System.currentTimeMillis()
            val attempt = runCatching {
                resolver.resolveA(
                    domain = "chatgpt.com",
                    dnsServer = preset.primaryDns,
                    timeoutMillis = 5_000,
                    options = DnsQueryOptions(preset.dotHost, preset.allowInvalidTls),
                )
            }
            val elapsed = System.currentTimeMillis() - started
            val ips = attempt.getOrDefault(emptyList())
            val error = attempt.exceptionOrNull()?.let { it::class.simpleName ?: "?" } ?: ""
            val handshake = strictHandshake(preset.dotHost ?: preset.primaryDns)
            line(
                String.format(
                    "%-34s %-7d %-18s %-26s %s",
                    preset.title.take(33),
                    elapsed,
                    ips.firstOrNull() ?: "—",
                    error,
                    handshake,
                ),
            )
            if (ips.isEmpty()) failures += "пресет ${preset.title}: 0 IP (${error.ifEmpty { "без ошибки" }})"
        }

        line("")
        line("== 2. Fallback на plain DNS, когда DoT недоступен (dotHost=127.0.0.1) ==")
        val fallbackIps = resolver.resolveA(
            domain = "example.com",
            dnsServer = "1.1.1.1",
            timeoutMillis = 5_000,
            options = DnsQueryOptions(dotHost = "127.0.0.1"),
        )
        line("example.com через 1.1.1.1 с мёртвым DoT: ${fallbackIps.joinToString()} (пусто = fallback сломан)")
        if (fallbackIps.isEmpty()) failures += "fallback на plain DNS не сработал"

        line("")
        line("== 3. Недостижимый проверочный DNS: как классифицируется домен ==")
        val failOpen = HostsGenerator(resolver).generate(
            presetTitle = "manual",
            request = GenerateHostsRequest(
                primaryDns = "1.1.1.1",
                primaryDotHost = "cloudflare-dns.com",
                checkDns = "192.0.2.1", // TEST-NET-1, гарантированно недостижим
                checkDotHost = "192.0.2.1",
                inputLines = listOf("example.com"),
                outputFileName = "live.txt",
                dedupEnabled = true,
                timeoutMillis = 1_500,
            ),
        )
        val failOpenLine = failOpen.lines.single()
        line("check DNS недоступен → ${failOpenLine::class.simpleName}")
        line("статистика: resolved=${failOpen.stats.resolvedCount}, forwarded=${failOpen.stats.forwardedCount}, unresolved=${failOpen.stats.unresolvedCount}, suspiciousForwarding=${failOpen.stats.suspiciousForwarding}")
        line("вывод: на сети с прозрачным перехватом DNS запрос даже к 192.0.2.1 получает ответ,")
        line("        поэтому «проверочный» резолвер перестаёт быть независимым и всё уходит в #forwarded")
        if (!failOpen.stats.suspiciousForwarding) {
            failures += "при недостоверном check DNS не взведён флаг suspiciousForwarding"
        }

        line("")
        line("== 4. Классификация на живых данных: правило «есть общий IP → #forwarded» ==")
        listOf("example.com", "chatgpt.com", "www.google.com").forEach { domain ->
            val primary = resolver.resolveA(
                domain, "1.1.1.1", 5_000, DnsQueryOptions("cloudflare-dns.com"),
            )
            val check = resolver.resolveA(
                domain, "8.8.8.8", 5_000, DnsQueryOptions("dns.google"),
            )
            val expectsForwarded = check.isNotEmpty() && check.any { it in primary }
            val result = HostsGenerator(resolver).generate(
                presetTitle = "manual",
                request = GenerateHostsRequest(
                    primaryDns = "1.1.1.1",
                    primaryDotHost = "cloudflare-dns.com",
                    checkDns = "8.8.8.8",
                    checkDotHost = "dns.google",
                    inputLines = listOf(domain),
                    outputFileName = "live.txt",
                    dedupEnabled = true,
                ),
            )
            val actual = result.lines.single()
            val actualForwarded = actual is HostLine.Forwarded
            line(
                "$domain: primary=${primary.joinToString()} check=${check.joinToString()} " +
                    "→ ${actual::class.simpleName} (правило: ${if (expectsForwarded) "Forwarded" else "Resolved"})",
            )
            if (actualForwarded != expectsForwarded) {
                failures += "$domain: классификация ${actual::class.simpleName}, а правило давало forwarded=$expectsForwarded"
            }
        }

        line("")
        line("== 6. Пресет с null-route (xbox-dns.ru): генерация должна быть отменена ==")
        val xbox = BuiltInDnsPresets.defaults.first { it.primaryDns == "176.99.11.77" }
        val rawIps = resolver.resolveA(
            domain = "chatgpt.com",
            dnsServer = xbox.primaryDns,
            timeoutMillis = 5_000,
            options = DnsQueryOptions(xbox.dotHost, xbox.allowInvalidTls),
        )
        val nullRouted = rawIps.isNotEmpty() && rawIps.all { it == "0.0.0.0" || it == "::" }
        line("сырой ответ xbox-dns.ru на chatgpt.com: ${rawIps.joinToString()} → null-route=$nullRouted")
        if (nullRouted) {
            val attempt = runCatching {
                HostsGenerator(resolver).generate(
                    presetTitle = xbox.title,
                    request = GenerateHostsRequest(
                        primaryDns = xbox.primaryDns,
                        primaryDotHost = xbox.dotHost,
                        checkDns = "8.8.8.8",
                        checkDotHost = "dns.google",
                        inputLines = listOf("chatgpt.com", "openai.com"),
                        outputFileName = "live.txt",
                        dedupEnabled = true,
                    ),
                )
            }
            val error = attempt.exceptionOrNull()
            line(
                "реакция генератора: " + if (error == null) {
                    "НЕ отменена, результат=${attempt.getOrNull()?.stats}"
                } else {
                    "${error::class.simpleName}: ${error.message}"
                },
            )
            if (error !is DnsPresetUnusableException) {
                failures += "пресет с null-route не отменил генерацию"
            }
        } else {
            line("пресет сейчас отвечает нормально — проверка отмены неприменима")
        }

        line("")
        line("== 5. Стабильность выбора IP: 3 прогона одного домена ==")
        repeat(3) { attempt ->
            val ips = resolver.resolveA("www.google.com", "1.1.1.1", 5_000, DnsQueryOptions("cloudflare-dns.com"))
            line("  прогон ${attempt + 1}: ${ips.joinToString()}")
        }
        line("(если порядок/состав меняется — hosts для такого домена нестабилен между запусками)")

        line("")
        line("== ИТОГ: ${if (failures.isEmpty()) "все проверки пройдены" else "проблем " + failures.size} ==")
        failures.forEach { line("  ! $it") }
        finish()
        assertTrue(failures.isEmpty(), "живые проверки DNS: $failures")
    }

    /** Строгий TLS-хендшейк к 853 с проверкой hostname — так же, как это делает резолвер. */
    private fun strictHandshake(host: String): String = runCatching {
        val socket = SSLSocketFactory.getDefault().createSocket(host, 853) as SSLSocket
        try {
            socket.soTimeout = 5_000
            socket.sslParameters = SSLParameters().apply { endpointIdentificationAlgorithm = "HTTPS" }
            socket.startHandshake()
            "OK"
        } finally {
            runCatching { socket.close() }
        }
    }.getOrElse { error -> "FAIL: ${error::class.simpleName}" }
}
