package com.arny.dnshostsgenerator.domain

import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.HttpProbeResult
import com.arny.dnshostsgenerator.resolver.HttpProber
import com.arny.dnshostsgenerator.resolver.HttpVerdict
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

/** Проверка одного домена профиля на выбранном адресе. */
data class ProfileHostCheck(
    val host: String,
    val tls: IpCheckResult,
    val http: HttpProbeResult,
) {
    /**
     * Домен считается обслуженным, если прошёл TLS и сервис не отказал по региону.
     *
     * [HttpVerdict.CLOUDFLARE_BLOCKED] и [HttpVerdict.CHALLENGE] не дисквалифицируют адрес:
     * измерено 31.10.2026 на телефоне — приложение ChatGPT работает через 45.13.237.213,
     * хотя анонимный запрос к `android.chat.openai.com` с этого адреса получает
     * `Request is not allowed`, а `ab.chatgpt.com` отдаёт антибот-страницу.
     * Региональный отказ ([HttpVerdict.REGION_BLOCKED]) — единственный вердикт,
     * который означает, что адрес не годится.
     */
    val isUsable: Boolean
        get() = tls == IpCheckResult.SERVES &&
            http.verdict != HttpVerdict.REGION_BLOCKED &&
            http.verdict != HttpVerdict.FAILED
}

/**
 * Итог подбора адреса для профиля приложения.
 *
 * [ip] заполнен только когда один и тот же адрес прошёл проверку для ВСЕХ доменов профиля —
 * подменять часть доменов на разные адреса нельзя: приложение ходит в несколько хостов
 * и ломается, если хотя бы один из них отвечает отказом.
 */
data class ProfileCheckResult(
    val profileId: String,
    val profileTitle: String,
    val presetId: String,
    val presetTitle: String,
    val ip: String?,
    val hostChecks: List<ProfileHostCheck>,
    val candidatesTried: List<String>,
    val elapsedMs: Long,
    val detail: String? = null,
) {
    val isUsable: Boolean get() = ip != null
}

/**
 * Подбор одного адреса, который обслуживает все домены профиля.
 *
 * Порядок: резолв всех доменов через выбранный DNS → объединение адресов → для каждого адреса
 * TLS-проверка и HTTP-проверка по каждому домену → первый адрес без отказов становится итогом.
 * HTTP-уровень обязателен: 31.10.2026 измерено, что прокси-адреса проходят TLS для
 * `android.chat.openai.com`, но получают `Request is not allowed`, а настоящие адреса
 * Cloudflare — `unsupported_country_region_territory`. Без этого различия подбор врёт.
 */
