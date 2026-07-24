package com.arny.dnshostsgenerator.domain

data class DnsProviderPreset(
    val id: String,
    val title: String,
    val primaryDns: String,
    val checkDns: String = "8.8.8.8",
    val outputFileName: String,
    val enabledByDefault: Boolean = true,
) {
    companion object {
        /**
         * Возвращает список моковых данных для использования в @Preview
         * и UI-тестах.
         */
        fun previewData(): List<DnsProviderPreset> = listOf(
            DnsProviderPreset(
                id = "1",
                title = "Recommended (Cloudflare)",
                primaryDns = "1.1.1.1",
                checkDns = "1.0.0.1",
                outputFileName = "hosts_cloudflare.txt"
            ),
            DnsProviderPreset(
                id = "2",
                title = "Google DNS",
                primaryDns = "8.8.8.8",
                checkDns = "8.8.4.4",
                outputFileName = "hosts_google.txt"
            ),
            DnsProviderPreset(
                id = "3",
                title = "OpenDNS",
                primaryDns = "205.155.74.6",
                checkDns = "208.67.222.222",
                outputFileName = "hosts_opendns.txt"
            ),
            // Добавляем случай с выключенным пресетом для проверки UI состояния
            DnsProviderPreset(
                id = "4",
                title = "Custom (Disabled)",
                primaryDns = "192.168.1.1",
                checkDns = "8.8.8.8",
                outputFileName = "hosts_custom.txt",
                enabledByDefault = false
            )
        )
    }
}


enum class DnsRecordType {
    A,
}

enum class IpSelectionStrategy {
    First,
}

data class GenerateHostsRequest(
    val primaryDns: String,
    val checkDns: String,
    val inputLines: List<String>,
    val outputFileName: String,
    val dedupEnabled: Boolean,
    val recordType: DnsRecordType = DnsRecordType.A,
    val ipSelectionStrategy: IpSelectionStrategy = IpSelectionStrategy.First,
    val preserveComments: Boolean = true,
    val preserveBlankLines: Boolean = true,
    val timeoutMillis: Int = 5_000,
)

sealed interface HostLine {
    fun toOutputLine(): String

    data class Comment(val text: String) : HostLine {
        override fun toOutputLine(): String = text
    }

    data object Blank : HostLine {
        override fun toOutputLine(): String = ""
    }

    data class Resolved(
        val domain: String,
        val ip: String,
        val allPrimaryIps: List<String>,
    ) : HostLine {
        override fun toOutputLine(): String = "$ip $domain"
    }

    data class Forwarded(
        val domain: String,
        val primaryIps: List<String>,
        val checkIps: List<String>,
    ) : HostLine {
        override fun toOutputLine(): String = primaryIps.firstOrNull()
            ?.let { ip -> "$ip $domain" }
            ?: "#unresolvedDomain $domain"
    }

    data class Unresolved(
        val domain: String,
        val reason: String? = null,
    ) : HostLine {
        override fun toOutputLine(): String = "#unresolvedDomain $domain"
    }

    data class Duplicate(val domain: String) : HostLine {
        override fun toOutputLine(): String = "#duplicate $domain"
    }
}

data class GenerationStats(
    val lineCount: Int,
    val resolvedCount: Int,
    val forwardedCount: Int,
    val unresolvedCount: Int,
    val duplicateCount: Int,
) {
    val activeHostsCount: Int
        get() = resolvedCount + forwardedCount

    val domainResultCount: Int
        get() = resolvedCount + forwardedCount + unresolvedCount + duplicateCount
}

data class GenerationResult(
    val presetTitle: String,
    val outputFileName: String,
    val lines: List<HostLine>,
    val stats: GenerationStats,
) {
    val outputText: String
        get() = lines.joinToString(separator = "\n") { it.toOutputLine() }
}

data class GenerationProgress(
    val processedLines: Int,
    val totalLines: Int,
    val currentDomain: String?,
)
