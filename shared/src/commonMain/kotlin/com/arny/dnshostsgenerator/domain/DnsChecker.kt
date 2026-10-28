package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

/** Итог проверки одного DNS-сервера. */
enum class DnsCheckStatus {
    /** DNS ответил, адрес подтверждён TLS с сертификатом на проверяемый домен. */
    Works,

    /** DNS ответил 0.0.0.0 — домен обнуляется этим сервером. */
    Blocked,

    /**
     * Сервер вернул те же адреса, что и обычный (провайдерский) DNS — он ничего не проксирует.
     * Живой пример: disabled-пресеты отвечают через UDP-фолбэк адресами 142.251.15x.2.
     */
    SameAsProvider,

    /** DNS ответил, но адрес не обслуживает домен (TLS не прошёл). */
    TlsFailed,

    /** DNS не отвечает: DoT/UDP недоступны. */
    Unreachable,
}

data class DnsCheckResult(
    val presetId: String,
    val title: String,
    val dnsAddress: String,
    val status: DnsCheckStatus,
    val elapsedMs: Long,
    val ip: String? = null,
    val allIps: List<String> = emptyList(),
    val detail: String? = null,
)

/**
 * Массовая проверка DNS-серверов: отвечает ли сервер на запрос домена и обслуживает ли
 * выданный адрес этот домен (TLS + SNI).
 *
 * Аналог внешних чекеров, но на нашем транспорте (DoT с fallback на UDP:53) и с той же
 * TLS-проверкой, что использует генерация hosts — поэтому вердикт совпадает с её результатом.
 */
class DnsChecker(
    private val dnsResolver: DnsResolver,
    private val ipValidator: IpValidator,
) {
    private val nullRouteAddresses = setOf("0.0.0.0", "::", "0:0:0:0:0:0:0:0")

    suspend fun check(
        presets: List<DnsProviderPreset>,
        domain: String,
        timeoutMillis: Int = 5_000,
        tlsTimeoutMillis: Int = 4_000,
    ): List<DnsCheckResult> = coroutineScope {
        val referenceIps = referenceIps(presets, domain, timeoutMillis)
        val semaphore = Semaphore(MAX_CONCURRENT_CHECKS)
        presets
            .map { preset ->
                async {
                    semaphore.withPermit {
                        checkOne(preset, domain, referenceIps, timeoutMillis, tlsTimeoutMillis)
                    }
                }
            }
            .awaitAll()
            .sortedWith(compareBy({ it.status != DnsCheckStatus.Works }, { it.elapsedMs }))
    }

    /**
     * Адреса «обычного» DNS (checkDns пресетов, по умолчанию 8.8.8.8 через DoT). С ними
     * сравнивается ответ: если совпал — сервер просто форвардит, а не проксирует домен.
     */
    private suspend fun referenceIps(
        presets: List<DnsProviderPreset>,
        domain: String,
        timeoutMillis: Int,
    ): List<String> {
        val checkDns = presets.firstOrNull()?.checkDns ?: return emptyList()
        return try {
            dnsResolver.resolveA(
                domain = domain,
                dnsServer = checkDns,
                timeoutMillis = timeoutMillis,
                options = DnsQueryOptions(dotHost = "dns.google", allowInvalidTls = false),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyList()
        }
    }

    private suspend fun checkOne(
        preset: DnsProviderPreset,
        domain: String,
        referenceIps: List<String>,
        timeoutMillis: Int,
        tlsTimeoutMillis: Int,
    ): DnsCheckResult {
        val startedAt = TimeSource.Monotonic.markNow()
        fun result(
            status: DnsCheckStatus,
            ip: String? = null,
            allIps: List<String> = emptyList(),
            detail: String? = null,
        ) = DnsCheckResult(
            presetId = preset.id,
            title = preset.title,
            dnsAddress = preset.dotHost ?: preset.primaryDns,
            status = status,
            elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
            ip = ip,
            allIps = allIps,
            detail = detail,
        )

        val ips = try {
            dnsResolver.resolveA(
                domain = domain,
                dnsServer = preset.primaryDns,
                timeoutMillis = timeoutMillis,
                options = DnsQueryOptions(
                    dotHost = preset.dotHost,
                    allowInvalidTls = preset.allowInvalidTls,
                    allowPlainFallback = false,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return result(
                DnsCheckStatus.Unreachable,
                detail = error.message ?: error::class.simpleName,
            )
        }

        if (ips.isEmpty()) {
            return result(DnsCheckStatus.Unreachable, detail = "DoT недоступен или нет A-записей")
        }
        if (ips.all { it in nullRouteAddresses }) {
            return result(DnsCheckStatus.Blocked, allIps = ips, detail = "сервер вернул 0.0.0.0")
        }
        if (referenceIps.isNotEmpty() && ips.all { it in referenceIps }) {
            return result(
                DnsCheckStatus.SameAsProvider,
                allIps = ips,
                detail = "адреса совпали с обычным DNS — домен не проксируется",
            )
        }

        // Проверяем не больше двух адресов: у проксирующих DNS остальные ведут на тот же узел.
        // Адреса, совпавшие с обычным DNS, проверяем последними.
        val candidates = ips.distinct().sortedBy { it in referenceIps }.take(MAX_CHECKED_IPS)
        for (ip in candidates) {
            val check = ipValidator.check(domain, ip, tlsTimeoutMillis)
            if (check == IpCheckResult.SERVES) {
                return result(DnsCheckStatus.Works, ip = ip, allIps = ips)
            }
        }
        return result(
            DnsCheckStatus.TlsFailed,
            allIps = ips,
            detail = "TLS не подтвердил домен для ${candidates.joinToString()}",
        )
    }

    private companion object {
        const val MAX_CONCURRENT_CHECKS = 6
        const val MAX_CHECKED_IPS = 2
    }
}
