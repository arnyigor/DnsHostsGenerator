package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.generator.HostsGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Сквозная живая проверка: реальный DNS пресета + реальная TLS-проверка адресов.
 * Гоняется только явно, сеть обязательна:
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :shared:jvmTest --tests "*LiveGenerationValidationTest*" --rerun-tasks
 */
class LiveGenerationValidationTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    /** Замер времени: валидация адресов не должна растягивать генерацию на десятки секунд. */
    @Test
    fun generationOfFullDefaultListFitsTimeBudget() = runBlocking {
        if (!enabled) {
            println("LiveGenerationValidationTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        // Пресет можно выбрать через DNSHOSTS_PRESET (id из BuiltInDnsPresets), по умолчанию рекомендуемый.
        val presetId = System.getenv("DNSHOSTS_PRESET") ?: BuiltInDnsPresets.RECOMMENDED_ID
        val preset = BuiltInDnsPresets.defaults.first { it.id == presetId }
        val domains = DefaultDomainList.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .take(30)
            .toList()
        val generator = HostsGenerator(createDnsResolver(), createIpValidator(), createHttpProber())

        val started = System.currentTimeMillis()
        val result = generator.generate(
            presetTitle = preset.title,
            request = GenerateHostsRequest(
                primaryDns = preset.primaryDns,
                primaryDotHost = preset.dotHost,
                primaryAllowInvalidTls = preset.allowInvalidTls,
                checkDns = preset.checkDns,
                checkDotHost = "dns.google",
                inputLines = domains,
                outputFileName = "hosts.txt",
                dedupEnabled = true,
            ),
        )
        val elapsed = System.currentTimeMillis() - started
        println("TIME: доменов=${domains.size}, прошло=${elapsed} мс, статистика=${result.stats}")
        assertTrue(elapsed < 20_000, "генерация ${domains.size} доменов заняла $elapsed мс")
    }

    @Test
    fun generationWritesOnlyTlsConfirmedIps() = runBlocking {
        if (!enabled) {
            println("LiveGenerationValidationTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        // Пресет можно выбрать через DNSHOSTS_PRESET (id из BuiltInDnsPresets), по умолчанию рекомендуемый.
        val presetId = System.getenv("DNSHOSTS_PRESET") ?: BuiltInDnsPresets.RECOMMENDED_ID
        val preset = BuiltInDnsPresets.defaults.first { it.id == presetId }
        val validator = createIpValidator()
        val generator = HostsGenerator(createDnsResolver(), validator)

        val result = generator.generate(
            presetTitle = preset.title,
            request = GenerateHostsRequest(
                primaryDns = preset.primaryDns,
                primaryDotHost = preset.dotHost,
                primaryAllowInvalidTls = preset.allowInvalidTls,
                checkDns = preset.checkDns,
                checkDotHost = "dns.google",
                inputLines = listOf("chatgpt.com", "gemini.google.com", "api.anthropic.com"),
                outputFileName = "hosts.txt",
                dedupEnabled = true,
            ),
        )

        println("== ${preset.title}")
        println(result.outputText)
        println(result.stats)

        val resolved = result.lines.filterIsInstance<HostLine.Resolved>()
        assertTrue(resolved.isNotEmpty(), "ни один домен не разрешён пресетом ${preset.primaryDns}")
        resolved.forEach { line ->
            assertTrue(
                validator.check(line.domain, line.ip) == IpCheckResult.SERVES,
                "в hosts попал неподтверждённый адрес: ${line.ip} для ${line.domain}",
            )
        }
        println("подтверждено адресов: ${resolved.size}")
    }
}
