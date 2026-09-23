package com.arny.dnshostsgenerator.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.presentation.theme.AppTheme

@Composable
fun HostsGeneratorScreen(
    state: HostsGeneratorState,
    onEvent: (HostsGeneratorEvent) -> Unit,
    onNavigateToNextDnsImport: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    HostsGeneratorScreen(
        presets = state.presets,
        selectedPresetIds = state.selectedPresetIds,
        domainText = state.domainText,
        domainGroups = state.domainGroups,
        dedupEnabled = state.dedupEnabled,
        isGenerating = state.isGenerating,
        progress = state.progress,
        progressText = state.progressText,
        statusText = state.statusText,
        results = state.results,
        selectedResultIndex = state.selectedResultIndex,
        onPresetChecked = { id, checked -> onEvent(HostsGeneratorEvent.OnPresetChecked(id, checked)) },
        onSelectAllPresets = { onEvent(HostsGeneratorEvent.OnSelectAllPresets) },
        onClearPresets = { onEvent(HostsGeneratorEvent.OnClearPresets) },
        onDedupChanged = { onEvent(HostsGeneratorEvent.OnDedupChanged(it)) },
        onDomainTextChanged = { onEvent(HostsGeneratorEvent.OnDomainTextChanged(it)) },
        onDomainGroupEnabledChanged = { groupId, enabled ->
            onEvent(HostsGeneratorEvent.OnDomainGroupEnabledChanged(groupId, enabled))
        },
        onSetAllDomainGroupsEnabled = { enabled ->
            onEvent(HostsGeneratorEvent.OnSetAllDomainGroupsEnabled(enabled))
        },
        onAddDomainGroup = { onEvent(HostsGeneratorEvent.OnAddDomainGroup(it)) },
        onRenameDomainGroup = { groupId, name -> onEvent(HostsGeneratorEvent.OnRenameDomainGroup(groupId, name)) },
        onDeleteDomainGroup = { onEvent(HostsGeneratorEvent.OnDeleteDomainGroup(it)) },
        onAddDomains = { groupId, text -> onEvent(HostsGeneratorEvent.OnAddDomains(groupId, text)) },
        onRenameDomain = { domainId, domain -> onEvent(HostsGeneratorEvent.OnRenameDomain(domainId, domain)) },
        onDeleteDomain = { onEvent(HostsGeneratorEvent.OnDeleteDomain(it)) },
        onGenerate = { onEvent(HostsGeneratorEvent.OnGenerate) },
        onCancelGeneration = { onEvent(HostsGeneratorEvent.OnCancelGeneration) },
        onSelectResult = { onEvent(HostsGeneratorEvent.OnSelectResult(it)) },
        onCopyText = { text ->
            clipboardManager.setText(AnnotatedString(text))
            onEvent(HostsGeneratorEvent.OnTextCopied)
        },
        onSaveTextFile = { fileName, content -> onEvent(HostsGeneratorEvent.OnSaveTextFile(fileName, content)) },
        onNavigateToNextDnsImport = onNavigateToNextDnsImport,
    )
}

/**
 * Stateless-компонент UI. Не содержит логики генерации и корутин. Идеален для Preview.
 */
