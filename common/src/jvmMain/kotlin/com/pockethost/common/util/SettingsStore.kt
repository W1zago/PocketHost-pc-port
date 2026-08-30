package com.pockethost.common.util

import java.io.File
import java.util.Properties

object SettingsStore {
    private val configFile: File by lazy {
        val dir = AppPaths.configDir
        dir.mkdirs()
        File(dir, "settings.properties")
    }

    private val props = Properties()

    init {
        load()
    }

    private fun load() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { props.load(it) }
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.outputStream().use { props.store(it, "PocketHost Settings") }
        } catch (_: Exception) {}
    }

    fun get(key: String, default: String? = null): String? {
        return props.getProperty(key, default)
    }

    fun set(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }

    fun getServersDir(): String? = get("serversDir")
    fun setServersDir(path: String) = set("serversDir", path)

    fun getLanguage(): String? = get("language")
    fun setLanguage(lang: String) = set("language", lang)
}
