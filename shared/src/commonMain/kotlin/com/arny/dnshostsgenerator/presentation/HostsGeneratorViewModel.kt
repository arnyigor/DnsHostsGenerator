package com.arny.dnshostsgenerator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.platform.saveTextFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HostsGeneratorState(
    val presets: List<DnsProviderPreset> = BuiltInDnsPresets.defaults,
    val domainText: String = DefaultDomainList.text,
    val selectedPresetIds: Set<String> = setOf(BuiltInDnsPresets.recommendedId),
    val dedupEnabled: Boolean = true,
    val isGenerating: Boolean = false,
    val progressText: String = "Готово к генерации",
    val statusText: String = "По умолчанию выбран один рекомендуемый DNS. Остальные нужны только для сравнения.",
    val results: List<GenerationResult> = emptyList(),
    val selectedResultIndex: Int = 0
) {
    val selectedPresets: List<DnsProviderPreset>
        get() = presets.filter { it.id in selectedPresetIds }

    val selectedResult: GenerationResult?
        get() = results.getOrNull(selectedResultIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0)))
}

sealed interface HostsGeneratorEvent {
    data class OnDomainTextChanged(val text: String) : HostsGeneratorEvent
    data class OnPresetChecked(val id: String, val checked: Boolean) : HostsGeneratorEvent
    data class OnDedupChanged(val enabled: Boolean) : HostsGeneratorEvent
    data class OnSelectResult(val index: Int) : HostsGeneratorEvent
    object OnSelectAllPresets : HostsGeneratorEvent
    object OnClearPresets : HostsGeneratorEvent
    object OnGenerate : HostsGeneratorEvent
    object OnResetDomains : HostsGeneratorEvent
    data class OnSaveTextFile(val fileName: String, val content: String) : HostsGeneratorEvent
}

class HostsGeneratorViewModel(
    private val generator: HostsGenerator // Лучше инжектить через Koin
) : ViewModel() {

    private val _state = MutableStateFlow(HostsGeneratorState())
    val state: StateFlow<HostsGeneratorState> = _state.asStateFlow()

    fun onEvent(event: HostsGeneratorEvent) {
        when (event) {
            is HostsGeneratorEvent.OnDomainTextChanged -> _state.update { it.copy(domainText = event.text) }
            is HostsGeneratorEvent.OnDedupChanged -> _state.update { it.copy(dedupEnabled = event.enabled) }
            is HostsGeneratorEvent.OnSelectResult -> _state.update { it.copy(selectedResultIndex = event.index) }
            is HostsGeneratorEvent.OnPresetChecked -> _state.update {
                val newIds = if (event.checked) it.selectedPresetIds + event.id else it.selectedPresetIds - event.id
                it.copy(selectedPresetIds = newIds)
            }
            HostsGeneratorEvent.OnSelectAllPresets -> _state.update {
                it.copy(selectedPresetIds = it.presets.map { preset -> preset.id }.toSet())
            }
            HostsGeneratorEvent.OnClearPresets -> _state.update {
                it.copy(selectedPresetIds = setOf(BuiltInDnsPresets.recommendedId))
            }
            HostsGeneratorEvent.OnResetDomains -> _state.update {
                it.copy(domainText = DefaultDomainList.text)
            }
            HostsGeneratorEvent.OnGenerate -> generateHosts()
            is HostsGeneratorEvent.OnSaveTextFile -> saveFile(event.fileName, event.content)
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
            val inputLines = currentState.domainText.replace("\r\n", "\n").replace('\r', '\n').split('\n')

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
                                    progress.currentDomain?.let { domain -> append(" — ").append(domain) }
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