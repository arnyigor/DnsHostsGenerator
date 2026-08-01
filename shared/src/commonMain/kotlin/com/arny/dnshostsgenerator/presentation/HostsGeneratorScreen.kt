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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerationResult

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
        onResetDomains = { onEvent(HostsGeneratorEvent.OnResetDomains) },
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
    onResetDomains: () -> Unit,
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
    val selectedResult =
        results.getOrNull(selectedResultIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0)))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val compact = maxWidth < 900.dp
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ControlsPanel(
                    modifier = Modifier.fillMaxWidth(),
                    domainEditorModifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    presets = presets,
                    selectedPresetIds = selectedPresetIds,
                    dedupEnabled = dedupEnabled,
                    domainText = domainText,
                    domainGroups = domainGroups,
                    isGenerating = isGenerating,
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
                    onResetDomains = onResetDomains,
                    onNavigateToNextDnsImport = onNavigateToNextDnsImport,
                    compact = compact,
                )
                ResultsPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                    results = results,
                    selectedResultIndex = selectedResultIndex,
                    selectedResult = selectedResult,
                    onSelectResult = onSelectResult,
                    onCopy = onCopyText,
                    onSave = onSaveTextFile
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ControlsPanel(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxSize(),
                    domainEditorModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    presets = presets,
                    selectedPresetIds = selectedPresetIds,
                    dedupEnabled = dedupEnabled,
                    domainText = domainText,
                    domainGroups = domainGroups,
                    isGenerating = isGenerating,
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
                    onResetDomains = onResetDomains,
                    onNavigateToNextDnsImport = onNavigateToNextDnsImport,
                    compact = compact,
                )
                ResultsPanel(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxSize(),
                    results = results,
                    selectedResultIndex = selectedResultIndex,
                    selectedResult = selectedResult,
                    onSelectResult = onSelectResult,
                    onCopy = onCopyText,
                    onSave = onSaveTextFile
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
    isGenerating: Boolean,
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
    onResetDomains: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dedupEnabled, onCheckedChange = onDedupChanged)
            Text(
                "Дедупликация (совместимо со скриптом)",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Интегрированный список дополнительно к тексту (переключение)
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isGenerating && selectedPresetsCount > 0,
                onClick = onGenerate,
            ) {
                Text(if (isGenerating) "Генерация..." else "Сгенерировать hosts")
            }
            TextButton(enabled = !isGenerating, onClick = onResetDomains) {
                Text("Включить все группы")
            }
        }
        Button(
            enabled = !isGenerating,
            onClick = onNavigateToNextDnsImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Импорт доменов в NextDNS / получить DNS")
        }
        Text(progressText, style = MaterialTheme.typography.bodySmall)
        Text(statusText, style = MaterialTheme.typography.bodySmall)
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
    var isListMode by remember { mutableStateOf(true) }

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = if (isListMode) 1 else 0) {
            Tab(
                selected = !isListMode,
                onClick = { isListMode = false },
                text = { Text("Текст") },
                icon = { Icon(Icons.Default.Edit, contentDescription = "Текст") }
            )
            Tab(
                selected = isListMode,
                onClick = { isListMode = true },
                text = { Text("Группы") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Группы") }
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
                label = { Text("domainlist.txt") },
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
    var newGroupName by rememberSaveable { mutableStateOf("") }
    val allGroupsEnabled = groups.isNotEmpty() && groups.all { it.isEnabled }
    val anyGroupEnabled = groups.any { it.isEnabled }
    val contentPadding = if (compact) 4.dp else 8.dp
    val contentSpacing = if (compact) 4.dp else 8.dp

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                modifier = Modifier.weight(1f),
                label = { Text("Новая группа") },
                singleLine = true,
            )
            Button(
                enabled = newGroupName.isNotBlank(),
                onClick = {
                    onAddDomainGroup(newGroupName)
                    newGroupName = ""
                },
            ) {
                Text("Добавить")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Выбрано групп: ${groups.count { it.isEnabled }} / ${groups.size}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = groups.isNotEmpty() && !allGroupsEnabled,
                onClick = { onSetAllGroupsEnabled(true) },
            ) {
                Text("Вкл все")
            }
            TextButton(
                enabled = anyGroupEnabled,
                onClick = { onSetAllGroupsEnabled(false) },
            ) {
                Text("Выкл все")
            }
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("Группы доменов пока не загружены", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
            ) {
                itemsIndexed(
                    items = groups,
                    key = { _, group -> group.id },
                ) { _, group ->
                    DomainGroupRow(
                        group = group,
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
}

@Composable
private fun DomainGroupRow(
    group: DomainGroupUi,
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
    val previewDomains = group.domains.take(previewCount).joinToString { domain -> domain.domain }
    val domainsSummary = buildString {
        append("Доменов: ")
        append(group.domains.size)
        if (previewDomains.isNotBlank()) {
            append(" • ")
            append(previewDomains)
            if (group.domains.size > previewCount) {
                append("…")
            }
        }
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DomainGroupArrowRotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(rowPadding),
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
            Spacer(modifier = Modifier.width(if (compact) 4.dp else 12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = if (compact) 0.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
            ) {
                Text(
                    text = group.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = domainsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .rotate(arrowRotation),
            )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Название группы") },
                        singleLine = true,
                    )
                    TextButton(
                        enabled = groupName.isNotBlank() && groupName != group.name,
                        onClick = { onRenameGroup(groupName) },
                    ) {
                        Text("Сохранить")
                    }
                    TextButton(onClick = { confirmDeleteGroup = true }) {
                        Text("Удалить")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newDomainsText,
                        onValueChange = { newDomainsText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Домены, по одному в строке") },
                        minLines = 1,
                        maxLines = 3,
                        isError = newDomainsError != null,
                        supportingText = {
                            newDomainsError?.let { error ->
                                Text(error)
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Button(
                        enabled = canAddDomains,
                        onClick = {
                            onAddDomains(newDomainsText)
                            newDomainsText = ""
                        },
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
                        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
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

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = domainText,
            onValueChange = { domainText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        TextButton(
            enabled = domainText.isNotBlank() && domainText != domain.domain,
            onClick = { onRenameDomain(domain.id, domainText) },
        ) {
            Text("OK")
        }
        TextButton(onClick = { confirmDeleteDomain = true }) {
            Text("Удалить")
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
        title = {
            Text(title)
        },
        text = {
            Text(text)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
        selectedResult?.let { result ->
            Text(
                text = result.summaryText(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ResultActions(
                result = result,
                onCopy = onCopy,
                onSave = onSave
            )
            ResultText(result.outputText)
        } ?: EmptyResult()
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "DNS/SNI Hosts Generator",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Генерирует один hosts-файл через рекомендуемый DNS. Несколько DNS-пресетов можно включить только для сравнения результатов.",
            style = MaterialTheme.typography.bodySmall,
        )
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
        selectedCount == 0 -> "Используется рекомендуемый режим"
        selectedPreset != null -> selectedPreset.title
        else -> "Выбрано $selectedCount из ${presets.size}"
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "PresetsArrowRotation",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    onExpandedChange(!expanded)
                }
                .padding(
                    horizontal = 4.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "DNS-пресеты",
                    style = MaterialTheme.typography.titleMedium,
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
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        Text("Рекомендуемые")
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
                            horizontal = 4.dp,
                            vertical = 12.dp,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                vertical = 8.dp,
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
                    append("Основной: ")
                    append(preset.primaryDns)
                    append(" • Проверка: ")
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
            "Результаты",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            results.forEachIndexed { index, result ->
                FilterChip(
                    selected = selectedResultIndex == index,
                    onClick = { onSelect(index) },
                    label = { Text(result.presetTitle) },
                )
            }
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
        ElevatedButton(onClick = { onCopy(result.outputText) }) {
            Text("Копировать hosts")
        }
        ElevatedButton(onClick = { onSave(result.outputFileName, result.outputText) }) {
            Text("Сохранить TXT")
        }
    }
}

@Composable
private fun ResultText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        SelectionContainer {
            Text(
                text = text,
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
private fun EmptyResult() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Результатов пока нет", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Запустите генерацию для одного или нескольких DNS-провайдеров.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

private fun GenerationResult.summaryText(): String =
    "$presetTitle → ${outputFileName}: строк ${stats.lineCount}, hosts ${stats.activeHostsCount}, forwarded ${stats.forwardedCount}, unresolved ${stats.unresolvedCount}, duplicate ${stats.duplicateCount}"

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
    MaterialTheme {
        HostsGeneratorScreen(
            presets = mockPresets,
            selectedPresetIds = setOf("1"),
            domainText = "# --- OpenAI ChatGPT ---\nchatgpt.com\napi.openai.com",
            domainGroups = mockDomainGroups,
            dedupEnabled = true,
            isGenerating = false,
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
            onResetDomains = {},
            onSelectResult = {},
            onCopyText = {},
            onSaveTextFile = { _, _ -> },
        )
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