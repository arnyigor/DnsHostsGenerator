package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.export.CsvExporter

/**
 * Stateless-компонент UI. Не содержит логики генерации и корутин. Идеален для Preview.
 */
@Composable
fun HostsGeneratorScreen(
    presets: List<DnsProviderPreset>,
    selectedPresetIds: Set<String>,
    domainText: String,
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
    onGenerate: () -> Unit,
    onResetDomains: () -> Unit,
    onSelectResult: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onSaveTextFile: (String, String) -> Unit,
) {
    val selectedPresetsCount = presets.count { it.id in selectedPresetIds }
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
                        .height(300.dp),
                    presets = presets,
                    selectedPresetIds = selectedPresetIds,
                    dedupEnabled = dedupEnabled,
                    domainText = domainText,
                    isGenerating = isGenerating,
                    selectedPresetsCount = selectedPresetsCount,
                    progressText = progressText,
                    statusText = statusText,
                    onPresetChecked = onPresetChecked,
                    onSelectAll = onSelectAllPresets,
                    onClear = onClearPresets,
                    onDedupChanged = onDedupChanged,
                    onDomainTextChanged = onDomainTextChanged,
                    onGenerate = onGenerate,
                    onResetDomains = onResetDomains,
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
                    isGenerating = isGenerating,
                    selectedPresetsCount = selectedPresetsCount,
                    progressText = progressText,
                    statusText = statusText,
                    onPresetChecked = onPresetChecked,
                    onSelectAll = onSelectAllPresets,
                    onClear = onClearPresets,
                    onDedupChanged = onDedupChanged,
                    onDomainTextChanged = onDomainTextChanged,
                    onGenerate = onGenerate,
                    onResetDomains = onResetDomains,
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
    isGenerating: Boolean,
    selectedPresetsCount: Int,
    progressText: String,
    statusText: String,
    onPresetChecked: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDedupChanged: (Boolean) -> Unit,
    onDomainTextChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onResetDomains: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header()
        PresetsPanel(
            presets = presets,
            selectedPresetIds = selectedPresetIds,
            onPresetChecked = onPresetChecked,
            onSelectAll = onSelectAll,
            onClear = onClear,
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
            modifier = domainEditorModifier,
            onDomainTextChanged = onDomainTextChanged
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isGenerating && selectedPresetsCount > 0,
                onClick = onGenerate,
            ) {
                Text(if (isGenerating) "Генерация..." else "Сгенерировать hosts")
            }
            TextButton(enabled = !isGenerating, onClick = onResetDomains) {
                Text("Сбросить список")
            }
        }
        Text(progressText, style = MaterialTheme.typography.bodySmall)
        Text(statusText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DomainInputArea(
    domainText: String,
    modifier: Modifier,
    onDomainTextChanged: (String) -> Unit
) {
    var isListMode by remember { mutableStateOf(false) }

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
                text = { Text("Список") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Список") }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (isListMode) {
            // Список дополнительно к тексту
            val lines =
                remember(domainText) { domainText.toInputLines().filter { it.isNotBlank() } }
            if (lines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Список пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            // Стандартное текстовое поле
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
                allResults = results,
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
    onPresetChecked: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DNS-пресет", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text("Рекомендуемый") }
                TextButton(onClick = onSelectAll) { Text("Сравнить все") }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = preset.id in selectedPresetIds,
                        onCheckedChange = { checked -> onPresetChecked(preset.id, checked) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.title, fontWeight = FontWeight.Medium)
                        Text(
                            "primary: ${preset.primaryDns}, check: ${preset.checkDns}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
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
    allResults: List<GenerationResult>,
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
        ElevatedButton(
            enabled = allResults.size > 1,
            onClick = {
                val csv = CsvExporter.toComparisonCsv(allResults)
                onCopy(csv)
            },
        ) {
            Text("Копировать CSV")
        }
        ElevatedButton(
            enabled = allResults.size > 1,
            onClick = {
                val csv = CsvExporter.toComparisonCsv(allResults)
                onSave("combined_hosts.csv", csv)
            },
        ) {
            Text("Сохранить CSV")
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

private fun String.toInputLines(): List<String> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

private fun GenerationResult.summaryText(): String =
    "$presetTitle → ${outputFileName}: строк ${stats.lineCount}, hosts ${stats.activeHostsCount}, unresolved ${stats.unresolvedCount}, duplicate ${stats.duplicateCount}"

// ============================================================================
// PREVIEWS (Доступны благодаря отделению логики от UI)
// ============================================================================


@Composable
private fun HostsGeneratorScreenPreviewMocks() {
    val mockPresets = DnsProviderPreset.previewData()
    MaterialTheme {
        HostsGeneratorScreen(
            presets = mockPresets,
            selectedPresetIds = setOf("1"),
            domainText = "google.com\nyoutube.com",
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