package com.arny.dnshostsgenerator.nextdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * UI State for the NextDNS import screen.
 */
data class NextDnsImportState(
    val domainText: String = DefaultDomainList.text,
    val targetIp: String = "127.0.0.1",
    val stripWww: Boolean = true,
    val skipLoopback: Boolean = true,
    val profileName: String = "My First Profile",

    // Parsed results
    val parsedCount: Int = 0,
    val parseIssues: Int = 0,

    // Import progress
    val isCreatingAccount: Boolean = false,
    val isImporting: Boolean = false,
    val isCancelling: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val currentDomain: String? = null,
    val importedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedExistingCount: Int = 0,
    val retryingCount: Int = 0,
    val retriesAttempted: Int = 0,
    val currentBurst: Int = 0,
    val totalBursts: Int = 0,
    val importMode: String = "готовность",
    val importPhase: ImportProgressPhase = ImportProgressPhase.CreatingAccount,
    val cooldownRemainingSeconds: Int = 0,
    val elapsedText: String = "00:00",
    val etaText: String = "--:--",
    val speedText: String = "0.00 записей/с",

    // Result
    val profile: NextDnsProfile? = null,
    val importSummary: ImportSummary? = null,

    // Status
    val statusMessage: String = "Введите домены и нажмите 'Парсить'",
    val errorMessage: String? = null,
)

sealed interface NextDnsImportEvent {
    data class OnDomainTextChanged(val text: String) : NextDnsImportEvent
    data class OnTargetIpChanged(val ip: String) : NextDnsImportEvent
    data class OnStripWwwChanged(val enabled: Boolean) : NextDnsImportEvent
    data class OnSkipLoopbackChanged(val enabled: Boolean) : NextDnsImportEvent
    data class OnProfileNameChanged(val name: String) : NextDnsImportEvent
    object OnParse : NextDnsImportEvent
    object OnImport : NextDnsImportEvent
    object OnRecreateAccount : NextDnsImportEvent
    object OnCancelImport : NextDnsImportEvent
    object OnReset : NextDnsImportEvent
}

sealed interface NextDnsImportEffect {
    data class ShowToast(val message: String) : NextDnsImportEffect
}

/**
 * ViewModel for the NextDNS import flow.
 *
 * Flow:
 * 1. User enters domains (text input)
 * 2. User clicks "Parse" to validate and count domains
 * 3. User clicks "Import" which:
 *    a. Creates a temporary NextDNS account
 *    b. Imports all domains as rewrites
 *    c. Shows the DNS server info
 */
