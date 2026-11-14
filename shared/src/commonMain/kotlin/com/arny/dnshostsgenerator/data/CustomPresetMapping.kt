package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.data.db.CustomPresetEntity
import com.arny.dnshostsgenerator.domain.DnsProviderPreset

/**
 * Пользовательские провайдеры → общий список пресетов.
 *
 * Встроенные идут первыми: рекомендуемый пресет должен остаться на своём месте,
 * иначе выбор по умолчанию сместится. Свои провайдеры всегда включены: человек
 * добавил их осознанно, отключать их некому.
 */
fun mergePresets(custom: List<CustomPresetEntity>): List<DnsProviderPreset> =
    BuiltInDnsPresets.defaults + custom.map { it.toPreset() }

fun CustomPresetEntity.toPreset(): DnsProviderPreset = DnsProviderPreset(
    id = id,
    title = title,
    primaryDns = primaryDns,
    dotHost = dotHost,
    allowInvalidTls = allowInvalidTls,
    checkDns = checkDns,
    outputFileName = outputFileName,
    enabledByDefault = true,
    isCustom = true,
)

/** Имя файла результата: без пробелов и путей, чтобы не ломать сохранение. */
fun customPresetFileName(title: String, fallbackId: String): String {
    val safe = title.lowercase()
        .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
        .joinToString("")
        .trim('_')
        .take(40)
        .ifBlank { fallbackId }
    return "hosts_$safe.txt"
}
