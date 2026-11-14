package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Временная живая проверка: профиль Gemini подбирает один адрес на dns.dns-ai.ru. */
class GeminiProfileLiveTest {

    @Test
    fun geminiProfileFindsAddress() = runTest {
        val preset = BuiltInDnsPresets.defaults.first { it.title.startsWith("dns.dns-ai.ru") }
        val checker = ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
        var result = checker.check(BuiltInAppProfiles.geminiAndroid, preset)
        if (!result.isUsable) {
            // Провайдер периодически не отвечает: одна повторная попытка, иначе тест
            // краснеет из-за сети, а не из-за кода.
            result = checker.check(BuiltInAppProfiles.geminiAndroid, preset)
        }
        println("Gemini: usable=${result.isUsable} ip=${result.ip} detail=${result.detail}")
        result.hostChecks.forEach { println("  ${it.host} -> ${it.tls} / ${it.http.verdict}") }
        assertTrue(result.isUsable, "профиль Gemini должен находить рабочий адрес")
    }
}
