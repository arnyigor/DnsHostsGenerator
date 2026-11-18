package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Живая проверка слитого подбора: профиль Gemini на всех включённых пресетах.
 *
 * 14.11.2026: dns.bezmezhau.com (рабочий для ChatGPT) отдаёт для Gemini мёртвый
 * 143.20.64.55, а dns.dns-ai.ru — живой 181.215.40.49. Поэтому домены должны
 * собираться из разных пресетов, а не из одного.
 */
class GeminiProfileLiveTest {

    /**
     * runBlocking + Dispatchers.Default намеренно, а не runTest: под runTest время виртуальное
     * и дедлайн мержа срабатывает мгновенно, не успевая проверить ни одного домена.
     */
    @Test
    fun geminiProfileCollectsAddressesAcrossPresets() = runBlocking {
        withContext(Dispatchers.Default) {
            // Перебор без двухдоменного отсева медленнее: проверяются все 19 хостов на каждом
            // пресете, а живые запросы уходят в интернет.
            val presets = BuiltInDnsPresets.defaults.filter { it.enabledByDefault }
            val checker = ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
            val merged = checker.resolvePerHostMerged(
                profile = BuiltInAppProfiles.geminiAndroid,
                presets = presets,
                deadlineMillis = 180_000,
            )
            println(
                "Gemini: ok=${merged.entries.size} failed=${merged.failed.size} " +
                    "unfinished=${merged.unfinished.size} presets=${merged.presetTitles} ms=${merged.elapsedMs}",
            )
            merged.entries.forEach { (host, ip) -> println("  $host -> $ip") }
            assertTrue(merged.entries.isNotEmpty(), "подбор должен дать хотя бы один рабочий адрес")
            assertTrue(
                merged.entries.keys.contains("gemini.google.com") ||
                    "gemini.google.com" in merged.unfinished,
                "gemini.google.com должен быть либо подобран, либо признан непроверенным: ${merged.failed}",
            )
        }
    }
}
