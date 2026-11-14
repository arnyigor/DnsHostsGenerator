package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.data.db.CustomPresetEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Свои DNS-провайдеры: преобразование из БД в пресет и имя файла результата. */
class CustomPresetMappingTest {

    private val entity = CustomPresetEntity(
        id = "custom_abc",
        title = "Мой SmartDNS",
        primaryDns = "dns.example.com",
        dotHost = "dot.example.com",
        checkDns = "dns.example.com",
        outputFileName = "hosts_мой_smartdns.txt",
    )

    @Test
    fun customPresetIsEnabledAndMarked() {
        val preset = entity.toPreset()

        assertTrue(preset.enabledByDefault, "свой провайдер всегда доступен для выбора")
        assertTrue(preset.isCustom)
        assertEquals("dns.example.com", preset.primaryDns)
        assertEquals("dot.example.com", preset.dotHost)
    }

    @Test
    fun builtInPresetsStayFirstAndUnchanged() {
        val merged = mergePresets(listOf(entity))

        assertEquals(BuiltInDnsPresets.defaults.size + 1, merged.size)
        assertEquals(BuiltInDnsPresets.defaults, merged.dropLast(1))
        // Рекомендуемый пресет должен остаться первым, иначе выбор по умолчанию сместится.
        assertEquals(BuiltInDnsPresets.defaults.first(), merged.first())
        assertEquals("custom_abc", merged.last().id)
    }

    @Test
    fun emptyCustomListKeepsBuiltIn() {
        assertEquals(BuiltInDnsPresets.defaults, mergePresets(emptyList()))
    }

    @Test
    fun fileNameIsSafeAndNeverEmpty() {
        // Кириллица в имени файла допустима и понятнее транслитерации.
        assertEquals("hosts_мой_smartdns.txt", customPresetFileName("Мой SmartDNS", "custom_x"))
        assertEquals("hosts_dns_example_com.txt", customPresetFileName("dns.example.com", "custom_x"))
        assertEquals("hosts_custom_x.txt", customPresetFileName("!!!", "custom_x"))
        assertTrue(customPresetFileName("a".repeat(80), "custom_x").length <= 60)
    }
}
