package com.arny.dnshostsgenerator.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

actual fun createAppDatabase(): AppDatabase {
    val dbFile = File(databaseDirectory(), DATABASE_NAME)
    val seedStatements = runBlocking { loadSeedStatements() }
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
        .addCallback(SeedCallback(seedStatements))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

private fun databaseDirectory(): File =
    File(System.getProperty("user.home"), ".dnshostsgenerator").apply {
        if (!exists()) {
            mkdirs()
        }
    }