class ProfileChecker(
    internal val dnsResolver: DnsResolver,
    internal val ipValidator: IpValidator,
    internal val httpProber: HttpProber,
    private val maxCandidateIps: Int = 3,
) {

    suspend fun check(
        profile: AppProfile,
        preset: DnsProviderPreset,
        timeoutMillis: Int = 5_000,
        tlsTimeoutMillis: Int = 6_000,
    ): ProfileCheckResult {
        val startedAt = TimeSource.Monotonic.markNow()
        var resolveError: String? = null
        val resolved = coroutineScope {
            profile.hosts.map { host ->
                async {
                    host to runCatching {
                        dnsResolver.resolveA(
                            domain = host,
                            dnsServer = preset.primaryDns,
                            timeoutMillis = timeoutMillis,
                            options = DnsQueryOptions(
                                dotHost = preset.dotHost,
                                allowInvalidTls = preset.allowInvalidTls,
                                allowPlainFallback = false,
                            ),
                        )
                    }.onFailure { error ->
                        // Ошибку DNS показываем в итоговом detail: иначе «не вернул адреса»
                        // выглядит одинаково и для таймаута, и для неверного конфига пресета.
                        if (resolveError == null) {
                            resolveError = "$host: ${error::class.simpleName}: ${error.message}"
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }

        // Адрес, который DNS отдаёт для большинства доменов профиля, проверяем первым.
        val ips = resolved
            .flatMap { it.second }
            .filterNot { it in NULL_ROUTES }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(maxCandidateIps)

        if (ips.isEmpty()) {
            return ProfileCheckResult(
                profileId = profile.id,
                profileTitle = profile.title,
                presetId = preset.id,
                presetTitle = preset.title,
                ip = null,
                hostChecks = emptyList(),
                candidatesTried = emptyList(),
                elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                detail = "DNS не вернул адреса для доменов профиля" +
                    (resolveError?.let { " ($it)" } ?: ""),
            )
        }

        val tried = mutableListOf<String>()
        var lastChecks: List<ProfileHostCheck> = emptyList()
        for (ip in ips) {
            tried += ip
            val checks = coroutineScope {
                profile.hosts.map { host ->
                    async {
                        val tls = try {
                            ipValidator.check(host, ip, tlsTimeoutMillis)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            IpCheckResult.UNKNOWN
                        }
                        val http = if (tls == IpCheckResult.SERVES) {
                            try {
                                httpProber.probe(host, ip)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                HttpProbeResult(HttpVerdict.FAILED, detail = error.message)
                            }
                        } else {
                            HttpProbeResult(HttpVerdict.FAILED, detail = "TLS не подтвердил домен")
                        }
                        ProfileHostCheck(host = host, tls = tls, http = http)
                    }
                }.awaitAll()
            }
            lastChecks = checks
            if (checks.all { it.isUsable }) {
                return ProfileCheckResult(
                    profileId = profile.id,
                    profileTitle = profile.title,
                    presetId = preset.id,
                    presetTitle = preset.title,
                    ip = ip,
                    hostChecks = checks,
                    candidatesTried = tried,
                    elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
                )
            }
        }

        val failed = lastChecks.filterNot { it.isUsable }
        return ProfileCheckResult(
            profileId = profile.id,
            profileTitle = profile.title,
            presetId = preset.id,
            presetTitle = preset.title,
            ip = null,
            hostChecks = lastChecks,
            candidatesTried = tried,
            elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
            detail = "ни один адрес не подошёл; отказы: " +
                failed.joinToString { "${it.host}=${it.http.verdict}" }.take(200),
        )
    }

    private companion object {
        val NULL_ROUTES = setOf("0.0.0.0", "::")
    }
}

/** Итог подбора адреса для каждого домена отдельно. */
data class PerHostRules(
    val profileId: String,
    val profileTitle: String,
    val entries: Map<String, String>,
    val failed: List<String>,
    val elapsedMs: Long,
) {
    val isUsable: Boolean get() = entries.isNotEmpty()
}

/**
 * Подбирает адрес **для каждого домена отдельно**.
 *
 * Требовать один общий адрес нельзя: часть доменов конкретный прокси не обслуживает
 * (`cdn.auth0.com` обрывает TLS), и тогда «общий» подбор возвращает пусто, хотя остальные
 * домены работают. Проверено 31.10.2026 на телефоне: из-за этого не работал вход.
 */
suspend fun ProfileChecker.resolvePerHost(
    profile: AppProfile,
    preset: DnsProviderPreset,
    timeoutMillis: Int = 5_000,
    tlsTimeoutMillis: Int = 6_000,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
): PerHostRules = coroutineScope {
    val startedAt = TimeSource.Monotonic.markNow()
    val semaphore = Semaphore(3)
    // awaitAll preserves input order; workers never mutate a shared map.
    val results = profile.hosts.map { host ->
        async {
            semaphore.withPermit {
                val ips = cancellableCheck {
                    dnsResolver.resolveA(
                        domain = host,
                        dnsServer = preset.primaryDns,
                        timeoutMillis = timeoutMillis,
                        options = DnsQueryOptions(
                            dotHost = preset.dotHost,
                            allowInvalidTls = preset.allowInvalidTls,
                            allowPlainFallback = false,
                        ),
                    )
                }.getOrDefault(emptyList()).filterNot { it in NULL_ROUTES }.distinct().take(MAX_CANDIDATES)
                var chosen: String? = null
                for (candidate in ips) {
                    val tls = cancellableCheck { ipValidator.check(host, candidate, tlsTimeoutMillis) }
                        .getOrDefault(IpCheckResult.REJECTED)
                    if (tls != IpCheckResult.SERVES) continue
                    val probe = cancellableCheck { httpProber.probe(host, candidate) }.getOrNull()
                    if (probe == null || probe.verdict == HttpVerdict.REGION_BLOCKED ||
                        probe.verdict == HttpVerdict.FAILED) continue
                    chosen = candidate
                    break
                }
                host to chosen
            }
        }
    }
    // Progress callbacks run sequentially in the caller context, not on network workers.
    val completed = results.mapIndexed { index, task ->
        task.await().also { onProgress(index + 1, profile.hosts.size) }
    }
    PerHostRules(
        profileId = profile.id,
        profileTitle = profile.title,
        entries = completed.mapNotNull { (host, ip) -> ip?.let { host to it } }.toMap(),
        failed = completed.filter { it.second == null }.map { it.first },
        elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
    )
}

/**
 * Итог подбора по нескольким пресетам: у каждого домена свой адрес и свой провайдер.
 */
data class MergedRules(
    val profileId: String,
    val profileTitle: String,
    /** Домен → адрес; порядок совпадает с порядком доменов в профиле. */
    val entries: Map<String, String>,
    /** Домен → название пресета, который дал рабочий адрес. */
    val sources: Map<String, String>,
    /** Домены, для которых ни один пресет не дал рабочего адреса. */
    val failed: List<String>,
    val elapsedMs: Long,
) {
    val isUsable: Boolean get() = entries.isNotEmpty()

    val presetTitles: List<String> get() = sources.values.distinct()
}

/**
 * Подбирает адрес для каждого домена, перебирая пресеты по порядку.
 *
 * Проверено 14.11.2026: один пресет редко обслуживает всё приложение. Для ChatGPT работает
 * dns.bezmezhau.com, для Gemini — dns.dns-ai.ru, а перекрёстно они дают мёртвые адреса.
 * Поэтому адрес берётся первым рабочим, а не «общий на весь профиль».
 *
 * Перебор останавливается, когда все домены закрыты или истёк [deadlineMillis]: 19 доменов
 * × 10 пресетов с TLS-проверкой иначе не укладываются в разумное время.
 */
suspend fun ProfileChecker.resolvePerHostMerged(
    profile: AppProfile,
    presets: List<DnsProviderPreset>,
    timeoutMillis: Int = 4_000,
    tlsTimeoutMillis: Int = 5_000,
    deadlineMillis: Long = 120_000,
    onProgress: (done: Int, total: Int, presetTitle: String) -> Unit = { _, _, _ -> },
): MergedRules {
    val startedAt = TimeSource.Monotonic.markNow()
    val entries = mutableMapOf<String, String>()
    val sources = mutableMapOf<String, String>()
    for (preset in presets) {
        val remaining = profile.hosts.filterNot { it in entries }
        if (remaining.isEmpty()) break
        if (startedAt.elapsedNow().inWholeMilliseconds > deadlineMillis) break
        onProgress(entries.size, profile.hosts.size, preset.title)
        // Быстрый отсев: сначала два домена. Пресет, который не обслуживает даже их,
        // не стоит проверять на всех 19 — иначе перебор десяти пресетов идёт минутами.
        val sample = remaining.take(2)
        val probe = resolvePerHost(
            profile = profile.copy(hosts = sample),
            preset = preset,
            timeoutMillis = timeoutMillis,
            tlsTimeoutMillis = tlsTimeoutMillis,
        )
        if (probe.entries.isEmpty() && remaining.size > sample.size) continue
        val rules = if (remaining.size == sample.size) probe else resolvePerHost(
            profile = profile.copy(hosts = remaining),
            preset = preset,
            timeoutMillis = timeoutMillis,
            tlsTimeoutMillis = tlsTimeoutMillis,
        )
        rules.entries.forEach { (host, ip) ->
            entries[host] = ip
            sources[host] = preset.title
        }
    }
    return MergedRules(
        profileId = profile.id,
        profileTitle = profile.title,
        entries = profile.hosts.filter { it in entries }.associateWith { entries.getValue(it) },
        sources = sources.filterKeys { it in entries },
        failed = profile.hosts.filterNot { it in entries },
        elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
    )
}

private val NULL_ROUTES = setOf("0.0.0.0", "::")
private const val MAX_CANDIDATES = 4

/**
 * Быстрая проверка текущих правил: живы ли уже подобранные адреса.
 *
 * Полный перебор 17 доменов × несколько адресов занимает до минуты, а нужен он редко:
 * адреса живут часами. Если выборочная проверка проходит, автоподбор ничего не меняет.
 */
suspend fun ProfileChecker.verifyRules(
    entries: Map<String, String>,
    sampleSize: Int = 3,
): Boolean {
    if (entries.isEmpty()) return false
    val sample = entries.entries.take(sampleSize)
    return sample.all { (host, ip) ->
        val tls = cancellableCheck { ipValidator.check(host, ip, 4_000) }
            .getOrDefault(IpCheckResult.REJECTED)
        if (tls != IpCheckResult.SERVES) return@all false
        val probe = cancellableCheck { httpProber.probe(host, ip) }.getOrNull() ?: return@all false
        probe.verdict != HttpVerdict.REGION_BLOCKED && probe.verdict != HttpVerdict.FAILED
    }
}

/** Network failures are recoverable; coroutine cancellation is not a failed candidate. */
private suspend inline fun <T> cancellableCheck(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
