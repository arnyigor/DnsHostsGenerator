package com.arny.dnshostsgenerator.data

/** Группа seed-данных: имя и домены в порядке появления в [DefaultDomainList.text]. */
data class SeedGroup(
    val name: String,
    val domains: List<String>,
)

/**
 * Разбор [DefaultDomainList.text] на группы. Нужен, чтобы `seed_data.sql` собирался из того же
 * источника, что и текст по умолчанию: до этого SQL был помечен «auto-generated», но генератора
 * в репозитории не было, и файлы расходились молча.
 */
object DomainSeed {

    fun groups(text: String = DefaultDomainList.text): List<SeedGroup> {
        val groups = mutableListOf<SeedGroup>()
        var currentName: String? = null
        var currentDomains = mutableListOf<String>()

        fun flush() {
            val name = currentName ?: return
            if (currentDomains.isNotEmpty()) groups += SeedGroup(name, currentDomains.toList())
        }

        text.replace("\r\n", "\n").split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("# ---") -> {
                    flush()
                    currentName = line.removePrefix("# ---").removeSuffix("---").trim()
                    currentDomains = mutableListOf()
                }
                line.startsWith("#") -> Unit
                else -> currentDomains += line.lowercase()
            }
        }
        flush()
        return groups
    }

    /** SQL в том же формате, что лежит в `composeResources/files/seed_data.sql`. */
    fun sql(text: String = DefaultDomainList.text): String {
        val groups = groups(text)
        return buildString {
            appendLine("-- Auto-generated seed SQL for groups and domains")
            appendLine("-- Source: DefaultDomainList.kt (перегенерировать: см. SeedDataSqlTest)")
            appendLine("BEGIN TRANSACTION;")
            appendLine()
            groups.forEachIndexed { index, group ->
                appendLine(
                    "INSERT OR IGNORE INTO groups (id, name, isEnabled) " +
                        "VALUES (${index + 1}, '${group.name.replace("'", "''")}', 1);",
                )
            }
            appendLine()
            var domainId = 1
            groups.forEachIndexed { index, group ->
                group.domains.forEach { domain ->
                    appendLine(
                        "INSERT OR IGNORE INTO domains (id, domain, groupId, isCustom) " +
                            "VALUES ($domainId, '$domain', ${index + 1}, 0);",
                    )
                    domainId++
                }
            }
            appendLine()
            appendLine("COMMIT;")
        }
    }
}
