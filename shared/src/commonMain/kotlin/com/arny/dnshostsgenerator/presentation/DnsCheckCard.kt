package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus

/** Состояние блока проверки DNS (не путать с проверкой доменов при генерации hosts). */
data class DnsCheckUiState(
    val domain: String = "gemini.google.com",
    val isRunning: Boolean = false,
    val results: List<DnsCheckResult> = emptyList(),
    val progressText: String = "Выберите домен и запустите проверку",
)

/**
 * Массовая проверка DNS-серверов: кто отвечает и чей адрес реально обслуживает домен.
 * Вердикт «работает» ставится только после TLS-проверки адреса — как и при генерации hosts.
 */
@Composable
fun DnsCheckCard(
    state: DnsCheckUiState,
    onDomainChanged: (String) -> Unit,
    onRunCheck: () -> Unit,
    onCancelCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Проверка DNS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Отвечает ли сервер и обслуживает ли его адрес домен (TLS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.domain,
                onValueChange = onDomainChanged,
                label = { Text("Домен для проверки") },
                singleLine = true,
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isRunning) {
                    OutlinedButton(onClick = onCancelCheck) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Остановить")
                    }
                } else {
                    FilledTonalButton(onClick = onRunCheck) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Проверить DNS")
                    }
                }
                Text(
                    state.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.results.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        state.results.forEach { result ->
                            DnsCheckRow(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsCheckRow(result: DnsCheckResult) {
    val color = when (result.status) {
        DnsCheckStatus.Works -> Color(0xFF2E7D32)
        DnsCheckStatus.Blocked -> Color(0xFFC62828)
        DnsCheckStatus.SameAsProvider -> Color(0xFFF9A825)
        DnsCheckStatus.TlsFailed -> Color(0xFFEF6C00)
        DnsCheckStatus.Unreachable -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(statusLabel(result.status))
                    result.ip?.let { append(" · $it") }
                    if (result.status != DnsCheckStatus.Works) {
                        result.allIps.firstOrNull()?.let { if (it != result.ip) append(" · $it") }
                    }
                    if (result.detail != null && result.status != DnsCheckStatus.Works) {
                        append(" · ${result.detail}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${result.elapsedMs} мс",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        if (result.status != DnsCheckStatus.Works) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun statusLabel(status: DnsCheckStatus): String = when (status) {
    DnsCheckStatus.Works -> "работает"
    DnsCheckStatus.Blocked -> "обнуляет домен"
    DnsCheckStatus.SameAsProvider -> "как у провайдера"
    DnsCheckStatus.TlsFailed -> "адрес не обслуживает домен"
    DnsCheckStatus.Unreachable -> "не отвечает"
}
