package com.arny.dnshostsgenerator.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private var appContext: Context? = null

fun setAndroidDatabaseContext(context: Context) {
    appContext = context.applicationContext
}

actual fun createAppDatabase(): AppDatabase {
    val context = appContext ?: error("Android database context is not initialized")
    val seedStatements = runBlocking { loadSeedStatements() }
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = DATABASE_NAME,
    )
        .addCallback(SeedCallback(seedStatements))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