@Composable
fun HostsGeneratorScreen(
    presets: List<DnsProviderPreset>,
    selectedPresetIds: Set<String>,
    domainText: String,
    domainGroups: List<DomainGroupUi>,
    dedupEnabled: Boolean,
    isGenerating: Boolean,
    progress: Float?,
    progressText: String,
    statusText: String,
    results: List<GenerationResult>,
    selectedResultIndex: Int,
    onPresetChecked: (String, Boolean) -> Unit,
    onSelectAllPresets: () -> Unit,
    onClearPresets: () -> Unit,
    onDedupChanged: (Boolean) -> Unit,
    onDomainTextChanged: (String) -> Unit,
    onDomainGroupEnabledChanged: (Long, Boolean) -> Unit,
    onSetAllDomainGroupsEnabled: (Boolean) -> Unit = {},
    onAddDomainGroup: (String) -> Unit,
    onRenameDomainGroup: (Long, String) -> Unit,
    onDeleteDomainGroup: (Long) -> Unit,
    onAddDomains: (Long, String) -> Unit,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit = {},
    onSelectResult: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onSaveTextFile: (String, String) -> Unit,
    onNavigateToNextDnsImport: () -> Unit = {},
) {
    val selectedPresetsCount = if (selectedPresetIds.isEmpty() && presets.isNotEmpty()) {
        1
    } else {
        presets.count { it.id in selectedPresetIds }
    }
    val domainCount = remember(domainText) { countDomains(domainText) }
    val selectedResult =
        results.getOrNull(selectedResultIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0)))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val compact = maxWidth < 900.dp

        @Composable
        fun Controls(modifier: Modifier, domainEditorModifier: Modifier) {
            ControlsPanel(
                modifier = modifier,
                domainEditorModifier = domainEditorModifier,
                presets = presets,
                selectedPresetIds = selectedPresetIds,
                dedupEnabled = dedupEnabled,
                domainText = domainText,
                domainGroups = domainGroups,
                domainCount = domainCount,
                isGenerating = isGenerating,
                progress = progress,
                selectedPresetsCount = selectedPresetsCount,
                progressText = progressText,
                statusText = statusText,
                onPresetChecked = onPresetChecked,
                onSelectAll = onSelectAllPresets,
                onSelectRecommended = onClearPresets,
                onDedupChanged = onDedupChanged,
                onDomainTextChanged = onDomainTextChanged,
                onDomainGroupEnabledChanged = onDomainGroupEnabledChanged,
                onSetAllDomainGroupsEnabled = onSetAllDomainGroupsEnabled,
                onAddDomainGroup = onAddDomainGroup,
                onRenameDomainGroup = onRenameDomainGroup,
                onDeleteDomainGroup = onDeleteDomainGroup,
                onAddDomains = onAddDomains,
                onRenameDomain = onRenameDomain,
                onDeleteDomain = onDeleteDomain,
                onGenerate = onGenerate,
                onCancelGeneration = onCancelGeneration,
                onNavigateToNextDnsImport = onNavigateToNextDnsImport,
                compact = compact,
            )
        }

        @Composable
        fun Results(modifier: Modifier) {
            ResultsPanel(
                modifier = modifier,
                results = results,
                isGenerating = isGenerating,
                selectedResultIndex = selectedResultIndex,
                selectedResult = selectedResult,
                onSelectResult = onSelectResult,
                onCopy = onCopyText,
                onSave = onSaveTextFile,
            )
        }

        if (compact) {
            val scrollState = rememberScrollState()
            val hasResults = results.isNotEmpty()
            // На узком экране результаты ниже формы: после первого готового результата
            // прокручиваем к нему, иначе пользователь не видит, что генерация завершилась.
            LaunchedEffect(hasResults) {
                if (hasResults) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Controls(
                    modifier = Modifier.fillMaxWidth(),
                    domainEditorModifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                )
                Results(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Controls(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxSize(),
                    domainEditorModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Results(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ControlsPanel(
    modifier: Modifier,
    domainEditorModifier: Modifier,
    presets: List<DnsProviderPreset>,
    selectedPresetIds: Set<String>,
    dedupEnabled: Boolean,
    domainText: String,
    domainGroups: List<DomainGroupUi>,
    domainCount: Int,
    isGenerating: Boolean,
    progress: Float?,
    selectedPresetsCount: Int,
    progressText: String,
    statusText: String,
    onPresetChecked: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onSelectRecommended: () -> Unit,
    onDedupChanged: (Boolean) -> Unit,
    onDomainTextChanged: (String) -> Unit,
    onDomainGroupEnabledChanged: (Long, Boolean) -> Unit,
    onSetAllDomainGroupsEnabled: (Boolean) -> Unit,
    onAddDomainGroup: (String) -> Unit,
    onRenameDomainGroup: (Long, String) -> Unit,
    onDeleteDomainGroup: (Long) -> Unit,
    onAddDomains: (Long, String) -> Unit,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onNavigateToNextDnsImport: () -> Unit,
    compact: Boolean,
) {
    var presetsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        PresetsPanel(
            presets = presets,
            selectedPresetIds = selectedPresetIds,
            expanded = presetsExpanded,
            onExpandedChange = { expanded ->
                presetsExpanded = expanded
            },
            onPresetChecked = onPresetChecked,
            onSelectRecommended = onSelectRecommended,
            onSelectAll = onSelectAll,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .toggleable(
                    value = dedupEnabled,
                    role = Role.Checkbox,
                    onValueChange = onDedupChanged,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = dedupEnabled, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Убирать дубли доменов", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Повторы помечаются #duplicate, как в исходном скрипте",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DomainInputArea(
            domainText = domainText,
            domainGroups = domainGroups,
            modifier = domainEditorModifier,
            onDomainTextChanged = onDomainTextChanged,
            onDomainGroupEnabledChanged = onDomainGroupEnabledChanged,
            onSetAllDomainGroupsEnabled = onSetAllDomainGroupsEnabled,
            onAddDomainGroup = onAddDomainGroup,
            onRenameDomainGroup = onRenameDomainGroup,
            onDeleteDomainGroup = onDeleteDomainGroup,
            onAddDomains = onAddDomains,
            onRenameDomain = onRenameDomain,
            onDeleteDomain = onDeleteDomain,
            compact = compact,
        )

        GenerateSection(
            isGenerating = isGenerating,
            progress = progress,
            progressText = progressText,
            statusText = statusText,
            domainCount = domainCount,
            selectedPresetsCount = selectedPresetsCount,
            onGenerate = onGenerate,
            onCancelGeneration = onCancelGeneration,
            onNavigateToNextDnsImport = onNavigateToNextDnsImport,
        )
    }
}

@Composable
private fun GenerateSection(
    isGenerating: Boolean,
    progress: Float?,
    progressText: String,
    statusText: String,
    domainCount: Int,
    selectedPresetsCount: Int,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onNavigateToNextDnsImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isGenerating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onCancelGeneration) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Стоп")
                }
            }
        } else {
            Button(
                enabled = domainCount > 0 && selectedPresetsCount > 0,
                onClick = onGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        domainCount == 0 -> "Нет доменов — включите группы"
                        selectedPresetsCount > 1 ->
                            "Сравнить $selectedPresetsCount DNS · $domainCount ${domainCount.domainsWord()}"
                        else -> "Сгенерировать hosts · $domainCount ${domainCount.domainsWord()}"
                    },
                )
            }
        }
        OutlinedButton(
            enabled = !isGenerating,
            onClick = onNavigateToNextDnsImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Импорт в NextDNS / получить DNS")
        }
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DomainInputArea(
    domainText: String,
    domainGroups: List<DomainGroupUi>,
    modifier: Modifier,
    onDomainTextChanged: (String) -> Unit,
    onDomainGroupEnabledChanged: (Long, Boolean) -> Unit,
    onSetAllDomainGroupsEnabled: (Boolean) -> Unit,
    onAddDomainGroup: (String) -> Unit,
    onRenameDomainGroup: (Long, String) -> Unit,
    onDeleteDomainGroup: (Long) -> Unit,
    onAddDomains: (Long, String) -> Unit,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    compact: Boolean,
) {
    var isListMode by rememberSaveable { mutableStateOf(true) }

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = if (isListMode) 0 else 1) {
            Tab(
                selected = isListMode,
                onClick = { isListMode = true },
                text = { Text("Группы") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
            )
            Tab(
                selected = !isListMode,
                onClick = { isListMode = false },
                text = { Text("Текст") },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isListMode) {
            DomainGroupsList(
                groups = domainGroups,
                modifier = Modifier.fillMaxSize(),
                onGroupEnabledChanged = onDomainGroupEnabledChanged,
                onSetAllGroupsEnabled = onSetAllDomainGroupsEnabled,
                onAddDomainGroup = onAddDomainGroup,
                onRenameDomainGroup = onRenameDomainGroup,
                onDeleteDomainGroup = onDeleteDomainGroup,
                onAddDomains = onAddDomains,
                onRenameDomain = onRenameDomain,
                onDeleteDomain = onDeleteDomain,
                compact = compact,
            )
        } else {
            OutlinedTextField(
                value = domainText,
                onValueChange = onDomainTextChanged,
                modifier = Modifier.fillMaxSize(),
                label = { Text("Домены, по одному в строке") },
                supportingText = {
                    Text("Строки с # — комментарии. Изменения групп перезапишут этот текст.")
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun DomainGroupsList(
    groups: List<DomainGroupUi>,
    modifier: Modifier,
    onGroupEnabledChanged: (Long, Boolean) -> Unit,
    onSetAllGroupsEnabled: (Boolean) -> Unit,
    onAddDomainGroup: (String) -> Unit,
    onRenameDomainGroup: (Long, String) -> Unit,
    onDeleteDomainGroup: (Long) -> Unit,
    onAddDomains: (Long, String) -> Unit,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    compact: Boolean,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAddGroupDialog by rememberSaveable { mutableStateOf(false) }
    val allGroupsEnabled = groups.isNotEmpty() && groups.all { it.isEnabled }
    val anyGroupEnabled = groups.any { it.isEnabled }
    val enabledDomainsCount = groups.filter { it.isEnabled }.sumOf { it.domains.size }
    val filteredGroups = remember(groups, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            groups
        } else {
            groups.filter { group ->
                group.name.contains(needle, ignoreCase = true) ||
                    group.domains.any { it.domain.contains(needle, ignoreCase = true) }
            }
        }
    }
    val contentPadding = if (compact) 4.dp else 8.dp
    val contentSpacing = if (compact) 4.dp else 8.dp

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск группы или домена") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить поиск")
                    }
                }
            },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Групп: ${groups.count { it.isEnabled }}/${groups.size} · " +
                    "доменов: $enabledDomainsCount",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = groups.isNotEmpty() && !allGroupsEnabled,
                onClick = { onSetAllGroupsEnabled(true) },
            ) {
                Text("Все")
            }
            TextButton(
                enabled = anyGroupEnabled,
                onClick = { onSetAllGroupsEnabled(false) },
            ) {
                Text("Ни одной")
            }
            IconButton(onClick = { showAddGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Новая группа")
            }
        }

        when {
            groups.isEmpty() -> EmptyListMessage(
                text = "Группы доменов пока не загружены",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            filteredGroups.isEmpty() -> EmptyListMessage(
                text = "Ничего не найдено по запросу «${query.trim()}»",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            ) {
                itemsIndexed(
                    items = filteredGroups,
                    key = { _, group -> group.id },
                ) { _, group ->
                    DomainGroupRow(
                        group = group,
                        highlight = query.trim(),
                        onEnabledChanged = { enabled ->
                            onGroupEnabledChanged(group.id, enabled)
                        },
                        onRenameGroup = { name ->
                            onRenameDomainGroup(group.id, name)
                        },
                        onDeleteGroup = {
                            onDeleteDomainGroup(group.id)
                        },
                        onAddDomains = { text ->
                            onAddDomains(group.id, text)
                        },
                        onRenameDomain = onRenameDomain,
                        onDeleteDomain = onDeleteDomain,
                        compact = compact,
                    )
                }
            }
        }
    }

    if (showAddGroupDialog) {
        AddGroupDialog(
            onConfirm = { name ->
                showAddGroupDialog = false
                onAddDomainGroup(name)
            },
            onDismiss = { showAddGroupDialog = false },
        )
    }
}

