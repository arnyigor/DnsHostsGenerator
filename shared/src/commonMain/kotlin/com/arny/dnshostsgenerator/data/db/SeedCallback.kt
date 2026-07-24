package com.arny.dnshostsgenerator.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dnshostsgenerator.shared.generated.resources.Res

private const val SEED_SQL_PATH = "files/seed_data.sql"

class SeedCallback(
    private val sqlStatements: List<String>,
) : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        super.onCreate(connection)
        sqlStatements.forEach { statement ->
            if (statement.isNotBlank()) {
                connection.execSQL(statement)
            }
        }
    }
}

suspend fun loadSeedStatements(): List<String> {
    val content = Res.readBytes(SEED_SQL_PATH).decodeToString()
    return parseSeedStatements(content)
}

internal fun parseSeedStatements(content: String): List<String> =
    content
        .split(';')
        .map { it.trim() }
        .filter { statement ->
            statement.startsWith("INSERT", ignoreCase = true)
        }
