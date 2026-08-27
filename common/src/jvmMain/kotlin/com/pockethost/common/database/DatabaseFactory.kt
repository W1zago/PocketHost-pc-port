package com.pockethost.common.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.pockethost.common.util.AppPaths
import com.pockethost.database.Database

object DatabaseFactory {
    @Volatile
    private var database: Database? = null

    fun createDriver(): SqlDriver {
        val dbFile = AppPaths.databaseFile()
        dbFile.parentFile?.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (!dbFile.exists() || dbFile.length() == 0L) {
            Database.Schema.create(driver)
        } else {
            try {
                // Ensure schema exists, try to create if missing tables
                Database.Schema.create(driver)
            } catch (e: Exception) {
                // ignore if already exists
            }
        }
        // Ensure foreign keys
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        } catch (_: Exception) {}
        return driver
    }

    fun createDatabase(): Database {
        if (database != null) return database!!
        synchronized(this) {
            if (database == null) {
                val driver = createDriver()
                database = Database(driver)
            }
            return database!!
        }
    }

    fun getDatabase(): Database = database ?: createDatabase()
}