@Composable
private fun EmptyListMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddGroupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая группа") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun DomainGroupRow(
    group: DomainGroupUi,
    highlight: String,
    onEnabledChanged: (Boolean) -> Unit,
    onRenameGroup: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onAddDomains: (String) -> Unit,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var confirmDeleteGroup by rememberSaveable(group.id) { mutableStateOf(false) }
    var groupName by remember(group.id, group.name) { mutableStateOf(group.name) }
    var newDomainsText by rememberSaveable(group.id) { mutableStateOf("") }
    val newDomainsError = remember(newDomainsText) { validateDomainsInputError(newDomainsText) }
    val canAddDomains = newDomainsText.isNotBlank() && newDomainsError == null
    val previewCount = if (compact) 1 else 3
    val rowPadding = if (compact) 4.dp else 8.dp
    val rowSpacing = if (compact) 4.dp else 8.dp
    // При поиске по домену показываем в превью совпавшие домены, а не первые по списку.
    val previewSource = if (highlight.isNotEmpty() && !group.name.contains(highlight, ignoreCase = true)) {
        group.domains.filter { it.domain.contains(highlight, ignoreCase = true) }
    } else {
        group.domains
    }
    val previewDomains = previewSource.take(previewCount).joinToString { domain -> domain.domain }
    val domainsSummary = buildString {
        append(group.domains.size)
        append(' ')
        append(group.domains.size.domainsWord())
        if (previewDomains.isNotBlank()) {
            append(" • ")
            append(previewDomains)
            if (previewSource.size > previewCount) {
                append("…")
            }
        }
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DomainGroupArrowRotation",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (group.isEnabled) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(rowPadding),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = group.isEnabled,
                    onCheckedChange = onEnabledChanged,
                )
                Spacer(modifier = Modifier.width(if (compact) 4.dp else 8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = if (expanded) "Свернуть" else "Развернуть") {
                            expanded = !expanded
                        }
                        .padding(vertical = if (compact) 2.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
                ) {
                    Text(
                        text = group.name,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (group.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = domainsSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Свернуть группу" else "Развернуть группу",
                        modifier = Modifier.rotate(arrowRotation),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Название группы") },
                            singleLine = true,
                        )
                        IconButton(
                            enabled = groupName.isNotBlank() && groupName != group.name,
                            onClick = { onRenameGroup(groupName) },
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Сохранить название")
                        }
                        IconButton(onClick = { confirmDeleteGroup = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить группу",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedTextField(
                            value = newDomainsText,
                            onValueChange = { newDomainsText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Добавить домены (по одному в строке)") },
                            minLines = 1,
                            maxLines = 3,
                            isError = newDomainsError != null,
                            supportingText = if (newDomainsError != null) {
                                { Text(newDomainsError) }
                            } else {
                                null
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        FilledTonalButton(
                            enabled = canAddDomains,
                            onClick = {
                                onAddDomains(newDomainsText)
                                newDomainsText = ""
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Добавить")
                        }
                    }

                    if (group.domains.isEmpty()) {
                        Text(
                            text = "В группе пока нет доменов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (compact) 180.dp else 280.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(
                                items = group.domains,
                                key = { _, domain -> domain.id },
                            ) { _, domain ->
                                DomainRowEditor(
                                    domain = domain,
                                    onRenameDomain = onRenameDomain,
                                    onDeleteDomain = onDeleteDomain,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteGroup) {
        ConfirmDeleteDialog(
            title = "Удалить группу?",
            text = "Группа \"${group.name}\" и все её домены будут удалены безвозвратно.",
            confirmButtonText = "Удалить группу",
            onConfirm = {
                confirmDeleteGroup = false
                onDeleteGroup()
            },
            onDismiss = {
                confirmDeleteGroup = false
            },
        )
    }
}

@Composable
private fun DomainRowEditor(
    domain: DomainUi,
    onRenameDomain: (Long, String) -> Unit,
    onDeleteDomain: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var domainText by remember(domain.id, domain.domain) { mutableStateOf(domain.domain) }
    var confirmDeleteDomain by rememberSaveable(domain.id) { mutableStateOf(false) }
    val changed = domainText.isNotBlank() && domainText != domain.domain

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = domainText,
            onValueChange = { domainText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        IconButton(
            enabled = changed,
            onClick = { onRenameDomain(domain.id, domainText) },
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Сохранить домен",
                tint = if (changed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        IconButton(onClick = { confirmDeleteDomain = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить домен")
        }
    }

    if (confirmDeleteDomain) {
        ConfirmDeleteDialog(
            title = "Удалить домен?",
            text = "Домен \"${domain.domain}\" будет удалён из группы.",
            confirmButtonText = "Удалить",
            onConfirm = {
                confirmDeleteDomain = false
                onDeleteDomain(domain.id)
            },
            onDismiss = {
                confirmDeleteDomain = false
            },
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null)
        },
        title = {
            Text(title)
        },
        text = {
            Text(text)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun ResultsPanel(
    modifier: Modifier,
    results: List<GenerationResult>,
    isGenerating: Boolean,
    selectedResultIndex: Int,
    selectedResult: GenerationResult?,
    onSelectResult: (Int) -> Unit,
    onCopy: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ResultsHeader(
            results = results,
            selectedResultIndex = selectedResultIndex,
            onSelect = onSelectResult,
        )
        if (selectedResult != null) {
            Text(
                text = "${selectedResult.presetTitle} → ${selectedResult.outputFileName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ResultStats(selectedResult)
            if (selectedResult.stats.suspiciousForwarding) {
                SuspiciousForwardingWarning()
            }
            ResultActions(
                result = selectedResult,
                onCopy = onCopy,
                onSave = onSave,
            )
            ResultText(selectedResult.outputText)
        } else {
            EmptyResult(isGenerating = isGenerating)
        }
    }
}

@Composable
private fun Header() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "DNS Hosts Generator",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Отметьте группы доменов и сгенерируйте hosts-файл через рекомендуемый DNS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetsPanel(
    presets: List<DnsProviderPreset>,
    selectedPresetIds: Set<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPresetChecked: (String, Boolean) -> Unit,
    onSelectRecommended: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCount = presets.count { preset ->
        preset.id in selectedPresetIds
    }
    val allSelected = presets.isNotEmpty() && selectedCount == presets.size
    val selectedPreset = if (selectedCount == 1) {
        presets.firstOrNull { preset ->
            preset.id in selectedPresetIds
        }
    } else {
        null
    }
    val selectionSummary = when {
        presets.isEmpty() -> "Нет доступных пресетов"
        selectedCount == 0 -> "Рекомендуемый DNS"
        selectedPreset != null -> selectedPreset.title
        else -> "Сравнение: выбрано $selectedCount из ${presets.size}"
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PresetsArrowRotation",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Свернуть" else "Развернуть") {
                    onExpandedChange(!expanded)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "DNS-провайдер",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(arrowRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onSelectRecommended,
                        enabled = selectedCount > 0,
                    ) {
                        Text("Только рекомендуемый")
                    }
                    TextButton(
                        onClick = onSelectAll,
                        enabled = presets.isNotEmpty() && !allSelected,
                    ) {
                        Text("Сравнить все")
                    }
                }

                if (presets.isEmpty()) {
                    Text(
                        text = "Пресеты пока недоступны",
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 12.dp,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    presets.forEach { preset ->
                        key(preset.id) {
                            PresetRow(
                                preset = preset,
                                checked = preset.id in selectedPresetIds,
                                onCheckedChange = { checked ->
                                    onPresetChecked(preset.id, checked)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: DnsProviderPreset,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(
                horizontal = 4.dp,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = preset.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(preset.dotHost ?: preset.primaryDns)
                    append(" • проверка через ")
                    append(preset.checkDns)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultsHeader(
    results: List<GenerationResult>,
    selectedResultIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Результат",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        // Переключатель нужен только при сравнении нескольких DNS.
        if (results.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                results.forEachIndexed { index, result ->
                    FilterChip(
                        selected = selectedResultIndex == index,
                        onClick = { onSelect(index) },
                        label = { Text("${result.presetTitle} · ${result.stats.activeHostsCount}") },
                        leadingIcon = if (result.stats.suspiciousForwarding) {
                            {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Возможен перехват DNS",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultStats(result: GenerationResult) {
    val stats = result.stats
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatBadge(label = "в hosts", value = stats.activeHostsCount, color = colors.primary)
        StatBadge(label = "forwarded", value = stats.forwardedCount, color = colors.tertiary)
        StatBadge(label = "не найдено", value = stats.unresolvedCount, color = colors.error)
        StatBadge(label = "дубли", value = stats.duplicateCount, color = colors.outline)
    }
}

@Composable
private fun StatBadge(label: String, value: Int, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuspiciousForwardingWarning() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(
                text = "Почти все домены помечены #forwarded: DNS-запросы, похоже, перехватываются " +
                    "(VPN или провайдер). Результат недостоверен — отключите VPN или выберите другой DNS.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResultActions(
    result: GenerationResult,
    onCopy: (String) -> Unit,
    onSave: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = { onCopy(result.outputText) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Копировать")
        }
        OutlinedButton(onClick = { onSave(result.outputFileName, result.outputText) }) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Сохранить файл")
        }
    }
}

@Composable
private fun ResultText(text: String) {
    val colors = MaterialTheme.colorScheme
    // Подсвечиваем служебные строки, чтобы активные записи hosts читались сразу.
    val highlighted = remember(text, colors) {
        buildAnnotatedString {
            text.lineSequence().forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val color = when {
                    line.startsWith("#unresolvedDomain") -> colors.error
                    line.startsWith("#forwarded") -> colors.tertiary
                    line.startsWith("#duplicate") -> colors.outline
                    line.startsWith("#") -> colors.onSurfaceVariant
                    else -> null
                }
                if (color != null) {
                    withStyle(SpanStyle(color = color)) { append(line) }
                } else {
                    append(line)
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        SelectionContainer {
            Text(
                text = highlighted,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun EmptyResult(isGenerating: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isGenerating) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Проверяем домены…", color = MaterialTheme.colorScheme.onSurface)
        } else {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Результатов пока нет",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Нажмите «Сгенерировать hosts» — готовый файл появится здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun countDomains(text: String): Int =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()
        .count()

/** Склонение слова «домен» для числа. */
private fun Int.domainsWord(): String {
    val mod100 = this % 100
    val mod10 = this % 10
    return when {
        mod100 in 11..14 -> "доменов"
        mod10 == 1 -> "домен"
        mod10 in 2..4 -> "домена"
        else -> "доменов"
    }
}

private fun validateDomainsInputError(text: String): String? {
    if (text.isBlank()) {
        return null
    }
    val lines = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() && !line.startsWith("#") }
    if (lines.isEmpty()) {
        return "Введите домен вида example.com"
    }
    val invalidLine = lines.firstOrNull { line ->
        line.toNormalizedDomainOrNull() == null
    }
    return invalidLine?.let { line ->
        "Некорректный домен: $line"
    }
}

private fun String.toNormalizedDomainOrNull(): String? {
    val domain = trim()
        .substringBefore('#')
        .trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .trim()
        .lowercase()
    return domain.takeIf { value ->
        value.matches(DOMAIN_PATTERN)
    }
}

private val DOMAIN_PATTERN = Regex(
    pattern = "^([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9][a-z0-9-]{1,62}$",
)

// ============================================================================
// PREVIEWS (Доступны благодаря отделению логики от UI)
// ============================================================================
@Composable
private fun HostsGeneratorScreenPreviewMocks() {
    val mockPresets = DnsProviderPreset.previewData()
    val mockDomainGroups = listOf(
        DomainGroupUi(
            id = 1,
            name = "OpenAI ChatGPT",
            isEnabled = true,
            domains = listOf(
                DomainUi(1, "chatgpt.com", false),
                DomainUi(2, "api.openai.com", false),
                DomainUi(3, "auth.openai.com", false),
            ),
        ),
        DomainGroupUi(
            id = 2,
            name = "Google Gemini AI",
            isEnabled = false,
            domains = listOf(
                DomainUi(4, "gemini.google.com", false),
                DomainUi(5, "aistudio.google.com", false),
            ),
        ),
    )
    AppTheme {
        Surface {
            HostsGeneratorScreen(
                presets = mockPresets,
                selectedPresetIds = setOf("1"),
                domainText = "# --- OpenAI ChatGPT ---\nchatgpt.com\napi.openai.com",
                domainGroups = mockDomainGroups,
                dedupEnabled = true,
                isGenerating = false,
                progress = null,
                progressText = "Готово",
                statusText = "Ожидание",
                results = emptyList(),
                selectedResultIndex = 0,
                onPresetChecked = { _, _ -> },
                onSelectAllPresets = {},
                onClearPresets = {},
                onDedupChanged = {},
                onDomainTextChanged = {},
                onDomainGroupEnabledChanged = { _, _ -> },
                onAddDomainGroup = {},
                onRenameDomainGroup = { _, _ -> },
                onDeleteDomainGroup = {},
                onAddDomains = { _, _ -> },
                onRenameDomain = { _, _ -> },
                onDeleteDomain = {},
                onGenerate = {},
                onSelectResult = {},
                onCopyText = {},
                onSaveTextFile = { _, _ -> },
            )
        }
    }
}

@Preview(
    name = "Compact",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Medium",
    widthDp = 840,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Expanded",
    widthDp = 1200,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Dark Theme",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Font Large",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    fontScale = 1.5f,
)
@Preview(
    name = "System UI",
    widthDp = 700,
    heightDp = 1000,
    showSystemUi = true,
)
@Preview(
    name = "Locale RU",
    widthDp = 700,
    heightDp = 1000,
    showBackground = true,
    locale = "ru",
)
@Composable
private fun HostsGeneratorScreenPreviewFontLarge() {
    HostsGeneratorScreenPreviewMocks()
}
