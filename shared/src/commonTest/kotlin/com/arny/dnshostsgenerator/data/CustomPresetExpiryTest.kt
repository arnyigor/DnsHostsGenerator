package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.data.db.CustomPresetEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Срок действия своего провайдера: персональные эндпоинты выдают токен на срок,
 * и протухший адрес не должен выглядеть рабочим.
 */
class CustomPresetExpiryTest {

    private fun entity(expiresAt: Long?) = CustomPresetEntity(
        id = "custom_1",
        title = "Personal relay",
        primaryDns = "relay.example",
        dotHost = "relay.example",
        checkDns = "8.8.8.8",
        outputFileName = "hosts_personal.txt",
        allowInvalidTls = false,
        expiresAt = expiresAt,
    )

    @Test
    fun noExpiryMeansAlwaysEnabled() {
        val preset = entity(null).toPreset(now = 1_000_000)

        assertTrue(preset.enabledByDefault)
        assertTrue(preset.isCustom)
        assertNull(preset.disabledReason)
    }

    @Test
    fun futureExpiryStaysEnabled() {
        val preset = entity(expiresAt = 2_000).toPreset(now = 1_000)

        assertTrue(preset.enabledByDefault, "провайдер действует до 2000, сейчас 1000")
    }

    @Test
    fun pastExpiryDisablesWithReason() {
        val preset = entity(expiresAt = 1_000).toPreset(now = 1_500)

        assertFalse(preset.enabledByDefault, "срок истёк — пресет не должен участвовать в генерации")
        assertTrue(
            preset.disabledReason.orEmpty().contains("срок"),
            "должна быть причина, иначе непонятно, почему провайдер пропал: ${preset.disabledReason}",
        )
    }

    @Test
    fun mergePresetsKeepsExpiredVisibleButDisabled() {
        val merged = mergePresets(listOf(entity(expiresAt = 500)), now = 1_000)
        val custom = merged.single { it.id == "custom_1" }

        // Провайдер остаётся в списке — человек должен видеть, что он был и почему отключён.
        assertFalse(custom.enabledByDefault)
        assertEquals("срок действия истёк: продлите доступ у провайдера", custom.disabledReason)
    }
}
