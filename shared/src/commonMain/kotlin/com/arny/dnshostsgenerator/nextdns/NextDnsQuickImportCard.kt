package com.arny.dnshostsgenerator.nextdns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.presentation.HostsGeneratorState
import com.arny.dnshostsgenerator.presentation.LocalSnackbarHostState
import kotlinx.coroutines.launch

/** Что именно уйдёт в NextDNS при быстром импорте. */
data class QuickImportSource(
    val text: String,
    val description: String,
    /** true — это список доменов без IP, нужен IP из поля; false — готовые hosts-строки. */
    val usesTargetIp: Boolean,
)

/**
 * Источник для быстрого импорта: готовый hosts-результат, если он есть,
 * иначе домены из включённых групп.
 */
fun quickImportSource(state: HostsGeneratorState): QuickImportSource {
    val result = state.results.getOrNull(state.selectedResultIndex)
    if (result != null && result.stats.activeHostsCount > 0) {
        return QuickImportSource(
            text = result.outputText,
            description = "${result.stats.activeHostsCount} записей из результата «${result.presetTitle}»",
            usesTargetIp = false,
        )
    }
    val domainsCount = state.domainText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()
        .count()
    return QuickImportSource(
        text = state.domainText,
        description = "$domainsCount доменов из включённых групп (hosts ещё не сгенерирован)",
        usesTargetIp = true,
    )
}

/**
 * Компактный импорт в NextDNS прямо на главном экране (для телефона):
 * одна кнопка вставляет текст, парсит его и сразу запускает импорт.
 */
@Composable
fun NextDnsQuickImportCard(
    state: NextDnsImportState,
    source: QuickImportSource,
    enabled: Boolean,
    onEvent: (NextDnsImportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val busy = state.isCreatingAccount || state.isImporting || state.isCancelling

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "Частный DNS через NextDNS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (source.usesTargetIp && !busy) {
                OutlinedTextField(
                    value = state.targetIp,
                    onValueChange = { onEvent(NextDnsImportEvent.OnTargetIpChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP для доменов") },
                    singleLine = true,
                )
            }

            if (busy) {
                ImportProgressCard(
                    state = state,
                    onCancel = { onEvent(NextDnsImportEvent.OnCancelImport) },
                )
            } else {
                Button(
                    enabled = enabled && source.text.isNotBlank(),
                    onClick = { onEvent(NextDnsImportEvent.OnQuickImport(source.text)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.profile == null) "Импортировать в NextDNS" else "Импортировать заново")
                }
            }

            state.errorMessage?.let { error ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val profile = state.profile
            val summary = state.importSummary
            if (profile != null && summary != null && !busy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (summary.verifiedSuccessfully) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (summary.verifiedSuccessfully) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.privateDns,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(profile.privateDns))
                                scope.launch {
                                    snackbarHostState?.currentSnackbarData?.dismiss()
                                    snackbarHostState?.showSnackbar("DNS-сервер скопирован")
                                }
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Копировать")
                        }
                    }
                }
                Text(
                    text = "Настройки → Сеть и интернет → Частный DNS → «Имя хоста провайдера» → вставьте адрес.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
