package com.arny.dnshostsgenerator

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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.data.DefaultDomainList
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.domain.GenerateHostsRequest
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.export.CsvExporter
import com.arny.dnshostsgenerator.generator.HostsGenerator
import com.arny.dnshostsgenerator.logging.AppLogger
import com.arny.dnshostsgenerator.platform.saveTextFile
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import kotlinx.coroutines.launch

@Composable
@Preview
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
private fun HostsGeneratorApp() {
    val presets = remember { BuiltInDnsPresets.defaults }
    val resolver = remember { createDnsResolver() }
    val generator = remember(resolver) { HostsGenerator(resolver) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var domainText by remember { mutableStateOf(DefaultDomainList.text) }
    var selectedPresetIds by remember { mutableStateOf(setOf(BuiltInDnsPresets.recommendedId)) }
    var dedupEnabled by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("Готово к генерации") }
    var statusText by remember { mutableStateOf("По умолчанию выбран один рекомендуемый DNS. Остальные нужны только для сравнения.") }
    var results by remember { mutableStateOf<List<GenerationResult>>(emptyList()) }
    var selectedResultIndex by remember { mutableIntStateOf(0) }

    val selectedPresets = presets.filter { it.id in selectedPresetIds }
    val selectedResult = results.getOrNull(selectedResultIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0)))
    val generate: () -> Unit = {
        scope.launch {
            isGenerating = true
            results = emptyList()
            selectedResultIndex = 0
            AppLogger.d("Generate button clicked: selectedPresets=${selectedPresets.map { it.id }}, domainsTextLength=${domainText.length}, dedup=$dedupEnabled")
            statusText = if (selectedPresets.size == 1) {
                "Запущена генерация hosts через ${selectedPresets.first().title}"
            } else {
                "Запущено сравнение для ${selectedPresets.size} DNS-провайдеров"
            }

            val generatedResults = mutableListOf<GenerationResult>()
            runCatching {
                val inputLines = domainText.toInputLines()
                selectedPresets.forEachIndexed { index, preset ->
                    progressText = "${index + 1}/${selectedPresets.size}: ${preset.title}"
                    val result = generator.generate(
                    presetTitle = preset.title,
                    request = GenerateHostsRequest(
                        primaryDns = preset.primaryDns,
                        checkDns = preset.checkDns,
                        inputLines = inputLines,
                        outputFileName = preset.outputFileName,
                        dedupEnabled = dedupEnabled,
                    ),
                ) { progress ->
                    progressText = buildString {
                        append("${preset.title}: ${progress.processedLines}/${progress.totalLines}")
                        progress.currentDomain?.let { append(" — ").append(it) }
                    }
                }
                    generatedResults += result
                    results = generatedResults.toList()
                    selectedResultIndex = generatedResults.lastIndex
                }
            }.onFailure { error ->
                AppLogger.e("Generation failed unexpectedly", error)
                statusText = "Ошибка генерации: ${error.message ?: error::class.simpleName}"
            }

            if (generatedResults.isNotEmpty()) {
                statusText = if (generatedResults.size == 1) {
                    "Готов один hosts-файл: ${generatedResults.first().outputFileName}"
                } else {
                    "Сравнение завершено: ${generatedResults.size} вариантов"
                }
            }
            progressText = "Готово"
            isGenerating = false
        }
    }

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
                        .height(260.dp),
                    presets = presets,
                    selectedPresetIds = selectedPresetIds,
                    dedupEnabled = dedupEnabled,
                    domainText = domainText,
                    isGenerating = isGenerating,
                    selectedPresetsCount = selectedPresets.size,
                    progressText = progressText,
                    statusText = statusText,
                    onPresetChecked = { id, checked ->
                        selectedPresetIds = if (checked) selectedPresetIds + id else selectedPresetIds - id
                    },
                    onSelectAll = { selectedPresetIds = presets.map { it.id }.toSet() },
                    onClear = { selectedPresetIds = setOf(BuiltInDnsPresets.recommendedId) },
                    onDedupChanged = { dedupEnabled = it },
                    onDomainTextChanged = { domainText = it },
                    onGenerate = generate,
                    onResetDomains = { domainText = DefaultDomainList.text },
                )
                ResultsPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                    results = results,
                    selectedResultIndex = selectedResultIndex,
                    selectedResult = selectedResult,
                    onSelectResult = { selectedResultIndex = it },
                    onStatus = { statusText = it },
                    onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
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
                    selectedPresetsCount = selectedPresets.size,
                    progressText = progressText,
                    statusText = statusText,
                    onPresetChecked = { id, checked ->
                        selectedPresetIds = if (checked) selectedPresetIds + id else selectedPresetIds - id
                    },
                    onSelectAll = { selectedPresetIds = presets.map { it.id }.toSet() },
                    onClear = { selectedPresetIds = setOf(BuiltInDnsPresets.recommendedId) },
                    onDedupChanged = { dedupEnabled = it },
                    onDomainTextChanged = { domainText = it },
                    onGenerate = generate,
                    onResetDomains = { domainText = DefaultDomainList.text },
                )
                ResultsPanel(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxSize(),
                    results = results,
                    selectedResultIndex = selectedResultIndex,
                    selectedResult = selectedResult,
                    onSelectResult = { selectedResultIndex = it },
                    onStatus = { statusText = it },
                    onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
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
            Text("Дедупликация совместимо со скриптом")
        }
        OutlinedTextField(
            value = domainText,
            onValueChange = onDomainTextChanged,
            modifier = domainEditorModifier,
            label = { Text("domainlist.txt") },
            minLines = 8,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
private fun ResultsPanel(
    modifier: Modifier,
    results: List<GenerationResult>,
    selectedResultIndex: Int,
    selectedResult: GenerationResult?,
    onSelectResult: (Int) -> Unit,
    onStatus: (String) -> Unit,
    onCopy: (String) -> Unit,
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
                onStatus = onStatus,
                onCopy = onCopy,
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
        Text("Результаты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    onStatus: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElevatedButton(onClick = {
            onCopy(result.outputText)
            onStatus("Hosts-результат скопирован в буфер обмена")
        }) {
            Text("Копировать hosts")
        }
        ElevatedButton(onClick = {
            scope.launch {
                val saveResult = saveTextFile(result.outputFileName, result.outputText)
                onStatus(saveResult.message)
            }
        }) {
            Text("Сохранить TXT")
        }
        ElevatedButton(
            enabled = allResults.size > 1,
            onClick = {
                val csv = CsvExporter.toComparisonCsv(allResults)
                onCopy(csv)
                onStatus("CSV-сравнение скопировано в буфер обмена")
            },
        ) {
            Text("Копировать CSV")
        }
        ElevatedButton(
            enabled = allResults.size > 1,
            onClick = {
                scope.launch {
                    val csv = CsvExporter.toComparisonCsv(allResults)
                    val saveResult = saveTextFile("combined_hosts.csv", csv)
                    onStatus(saveResult.message)
                }
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
        Text("Результатов пока нет")
        Spacer(Modifier.height(8.dp))
        Text("Запустите генерацию для одного или нескольких DNS-провайдеров.")
    }
}

private fun String.toInputLines(): List<String> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

private fun GenerationResult.summaryText(): String =
    "${presetTitle} → ${outputFileName}: строк ${stats.lineCount}, hosts ${stats.activeHostsCount}, unresolved ${stats.unresolvedCount}, duplicate ${stats.duplicateCount}"
