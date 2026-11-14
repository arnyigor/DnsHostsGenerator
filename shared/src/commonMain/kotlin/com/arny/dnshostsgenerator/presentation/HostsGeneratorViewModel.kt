package com.arny.dnshostsgenerator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.data.customPresetFileName
import com.arny.dnshostsgenerator.data.mergePresets
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.data.db.CustomPresetEntity
import com.arny.dnshostsgenerator.data.db.DomainEntity
import com.arny.dnshostsgenerator.data.db.GroupDao
import com.arny.dnshostsgenerator.data.db.GroupEntity
import com.arny.dnshostsgenerator.data.db.GroupWithDomains
import com.arny.dnshostsgenerator.data.db.PresetDao
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus
import com.arny.dnshostsgenerator.domain.DnsChecker
import com.arny.dnshostsgenerator.domain.DnsPresetUnusableException
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.domain.resolvePerHostMerged
import com.arny.dnshostsgenerator.domain.ProfileChecker
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.nextdns.HostsParser
import com.arny.dnshostsgenerator.platform.saveTextFile
import com.arny.dnshostsgenerator.platform.updateSystemHosts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Пользователь часто вставляет адрес из браузера или из инструкции провайдера.
 * Оставляем только хост/адрес: схема, путь и порт DoT добавляются приложением.
 */
internal fun normalizeDnsAddress(raw: String): String {
    var value = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .trim()
    // Порт отбрасываем только у IPv4/хоста: у IPv6 двоеточий несколько, и резать по
    // первому нельзя — получится невалидный адрес.
    if (value.count { it == ':' } == 1) {
        value = Regex(":\\d{1,5}$").find(value)?.let { value.removeRange(it.range) } ?: value
    }
    return value.trim()
}

data class DomainUi(
    val id: Long,
    val domain: String,
    val isCustom: Boolean,
)

data class DomainGroupUi(
    val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val domains: List<DomainUi>,
)

data class HostsGeneratorState(
    val presets: List<DnsProviderPreset> = BuiltInDnsPresets.defaults,
    val domainText: String = DefaultDomainList.text,
    val domainGroups: List<DomainGroupUi> = emptyList(),
    val selectedPresetIds: Set<String> = emptySet(),
    val dedupEnabled: Boolean = true,
    val isGenerating: Boolean = false,
    /** Доля выполненной генерации 0..1 (по всем выбранным пресетам); null — генерация не идёт. */
    val progress: Float? = null,
    val progressText: String = "Готово к генерации",
    val statusText: String = "По умолчанию выбран один рекомендуемый DNS. Остальные нужны только для сравнения.",
    val results: List<GenerationResult> = emptyList(),
    val selectedResultIndex: Int = 0,
    val toastText: String? = null,
    /** Блок «Проверка DNS»: какой сервер сейчас реально работает. */
    val dnsCheck: DnsCheckUiState = DnsCheckUiState(),
    /** Блок «Правила для приложения»: подбор одного адреса под все домены профиля. */
    val appProfile: AppProfileUiState = AppProfileUiState(),
) {
    val selectedPresets: List<DnsProviderPreset>
        get() = if (selectedPresetIds.isEmpty()) {
            presets.filter { it.id == BuiltInDnsPresets.RECOMMENDED_ID && it.enabledByDefault }
        } else {
            // Отключённые пресеты не участвуют в генерации, даже если остались в выбранных.
            presets.filter { it.id in selectedPresetIds && it.enabledByDefault }
        }
}

sealed interface UiEffect {
    data class ShowToast(val message: String) : UiEffect
}

sealed interface HostsGeneratorEvent {
    data class OnDomainTextChanged(val text: String) : HostsGeneratorEvent
    data class OnDomainGroupEnabledChanged(val groupId: Long, val enabled: Boolean) :
        HostsGeneratorEvent
    data class OnSetAllDomainGroupsEnabled(val enabled: Boolean) : HostsGeneratorEvent

