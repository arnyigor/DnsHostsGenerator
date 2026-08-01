package com.arny.dnshostsgenerator.generator

import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationProgress
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.DnsResolver

class HostsGenerator(
    private val dnsResolver: DnsResolver,
) {
    suspend fun generate(
        presetTitle: String,
        request: GenerateHostsRequest,
        onProgress: ((GenerationProgress) -> Unit)? = null,
    ): GenerationResult {
        val outputLines = mutableListOf<HostLine>()
        val processedDomains = mutableSetOf<String>()
        val totalLines = request.inputLines.size

        var lineCount = 0
        var resolvedCount = 0
        var forwardedCount = 0
        var unresolvedCount = 0
        var duplicateCount = 0

        AppLogger.d(
            "Generation started: preset=$presetTitle, primaryDns=${request.primaryDns}, " +
                "checkDns=${request.checkDns}, lines=$totalLines, dedup=${request.dedupEnabled}",
        )

        for (line in request.inputLines) {
            lineCount++
            onProgress?.invoke(
                GenerationProgress(
                    processedLines = lineCount,
                    totalLines = totalLines,
                    currentDomain = line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") },
                ),
            )

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

            val primaryIps = runCatching {
                dnsResolver.resolveA(
                    domain = domain,
                    dnsServer = request.primaryDns,
                    timeoutMillis = request.timeoutMillis,
                    options = DnsQueryOptions(
                        dotHost = request.primaryDotHost,
                        allowInvalidTls = request.primaryAllowInvalidTls,
                    ),
                )
            }.onFailure { error ->
                AppLogger.e(
                    "Primary DNS resolve failed: line=$lineCount, domain=$domain, dns=${request.primaryDns}",
                    error,
                )
            }.getOrDefault(emptyList())

            if (primaryIps.isEmpty()) {
                unresolvedCount++
                outputLines += HostLine.Unresolved(domain)
                continue
            }

            // Compatibility with the original PowerShell script: mark the domain
            // as processed only after a successful primary A-record lookup.
            processedDomains += domain

            val firstIp = primaryIps.first()
            val checkIps = runCatching {
                dnsResolver.resolveA(
                    domain = domain,
                    dnsServer = request.checkDns,
                    timeoutMillis = request.timeoutMillis,
                    options = DnsQueryOptions(
                        dotHost = request.checkDotHost,
                        allowInvalidTls = request.checkAllowInvalidTls,
                    ),
                )
            }.onFailure { error ->
                AppLogger.e(
                    "Check DNS resolve failed, domain will be treated as resolved: " +
                        "line=$lineCount, domain=$domain, dns=${request.checkDns}",
                    error,
                )
            }.getOrDefault(emptyList())

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
                    ip = firstIp,
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
}
