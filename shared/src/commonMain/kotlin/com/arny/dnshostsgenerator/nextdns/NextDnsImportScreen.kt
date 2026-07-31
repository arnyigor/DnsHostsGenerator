package com.arny.dnshostsgenerator.nextdns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NextDnsImportScreen(
    state: NextDnsImportState,
    onEvent: (NextDnsImportEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current

    toastMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            toastMessage = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("NextDNS Import") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(NextDnsImportEvent.OnReset) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Сбросить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DomainInputSection(
                domainText = state.domainText,
                targetIp = state.targetIp,
                stripWww = state.stripWww,
                skipLoopback = state.skipLoopback,
                onDomainTextChanged = { onEvent(NextDnsImportEvent.OnDomainTextChanged(it)) },
                onTargetIpChanged = { onEvent(NextDnsImportEvent.OnTargetIpChanged(it)) },
                onStripWwwChanged = { onEvent(NextDnsImportEvent.OnStripWwwChanged(it)) },
                onSkipLoopbackChanged = { onEvent(NextDnsImportEvent.OnSkipLoopbackChanged(it)) },
                onPasteFromClipboard = {
                    val clipboardText = clipboardManager.getText()?.text.orEmpty()
                    if (clipboardText.isBlank()) {
                        toastMessage = "Буфер обмена пуст"
                    } else {
                        onEvent(NextDnsImportEvent.OnDomainTextChanged(clipboardText))
                        toastMessage = "Домены вставлены из буфера"
                    }
                },
                onParse = { onEvent(NextDnsImportEvent.OnParse) },
            )

            AnimatedVisibility(state.parsedCount > 0 || state.parseIssues > 0) {
                ParseResultsCard(
                    parsedCount = state.parsedCount,
                    parseIssues = state.parseIssues,
                )
            }

            OutlinedTextField(
                value = state.profileName,
                onValueChange = { onEvent(NextDnsImportEvent.OnProfileNameChanged(it)) },
                label = { Text("Имя профиля") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                },
            )

            ImportActionRow(
                parsedCount = state.parsedCount,
                isCreatingAccount = state.isCreatingAccount,
                isImporting = state.isImporting,
                isCancelling = state.isCancelling,
                onImport = { onEvent(NextDnsImportEvent.OnImport) },
                onCancel = { onEvent(NextDnsImportEvent.OnCancelImport) },
            )

            AnimatedVisibility(state.isCreatingAccount || state.isImporting) {
                ImportProgressCard(
                    isCreatingAccount = state.isCreatingAccount,
                    progress = state.importProgress,
                    total = state.importTotal,
                    currentDomain = state.currentDomain,
                    imported = state.importedCount,
                    failed = state.failedCount,
                    skippedExisting = state.skippedExistingCount,
                    retrying = state.retryingCount,
                    retriesAttempted = state.retriesAttempted,
                    currentBurst = state.currentBurst,
                    totalBursts = state.totalBursts,
                    mode = state.importMode,
                    phase = state.importPhase,
                    cooldownRemainingSeconds = state.cooldownRemainingSeconds,
                    elapsedText = state.elapsedText,
                    etaText = state.etaText,
                    speedText = state.speedText,
                )
            }

            StatusMessage(
                message = state.statusMessage,
                isError = state.errorMessage != null,
            )

            val errorMessage = state.errorMessage
            AnimatedVisibility(errorMessage != null) {
                errorMessage?.let { message ->
                    ErrorMessage(text = message)
                }
            }

            AnimatedVisibility(
                visible = errorMessage != null && !state.isCreatingAccount && !state.isImporting && !state.isCancelling,
            ) {
                Button(
                    onClick = { onEvent(NextDnsImportEvent.OnRecreateAccount) },
                    enabled = state.parsedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Повторить с новым аккаунтом")
                }
            }

            val profile = state.profile
            val importSummary = state.importSummary
            AnimatedVisibility(profile != null || importSummary != null) {
                ProfileResultCard(
                    profile = profile,
                    summary = importSummary,
                    onRecreateAccount = { onEvent(NextDnsImportEvent.OnRecreateAccount) },
                    canRecreateAccount = state.parsedCount > 0 && !state.isCreatingAccount && !state.isImporting && !state.isCancelling,
                    onCopyDnsServer = { dnsServer ->
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(dnsServer))
                        toastMessage = "DNS сервер скопирован"
                    },
                )
            }
        }
    }
}