    data class OnAddDomainGroup(val name: String) : HostsGeneratorEvent
    data class OnRenameDomainGroup(val groupId: Long, val name: String) : HostsGeneratorEvent
    data class OnDeleteDomainGroup(val groupId: Long) : HostsGeneratorEvent
    data class OnAddDomains(val groupId: Long, val text: String) : HostsGeneratorEvent
    data class OnRenameDomain(val domainId: Long, val domain: String) : HostsGeneratorEvent
    data class OnDeleteDomain(val domainId: Long) : HostsGeneratorEvent
    data class OnPresetChecked(val id: String, val checked: Boolean) : HostsGeneratorEvent
    data class OnDedupChanged(val enabled: Boolean) : HostsGeneratorEvent
    data class OnSelectResult(val index: Int) : HostsGeneratorEvent
    object OnSelectAllPresets : HostsGeneratorEvent
    object OnClearPresets : HostsGeneratorEvent
    /** Свой DNS-провайдер: адрес вводится вручную, хранится в БД. */
    data class OnAddCustomPreset(
        val title: String,
        val primaryDns: String,
        val dotHost: String?,
    ) : HostsGeneratorEvent

    data class OnDeleteCustomPreset(val id: String) : HostsGeneratorEvent
    data class OnDnsCheckDomainChanged(val domain: String) : HostsGeneratorEvent
    /** Подбор адреса для профиля приложения на выбранном пресете. */
    object OnAppProfileCheck : HostsGeneratorEvent

    /** Переключение набора доменов в карточке правил: ChatGPT или Gemini. */
    data class OnAppProfileSelected(val profileId: String) : HostsGeneratorEvent
    object OnDnsCheck : HostsGeneratorEvent
    object OnDnsCheckCancel : HostsGeneratorEvent
    object OnGenerate : HostsGeneratorEvent
    object OnCancelGeneration : HostsGeneratorEvent
    object OnTextCopied : HostsGeneratorEvent
    data class OnSaveTextFile(val fileName: String, val content: String) : HostsGeneratorEvent
    data class OnUpdateSystemHosts(val content: String) : HostsGeneratorEvent
}

