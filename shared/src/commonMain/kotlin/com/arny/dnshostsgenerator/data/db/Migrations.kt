package com.arny.dnshostsgenerator.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: таблица пользовательских DNS-провайдеров.
 *
 * Схема должна совпадать с [CustomPresetEntity] до последнего символа, иначе Room
 * упадёт при открытии базы. Существующие группы и домены не затрагиваются.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_presets` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`primaryDns` TEXT NOT NULL, " +
                "`dotHost` TEXT, " +
                "`checkDns` TEXT NOT NULL, " +
                "`outputFileName` TEXT NOT NULL, " +
                "`allowInvalidTls` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v2 → v3: срок действия своего провайдера.
 *
 * У существующих записей срока нет — NULL значит «бессрочно», поведение не меняется.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `custom_presets` ADD COLUMN `expiresAt` INTEGER")
    }
}
