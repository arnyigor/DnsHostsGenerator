package com.arny.dnshostsgenerator.nextdns

import kotlinx.serialization.Serializable

/**
 * Domain rewrite entry: maps a domain to a target IP.
 */
@Serializable
data class NextDnsRewrite(
    val domain: String,
    val target: String,
) {
    fun asApiPayload(): Map<String, String> = mapOf(
        "name" to domain,
        "content" to target,
    )
}

/**
 * A rewrite returned from the NextDNS API.
 */
@Serializable
data class RemoteRewrite(
    val id: String?,
    val name: String,
    val content: String,
)

/**
 * Temporary NextDNS profile created via API.
 */
@Serializable
data class NextDnsProfile(
    val profileId: String,
    val expiresOn: Long?,
    val name: String?,
) {
    val privateDns: String
        get() = "$profileId.dns.nextdns.io"

    val dohUrl: String
        get() = "https://dns.nextdns.io/$profileId"
}

/**
 * Account snapshot containing profiles.
 */
@Serializable
data class AccountSnapshot(
    val email: String?,
    val expiresOn: Long?,
    val profiles: List<NextDnsProfile>,
)

/**
 * Parsed domain input result.
 */
data class ParsedDomainsResult(
    val rewrites: List<NextDnsRewrite>,
    val issues: List<ParseIssue>,
    val conflicts: List<DomainConflict>,
    val skippedLoopback: Int,
    val exactDuplicates: Int,
)

data class ParseIssue(
    val line: String,
    val reason: String,
    val lineNumber: Int? = null,
)

data class DomainConflict(
    val domain: String,
    val firstTarget: String,
    val rejectedTarget: String,
    val lineNumber: Int? = null,
)

data class ImportFailure(
    val rewrite: NextDnsRewrite,
    val reason: String,
    val status: Int? = null,
    val attempts: Int = 1,
)

enum class ImportProgressPhase {
    CreatingAccount,
    Processing,
    Retrying,
    Resting,
    Verifying,
    Completed,
}

data class NextDnsImportProgress(
    val processed: Int,
    val total: Int,
    val imported: Int,
    val failed: Int,
    val skippedExisting: Int,
    val retrying: Int,
    val retriesAttempted: Int,
    val currentDomain: String?,
    val currentBurst: Int,
    val totalBursts: Int,
    val mode: String,
    val phase: ImportProgressPhase,
    val cooldownRemainingSeconds: Int = 0,
)

/**
 * Import summary after uploading rewrites.
 */
data class ImportSummary(
    val requested: Int,
    val imported: Int,
    val skippedExisting: Int,
    val failed: Int,
    val statusCounts: Map<String, Int>,
    val failures: List<ImportFailure> = emptyList(),
    val missingAfterVerify: List<NextDnsRewrite> = emptyList(),
    val cancelled: Boolean = false,
) {
    val verifiedSuccessfully: Boolean
        get() = !cancelled && failed == 0 && missingAfterVerify.isEmpty()
}
