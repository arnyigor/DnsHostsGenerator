package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.data.db.CustomPresetEntity
import com.arny.dnshostsgenerator.domain.DnsProviderPreset

/**
 * Пользовательские провайдеры → общий список пресетов.
 *
 * Встроенные идут первыми: рекомендуемый пресет должен остаться на своём месте,
 * иначе выбор по умолчанию сместится. Свои провайдеры включены, пока не истёк срок:
 * человек добавил их осознанно, отключать их некому.
 */
fun mergePresets(custom: List<CustomPresetEntity>, now: Long = currentEpochMillis()): List<DnsProviderPreset> =
    BuiltInDnsPresets.defaults + custom.map { it.toPreset(now) }

fun CustomPresetEntity.toPreset(now: Long = currentEpochMillis()): DnsProviderPreset {
    val expired = expiresAt != null && expiresAt <= now
    return DnsProviderPreset(
        id = id,
        title = title,
        primaryDns = primaryDns,
        dotHost = dotHost,
        allowInvalidTls = allowInvalidTls,
        checkDns = checkDns,
        outputFileName = outputFileName,
        enabledByDefault = !expired,
        isCustom = true,
        disabledReason = if (expired) "срок действия истёк: продлите доступ у провайдера" else null,
    )
}

/** Текущее время в epoch millis: платформенно-нейтрально, без java.time в common-коде. */
expect fun currentEpochMillis(): Long

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
