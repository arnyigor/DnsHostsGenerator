package com.arny.dnshostsgenerator.nextdns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * HTTP client for NextDNS API.
 *
 * Ported from the Python nextdns_cli tool. Handles:
 * - Creating temporary anonymous accounts
 * - Creating a profile with rewrites
 * - Importing domain rewrites with retry and rate limiting
 */
class NextDnsClient(
    callFactory: Call.Factory = createDefaultClient(),
) {
    private var callFactory: Call.Factory = callFactory
    companion object {
        private const val API_BASE = "https://api.nextdns.io"
        private const val WEB_ORIGIN = "https://my.nextdns.io"
        private const val IMPORT_BURST_SIZE = 58
        private const val IMPORT_REST_DELAY_MILLIS = 5_000L
        private const val IMPORT_BATCH_SIZE = 4
        private val PROFILE_ID_PATTERN = Regex("^[a-f0-9]{6}$", RegexOption.IGNORE_CASE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Drop NextDNS cookies/session. The next import creates a fresh temporary account.
     */
    fun resetSession() {
        val client = callFactory as? OkHttpClient
        val clearableCookieJar = client?.cookieJar as? ClearableCookieJar
        clearableCookieJar?.clear()
        client?.dispatcher?.cancelAll()
        client?.connectionPool?.evictAll()
        callFactory = createDefaultClient()
    }

    /**
     * Create a temporary anonymous NextDNS account with a profile.
     * Returns the created profile.
     */
    suspend fun createTemporaryAccount(profileName: String = "My First Profile"): NextDnsProfile {
        val payload = buildJsonObject {
            put("profile", buildJsonObject {
                put("name", profileName)
                put("security", buildJsonObject {
                    put("threatIntelligenceFeeds", true)
                    put("googleSafeBrowsing", true)
                    put("cryptojacking", true)
                    put("idnHomographs", true)
                    put("typosquatting", true)
                    put("dga", true)
                    put("csam", true)
                })
                put("privacy", buildJsonObject {
                    put("blocklists", buildJsonArray {
                        add(buildJsonObject { put("id", "nextdns-recommended") })
                    })
                    put("disguisedTrackers", true)
                })
                put("settings", buildJsonObject {
                    put("logs", buildJsonObject { put("enabled", true) })
                    put("performance", buildJsonObject { put("ecs", true) })
                })
            })
        }

        val response = postJson("$API_BASE/accounts", payload.toString())
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            throw NextDnsApiException(
                message = "NextDNS не создал временный аккаунт: HTTP ${response.code}${body.toCompactErrorSuffix()}",
                statusCode = response.code,
            )
        }

        // Get account info to retrieve the profile
        val account = getAccount()
            ?: throw NextDnsApiException("Account created but no profile found")

        return account.profiles.firstOrNull()
            ?: throw NextDnsApiException("Account created but profile list is empty")
    }

    /**
     * Get the current account and profiles.
     */
    suspend fun getAccount(): AccountSnapshot? {
        val response = getRequest("$API_BASE/accounts/@me", mapOf("withProfiles" to "true"))
        if (response.code == 403) return null

        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            throw NextDnsApiException(
                message = "NextDNS не вернул аккаунт: HTTP ${response.code}${body.toCompactErrorSuffix()}",
                statusCode = response.code,
            )
        }

        return parseAccount(response.body?.string() ?: "")
    }

    /**
     * Get existing rewrites for a profile.
     */
    suspend fun getRewrites(profileId: String): List<RemoteRewrite> {
        require(PROFILE_ID_PATTERN.matches(profileId)) { "Invalid profile ID: $profileId" }

        val response = getRequest("$API_BASE/profiles/$profileId/rewrites")
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            throw NextDnsApiException(
                message = "NextDNS не вернул Rewrites: HTTP ${response.code}${body.toCompactErrorSuffix()}",
                statusCode = response.code,
            )
        }

        val body = response.body?.string() ?: "[]"
        val root = json.parseToJsonElement(body)
        val data = if (root is kotlinx.serialization.json.JsonObject) {
            root["data"]
        } else {
            null
        }

        if (data !is kotlinx.serialization.json.JsonArray) {
            return emptyList()
        }

        val result = mutableListOf<RemoteRewrite>()
        for (item in data) {
            if (item !is kotlinx.serialization.json.JsonObject) continue
            val domain = item["name"]?.jsonPrimitive?.content
            val target = item["content"]?.jsonPrimitive?.content
            val id = item["id"]?.jsonPrimitive?.content
            if (domain != null && target != null) {
                result.add(RemoteRewrite(id = id, name = domain.lowercase(), content = target))
            }
        }
        return result
    }

    /**
     * Create a single rewrite for a profile.
     * Returns the API response status and body.
     */
    suspend fun createRewrite(profileId: String, rewrite: NextDnsRewrite): ApiResult {
        require(PROFILE_ID_PATTERN.matches(profileId)) { "Invalid profile ID: $profileId" }

        val payload = buildJsonObject {
            put("name", rewrite.domain)
            put("content", rewrite.target)
        }

        val response = postJson("$API_BASE/profiles/$profileId/rewrites", payload.toString())
        val body = response.body?.string() ?: ""

        return ApiResult(
            status = response.code,
            text = body,
            headers = response.headers.toMultimap(),
        )
    }

    /**
     * Import multiple rewrites with concurrency control and retry.
     */
    suspend fun importRewrites(
        profileId: String,
        rewrites: List<NextDnsRewrite>,
        concurrency: Int = 2,
        maxAttempts: Int = 8,
        onProgress: ((NextDnsImportProgress) -> Unit)? = null,
    ): ImportSummary {
        // Get existing rewrites
        val existing = getRewrites(profileId)
        val existingPairs = existing.associateBy { it.name.lowercase() to it.content }
        val existingTargetByDomain = existing.associateBy({ it.name.lowercase() }) { it.content }

        val pending = mutableListOf<NextDnsRewrite>()
        var skippedExisting = 0
        val failures = mutableListOf<ImportFailure>()
        val statusCounts = mutableMapOf<String, Int>()

        for (rewrite in rewrites) {
            val pair = rewrite.domain.lowercase() to rewrite.target
            if (pair in existingPairs) {
                skippedExisting++
                continue
            }
            val existingTarget = existingTargetByDomain[rewrite.domain.lowercase()]
            if (existingTarget != null && existingTarget != rewrite.target) {
                failures += ImportFailure(
                    rewrite = rewrite,
                    reason = "В профиле уже есть этот домен с другим IP: $existingTarget",
                    status = 409,
                )
                statusCounts["409"] = statusCounts.getOrDefault("409", 0) + 1
                continue
            }
            pending += rewrite
        }

        // Import with concurrency control
        var imported = 0
        var failed = 0
        var processed = 0

        if (pending.isEmpty()) {
            return ImportSummary(
                requested = rewrites.size,
                imported = 0,
                skippedExisting = skippedExisting,
                failed = failures.size,
                statusCounts = statusCounts,
                failures = failures,
                missingAfterVerify = verifyMissing(profileId, rewrites),
            )
        }

        var retriesAttempted = 0
        val bursts = pending.chunked(IMPORT_BURST_SIZE)
        val totalBursts = bursts.size.coerceAtLeast(1)
        onProgress?.invoke(
            NextDnsImportProgress(
                processed = 0,
                total = pending.size,
                imported = imported,
                failed = failed,
                skippedExisting = skippedExisting,
                retrying = 0,
                retriesAttempted = retriesAttempted,
                currentDomain = null,
                currentBurst = 1,
                totalBursts = totalBursts,
                mode = "готовность",
                phase = ImportProgressPhase.Processing,
            )
        )

        bursts.forEachIndexed { burstIndex, burst ->
            val currentBurst = burstIndex + 1
            val batches = burst.chunked(IMPORT_BATCH_SIZE.coerceAtLeast(concurrency.coerceAtLeast(1)))
            var positionInBurst = 1

            for (batch in batches) {
                val mode = modeForPosition(positionInBurst)
                val batchStartPosition = positionInBurst

                for (rewrite in batch) {
                    currentCoroutineContext().ensureActive()
                    var success = false
                    var lastStatus: Int? = null
                    var lastReason = "неизвестная ошибка"
                    var attemptsUsed = 1

                    for (attempt in 1..maxAttempts) {
                        currentCoroutineContext().ensureActive()
                        attemptsUsed = attempt
                        onProgress?.invoke(
                            NextDnsImportProgress(
                                processed = processed,
                                total = pending.size,
                                imported = imported,
                                failed = failed,
                                skippedExisting = skippedExisting,
                                retrying = if (attempt > 1) 1 else 0,
                                retriesAttempted = retriesAttempted,
                                currentDomain = rewrite.domain,
                                currentBurst = currentBurst,
                                totalBursts = totalBursts,
                                mode = mode,
                                phase = if (attempt > 1) ImportProgressPhase.Retrying else ImportProgressPhase.Processing,
                            )
                        )

                        val result = createRewrite(profileId, rewrite)
                        lastStatus = result.status

                        if (result.status in 200..299) {
                            statusCounts[result.status.toString()] = statusCounts.getOrDefault(result.status.toString(), 0) + 1
                            success = true
                            break
                        }

                        lastReason = formatApiError(result.status, result.text)
                        statusCounts[result.status.toString()] = statusCounts.getOrDefault(result.status.toString(), 0) + 1

                        val retryDelayMillis = when {
                            result.status == 429 -> (8 + attempt * 3).coerceAtMost(30) * 1000L
                            result.status in 500..599 -> ((1.5 * attempt).coerceAtMost(6.0) * 1000L).toLong()
                            else -> null
                        }

                        if (retryDelayMillis == null || attempt >= maxAttempts) {
                            break
                        }

                        retriesAttempted++
                        reportCooldown(
                            delayMillis = retryDelayMillis,
                            processed = processed,
                            total = pending.size,
                            imported = imported,
                            failed = failed,
                            skippedExisting = skippedExisting,
                            retriesAttempted = retriesAttempted,
                            currentDomain = rewrite.domain,
                            currentBurst = currentBurst,
                            totalBursts = totalBursts,
                            mode = "повтор после HTTP ${result.status}",
                            phase = ImportProgressPhase.Retrying,
                            onProgress = onProgress,
                        )
                    }

                    processed++
                    if (success) {
                        imported++
                    } else {
                        failed++
                        failures += ImportFailure(
                            rewrite = rewrite,
                            reason = lastReason,
                            status = lastStatus,
                            attempts = attemptsUsed,
                        )
                    }

                    onProgress?.invoke(
                        NextDnsImportProgress(
                            processed = processed,
                            total = pending.size,
                            imported = imported,
                            failed = failed,
                            skippedExisting = skippedExisting,
                            retrying = 0,
                            retriesAttempted = retriesAttempted,
                            currentDomain = rewrite.domain,
                            currentBurst = currentBurst,
                            totalBursts = totalBursts,
                            mode = mode,
                            phase = ImportProgressPhase.Processing,
                        )
                    )
                }

                positionInBurst += batch.size
                val delayMillis = progressiveDelayMillis(batchStartPosition)
                if (positionInBurst <= burst.size && delayMillis > 0) {
                    reportCooldown(
                        delayMillis = delayMillis,
                        processed = processed,
                        total = pending.size,
                        imported = imported,
                        failed = failed,
                        skippedExisting = skippedExisting,
                        retriesAttempted = retriesAttempted,
                        currentDomain = batch.lastOrNull()?.domain,
                        currentBurst = currentBurst,
                        totalBursts = totalBursts,
                        mode = modeForPosition(positionInBurst),
                        phase = ImportProgressPhase.Processing,
                        onProgress = onProgress,
                    )
                }
            }

            if (currentBurst < totalBursts) {
                reportCooldown(
                    delayMillis = IMPORT_REST_DELAY_MILLIS,
                    processed = processed,
                    total = pending.size,
                    imported = imported,
                    failed = failed,
                    skippedExisting = skippedExisting,
                    retriesAttempted = retriesAttempted,
                    currentDomain = null,
                    currentBurst = currentBurst,
                    totalBursts = totalBursts,
                    mode = "отдых между всплесками",
                    phase = ImportProgressPhase.Resting,
                    onProgress = onProgress,
                )
            }
        }

        onProgress?.invoke(
            NextDnsImportProgress(
                processed = processed,
                total = pending.size,
                imported = imported,
                failed = failed,
                skippedExisting = skippedExisting,
                retrying = 0,
                retriesAttempted = retriesAttempted,
                currentDomain = null,
                currentBurst = totalBursts,
                totalBursts = totalBursts,
                mode = "проверка",
                phase = ImportProgressPhase.Verifying,
            )
        )
        return ImportSummary(
            requested = rewrites.size,
            imported = imported,
            skippedExisting = skippedExisting,
            failed = failed,
            statusCounts = statusCounts,
            failures = failures,
            missingAfterVerify = verifyMissing(profileId, rewrites),
        )
    }

    private suspend fun reportCooldown(
        delayMillis: Long,
        processed: Int,
        total: Int,
        imported: Int,
        failed: Int,
        skippedExisting: Int,
        retriesAttempted: Int,
        currentDomain: String?,
        currentBurst: Int,
        totalBursts: Int,
        mode: String,
        phase: ImportProgressPhase,
        onProgress: ((NextDnsImportProgress) -> Unit)?,
    ) {
        if (delayMillis <= 0L) return
        var remainingMillis = delayMillis
        while (remainingMillis > 0L) {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(
                NextDnsImportProgress(
                    processed = processed,
                    total = total,
                    imported = imported,
                    failed = failed,
                    skippedExisting = skippedExisting,
                    retrying = if (phase == ImportProgressPhase.Retrying) 1 else 0,
                    retriesAttempted = retriesAttempted,
                    currentDomain = currentDomain,
                    currentBurst = currentBurst,
                    totalBursts = totalBursts,
                    mode = mode,
                    phase = phase,
                    cooldownRemainingSeconds = ((remainingMillis + 999L) / 1000L).toInt(),
                )
            )
            val step = minOf(remainingMillis, 1_000L)
            kotlinx.coroutines.delay(step)
            remainingMillis -= step
        }
    }

    private fun progressiveDelayMillis(positionInBurst: Int): Long = when (positionInBurst) {
        in 1..8 -> 1_000L
        in 9..16 -> 700L
        in 17..25 -> 400L
        in 26..35 -> 200L
        in 36..45 -> 50L
        else -> 10L
    }

    private fun modeForPosition(positionInBurst: Int): String = when (positionInBurst) {
        in 1..8 -> "медленный старт"
        in 9..16 -> "медленно"
        in 17..25 -> "средне"
        in 26..35 -> "быстро"
        in 36..45 -> "очень быстро"
        else -> "максимально быстро"
    }

    private suspend fun verifyMissing(
        profileId: String,
        expected: List<NextDnsRewrite>,
    ): List<NextDnsRewrite> {
        val remotePairs = getRewrites(profileId)
            .map { it.name.lowercase() to it.content }
            .toSet()
        return expected.filter { rewrite ->
            rewrite.domain.lowercase() to rewrite.target !in remotePairs
        }
    }

    private fun formatApiError(status: Int, body: String): String {
        val compact = body.splitToSequence(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(500)
        return "HTTP $status: ${compact.ifBlank { "<пустой ответ>" }}"
    }

    private fun parseAccount(body: String): AccountSnapshot {
        val root = json.parseToJsonElement(body)
        if (root !is kotlinx.serialization.json.JsonObject) {
            throw NextDnsApiException("Invalid account response")
        }

        val expiresOn = root["expiresOn"]?.jsonPrimitive?.longOrNull
        val profilesPayload = root["profiles"]

        if (profilesPayload !is kotlinx.serialization.json.JsonArray) {
            throw NextDnsApiException("Invalid profiles in account response")
        }

        val profiles = mutableListOf<NextDnsProfile>()
        for (item in profilesPayload) {
            if (item !is kotlinx.serialization.json.JsonObject) continue
            val profileId = item["id"]?.jsonPrimitive?.content
            if (profileId == null || !PROFILE_ID_PATTERN.matches(profileId)) continue
            val name = item["name"]?.jsonPrimitive?.content
            profiles.add(
                NextDnsProfile(
                    profileId = profileId.lowercase(),
                    expiresOn = expiresOn,
                    name = name,
                )
            )
        }

        val email = root["email"]?.jsonPrimitive?.content

        return AccountSnapshot(
            email = email,
            expiresOn = expiresOn,
            profiles = profiles,
        )
    }

    private suspend fun getRequest(url: String, params: Map<String, String> = emptyMap()): Response = withContext(Dispatchers.IO) {
        val urlBuilder = url.toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid URL: $url")

        val finalUrl = params.entries.fold(urlBuilder.newBuilder()) { builder, (key, value) ->
            builder.addQueryParameter(key, value)
        }.build()

        val request = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", WEB_ORIGIN)
            .header("Referer", "$WEB_ORIGIN/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        callFactory.newCall(request).execute()
    }

    private suspend fun postJson(url: String, jsonBody: String): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: throw IllegalStateException("Invalid URL: $url"))
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json")
            .header("Origin", WEB_ORIGIN)
            .header("Referer", "$WEB_ORIGIN/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        callFactory.newCall(request).execute()
    }
}

/**
 * API response result.
 */
data class ApiResult(
    val status: Int,
    val text: String,
    val headers: Map<String, List<String>>,
)

/**
 * Exception for NextDNS API errors.
 */
class NextDnsApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

private fun String.toCompactErrorSuffix(): String {
    val compact = splitToSequence(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(500)
    return if (compact.isBlank()) "" else ": $compact"
}

private interface ClearableCookieJar {
    fun clear()
}

private class InMemoryCookieJar : CookieJar, ClearableCookieJar {
    private val cookiesByHost = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = cookiesByHost.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            hostCookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            hostCookies += cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookiesByHost.values
            .flatten()
            .filter { cookie -> cookie.expiresAt > now && cookie.matches(url) }
    }

    override fun clear() {
        cookiesByHost.clear()
    }
}

/**
 * Create a default OkHttp client suitable for NextDNS API calls.
 */
fun createDefaultClient(): Call.Factory {
    return OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
