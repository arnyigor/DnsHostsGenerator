package com.arny.dnshostsgenerator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.data.db.DomainEntity
import com.arny.dnshostsgenerator.data.db.GroupDao
import com.arny.dnshostsgenerator.data.db.GroupEntity
import com.arny.dnshostsgenerator.data.db.GroupWithDomains
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.platform.saveTextFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val progressText: String = "Готово к генерации",
    val statusText: String = "По умолчанию выбран один рекомендуемый DNS. Остальные нужны только для сравнения.",
    val results: List<GenerationResult> = emptyList(),
    val selectedResultIndex: Int = 0,
    val toastText: String? = null
) {
    val selectedPresets: List<DnsProviderPreset>
        get() = if (selectedPresetIds.isEmpty()) {
            presets.filter { it.id == BuiltInDnsPresets.recommendedId }
        } else {
            presets.filter { it.id in selectedPresetIds }
        }
}

sealed interface UiEffect {
    data class ShowToast(val message: String) : UiEffect
}

sealed interface HostsGeneratorEvent {
    data class OnDomainTextChanged(val text: String) : HostsGeneratorEvent
    data class OnDomainGroupEnabledChanged(val groupId: Long, val enabled: Boolean) :
        HostsGeneratorEvent

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
    object OnGenerate : HostsGeneratorEvent
    object OnResetDomains : HostsGeneratorEvent
    object OnTextCopied : HostsGeneratorEvent
    data class OnSaveTextFile(val fileName: String, val content: String) : HostsGeneratorEvent
}

class HostsGeneratorViewModel(
    private val generator: HostsGenerator,
    private val groupDao: GroupDao,
) : ViewModel() {

    private val _state = MutableStateFlow(HostsGeneratorState())
    val state: StateFlow<HostsGeneratorState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<UiEffect>()
    val effect = _effect.asSharedFlow()

    init {
        observeDomainGroups()
    }

    fun onEvent(event: HostsGeneratorEvent) {
        when (event) {
            is HostsGeneratorEvent.OnDomainTextChanged -> _state.update { it.copy(domainText = event.text) }
            is HostsGeneratorEvent.OnDomainGroupEnabledChanged -> setDomainGroupEnabled(
                event.groupId,
                event.enabled
            )

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
            is HostsGeneratorEvent.OnPresetChecked -> _state.update {
                val newIds =
                    if (event.checked) it.selectedPresetIds + event.id else it.selectedPresetIds - event.id
                it.copy(selectedPresetIds = newIds)
            }

            HostsGeneratorEvent.OnSelectAllPresets -> _state.update {
                it.copy(selectedPresetIds = it.presets.map { preset -> preset.id }.toSet())
            }

            HostsGeneratorEvent.OnClearPresets -> _state.update {
                it.copy(selectedPresetIds = emptySet())
            }

            HostsGeneratorEvent.OnResetDomains -> enableAllDomainGroups()
            HostsGeneratorEvent.OnGenerate -> generateHosts()
            is HostsGeneratorEvent.OnTextCopied -> showToast("Текст скопирован")
            is HostsGeneratorEvent.OnSaveTextFile -> saveFile(event.fileName, event.content)
        }
    }

    fun showToast(text: String) {
        viewModelScope.launch {
            _effect.emit(UiEffect.ShowToast(text))
        }
    }

    private fun observeDomainGroups() {
        viewModelScope.launch {
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

    private fun enableAllDomainGroups() {
        viewModelScope.launch {
            runCatching {
                groupDao.setAllGroupsEnabled(true)
            }.onFailure { error ->
                AppLogger.e("Failed to enable all domain groups", error)
                _state.update {
                    it.copy(statusText = "Ошибка сброса групп: ${error.message ?: error::class.simpleName}")
                }
            }.onSuccess {
                _state.update { it.copy(statusText = "Все группы доменов включены") }
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
        val domains = text.toDomainLines()
        if (domains.isEmpty()) {
            _state.update { it.copy(statusText = "Введите один или несколько доменов") }
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
                _state.update { it.copy(statusText = "Добавлено доменов: ${domains.size}") }
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
        viewModelScope.launch {
            val generatedResults = mutableListOf<GenerationResult>()
            val inputLines =
                currentState.domainText.replace("\r\n", "\n").replace('\r', '\n').split('\n')

            runCatching {
                currentState.selectedPresets.forEachIndexed { index, preset ->
                    _state.update { it.copy(progressText = "${index + 1}/${currentState.selectedPresets.size}: ${preset.title}") }

                    val result = generator.generate(
                        presetTitle = preset.title,
                        request = GenerateHostsRequest(
                            primaryDns = preset.primaryDns,
                            checkDns = preset.checkDns,
                            inputLines = inputLines,
                            outputFileName = preset.outputFileName,
                            dedupEnabled = currentState.dedupEnabled,
                        ),
                    ) { progress ->
                        _state.update {
                            it.copy(
                                progressText = buildString {
                                    append("${preset.title}: ${progress.processedLines}/${progress.totalLines}")
                                    progress.currentDomain?.let { domain ->
                                        append(" — ").append(
                                            domain
                                        )
                                    }
                                }
                            )
                        }
                    }
                    generatedResults += result
                    _state.update {
                        it.copy(
                            results = generatedResults.toList(),
                            selectedResultIndex = generatedResults.lastIndex
                        )
                    }
                }
            }.onFailure { error ->
                AppLogger.e("Generation failed unexpectedly", error)
                _state.update { it.copy(statusText = "Ошибка генерации: ${error.message ?: error::class.simpleName}") }
            }.onSuccess {
                if (generatedResults.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            statusText = if (generatedResults.size == 1) {
                                "Готов один hosts-файл: ${generatedResults.first().outputFileName}"
                            } else {
                                "Сравнение завершено: ${generatedResults.size} вариантов"
                            }
                        )
                    }
                }
            }

            _state.update { it.copy(isGenerating = false, progressText = "Готово") }
        }
    }

    private fun saveFile(fileName: String, content: String) {
        viewModelScope.launch {
            val saveResult = saveTextFile(fileName, content) // Твой платформенный метод
            _state.update { it.copy(statusText = saveResult.message) }
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

private fun String.toDomainLines(): List<String> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .mapNotNull { line -> line.toDomainLineOrNull() }
        .distinct()

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
