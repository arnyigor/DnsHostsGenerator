package com.arny.dnshostsgenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.arny.dnshostsgenerator.platform.Notify
import com.arny.dnshostsgenerator.presentation.HostsGeneratorEvent
import com.arny.dnshostsgenerator.presentation.HostsGeneratorScreen
import com.arny.dnshostsgenerator.presentation.HostsGeneratorViewModel
import com.arny.dnshostsgenerator.presentation.UiEffect
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
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is UiEffect.ShowToast -> toastMessage = effect.message
            }
        }
    }

    toastMessage?.let { message ->
        Notify(message)
        LaunchedEffect(message) {
            toastMessage = null
        }
    }

    HostsGeneratorScreen(
        presets = state.presets,
        selectedPresetIds = state.selectedPresetIds,
        domainText = state.domainText,
        domainGroups = state.domainGroups,
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
        onDomainGroupEnabledChanged = { groupId, enabled ->
            viewModel.onEvent(
                HostsGeneratorEvent.OnDomainGroupEnabledChanged(
                    groupId,
                    enabled,
                )
            )
        },
        onAddDomainGroup = { name ->
            viewModel.onEvent(HostsGeneratorEvent.OnAddDomainGroup(name))
        },
        onRenameDomainGroup = { groupId, name ->
            viewModel.onEvent(HostsGeneratorEvent.OnRenameDomainGroup(groupId, name))
        },
        onDeleteDomainGroup = { groupId ->
            viewModel.onEvent(HostsGeneratorEvent.OnDeleteDomainGroup(groupId))
        },
        onAddDomains = { groupId, text ->
            viewModel.onEvent(HostsGeneratorEvent.OnAddDomains(groupId, text))
        },
        onRenameDomain = { domainId, domain ->
            viewModel.onEvent(HostsGeneratorEvent.OnRenameDomain(domainId, domain))
        },
        onDeleteDomain = { domainId ->
            viewModel.onEvent(HostsGeneratorEvent.OnDeleteDomain(domainId))
        },
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
            viewModel.onEvent(HostsGeneratorEvent.OnTextCopied)
        }
    )
}
