package com.arny.dnshostsgenerator.controld

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Импорт hosts-правил в Control D: токен → профиль → SPOOF-правила.
 * Анонимного аккаунта у Control D нет, поэтому токен вводит пользователь.
 */
data class ControlDImportState(
    val token: String = "",
    val profiles: List<ControlDProfile> = emptyList(),
    val selectedProfilePk: String? = null,
    val isLoadingProfiles: Boolean = false,
    val isImporting: Boolean = false,
    val statusMessage: String = "Вставьте API-токен Control D (dashboard → API)",
    val errorMessage: String? = null,
    val lastResult: ControlDImportResult? = null,
)

sealed interface ControlDImportEvent {
    data class OnTokenChanged(val token: String) : ControlDImportEvent
    data object OnLoadProfiles : ControlDImportEvent
    data class OnSelectProfile(val pk: String) : ControlDImportEvent
    /** Импорт готового hosts-текста (результат генерации). */
    data class OnImport(val hostsText: String) : ControlDImportEvent
}

class ControlDImportViewModel(
    private val client: ControlDClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ControlDImportState())
    val state: StateFlow<ControlDImportState> = _state.asStateFlow()

    fun onEvent(event: ControlDImportEvent) {
        when (event) {
            is ControlDImportEvent.OnTokenChanged -> _state.update {
                it.copy(token = event.token.trim(), errorMessage = null)
            }

            ControlDImportEvent.OnLoadProfiles -> loadProfiles()
            is ControlDImportEvent.OnSelectProfile -> _state.update {
                it.copy(selectedProfilePk = event.pk)
            }

            is ControlDImportEvent.OnImport -> importHosts(event.hostsText)
        }
    }

    private fun loadProfiles() {
        val token = _state.value.token
        if (token.isBlank()) {
            _state.update { it.copy(errorMessage = "Сначала вставьте API-токен") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoadingProfiles = true,
                    errorMessage = null,
                    statusMessage = "Загружаю профили…",
                )
            }
            try {
                val profiles = client.listProfiles(token)
                _state.update {
                    it.copy(
                        isLoadingProfiles = false,
                        profiles = profiles,
                        selectedProfilePk = it.selectedProfilePk
                            ?: profiles.firstOrNull()?.pk,
                        statusMessage = if (profiles.isEmpty()) {
                            "В аккаунте нет профилей"
                        } else {
                            "Профилей: ${profiles.size}. Выберите профиль и запустите импорт"
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ControlD: failed to load profiles", error)
                _state.update {
                    it.copy(
                        isLoadingProfiles = false,
                        statusMessage = "Не удалось загрузить профили",
                        errorMessage = error.message ?: error::class.simpleName,
                    )
                }
            }
        }
    }

    private fun importHosts(hostsText: String) {
        val token = _state.value.token
        val pk = _state.value.selectedProfilePk
        if (token.isBlank()) {
            _state.update { it.copy(errorMessage = "Сначала вставьте API-токен") }
            return
        }
        if (pk == null) {
            _state.update { it.copy(errorMessage = "Сначала загрузите и выберите профиль") }
            return
        }
        val entries = parseHostEntries(hostsText)
        if (entries.isEmpty()) {
            _state.update {
                it.copy(errorMessage = "Нет hosts-строк для импорта: сначала сгенерируйте результат")
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    lastResult = null,
                    statusMessage = "Заливаю ${entries.size} правил…",
                )
            }
            try {
                val result = client.importHosts(token, pk, entries) { done, total ->
                    _state.update { it.copy(statusMessage = "Залито $done из $total…") }
                }
                _state.update {
                    it.copy(
                        isImporting = false,
                        lastResult = result,
                        statusMessage = buildString {
                            append("Создано правил: ${result.createdRules}")
                            if (result.failures.isNotEmpty()) {
                                append(", ошибок: ${result.failures.size}")
                            }
                        },
                        errorMessage = result.failures.firstOrNull(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ControlD: import failed", error)
                _state.update {
                    it.copy(
                        isImporting = false,
                        statusMessage = "Импорт не удался",
                        errorMessage = error.message ?: error::class.simpleName,
                    )
                }
            }
        }
    }
}
