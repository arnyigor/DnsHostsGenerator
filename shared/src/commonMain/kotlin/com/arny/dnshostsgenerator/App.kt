package com.arny.dnshostsgenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.arny.dnshostsgenerator.presentation.HostsGeneratorEvent
import com.arny.dnshostsgenerator.presentation.HostsGeneratorScreen
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            HostsGeneratorApp()
        }
    }
}

@Composable
fun HostsGeneratorApp(
    viewModel: HostsGeneratorViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    HostsGeneratorScreen(
        presets = state.presets,
        selectedPresetIds = state.selectedPresetIds,
        domainText = state.domainText,
        dedupEnabled = state.dedupEnabled,
        isGenerating = state.isGenerating,
        progressText = state.progressText,
        statusText = state.statusText,
        results = state.results,
        selectedResultIndex = state.selectedResultIndex,

        onPresetChecked = { id, checked ->
            viewModel.onEvent(
                HostsGeneratorEvent.OnPresetChecked(
                    id,
                    checked
                )
            )
        },
        onSelectAllPresets = { viewModel.onEvent(HostsGeneratorEvent.OnSelectAllPresets) },
        onClearPresets = { viewModel.onEvent(HostsGeneratorEvent.OnClearPresets) },
        onDedupChanged = { viewModel.onEvent(HostsGeneratorEvent.OnDedupChanged(it)) },
        onDomainTextChanged = { viewModel.onEvent(HostsGeneratorEvent.OnDomainTextChanged(it)) },
        onGenerate = { viewModel.onEvent(HostsGeneratorEvent.OnGenerate) },
        onResetDomains = { viewModel.onEvent(HostsGeneratorEvent.OnResetDomains) },
        onSelectResult = { viewModel.onEvent(HostsGeneratorEvent.OnSelectResult(it)) },
        onSaveTextFile = { fileName, content ->
            viewModel.onEvent(
                HostsGeneratorEvent.OnSaveTextFile(
                    fileName,
                    content
                )
            )
        },
        onCopyText = { text ->
            clipboardManager.setText(AnnotatedString(text))
            // Если нужно, можно добавить событие в VM, чтобы обновить statusText на "Скопировано"
        }
    )
}
