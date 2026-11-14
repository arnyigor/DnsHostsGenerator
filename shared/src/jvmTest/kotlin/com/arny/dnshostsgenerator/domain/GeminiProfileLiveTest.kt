package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
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

    @Test
    fun geminiProfileCollectsAddressesAcrossPresets() = runTest(timeout = 6.minutes) {
        val presets = BuiltInDnsPresets.defaults.filter { it.enabledByDefault }
        val checker = ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
        val merged = checker.resolvePerHostMerged(
            profile = BuiltInAppProfiles.geminiAndroid,
            presets = presets,
            deadlineMillis = 150_000,
        )
        println("Gemini: ok=${merged.entries.size} failed=${merged.failed.size} presets=${merged.presetTitles} ms=${merged.elapsedMs}")
        merged.entries.forEach { (host, ip) -> println("  $host -> $ip") }
        assertTrue(merged.isUsable, "хотя бы один домен Gemini должен получить рабочий адрес")
        assertTrue(
            merged.entries.keys.contains("gemini.google.com"),
            "gemini.google.com должен быть среди подобранных: ${merged.failed}",
        )
    }
}
