package com.arny.dnshostsgenerator.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Страховка от рассинхрона `seed_data.sql` ↔ `DefaultDomainList.text`.
 *
 * Файл помечен «auto-generated», но генератора в репозитории не было, и они расходились молча.
 * Перегенерация после правки [DefaultDomainList]:
 *
 *     DNSHOSTS_WRITE_SEED=1 ./gradlew :shared:jvmTest --tests "*SeedDataSqlTest*" --rerun-tasks
 *
 * Тест запускается из каталога модуля `shared/`, поэтому путь ниже — относительный.
 */
class SeedDataSqlTest {

    private val seedFile = File("src/commonMain/composeResources/files/seed_data.sql")

    @Test
    fun seedSqlMatchesDefaultDomainList() {
        if (!seedFile.exists()) return
        val generated = DomainSeed.sql()
        val current = seedFile.readText()
        if (System.getenv("DNSHOSTS_WRITE_SEED") == "1") {
            val separator = if ("\r\n" in current) "\r\n" else "\n"
            seedFile.writeText(generated.replace("\n", separator))
            println("seed_data.sql перегенерирован: строк=${generated.lines().size}")
            return
        }
        assertEquals(
            current.replace("\r\n", "\n"),
            generated,
            "seed_data.sql разошёлся с DefaultDomainList.kt — перегенерируй тестом (DNSHOSTS_WRITE_SEED=1)",
        )
    }

    @Test
    fun seedParsesAllGroupsAndDomains() {
        val groups = DomainSeed.groups()

        assertTrue(groups.size >= 29, "групп: ${groups.size}")
        assertTrue(groups.flatMap { it.domains }.size >= 348, "доменов: ${groups.flatMap { it.domains }.size}")
        assertTrue(groups.all { it.name.isNotBlank() && it.domains.isNotEmpty() })
        // Группы из текста должны совпадать с тем, что уезжает в базу.
        assertEquals(groups.map { it.name }, groups.map { it.name }.distinct())
    }
}
