package com.arny.dnshostsgenerator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Провайдер, которого нет во встроенном списке: пользователь вводит адрес сам
 * (личный SmartDNS, домашний резолвер, DoT-хост провайдера).
 *
 * Хранится отдельно от [com.arny.dnshostsgenerator.data.BuiltInDnsPresets], чтобы
 * встроенный список оставался неизменным и обновлялся с версией приложения.
 */
@Entity(tableName = "custom_presets")
data class CustomPresetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val primaryDns: String,
    /** DoT-хост на порту 853; null — используется [primaryDns]. */
    val dotHost: String?,
    val checkDns: String,
    val outputFileName: String,
    /** Провайдер с самоподписанным/просроченным сертификатом. */
    val allowInvalidTls: Boolean = false,
)
