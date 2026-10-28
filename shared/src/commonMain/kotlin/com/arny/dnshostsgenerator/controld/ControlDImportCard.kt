package com.arny.dnshostsgenerator.controld

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Импорт сгенерированных hosts в Control D как SPOOF-правила (аналог NextDNS-rewrites).
 * Нужен токен из дашборда: анонимных аккаунтов у Control D нет.
 */
/** Дашборд Control D, где выпускается API-токен (права Write). */
private const val CONTROLD_TOKEN_URL = "https://controld.com/dashboard/api"

@Composable
fun ControlDImportCard(
    state: ControlDImportState,
    hostsTextProvider: () -> String,
    onEvent: (ControlDImportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(hostsTextProvider) { parseHostEntries(hostsTextProvider()) }
    val uriHandler = LocalUriHandler.current

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
                    Text("Импорт в Control D", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Правила SPOOF вместо hosts · нужен платный тариф Full Control",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.token,
                onValueChange = { onEvent(ControlDImportEvent.OnTokenChanged(it)) },
                label = { Text("API-токен (тариф Full Control, права Write)") },
                singleLine = true,
                enabled = !state.isImporting,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { uriHandler.openUri(CONTROLD_TOKEN_URL) },
                    enabled = !state.isImporting,
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Получить токен")
                }
                Text(
                    "dashboard → API → «+», права Write (Free-тариф API не даёт)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onEvent(ControlDImportEvent.OnLoadProfiles) },
                    enabled = !state.isLoadingProfiles && !state.isImporting,
                ) {
                    Text(if (state.isLoadingProfiles) "Загружаю…" else "Загрузить профили")
                }
                Text(
                    state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.profiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.profiles.take(4).forEach { profile ->
                        FilterChip(
                            selected = state.selectedProfilePk == profile.pk,
                            onClick = { onEvent(ControlDImportEvent.OnSelectProfile(profile.pk)) },
                            label = { Text(profile.name.take(18)) },
                            enabled = !state.isImporting,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onEvent(ControlDImportEvent.OnImport(hostsTextProvider())) },
                    enabled = !state.isImporting && entries.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Залить ${entries.size} правил")
                }
            }

            if (state.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.errorMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