@Composable
private fun DomainInputSection(
    domainText: String,
    targetIp: String,
    stripWww: Boolean,
    skipLoopback: Boolean,
    onDomainTextChanged: (String) -> Unit,
    onTargetIpChanged: (String) -> Unit,
    onStripWwwChanged: (Boolean) -> Unit,
    onSkipLoopbackChanged: (Boolean) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onParse: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Домены",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = onPasteFromClipboard) {
                    Icon(Icons.Default.CopyAll, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Вставить из буфера")
                }
            }

            OutlinedTextField(
                value = domainText,
                onValueChange = onDomainTextChanged,
                label = { Text("Домены (по одному в строке)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                placeholder = {
                    Text(
                        "example.com\n# или hosts-формат:\n1.2.3.4 test.org",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = onTargetIpChanged,
                    label = { Text("IP для списка доменов") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = {
                        Text("Только для строк example.com. В hosts-строках IP берётся из самой строки.")
                    },
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = stripWww, onCheckedChange = onStripWwwChanged)
                        Text("Убрать www.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = skipLoopback, onCheckedChange = onSkipLoopbackChanged)
                        Text("Пропустить loopback", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(onClick = onParse, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Парсить")
            }
        }
    }
}

@Composable
private fun ParseResultsCard(parsedCount: Int, parseIssues: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (parseIssues > 0) Icons.Default.Info else Icons.Default.Check,
                    contentDescription = null,
                    tint = if (parseIssues > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "$parsedCount доменов",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (parseIssues > 0) {
                Text(
                    text = "$parseIssues ошибок",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImportActionRow(
    parsedCount: Int,
    isCreatingAccount: Boolean,
    isImporting: Boolean,
    isCancelling: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onImport,
            enabled = parsedCount > 0 && !isCreatingAccount && !isImporting && !isCancelling,
            modifier = Modifier.weight(1f),
        ) {
            if (isCreatingAccount || isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isCreatingAccount) "Создание аккаунта..." else "Импорт...")
            } else {
                Icon(Icons.Default.Dns, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Импортировать в NextDNS")
            }
        }

        if (isCreatingAccount || isImporting || isCancelling) {
            Button(
                onClick = onCancel,
                enabled = !isCancelling,
            ) {
                Text(if (isCancelling) "Отмена..." else "Отменить")
            }
        }
    }
}

@Composable
private fun ImportProgressCard(
    isCreatingAccount: Boolean,
    progress: Int,
    total: Int,
    currentDomain: String?,
    imported: Int,
    failed: Int,
    skippedExisting: Int,
    retrying: Int,
    retriesAttempted: Int,
    currentBurst: Int,
    totalBursts: Int,
    mode: String,
    phase: ImportProgressPhase,
    cooldownRemainingSeconds: Int,
    elapsedText: String,
    etaText: String,
    speedText: String,
) {
    val containerColor = when (phase) {
        ImportProgressPhase.CreatingAccount -> MaterialTheme.colorScheme.primaryContainer
        ImportProgressPhase.Processing -> MaterialTheme.colorScheme.primaryContainer
        ImportProgressPhase.Retrying -> MaterialTheme.colorScheme.errorContainer
        ImportProgressPhase.Resting -> MaterialTheme.colorScheme.tertiaryContainer
        ImportProgressPhase.Verifying -> MaterialTheme.colorScheme.secondaryContainer
        ImportProgressPhase.Completed -> MaterialTheme.colorScheme.primaryContainer
    }
    val progressFraction = if (total > 0) progress.toFloat() / total else 0f

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = if (isCreatingAccount) "Создание временного аккаунта NextDNS..." else "Импорт записей NextDNS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = when (phase) {
                    ImportProgressPhase.CreatingAccount -> "Подготовка аккаунта"
                    ImportProgressPhase.Processing -> "Всплеск $currentBurst/$totalBursts — обработка"
                    ImportProgressPhase.Retrying -> "Повтор запроса${if (cooldownRemainingSeconds > 0) ": ${cooldownRemainingSeconds}с" else ""}"
                    ImportProgressPhase.Resting -> "Отдых между всплесками: ${cooldownRemainingSeconds}с"
                    ImportProgressPhase.Verifying -> "Проверка добавленных записей"
                    ImportProgressPhase.Completed -> "Завершено"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Режим: $mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LinearProgressIndicator(
                progress = progressFraction,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "$progress / $total (${(progressFraction * 100).toInt()}%)  ✅ $imported  ❌ $failed  ⏭ $skippedExisting  🔄 $retrying",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Прошло: $elapsedText • Осталось: $etaText • Скорость: $speedText • Повторов: $retriesAttempted",
                style = MaterialTheme.typography.bodySmall,
            )
            if (totalBursts > 0) {
                Text(
                    text = "Всплесков: $currentBurst / $totalBursts",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            currentDomain?.let {
                Text(
                    text = "Текущий: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Error else Icons.Default.Info,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorMessage(text: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfileResultCard(
    profile: NextDnsProfile?,
    summary: ImportSummary?,
    onRecreateAccount: () -> Unit,
    canRecreateAccount: Boolean,
    onCopyDnsServer: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "NextDNS Профиль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            profile?.let { value ->
                Divider()
                ResultRow("Profile ID", value.profileId)
                ResultRow("Private DNS", value.privateDns)
                ResultRow("DoH URL", value.dohUrl)
                Button(
                    onClick = { onCopyDnsServer(value.privateDns) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Скопировать DNS сервер")
                }
            }

            summary?.let {
                Divider()
                Text("Итог импорта", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        summary.cancelled -> "Импорт отменён. Часть записей могла быть добавлена."
                        summary.verifiedSuccessfully -> "Готово: все записи добавлены и проверены."
                        summary.missingAfterVerify.isNotEmpty() -> "Есть проблемы: часть записей не найдена после проверки."
                        summary.failed > 0 -> "Есть ошибки при добавлении записей."
                        else -> "Импорт завершён."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.verifiedSuccessfully) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                ResultRow("Запрошено", summary.requested.toString())
                ResultRow("Импортировано", summary.imported.toString())
                ResultRow("Уже были", summary.skippedExisting.toString())
                ResultRow("Ошибок", summary.failed.toString())
                ResultRow("Не найдено после проверки", summary.missingAfterVerify.size.toString())

                if (summary.failures.isNotEmpty()) {
                    Text("Ошибки:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    summary.failures.take(8).forEach { failure ->
                        Text(
                            text = "${failure.rewrite.domain} -> ${failure.rewrite.target}: ${failure.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (summary.failures.size > 8) {
                        Text("...ещё ошибок: ${summary.failures.size - 8}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (summary.missingAfterVerify.isNotEmpty()) {
                    Text("Не найдены после проверки:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    summary.missingAfterVerify.take(8).forEach { rewrite ->
                        Text(
                            text = "${rewrite.domain} -> ${rewrite.target}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (summary.missingAfterVerify.size > 8) {
                        Text("...ещё: ${summary.missingAfterVerify.size - 8}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (summary.statusCounts.isNotEmpty()) {
                    ResultRow(
                        "HTTP статусы",
                        summary.statusCounts.entries.joinToString { (status, count) -> "$status=$count" },
                    )
                }
            }

            Button(
                onClick = onRecreateAccount,
                enabled = canRecreateAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Создать новый аккаунт и повторить")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
