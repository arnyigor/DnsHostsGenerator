package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.CertDates
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus
import com.arny.dnshostsgenerator.platform.nowEpochMillis
import kotlinx.coroutines.launch

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
    // Развёрнутость — не часть данных проверки: это локальное предпочтение экрана.
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (expanded) 16.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 0.dp),
        ) {
            // Заголовок и есть переключатель: блок нужен не всегда, а место на телефоне дорого.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = if (expanded) 0.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Проверка DNS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (expanded) {
                            "Отвечает ли сервер и обслуживает ли его адрес домен (TLS)"
                        } else {
                            // Свёрнутый вид не теряет результат: короткая сводка остаётся видимой.
                            collapsedSummary(state)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть проверку DNS" else "Развернуть проверку DNS",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!expanded) return@Column

            // За VPN проверяется VPN, а не оператор: вердикт будет про туннель, а не про сеть.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Проверяйте без VPN: через туннель измеряется VPN, а не ваш провайдер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.results.isNotEmpty()) {
                DnsCheckSummary(state.results)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                        state.results.forEach { result ->
                            DnsCheckRow(result)
                        }
                    }
                }
            }
        }
    }
}

/** Короткая сводка для свёрнутого вида: результат проверки не должен исчезать вместе с телом. */
private fun collapsedSummary(state: DnsCheckUiState): String {
    if (state.isRunning) return "идёт проверка · ${state.domain}"
    if (state.results.isEmpty()) return "свёрнуто · ${state.domain}"
    val works = state.results.count { it.status == DnsCheckStatus.Works }
    return "работает $works из ${state.results.size} · ${state.domain}"
}

/** Сводка: сколько серверов работает, сколько обнуляет домен, сколько недоступно. */
@Composable
private fun DnsCheckSummary(results: List<DnsCheckResult>) {
    val works = results.count { it.status == DnsCheckStatus.Works }
    val unreachable = results.count { it.status == DnsCheckStatus.Unreachable }
    val other = results.size - works - unreachable
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip("работает $works", StatusColor.Works)
        if (other > 0) StatusChip("проблемы $other", StatusColor.Warn)
        if (unreachable > 0) StatusChip("не отвечает $unreachable", StatusColor.Muted)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun DnsCheckRow(result: DnsCheckResult) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val color = statusColor(result.status)
    // Срок действия сертификата считаем один раз на отрисовку списка, а не для каждой строки.
    val now = remember(result) { nowEpochMillis() }
    val certLine = certificateLine(result.certificate, now)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusLabel(result.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
            Text(
                text = buildString {
                    result.ip?.let { append(it) }
                    if (result.ip == null) {
                        result.allIps.firstOrNull()?.let { append(it) }
                    }
                    if (result.status != DnsCheckStatus.Works) {
                        result.allIps.drop(1).firstOrNull()?.let { append(" · $it") }
                        if (result.detail != null) append(" · ${result.detail}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (certLine != null) {
                Text(
                    text = certLine.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (certLine.tone == StatusColor.Warn) StatusColor.Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${result.elapsedMs} мс",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        val ip = result.ip
        if (ip != null) {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(ip))
                    scope.launch { snackbarHostState?.showSnackbar("IP скопирован: $ip") }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Скопировать IP",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.size(32.dp))
        }
    }
}

private data class CertLine(val text: String, val tone: Color)

/**
 * Строка про сертификат: эмитент и дата окончания. Просроченный или истекающий сертификат
 * подсвечивается — он объясняет, почему рабочий сегодня адрес завтра перестанет работать.
 */
private fun certificateLine(
    certificate: com.arny.dnshostsgenerator.resolver.CertificateFacts?,
    nowEpochMillis: Long,
): CertLine? {
    certificate ?: return null
    val days = CertDates.daysLeft(certificate.notAfterEpochMillis, nowEpochMillis)
    val date = CertDates.formatDateUtc(certificate.notAfterEpochMillis)
    val issuer = shortenIssuer(certificate.issuer)
    val text = when {
        days < 0 -> "сертификат истёк $date · $issuer"
        days <= CERT_WARNING_DAYS -> "сертификат до $date · осталось $days дн. · $issuer"
        else -> "сертификат до $date · $issuer"
    }
    return CertLine(text = text, tone = if (days <= CERT_WARNING_DAYS) StatusColor.Warn else StatusColor.Works)
}

/** `CN=R11, O=Let's Encrypt, C=US` → `Let's Encrypt`; если организации нет, оставляем CN. */
private fun shortenIssuer(dn: String): String {
    val parts = dn.split(',').map { it.trim() }
    val organization = parts.firstOrNull { it.startsWith("O=", ignoreCase = true) }
    if (organization != null) return organization.removePrefix("O=").removePrefix("o=").trim()
    val commonName = parts.firstOrNull { it.startsWith("CN=", ignoreCase = true) }
    return commonName?.substringAfter('=')?.trim() ?: dn.take(32)
}

private object StatusColor {
    val Works = Color(0xFF2E7D32)
    val Warn = Color(0xFFEF6C00)
    val Blocked = Color(0xFFC62828)
    val Muted = Color(0xFF757575)
}

private fun statusColor(status: DnsCheckStatus): Color = when (status) {
    DnsCheckStatus.Works -> StatusColor.Works
    DnsCheckStatus.Blocked -> StatusColor.Blocked
    DnsCheckStatus.SameAsProvider -> Color(0xFFF9A825)
    DnsCheckStatus.TlsFailed -> StatusColor.Warn
    DnsCheckStatus.Unreachable -> StatusColor.Muted
}

private fun statusLabel(status: DnsCheckStatus): String = when (status) {
    DnsCheckStatus.Works -> "работает"
    DnsCheckStatus.Blocked -> "обнуляет домен"
    DnsCheckStatus.SameAsProvider -> "как у провайдера"
    DnsCheckStatus.TlsFailed -> "адрес не обслуживает домен"
    DnsCheckStatus.Unreachable -> "не отвечает"
}

/** За сколько дней до истечения сертификат считается проблемным. */
private const val CERT_WARNING_DAYS = 14L
