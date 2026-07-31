package com.arny.dnshostsgenerator.nextdns

/**
 * Parser for hosts file format input.
 * Ported from Python hosts_parser.py
 */
object HostsParser {

    private val DOMAIN_LABEL = Regex("^[a-z0-9-]+$")

    /**
     * Auto-detect input format: hosts file lines (`ip domain`) or plain domain list.
     */
    fun parseInput(
        text: String,
        defaultTarget: String = "127.0.0.1",
        stripWww: Boolean = true,
        skipLoopback: Boolean = true,
    ): ParsedDomainsResult {
        val hasHostsLines = text.splitLines().any { line ->
            val content = line.substringBefore("#").trim()
            val parts = content.splitToSequence(Regex("\\s+")).toList()
            parts.size >= 2 && parseIpAddress(parts.first()) != null
        }
        return if (hasHostsLines) {
            parseHostsText(
                text = text,
                stripWww = stripWww,
                skipLoopback = skipLoopback,
                defaultTarget = defaultTarget,
            )
        } else {
            parseDomainList(
                text = text,
                defaultTarget = defaultTarget,
                stripWww = stripWww,
            )
        }
    }

    /**
     * Parse hosts-format text and extract domain-to-IP rewrites.
     */
    fun parseHostsText(
        text: String,
        stripWww: Boolean = true,
        skipLoopback: Boolean = true,
        defaultTarget: String = "127.0.0.1",
    ): ParsedDomainsResult {
        val rewritesByPair = mutableMapOf<Pair<String, String>, NextDnsRewrite>()
        val firstTargetByDomain = mutableMapOf<String, String>()
        val issues = mutableListOf<ParseIssue>()
        val conflicts = mutableListOf<DomainConflict>()
        var skippedLoopback = 0
        var exactDuplicates = 0

        for ((index, line) in text.splitLines().withIndex()) {
            val lineNumber = index + 1
            val content = line.substringBefore("#").trim()
            if (content.isEmpty()) continue

            val parts = content.splitToSequence(Regex("\\s+")).toList()
            if (parts.size < 2) {
                issues += ParseIssue(line = line, reason = "нет домена", lineNumber = lineNumber)
                continue
            }

            val rawTarget = parts.first()
            val rawDomains = parts.drop(1)

            val targetIp = parseIpAddress(rawTarget)
            if (targetIp == null) {
                issues += ParseIssue(line = line, reason = "некорректный IP", lineNumber = lineNumber)
                continue
            }

            val target = targetIp.normalized

            for (rawDomain in rawDomains) {
                val normalized = rawDomain.trim().removeSuffix(".").lowercase()

                // Skip loopback/localhost
                if (skipLoopback && (targetIp.isLoopback || normalized == "localhost")) {
                    skippedLoopback++
                    continue
                }

                val domain = try {
                    normalizeDomain(normalized, stripWww)
                } catch (e: Exception) {
                    issues += ParseIssue(line = line, reason = e.message ?: "ошибка домена", lineNumber = lineNumber)
                    continue
                }

                val previousTarget = firstTargetByDomain[domain]
                if (previousTarget != null && previousTarget != target) {
                    conflicts += DomainConflict(
                        domain = domain,
                        firstTarget = previousTarget,
                        rejectedTarget = target,
                        lineNumber = lineNumber,
                    )
                    continue
                }

                firstTargetByDomain.putIfAbsent(domain, target)

                val key = domain to target
                if (key in rewritesByPair) {
                    exactDuplicates++
                    continue
                }

                rewritesByPair[key] = NextDnsRewrite(domain = domain, target = target)
            }
        }

        return ParsedDomainsResult(
            rewrites = rewritesByPair.values.toList(),
            issues = issues,
            conflicts = conflicts,
            skippedLoopback = skippedLoopback,
            exactDuplicates = exactDuplicates,
        )
    }

    /**
     * Parse simple domain list (one domain per line).
     * Returns rewrites with the default target IP.
     */
    fun parseDomainList(
        text: String,
        defaultTarget: String = "127.0.0.1",
        stripWww: Boolean = true,
    ): ParsedDomainsResult {
        val target = parseIpAddress(defaultTarget)?.normalized
            ?: throw IllegalArgumentException("некорректный Target IP")
        val rewritesByPair = mutableMapOf<Pair<String, String>, NextDnsRewrite>()
        val issues = mutableListOf<ParseIssue>()
        var exactDuplicates = 0

        for ((index, line) in text.splitLines().withIndex()) {
            val lineNumber = index + 1
            val content = line.substringBefore("#").trim()
            if (content.isEmpty()) continue

            val domain = try {
                normalizeDomain(content, stripWww)
            } catch (e: Exception) {
                issues += ParseIssue(line = line, reason = e.message ?: "ошибка домена", lineNumber = lineNumber)
                continue
            }

            val key = domain to target
            if (key in rewritesByPair) {
                exactDuplicates++
                continue
            }

            rewritesByPair[key] = NextDnsRewrite(domain = domain, target = target)
        }

        return ParsedDomainsResult(
            rewrites = rewritesByPair.values.toList(),
            issues = issues,
            conflicts = emptyList(),
            skippedLoopback = 0,
            exactDuplicates = exactDuplicates,
        )
    }

    private fun normalizeDomain(rawDomain: String, stripWww: Boolean): String {
        var domain = rawDomain.trim()
            .substringBefore('#')
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .removeSuffix(".")
            .lowercase()
        if (stripWww && domain.startsWith("www.")) {
            domain = domain.substring(4)
        }

        if (domain.isEmpty()) throw IllegalArgumentException("пустой домен")

        val asciiDomain = try {
            domainToAscii(domain)
        } catch (e: Exception) {
            throw IllegalArgumentException("ошибка IDNA")
        }
        if (asciiDomain.length > 253) throw IllegalArgumentException("домен длиннее 253 символов")

        val labels = asciiDomain.split(".")
        if (labels.size < 2) throw IllegalArgumentException("ожидается полное доменное имя")

        for (label in labels) {
            if (label.isEmpty()) throw IllegalArgumentException("пустая часть домена")
            if (label.length > 63) throw IllegalArgumentException("часть домена длиннее 63 символов")
            if (label.startsWith("-") || label.endsWith("-")) {
                throw IllegalArgumentException("часть домена начинается/заканчивается дефисом")
            }
            if (!DOMAIN_LABEL.matches(label)) {
                throw IllegalArgumentException("недопустимые символы в домене")
            }
        }

        return asciiDomain
    }

    private fun String.splitLines(): List<String> =
        replace("\r\n", "\n").replace("\r", "\n").split("\n")
}
