package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Живой подбор адреса для профиля ChatGPT на реальной сети:
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :shared:jvmTest --tests "*LiveProfileCheckTest*" --rerun-tasks
 */
class LiveProfileCheckTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    @Test
    fun chatGptProfile_picksAddressAndPrintsRules() = runBlocking {
        if (!enabled) {
            println("LiveProfileCheckTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        val checker = ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
        val profile = BuiltInAppProfiles.chatGptAndroid
        val presets = BuiltInDnsPresets.defaults.filter { it.id in setOf("dns_eu_geohide_ru", "dns_zapret_moe") }
        presets.forEach { preset ->
            val result = checker.check(profile, preset)
            println(
                "== ${result.presetTitle}: ip=${result.ip} за ${result.elapsedMs} мс, " +
                    "кандидаты=${result.candidatesTried}",
            )
            result.hostChecks.forEach { check ->
                println("   %-32s tls=%-8s http=%-18s".format(check.host, check.tls, check.http.verdict))
            }
            result.ip?.let { println(buildPersonalDnsFilterRules(profile, it)) }
            result.detail?.let { println("   detail: $it") }
        }
    }
}
