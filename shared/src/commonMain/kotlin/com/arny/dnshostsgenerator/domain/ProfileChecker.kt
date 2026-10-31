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
    private val dnsResolver: DnsResolver,
    private val ipValidator: IpValidator,
    private val httpProber: HttpProber,
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