class HostsGeneratorViewModel(
    private val generator: HostsGenerator,
    private val groupDao: GroupDao,
    private val presetDao: PresetDao,
    private val dnsChecker: DnsChecker,
    private val profileChecker: ProfileChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(HostsGeneratorState())
    val state: StateFlow<HostsGeneratorState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<UiEffect>()
    val effect = _effect.asSharedFlow()

    private var generationJob: Job? = null
    private var dnsCheckJob: Job? = null
    private var appProfileJob: Job? = null

    init {
        observeDomainGroups()
        observeCustomPresets()
    }

    fun onEvent(event: HostsGeneratorEvent) {
        when (event) {
            is HostsGeneratorEvent.OnDomainTextChanged -> _state.update { it.copy(domainText = event.text) }
            is HostsGeneratorEvent.OnDomainGroupEnabledChanged -> setDomainGroupEnabled(
                event.groupId,
                event.enabled
            )
            is HostsGeneratorEvent.OnSetAllDomainGroupsEnabled -> setAllDomainGroupsEnabled(event.enabled)

            is HostsGeneratorEvent.OnAddDomainGroup -> addDomainGroup(event.name)
            is HostsGeneratorEvent.OnRenameDomainGroup -> renameDomainGroup(
                event.groupId,
                event.name
            )

            is HostsGeneratorEvent.OnDeleteDomainGroup -> deleteDomainGroup(event.groupId)
            is HostsGeneratorEvent.OnAddDomains -> addDomains(event.groupId, event.text)
            is HostsGeneratorEvent.OnRenameDomain -> renameDomain(event.domainId, event.domain)
            is HostsGeneratorEvent.OnDeleteDomain -> deleteDomain(event.domainId)
            is HostsGeneratorEvent.OnDedupChanged -> _state.update { it.copy(dedupEnabled = event.enabled) }
            is HostsGeneratorEvent.OnSelectResult -> _state.update { it.copy(selectedResultIndex = event.index) }
            is HostsGeneratorEvent.OnPresetChecked -> _state.update { state ->
                val preset = state.presets.firstOrNull { it.id == event.id }
                if (preset != null && !preset.enabledByDefault) {
                    // Отключённый пресет нельзя включить галочкой: говорим почему и ничего не меняем.
                    state.copy(
                        statusText = "Пресет «${preset.title}» отключён: " +
                            (preset.disabledReason ?: "провайдер сейчас не работает корректно"),
                    )
                } else {
                    val newIds =
                        if (event.checked) state.selectedPresetIds + event.id
                        else state.selectedPresetIds - event.id
                    state.copy(selectedPresetIds = newIds)
                }
            }

            HostsGeneratorEvent.OnSelectAllPresets -> _state.update {
                it.copy(selectedPresetIds = it.presets.filter { preset -> preset.enabledByDefault }.map { preset -> preset.id }.toSet())
            }

            HostsGeneratorEvent.OnClearPresets -> _state.update {
                it.copy(selectedPresetIds = emptySet())
            }

            is HostsGeneratorEvent.OnAppProfileSelected -> _state.update {
                // Результат прошлого профиля к новому не относится: иначе можно скопировать
                // правила ChatGPT, думая, что это Gemini.
                it.copy(
                    appProfile = it.appProfile.copy(
                        profileId = event.profileId,
                        result = null,
                        progressText = "Один адрес для всех доменов приложения",
                    ),
                )
            }

            is HostsGeneratorEvent.OnAddCustomPreset -> addCustomPreset(event)
            is HostsGeneratorEvent.OnDeleteCustomPreset -> deleteCustomPreset(event.id)

            is HostsGeneratorEvent.OnDnsCheckDomainChanged -> _state.update {
                it.copy(dnsCheck = it.dnsCheck.copy(domain = event.domain))
            }

            HostsGeneratorEvent.OnAppProfileCheck -> runAppProfileCheck()
            HostsGeneratorEvent.OnDnsCheck -> runDnsCheck()
            HostsGeneratorEvent.OnDnsCheckCancel -> {
                dnsCheckJob?.cancel()
                _state.update {
                    it.copy(dnsCheck = it.dnsCheck.copy(isRunning = false, progressText = "Проверка остановлена"))
                }
            }

            HostsGeneratorEvent.OnGenerate -> generateHosts()
            HostsGeneratorEvent.OnCancelGeneration -> generationJob?.cancel()
            is HostsGeneratorEvent.OnTextCopied -> showToast("Текст скопирован")
            is HostsGeneratorEvent.OnSaveTextFile -> saveFile(event.fileName, event.content)
            is HostsGeneratorEvent.OnUpdateSystemHosts -> updateHostsFile(event.content)
        }
    }

    fun showToast(text: String) {
        viewModelScope.launch {
            _effect.emit(UiEffect.ShowToast(text))
        }
    }

    /**
     * Проверяет все DNS-пресеты по одному домену: жив ли сервер и обслуживает ли выданный
     * адрес этот домен. Результат сортируется: рабочие сверху, по времени ответа.
     */
    /**
     * Подбирает один адрес, который обслуживает ВСЕ домены профиля приложения.
     * Проверяется только выбранный пресет: правило подмены строится из одного адреса,
     * поэтому смешивать серверы нельзя.
     */
    private fun runAppProfileCheck() {
        // Адрес подбирается не «один на всё приложение» и не только на первом пресете:
        // разные провайдеры обслуживают разные домены (14.11.2026: ChatGPT — bezmezhau,
        // Gemini — dns.dns-ai.ru), поэтому перебираем пресеты и сливаем результат по доменам.
        val state = _state.value
        val allEnabled = state.presets.filter { it.enabledByDefault }
        val presetsToTry = state.selectedPresets + allEnabled.filter { it.id !in state.selectedPresetIds }
        if (presetsToTry.isEmpty()) {
            _state.update {
                it.copy(appProfile = it.appProfile.copy(progressText = "Сначала выберите DNS-пресет"))
            }
            return
        }
        val profile = AppProfileHolder.profile(state.appProfile.profileId)
        appProfileJob?.cancel()
        _state.update {
            it.copy(
                appProfile = it.appProfile.copy(
                    isRunning = true,
                    result = null,
                    progressText = "Подбираю адреса для ${profile.title}…",
                ),
            )
        }
        appProfileJob = viewModelScope.launch {
            try {
                val merged = profileChecker.resolvePerHostMerged(
                    profile = profile,
                    presets = presetsToTry,
                    onProgress = { done, total, presetTitle ->
                        _state.update {
                            it.copy(
                                appProfile = it.appProfile.copy(
                                    progressText = "Подобрано $done из $total, проверяю $presetTitle…",
                                ),
                            )
                        }
                    },
                )
                AppLogger.d(
                    "App profile merged: profile=${profile.title}, ok=${merged.entries.size}, " +
                        "failed=${merged.failed.size}, presets=${merged.presetTitles}, " +
                        "ms=${merged.elapsedMs}",
                )
                _state.update {
                    it.copy(
                        appProfile = it.appProfile.copy(
                            isRunning = false,
                            result = merged,
                            progressText = if (merged.isUsable) {
                                "Адресов: ${merged.entries.size} из " +
                                    "${merged.entries.size + merged.failed.size}" +
                                    " · пресеты: ${merged.presetTitles.joinToString()}"
                            } else {
                                "Рабочих адресов не найдено: ни один пресет не ответил за " +
                                    "${merged.elapsedMs / 1000} с"
                            },
                        ),
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(appProfile = it.appProfile.copy(isRunning = false, progressText = "Подбор остановлен")) }
                throw error
            } catch (error: Exception) {
                AppLogger.e("App profile check failed", error)
                _state.update {
                    it.copy(
                        appProfile = it.appProfile.copy(
                            isRunning = false,
                            progressText = "Ошибка: ${error.message ?: error::class.simpleName}",
                        ),
                    )
                }
            }
        }
    }

    private fun runDnsCheck() {
        val rawDomain = _state.value.dnsCheck.domain.trim()
        val domain = rawDomain
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        if (domain.isBlank() || '.' !in domain) {
            _state.update {
                it.copy(dnsCheck = it.dnsCheck.copy(progressText = "Укажите домен, например gemini.google.com"))
            }
            return
        }
        val presets = _state.value.presets
        dnsCheckJob?.cancel()
        _state.update {
            it.copy(
                dnsCheck = it.dnsCheck.copy(
                    domain = domain,
                    isRunning = true,
                    results = emptyList(),
                    progressText = "Проверяю ${presets.size} DNS…",
                ),
            )
        }
        dnsCheckJob = viewModelScope.launch {
            try {
                val results = dnsChecker.check(presets = presets, domain = domain)
                _state.update {
                    it.copy(
                        dnsCheck = it.dnsCheck.copy(
                            isRunning = false,
                            results = results,
                            progressText = summarizeDnsCheck(results, domain),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                _state.update {
                    it.copy(dnsCheck = it.dnsCheck.copy(isRunning = false, progressText = "Проверка остановлена"))
                }
                throw error
            } catch (error: Exception) {
                AppLogger.e("DNS check failed: domain=$domain", error)
                _state.update {
                    it.copy(
                        dnsCheck = it.dnsCheck.copy(
                            isRunning = false,
                            progressText = "Ошибка проверки: ${error.message ?: error::class.simpleName}",
                        ),
                    )
                }
            }
        }
    }

    private fun summarizeDnsCheck(results: List<DnsCheckResult>, domain: String): String {
        val works = results.count { it.status == DnsCheckStatus.Works }
        return if (results.isEmpty()) {
            "Нет DNS для проверки"
        } else {
            "Работает $works из ${results.size} · $domain"
        }
    }

    private fun observeCustomPresets() {
        viewModelScope.launch {
            presetDao.getAll()
                .catch { error ->
                    AppLogger.e("Failed to observe custom presets", error)
                    _state.update {
                        it.copy(
                            statusText = "Ошибка загрузки своих DNS: " +
                                (error.message ?: error::class.simpleName),
                        )
                    }
                }
                .collect { rows ->
                    _state.update { it.copy(presets = mergePresets(rows)) }
                }
        }
    }

    private fun addCustomPreset(event: HostsGeneratorEvent.OnAddCustomPreset) {
        val title = event.title.trim()
        val address = normalizeDnsAddress(event.primaryDns)
        val dotHost = normalizeDnsAddress(event.dotHost.orEmpty()).takeIf { it.isNotEmpty() }
        if (title.isEmpty() || address.isEmpty()) {
            _state.update { it.copy(statusText = "Укажите название и адрес DNS") }
            return
        }
        val id = "custom_" + Random.nextLong().toULong().toString(16)
        val entity = CustomPresetEntity(
            id = id,
            title = title,
            primaryDns = address,
            dotHost = dotHost,
            checkDns = address,
            outputFileName = customPresetFileName(title, id),
            allowInvalidTls = false,
        )
        viewModelScope.launch {
            runCatching { presetDao.upsert(entity) }
                .onSuccess {
                    // Свой провайдер сразу выбран: иначе после добавления ничего не изменится.
                    _state.update {
                        it.copy(
                            selectedPresetIds = it.selectedPresetIds + id,
                            statusText = "Добавлен свой DNS: $title",
                        )
                    }
                }
                .onFailure { error ->
                    AppLogger.e("Failed to add custom preset", error)
                    _state.update { it.copy(statusText = "Не удалось сохранить свой DNS") }
                }
        }
    }

    private fun deleteCustomPreset(id: String) {
        viewModelScope.launch {
            runCatching { presetDao.delete(id) }
                .onSuccess {
                    _state.update {
                        it.copy(selectedPresetIds = it.selectedPresetIds - id)
                    }
                }
                .onFailure { error ->
                    AppLogger.e("Failed to delete custom preset", error)
                    _state.update { it.copy(statusText = "Не удалось удалить свой DNS") }
                }
        }
    }

    private fun observeDomainGroups() {        viewModelScope.launch {
            groupDao.getAllGroupsWithDomains()
                .catch { error ->
                    AppLogger.e("Failed to observe domain groups", error)
                    _state.update {
                        it.copy(statusText = "Ошибка загрузки групп доменов: ${error.message ?: error::class.simpleName}")
                    }
                }
                .collect { groups ->
                    val domainGroups = groups.toDomainGroupUi()
                    _state.update {
                        it.copy(
                            domainGroups = domainGroups,
                            domainText = if (domainGroups.isEmpty()) {
                                it.domainText
                            } else {
                                domainGroups.toEnabledDomainText()
                            },
                        )
                    }
                }
        }
    }

    private fun setDomainGroupEnabled(groupId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                groupDao.setGroupEnabled(groupId, enabled)
            }.onFailure { error ->
                AppLogger.e(
                    "Failed to update domain group enabled state: groupId=$groupId, enabled=$enabled",
                    error
                )
                _state.update {
                    it.copy(statusText = "Ошибка обновления группы: ${error.message ?: error::class.simpleName}")
                }
            }
        }
    }

    private fun setAllDomainGroupsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                groupDao.setAllGroupsEnabled(enabled)
            }.onFailure { error ->
                AppLogger.e("Failed to set all domain groups enabled: enabled=$enabled", error)
                _state.update {
                    it.copy(statusText = "Ошибка обновления групп: ${error.message ?: error::class.simpleName}")
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        statusText = if (enabled) {
                            "Все группы доменов включены"
                        } else {
                            "Все группы доменов выключены"
                        }
                    )
                }
            }
        }
    }

    private fun addDomainGroup(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(statusText = "Введите название группы") }
            return
        }
        viewModelScope.launch {
            runCatching {
                groupDao.insertGroup(GroupEntity(name = cleanName, isEnabled = true))
            }.onFailure { error ->
                AppLogger.e("Failed to add domain group: name=$cleanName", error)
                _state.update { it.copy(statusText = "Ошибка добавления группы: ${error.message ?: error::class.simpleName}") }
            }.onSuccess { id ->
                _state.update {
                    it.copy(
                        statusText = if (id == -1L) {
                            "Группа уже существует: $cleanName"
                        } else {
                            "Группа добавлена: $cleanName"
                        }
                    )
                }
            }
        }
    }

    private fun renameDomainGroup(groupId: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _state.update { it.copy(statusText = "Название группы не может быть пустым") }
            return
        }
        viewModelScope.launch {
            runCatching {
                groupDao.updateGroupName(groupId, cleanName)
            }.onFailure { error ->
                AppLogger.e(
                    "Failed to rename domain group: groupId=$groupId, name=$cleanName",
                    error
                )
                _state.update { it.copy(statusText = "Ошибка переименования группы: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                _state.update { it.copy(statusText = "Группа переименована: $cleanName") }
            }
        }
    }

    private fun deleteDomainGroup(groupId: Long) {
        viewModelScope.launch {
            runCatching {
                groupDao.deleteGroup(groupId)
            }.onFailure { error ->
                AppLogger.e("Failed to delete domain group: groupId=$groupId", error)
                _state.update { it.copy(statusText = "Ошибка удаления группы: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                _state.update { it.copy(statusText = "Группа удалена") }
            }
        }
    }

    private fun addDomains(groupId: Long, text: String) {
        // Список может быть в любом из диалектов, встречающихся в теме (hosts, dnsmasq,
        // AdGuard/zapret, plain). Для групп важны только домены, поэтому loopback-цели
        // (127.0.0.1/0.0.0.0 в блокирующих списках) не отбрасываем.
        // Прежняя реализация теряла hosts-строки целиком, а adguard-правила
        // («||d^$dnsrewrite=…») записывала в БД как «домены».
        val parsed = HostsParser.parseInput(
            text = text,
            defaultTarget = "127.0.0.1",
            skipLoopback = false,
        )
        val domains = parsed.rewrites.map { it.domain }.distinct()
        if (domains.isEmpty()) {
            val reason = parsed.issues.firstOrNull()?.let { issue ->
                ": ${issue.reason} (строка ${issue.lineNumber ?: "?"})"
            } ?: ""
            _state.update {
                it.copy(
                    statusText = if (parsed.issues.isEmpty()) {
                        "Введите один или несколько доменов"
                    } else {
                        "Не распознан ни один домен$reason"
                    },
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                groupDao.insertDomains(
                    domains.map { domain ->
                        DomainEntity(
                            domain = domain,
                            groupId = groupId,
                            isCustom = true,
                        )
                    }
                )
            }.onFailure { error ->
                AppLogger.e("Failed to add domains: groupId=$groupId", error)
                _state.update { it.copy(statusText = "Ошибка добавления доменов: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                _state.update {
                    it.copy(
                        statusText = buildString {
                            append("Добавлено доменов: ${domains.size}")
                            if (parsed.issues.isNotEmpty()) append("; нераспознанных строк: ${parsed.issues.size}")
                            if (parsed.conflicts.isNotEmpty()) append("; конфликтов IP: ${parsed.conflicts.size}")
                        },
                    )
                }
            }
        }
    }

    private fun renameDomain(domainId: Long, domain: String) {
        val cleanDomain = domain.toDomainLineOrNull()
        if (cleanDomain == null) {
            _state.update { it.copy(statusText = "Введите корректный домен") }
            return
        }
        viewModelScope.launch {
            runCatching {
                groupDao.updateDomain(domainId, cleanDomain)
            }.onFailure { error ->
                AppLogger.e(
                    "Failed to rename domain: domainId=$domainId, domain=$cleanDomain",
                    error
                )
                _state.update { it.copy(statusText = "Ошибка изменения домена: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                _state.update { it.copy(statusText = "Домен изменён: $cleanDomain") }
            }
        }
    }

    private fun deleteDomain(domainId: Long) {
        viewModelScope.launch {
            runCatching {
                groupDao.deleteDomain(domainId)
            }.onFailure { error ->
                AppLogger.e("Failed to delete domain: domainId=$domainId", error)
                _state.update { it.copy(statusText = "Ошибка удаления домена: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                _state.update { it.copy(statusText = "Домен удалён") }
            }
        }
    }

    private fun generateHosts() {
        val currentState = _state.value
        if (currentState.isGenerating || currentState.selectedPresets.isEmpty()) return

        _state.update {
            it.copy(
                isGenerating = true,
                progress = 0f,
                results = emptyList(),
                selectedResultIndex = 0,
                statusText = if (it.selectedPresets.size == 1) {
                    "Запущена генерация hosts через ${it.selectedPresets.first().title}"
                } else {
                    "Запущено сравнение для ${it.selectedPresets.size} DNS-провайдеров"
                }
            )
        }

        // viewModelScope гарантирует, что если UI уничтожится, тяжелый процесс DNS-резолвинга прервется
        generationJob = viewModelScope.launch {
            val generatedResults = mutableListOf<GenerationResult>()
            val presets = currentState.selectedPresets
            val inputLines = HostsParser.normalizeToDomainLines(currentState.domainText)

            try {
                presets.forEachIndexed { index, preset ->
                    val result = try {
                        generator.generate(
                            presetTitle = preset.title,
                            request = GenerateHostsRequest(
                                primaryDns = preset.primaryDns,
                                primaryDotHost = preset.dotHost,
                                primaryAllowInvalidTls = preset.allowInvalidTls,
                                checkDns = preset.checkDns,
                                inputLines = inputLines,
                                outputFileName = preset.outputFileName,
                                dedupEnabled = currentState.dedupEnabled,
                            ),
                        ) { progress ->
                            val presetFraction = if (progress.totalLines > 0) {
                                progress.processedLines.toFloat() / progress.totalLines
                            } else {
                                0f
                            }
                            _state.update {
                                it.copy(
                                    progress = (index + presetFraction) / presets.size,
                                    progressText = buildString {
                                        if (presets.size > 1) {
                                            append("${index + 1}/${presets.size} · ")
                                        }
                                        append("${preset.title}: ${progress.processedLines}/${progress.totalLines}")
                                        progress.currentDomain?.let { domain ->
                                            append(" — ").append(domain)
                                        }
                                    }
                                )
                            }
                        }
                    } catch (unusable: DnsPresetUnusableException) {
                        // Пресет отвечает 0.0.0.0 на всё — это блокировка, а не разблокировка.
                        // Отменяем генерацию по нему, остальные пресеты доводим до конца.
                        AppLogger.e("Preset unusable, generation cancelled: ${unusable.presetTitle}", unusable)
                        _state.update {
                            it.copy(
                                statusText = "Пресет «${unusable.presetTitle}» сейчас не работает корректно: " +
                                    "DNS отвечает 0.0.0.0 на все домены (${unusable.blockedCount}). " +
                                    "Генерация по нему отменена — выбери другой DNS.",
                            )
                        }
                        return@forEachIndexed
                    }
                    generatedResults += result
                    _state.update {
                        it.copy(
                            results = generatedResults.toList(),
                            selectedResultIndex = generatedResults.lastIndex
                        )
                    }
                }

                if (generatedResults.isNotEmpty()) {
                    val hasSuspiciousForwarding = generatedResults.any { it.stats.suspiciousForwarding }
                    _state.update {
                        it.copy(
                            statusText = buildString {
                                if (generatedResults.size == 1) {
                                    append("Готов один hosts-файл: ${generatedResults.first().outputFileName}")
                                } else {
                                    append("Сравнение завершено: ${generatedResults.size} вариантов")
                                }
                                if (hasSuspiciousForwarding) {
                                    append(
                                        " | Внимание: почти все домены помечены #forwarded — DNS-запросы " +
                                            "могут перехватываться (VPN/провайдер). Результат недостоверен.",
                                    )
                                }
                            },
                        )
                    }
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(statusText = "Генерация остановлена") }
                throw error
            } catch (error: Exception) {
                AppLogger.e("Generation failed unexpectedly", error)
                _state.update { it.copy(statusText = "Ошибка генерации: ${error.message ?: error::class.simpleName}") }
            } finally {
                _state.update { it.copy(isGenerating = false, progress = null, progressText = "Готово") }
            }
        }
    }

    private fun saveFile(fileName: String, content: String) {
        viewModelScope.launch {
            val saveResult = saveTextFile(fileName, content) // Твой платформенный метод
            _state.update { it.copy(statusText = saveResult.message) }
            _effect.emit(UiEffect.ShowToast(saveResult.message))
        }
    }

    private fun updateHostsFile(content: String) {
        viewModelScope.launch {
            val saveResult = updateSystemHosts(content)
            _state.update { it.copy(statusText = saveResult.message) }
            _effect.emit(UiEffect.ShowToast(saveResult.message))
        }
    }
}

private fun List<GroupWithDomains>.toDomainGroupUi(): List<DomainGroupUi> =
    map { groupWithDomains ->
        DomainGroupUi(
            id = groupWithDomains.group.id,
            name = groupWithDomains.group.name,
            isEnabled = groupWithDomains.group.isEnabled,
            domains = groupWithDomains.domains
                .sortedBy { domain -> domain.id }
                .map { domain ->
                    DomainUi(
                        id = domain.id,
                        domain = domain.domain,
                        isCustom = domain.isCustom,
                    )
                },
        )
    }

private fun List<DomainGroupUi>.toEnabledDomainText(): String =
    filter { group -> group.isEnabled }
        .joinToString(separator = "\n\n") { group ->
            buildString {
                appendLine("# --- ${group.name} ---")
                group.domains.forEach { domain ->
                    appendLine(domain.domain)
                }
            }.trimEnd()
        }

private fun String.toDomainLineOrNull(): String? {
    val domain = trim()
        .substringBefore('#')
        .trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .trim()
        .lowercase()
    return domain.takeIf { it.isNotBlank() && '.' in it && ' ' !in it }
}
