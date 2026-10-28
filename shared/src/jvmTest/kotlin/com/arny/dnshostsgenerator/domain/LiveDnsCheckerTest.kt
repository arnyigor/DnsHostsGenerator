package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Живая проверка чекера на всех пресетах (сеть обязательна):
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :shared:jvmTest --tests "*LiveDnsCheckerTest*" --rerun-tasks
 */
class LiveDnsCheckerTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    @Test
    fun checkAllPresetsOnRealNetwork() = runBlocking {
        if (!enabled) {
            println("LiveDnsCheckerTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        val checker = DnsChecker(createDnsResolver(), createIpValidator())
        val results = checker.check(
            presets = BuiltInDnsPresets.defaults,
            domain = "gemini.google.com",
        )
        println("== проверка DNS: gemini.google.com")
        results.forEach { r ->
            println(
                "%-28s %-18s %5d мс  %s  %s".format(
                    r.title.take(28),
                    r.status,
                    r.elapsedMs,
                    r.ip ?: r.allIps.firstOrNull().orEmpty(),
                    r.certificate?.let {
                        "cert до ${CertDates.formatDateUtc(it.notAfterEpochMillis)} (${it.issuer.take(40)})"
                    }.orEmpty(),
                ),
            )
        }
        assertTrue(
            results.any { it.status == DnsCheckStatus.Works },
            "ни один пресет не признан рабочим: ${results.map { it.status }}",
        )
    }
}
