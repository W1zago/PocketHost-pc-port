package com.pockethost.common.util

import java.io.File

object AppPaths {
    val homeDir: File get() = File(System.getProperty("user.home"))
    val appDir: File get() = File(homeDir, ".pockethost").also { it.mkdirs() }
    val serversDir: File
        get() {
            val custom = try { SettingsStore.getServersDir() } catch (_: Exception) { null }
            return if (!custom.isNullOrBlank()) {
                val f = File(custom)
                try { f.mkdirs() } catch (_: Exception) {}
                f
            } else {
                File(appDir, "servers").also { it.mkdirs() }
            }
        }
    val configDir: File get() = File(appDir, "config").also { it.mkdirs() }
    val javaDir: File get() = File(appDir, "java").also { it.mkdirs() }
    val logsDir: File get() = File(appDir, "logs").also { it.mkdirs() }
    val tempDir: File get() = File(appDir, "temp").also { it.mkdirs() }

    fun serverDir(name: String): File = File(serversDir, name)

    fun databaseFile(): File = File(configDir, "pockethost.db")

    fun setCustomServersDir(path: String) {
        SettingsStore.setServersDir(path)
        File(path).mkdirs()
    }

    fun resetServersDir() {
        val f = File(configDir, "settings.properties")
        try {
            val props = java.util.Properties()
            if (f.exists()) f.inputStream().use { props.load(it) }
            props.remove("serversDir")
            f.outputStream().use { props.store(it, "PocketHost Settings") }
        } catch (_: Exception) {}
    }

    fun isCustomServersDir(): Boolean = !SettingsStore.getServersDir().isNullOrBlank()
}