class NextDnsImportViewModel(
    private val nextDnsClient: NextDnsClient,
) : ViewModel() {

    private val _state = MutableStateFlow(NextDnsImportState())
    val state: StateFlow<NextDnsImportState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<NextDnsImportEffect>()
    val effect = _effect.asSharedFlow()

    private var parsedResult: ParsedDomainsResult? = null
    private var importJob: Job? = null
    private var importStartedAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastSpeedProcessed: Int = 0
    private var lastSpeedMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var stableImportSpeed: Double = 0.0

    fun onEvent(event: NextDnsImportEvent) {
        when (event) {
            is NextDnsImportEvent.OnDomainTextChanged -> {
                _state.update { it.copy(domainText = event.text) }
            }
            is NextDnsImportEvent.OnTargetIpChanged -> {
                _state.update { it.copy(targetIp = event.ip) }
            }
            is NextDnsImportEvent.OnStripWwwChanged -> {
                _state.update { it.copy(stripWww = event.enabled) }
            }
            is NextDnsImportEvent.OnSkipLoopbackChanged -> {
                _state.update { it.copy(skipLoopback = event.enabled) }
            }
            is NextDnsImportEvent.OnProfileNameChanged -> {
                _state.update { it.copy(profileName = event.name) }
            }
            is NextDnsImportEvent.OnParse -> parseDomains()
            is NextDnsImportEvent.OnImport -> startImport(resetSession = true)
            is NextDnsImportEvent.OnRecreateAccount -> recreateAccountAndImport()
            is NextDnsImportEvent.OnCancelImport -> cancelImport()
            is NextDnsImportEvent.OnReset -> reset()
        }
    }

    private fun parseDomains() {
        val current = _state.value
        _state.update { it.copy(errorMessage = null, statusMessage = "Парсинг...") }

        try {
            val result = HostsParser.parseInput(
                text = current.domainText,
                defaultTarget = current.targetIp,
                stripWww = current.stripWww,
                skipLoopback = current.skipLoopback,
            )
            parsedResult = result

            _state.update {
                it.copy(
                    parsedCount = result.rewrites.size,
                    parseIssues = result.issues.size,
                    statusMessage = "Найдено ${result.rewrites.size} доменов, ошибок: ${result.issues.size}, дублей: ${result.exactDuplicates}",
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Parse failed", e)
            _state.update {
                it.copy(
                    errorMessage = "Ошибка парсинга: ${e.message}",
                    statusMessage = "Ошибка",
                )
            }
        }
    }

    private fun startImport(resetSession: Boolean) {
        val result = parsedResult
        if (result == null || result.rewrites.isEmpty()) {
            _state.update { it.copy(statusMessage = "Сначала нажмите 'Парсить'") }
            return
        }

        importJob?.cancel()
        nextDnsClient.resetSession()
        importStartedAt = TimeSource.Monotonic.markNow()
        resetSpeedEstimator()
        _state.update {
            it.copy(
                isCreatingAccount = true,
                isImporting = false,
                isCancelling = false,
                errorMessage = null,
                statusMessage = "Создание нового временного аккаунта NextDNS...",
                profile = null,
                importSummary = null,
                importProgress = 0,
                importTotal = result.rewrites.size,
                importedCount = 0,
                failedCount = 0,
                skippedExistingCount = 0,
                retryingCount = 0,
                retriesAttempted = 0,
                currentBurst = 0,
                totalBursts = 0,
                importMode = "создание аккаунта",
                importPhase = ImportProgressPhase.CreatingAccount,
                cooldownRemainingSeconds = 0,
                elapsedText = "00:00",
                etaText = "--:--",
                speedText = "0.00 записей/с",
            )
        }

        importJob = viewModelScope.launch {
            try {
                // Step 1: Create temporary account
                val profile = createTemporaryAccountWithRetry(
                    profileName = _state.value.profileName,
                )

                _state.update {
                    it.copy(
                        isCreatingAccount = false,
                        isImporting = true,
                        isCancelling = false,
                        profile = profile,
                        statusMessage = "Аккаунт создан. Импорт доменов...",
                        importProgress = 0,
                        importTotal = result.rewrites.size,
                    )
                }

                // Step 2: Import rewrites
                resetSpeedEstimator()
                val summary = nextDnsClient.importRewrites(
                    profileId = profile.profileId,
                    rewrites = result.rewrites,
                    concurrency = 2,
                    maxAttempts = 8,
                    onProgress = { progress ->
                        updateImportProgress(progress)
                    },
                )

                _state.update {
                    it.copy(
                        isImporting = false,
                        isCancelling = false,
                        importSummary = summary,
                        statusMessage = buildImportStatusMessage(summary),
                    )
                }

            } catch (e: CancellationException) {
                finishCancelledImport(result.rewrites.size)
            } catch (e: NextDnsApiException) {
                if (!isActive) {
                    finishCancelledImport(result.rewrites.size)
                    return@launch
                }
                AppLogger.d("NextDNS API error: status=${e.statusCode ?: "unknown"}")
                _state.update {
                    it.copy(
                        isCreatingAccount = false,
                        isImporting = false,
                        isCancelling = false,
                        errorMessage = e.message ?: "Ошибка NextDNS API",
                        statusMessage = "Ошибка NextDNS API",
                    )
                }
            } catch (e: Exception) {
                if (!isActive) {
                    finishCancelledImport(result.rewrites.size)
                    return@launch
                }
                AppLogger.e("Import failed", e)
                _state.update {
                    it.copy(
                        isCreatingAccount = false,
                        isImporting = false,
                        isCancelling = false,
                        errorMessage = "Ошибка: ${e.message}",
                        statusMessage = "Ошибка импорта",
                    )
                }
            }
        }
    }

    private fun updateImportProgress(progress: NextDnsImportProgress) {
        val elapsedSeconds = importStartedAt?.elapsedNow()?.inWholeSeconds?.coerceAtLeast(0) ?: 0L
        updateStableSpeed(progress)
        val speed = stableImportSpeed
        val remaining = (progress.total - progress.processed).coerceAtLeast(0)
        val etaSeconds = if (speed > 0.0) {
            (remaining / speed).toLong() + estimateWaitSeconds(progress)
        } else {
            null
        }

        _state.update {
            it.copy(
                importProgress = progress.processed,
                importTotal = progress.total,
                currentDomain = progress.currentDomain,
                importedCount = progress.imported,
                failedCount = progress.failed,
                skippedExistingCount = progress.skippedExisting,
                retryingCount = progress.retrying,
                retriesAttempted = progress.retriesAttempted,
                currentBurst = progress.currentBurst,
                totalBursts = progress.totalBursts,
                importMode = progress.mode,
                importPhase = progress.phase,
                cooldownRemainingSeconds = progress.cooldownRemainingSeconds,
                elapsedText = formatDuration(elapsedSeconds),
                etaText = etaSeconds?.let(::formatDuration) ?: "--:--",
                speedText = "${speed.formatSpeed()} записей/с",
            )
        }
    }

    private fun updateStableSpeed(progress: NextDnsImportProgress) {
        val now = TimeSource.Monotonic.markNow()
        val previousMark = lastSpeedMark
        if (previousMark == null) {
            lastSpeedMark = now
            lastSpeedProcessed = progress.processed
            return
        }

        if (progress.processed > lastSpeedProcessed) {
            val deltaItems = progress.processed - lastSpeedProcessed
            val elapsedMillis = previousMark.elapsedNow().inWholeMilliseconds.coerceAtLeast(250L)
            val sampleSpeed = deltaItems * 1_000.0 / elapsedMillis
            stableImportSpeed = if (stableImportSpeed > 0.0) {
                stableImportSpeed * 0.65 + sampleSpeed * 0.35
            } else {
                sampleSpeed
            }
            lastSpeedProcessed = progress.processed
            lastSpeedMark = now
            return
        }

        // During planned waiting/cooldown there is no active work. Keep the displayed
        // speed stable and do not let the next processed item include idle time.
        if (progress.cooldownRemainingSeconds > 0 || progress.phase == ImportProgressPhase.Resting) {
            lastSpeedMark = now
        }
    }

    private fun estimateWaitSeconds(progress: NextDnsImportProgress): Long {
        val futureBurstRests = when {
            progress.totalBursts <= 0 -> 0
            progress.phase == ImportProgressPhase.Resting ->
                (progress.totalBursts - progress.currentBurst - 1).coerceAtLeast(0)
            else ->
                (progress.totalBursts - progress.currentBurst).coerceAtLeast(0)
        }
        return progress.cooldownRemainingSeconds.toLong() + futureBurstRests * 5L
    }

    private fun resetSpeedEstimator() {
        lastSpeedProcessed = 0
        lastSpeedMark = TimeSource.Monotonic.markNow()
        stableImportSpeed = 0.0
    }

    private fun formatDuration(totalSeconds: Long): String {
        val duration = totalSeconds.seconds
        val hours = duration.inWholeHours
        val minutes = (duration.inWholeMinutes % 60).toString().padStart(2, '0')
        val seconds = (duration.inWholeSeconds % 60).toString().padStart(2, '0')
        return if (hours > 0) {
            "$hours:$minutes:$seconds"
        } else {
            "$minutes:$seconds"
        }
    }

    private fun Double.formatSpeed(): String =
        (kotlin.math.round(this * 100.0) / 100.0).toString().let { value ->
            if ('.' in value) value else "$value.0"
        }

    private suspend fun createTemporaryAccountWithRetry(
        profileName: String,
        maxAttempts: Int = 3,
    ): NextDnsProfile {
        var lastError: NextDnsApiException? = null
        repeat(maxAttempts) { index ->
            val attempt = index + 1
            if (attempt > 1) {
                nextDnsClient.resetSession()
                _state.update {
                    it.copy(statusMessage = "Повтор создания аккаунта NextDNS: попытка $attempt / $maxAttempts")
                }
                delay(1500L * attempt)
            }
            try {
                return nextDnsClient.createTemporaryAccount(profileName = profileName)
            } catch (error: NextDnsApiException) {
                lastError = error
                if (error.statusCode != 500 || attempt >= maxAttempts) {
                    throw error
                }
            }
        }
        throw lastError ?: NextDnsApiException("NextDNS не создал временный аккаунт")
    }

    private fun finishCancelledImport(requested: Int) {
        AppLogger.d("Import cancelled")
        val current = _state.value
        val cancelledSummary = ImportSummary(
            requested = requested,
            imported = current.importProgress,
            skippedExisting = 0,
            failed = 0,
            statusCounts = emptyMap(),
            cancelled = true,
        )
        _state.update {
            it.copy(
                isCreatingAccount = false,
                isImporting = false,
                isCancelling = false,
                importSummary = cancelledSummary,
                statusMessage = "Импорт отменён пользователем. Обработано: ${it.importProgress} / ${it.importTotal}",
            )
        }
    }

    private fun recreateAccountAndImport() {
        if (_state.value.isCreatingAccount || _state.value.isImporting) return
        if (parsedResult == null) {
            parseDomains()
        }
        startImport(resetSession = true)
    }

    private fun cancelImport() {
        if (importJob?.isActive == true) {
            _state.update { it.copy(isCancelling = true, statusMessage = "Отмена импорта...") }
            importJob?.cancel()
        }
    }

    private fun buildImportStatusMessage(summary: ImportSummary): String {
        return when {
            summary.cancelled -> "Импорт отменён"
            summary.verifiedSuccessfully -> "Импорт завершён: все ${summary.requested} записей проверены и добавлены"
            summary.missingAfterVerify.isNotEmpty() -> "Импорт завершён с проблемами: не найдено после проверки ${summary.missingAfterVerify.size} записей"
            summary.failed > 0 -> "Импорт завершён с ошибками: ${summary.failed}"
            else -> "Импорт завершён"
        }
    }

    private fun reset() {
        importJob?.cancel()
        importJob = null
        parsedResult = null
        importStartedAt = null
        resetSpeedEstimator()
        _state.update { NextDnsImportState() }
    }
}
