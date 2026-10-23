package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.DnsPresetUnusableException
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationProgress
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import com.arny.dnshostsgenerator.resolver.IpValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource

class HostsGenerator(
    private val dnsResolver: DnsResolver,
    /**
     * Проверка, что адрес из DNS-ответа реально обслуживает домен (TLS + SNI).
     * null — проверка выключена (используется в юнит-тестах с фиктивным DNS).
     */
    private val ipValidator: IpValidator? = null,
) {
    /** Адреса-«обнуляторы»: такой ответ означает блокировку домена, а не разблокировку. */
    private val nullRouteAddresses = setOf("0.0.0.0", "::", "0:0:0:0:0:0:0:0")

    /** Сколько адресов одного домена проверять TLS-хендшейком (дорого по времени). */
    private val maxValidatedCandidates = 2

    /** Результат DNS-запросов для одного уникального домена. */
    private data class DomainLookup(
        val primaryIps: List<String>,
        val checkIps: List<String>,
        /** Адрес, который пойдёт в hosts: подтверждённый TLS-проверкой либо первый из ответа. */
        val selectedIp: String? = null,
    )

    suspend fun generate(
        presetTitle: String,
        request: GenerateHostsRequest,
        onProgress: ((GenerationProgress) -> Unit)? = null,
    ): GenerationResult {
        val startedAt = TimeSource.Monotonic.markNow()
        val totalLines = request.inputLines.size

        AppLogger.d(
            "Generation started: preset=$presetTitle, primaryDns=${request.primaryDns}, " +
                "checkDns=${request.checkDns}, lines=$totalLines, dedup=${request.dedupEnabled}, " +
                "concurrency=${request.concurrency}",
        )

        // Этап 1: параллельно резолвим каждый уникальный домен ровно один раз.
        // Раньше домены обрабатывались строго последовательно, и на сотнях доменов
        // с таймаутами генерация занимала минуты.
        val uniqueDomains = request.inputLines
            .filterNot { it.isBlank() || it.trimStart().startsWith("#") }
            .map { it.trim() }
            .distinct()
        val lookups = resolveAll(uniqueDomains, request, onProgress)

        // Этап 2: собираем вывод в исходном порядке строк с прежней семантикой.
        val outputLines = mutableListOf<HostLine>()
        val processedDomains = mutableSetOf<String>()
        var lineCount = 0
        var resolvedCount = 0
        var forwardedCount = 0
        var unresolvedCount = 0
        var duplicateCount = 0
        var blockedCount = 0

        for (line in request.inputLines) {
            lineCount++

            if (line.isBlank()) {
                if (request.preserveBlankLines) {
                    outputLines += HostLine.Blank
                }
                continue
            }

            if (line.trimStart().startsWith("#")) {
                if (request.preserveComments) {
                    outputLines += HostLine.Comment(line)
                }
                continue
            }

            val domain = line.trim()
            if (request.dedupEnabled && domain in processedDomains) {
                duplicateCount++
                AppLogger.d("Duplicate domain skipped: line=$lineCount, domain=$domain")
                outputLines += HostLine.Duplicate(domain)
                continue
            }

            val lookup = lookups.getValue(domain)
            val primaryIps = lookup.primaryIps
            if (primaryIps.isEmpty()) {
                unresolvedCount++
                outputLines += HostLine.Unresolved(domain)
                continue
            }

            // 0.0.0.0/:: от DNS означает «домен заблокирован». Писать такое в hosts нельзя:
            // цель приложения — найти адрес, по которому домен работает.
            if (primaryIps.all { it in nullRouteAddresses }) {
                blockedCount++
                AppLogger.d(
                    "Null-route answer excluded from hosts output: line=$lineCount, " +
                        "domain=$domain, ips=${primaryIps.joinToString()}",
                )
                outputLines += HostLine.Blocked(domain)
                continue
            }

            val selectedIp = lookup.selectedIp
            if (selectedIp == null) {
                unresolvedCount++
                AppLogger.d(
                    "Domain has no TLS-confirmed IP: line=$lineCount, domain=$domain, " +
                        "primaryIps=${primaryIps.joinToString()}",
                )
                outputLines += HostLine.Unresolved(domain, reason = "адрес не подтверждён TLS")
                continue
            }

            // Compatibility with the original PowerShell script: mark the domain
            // as processed only after a successful primary A-record lookup.
            processedDomains += domain

            val checkIps = lookup.checkIps
            // #forwarded — только когда ВСЕ адреса пресета совпали с ответом проверочного DNS:
            // тогда пресет домен не проксирует, а форвардит. Частичное совпадение — рабочий случай:
            // dns.comss.one на chatgpt.com отвечает общим прокси провайдера и своим адресом.
            val allForwarded = checkIps.isNotEmpty() && primaryIps.all { it in checkIps }
            if (allForwarded) {
                forwardedCount++
                AppLogger.d(
                    "Forwarded match excluded from hosts output: line=$lineCount, " +
                        "domain=$domain, primaryIps=${primaryIps.joinToString()}, " +
                        "checkIps=${checkIps.joinToString()}",
                )
                outputLines += HostLine.Forwarded(
                    domain = domain,
                    primaryIps = primaryIps,
                    checkIps = checkIps,
                )
            } else {
                resolvedCount++
                outputLines += HostLine.Resolved(
                    domain = domain,
                    ip = selectedIp,
                    allPrimaryIps = primaryIps,
                )
            }
        }

        val stats = GenerationStats(
            lineCount = lineCount,
            resolvedCount = resolvedCount,
            forwardedCount = forwardedCount,
            unresolvedCount = unresolvedCount,
            duplicateCount = duplicateCount,
            blockedCount = blockedCount,
        )

        // Пресет, который на всё ответил 0.0.0.0, сейчас не разблокирует ничего:
        // отменяем генерацию по нему целиком, чтобы не отдать вредный hosts.
        if (blockedCount > 0 && resolvedCount == 0 && forwardedCount == 0) {
            AppLogger.e(
                "Preset unusable: preset=$presetTitle, blockedDomains=$blockedCount, " +
                    "lines=${stats.lineCount}",
                DnsPresetUnusableException(presetTitle, blockedCount),
            )
            throw DnsPresetUnusableException(presetTitle, blockedCount)
        }

        AppLogger.d(
            "Generation finished: preset=$presetTitle, elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}, " +
                "output=${request.outputFileName}, " +
                "lines=${stats.lineCount}, resolved=${stats.resolvedCount}, " +
                "forwarded=${stats.forwardedCount}, unresolved=${stats.unresolvedCount}, " +
                "duplicate=${stats.duplicateCount}, blocked=${stats.blockedCount}",
        )

        return GenerationResult(
            presetTitle = presetTitle,
            outputFileName = request.outputFileName,
            lines = outputLines,
            stats = stats,
        )
    }

    private suspend fun resolveAll(
        domains: List<String>,
        request: GenerateHostsRequest,
        onProgress: ((GenerationProgress) -> Unit)?,
    ): Map<String, DomainLookup> = coroutineScope {
        val semaphore = Semaphore(request.concurrency.coerceAtLeast(1))
        val progressMutex = Mutex()
        var completed = 0

        onProgress?.invoke(
            GenerationProgress(processedLines = 0, totalLines = domains.size, currentDomain = null),
        )

        domains.map { domain ->
            async {
                val lookup = semaphore.withPermit { lookup(domain, request) }
                progressMutex.withLock {
                    completed++
                    onProgress?.invoke(
                        GenerationProgress(
                            processedLines = completed,
                            totalLines = domains.size,
                            currentDomain = domain,
                        ),
                    )
                }
                domain to lookup
            }
        }.awaitAll().toMap()
    }

    private suspend fun lookup(domain: String, request: GenerateHostsRequest): DomainLookup {
        val primaryIps = resolveSafely(
            domain = domain,
            dnsServer = request.primaryDns,
            timeoutMillis = request.timeoutMillis,
            options = DnsQueryOptions(
                dotHost = request.primaryDotHost,
                allowInvalidTls = request.primaryAllowInvalidTls,
            ),
            failureMessage = "Primary DNS resolve failed",
        )
        if (primaryIps.isEmpty()) {
            return DomainLookup(primaryIps = emptyList(), checkIps = emptyList())
        }

        val checkIps = resolveSafely(
            domain = domain,
            dnsServer = request.checkDns,
            timeoutMillis = request.timeoutMillis,
            options = DnsQueryOptions(
                dotHost = request.checkDotHost,
                allowInvalidTls = request.checkAllowInvalidTls,
            ),
            failureMessage = "Check DNS resolve failed, domain will be treated as resolved",
        )
        return DomainLookup(
            primaryIps = primaryIps,
            checkIps = checkIps,
            selectedIp = selectWorkingIp(domain, primaryIps, checkIps, request),
        )
    }

    /**
     * Выбирает адрес для hosts: сначала отбрасывает совпавшие с проверочным DNS (это форвард),
     * затем проверяет кандидатов TLS-хендшейком и берёт первый подтверждённый.
     * Если валидатор не подключён или ни один адрес не подтвердился — остаётся первый адрес
     * ответа: лучше отдать прежний результат, чем потерять домен из-за сетевого сбоя.
     */
    private suspend fun selectWorkingIp(
        domain: String,
        primaryIps: List<String>,
        checkIps: List<String>,
        request: GenerateHostsRequest,
    ): String? {
        val proxied = if (checkIps.isEmpty()) primaryIps else primaryIps.filterNot { it in checkIps }
        val candidates = proxied.ifEmpty { primaryIps }
            .distinct()
            .take(maxValidatedCandidates)
        val validator = ipValidator ?: return candidates.firstOrNull()
        // Кандидаты проверяются параллельно: мёртвый адрес отваливается по таймауту,
        // и последовательная проверка растягивала один домен до десятков секунд.
        val fast = checkAll(validator, domain, candidates, request.ipValidationTimeoutMillis)
        fast.firstOrNull { it.second == IpCheckResult.SERVES }?.let { return it.first }
        // Медленные прокси отдают хендшейк за ~10 с: таких перепроверяем с большим таймаутом,
        // иначе рабочие адреса теряются (gemini.google.com через dns.comss.one).
        val unknown = fast.filter { it.second == IpCheckResult.UNKNOWN }.map { it.first }
        if (unknown.isNotEmpty()) {
            val retried = checkAll(validator, domain, unknown, request.ipValidationRetryTimeoutMillis)
            val confirmed = retried.firstOrNull { it.second == IpCheckResult.SERVES }?.first
            if (confirmed == null) {
                AppLogger.e(
                    "No IP confirmed by TLS for domain: domain=$domain, " +
                        "candidates=${candidates.joinToString()}",
                    IllegalStateException("preset returned only unusable IPs"),
                )
            }
            return confirmed
        }
        AppLogger.e(
            "No IP confirmed by TLS for domain: domain=$domain, " +
                "candidates=${candidates.joinToString()}, reasons=${fast.joinToString { it.second.name }}",
            IllegalStateException("preset returned only unusable IPs"),
        )
        return null
    }

    private suspend fun checkAll(
        validator: IpValidator,
        domain: String,
        ips: List<String>,
        timeoutMillis: Int,
    ): List<Pair<String, IpCheckResult>> = coroutineScope {
        ips.map { ip -> async { ip to validator.check(domain, ip, timeoutMillis) } }.awaitAll()
    }

    private suspend fun resolveSafely(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
        options: DnsQueryOptions,
        failureMessage: String,
    ): List<String> = try {
        dnsResolver.resolveA(
            domain = domain,
            dnsServer = dnsServer,
            timeoutMillis = timeoutMillis,
            options = options,
        )
    } catch (error: CancellationException) {
        // Не глотаем отмену: иначе остановка генерации (уход с экрана) не прерывает резолвинг.
        throw error
    } catch (error: Exception) {
        AppLogger.e("$failureMessage: domain=$domain, dns=$dnsServer", error)
        emptyList()
    }
}
