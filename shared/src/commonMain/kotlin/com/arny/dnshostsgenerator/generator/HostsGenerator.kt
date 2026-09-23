package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationProgress
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class HostsGenerator(
    private val dnsResolver: DnsResolver,
) {
    /** Результат DNS-запросов для одного уникального домена. */
    private data class DomainLookup(
        val primaryIps: List<String>,
        val checkIps: List<String>,
    )

    suspend fun generate(
        presetTitle: String,
        request: GenerateHostsRequest,
        onProgress: ((GenerationProgress) -> Unit)? = null,
    ): GenerationResult {
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

            // Compatibility with the original PowerShell script: mark the domain
            // as processed only after a successful primary A-record lookup.
            processedDomains += domain

            val checkIps = lookup.checkIps
            val hasCommonIp = checkIps.any { it in primaryIps }
            if (checkIps.isNotEmpty() && hasCommonIp) {
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
                    ip = primaryIps.first(),
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
        )
        AppLogger.d(
            "Generation finished: preset=$presetTitle, output=${request.outputFileName}, " +
                "lines=${stats.lineCount}, resolved=${stats.resolvedCount}, " +
                "forwarded=${stats.forwardedCount}, unresolved=${stats.unresolvedCount}, " +
                "duplicate=${stats.duplicateCount}",
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
        return DomainLookup(primaryIps = primaryIps, checkIps = checkIps)
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
