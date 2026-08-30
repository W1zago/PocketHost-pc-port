package com.pockethost.desktop.util

import java.io.File
import java.util.Properties
import java.util.UUID

object ServerPropertiesManager {

    private fun getPropertiesFile(serverDir: File): File = File(serverDir, "server.properties")

    private fun loadProperties(serverDir: File): Properties {
        val props = Properties()
        val file = getPropertiesFile(serverDir)
        if (file.exists()) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (_: Exception) {}
        }
        return props
    }

    private fun storeProperties(serverDir: File, props: Properties) {
        try {
            serverDir.mkdirs()
            val file = getPropertiesFile(serverDir)
            file.outputStream().use { props.store(it, "PocketHost server.properties") }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOfflineMode(serverDir: File): Boolean {
        val props = loadProperties(serverDir)
        return props.getProperty("online-mode") == "false"
    }

    fun setOfflineMode(serverDir: File, offline: Boolean) {
        val props = loadProperties(serverDir)
        props.setProperty("online-mode", if (offline) "false" else "true")
        storeProperties(serverDir, props)
        if (offline) {
            try {
                val uuid = UUID.randomUUID().toString()
                File(serverDir, ".offline-uuid").writeText(uuid)
            } catch (_: Exception) {}
        }
    }

    fun getRenderDistance(serverDir: File): Int {
        val props = loadProperties(serverDir)
        return props.getProperty("view-distance")?.toIntOrNull()?.coerceIn(2, 32) ?: 10
    }

    fun setRenderDistance(serverDir: File, distance: Int) {
        val clamped = distance.coerceIn(2, 32)
        val props = loadProperties(serverDir)
        props.setProperty("view-distance", clamped.toString())
        storeProperties(serverDir, props)
    }

    fun getProperty(serverDir: File, key: String, default: String? = null): String? {
        val props = loadProperties(serverDir)
        return props.getProperty(key, default)
    }

    fun setProperty(serverDir: File, key: String, value: String) {
        val props = loadProperties(serverDir)
        props.setProperty(key, value)
        storeProperties(serverDir, props)
    }

    fun ensureDefaults(serverDir: File) {
        val props = loadProperties(serverDir)
        var changed = false
        if (!props.containsKey("online-mode")) {
            props.setProperty("online-mode", "true")
            changed = true
        }
        if (!props.containsKey("view-distance")) {
            props.setProperty("view-distance", "10")
            changed = true
        }
        if (changed) storeProperties(serverDir, props)
    }
}
