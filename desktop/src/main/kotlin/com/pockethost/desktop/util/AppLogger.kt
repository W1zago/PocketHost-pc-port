package com.pockethost.desktop.util

import com.pockethost.common.util.AppPaths
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AppLogger {
    private val logger = LoggerFactory.getLogger("PocketHost")

    private val _logFile: File by lazy {
        val dir = AppPaths.logsDir
        dir.mkdirs()
        File(dir, "pockethost.log")
    }

    private val serverLogDir: File by lazy {
        AppPaths.logsDir
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun info(msg: String) {
        logger.info(msg)
        appendRaw("[INFO] ${now()} $msg")
    }

    fun warn(msg: String) {
        logger.warn(msg)
        appendRaw("[WARN] ${now()} $msg")
    }

    fun error(msg: String, t: Throwable? = null) {
        if (t != null) logger.error(msg, t) else logger.error(msg)
        appendRaw("[ERROR] ${now()} $msg ${t?.message ?: ""}")
    }

    fun debug(msg: String) {
        logger.debug(msg)
    }

    fun logServer(serverId: String, message: String) {
        logger.info("[$serverId] $message")
        val f = File(serverLogDir, "server-$serverId.log")
        try {
            f.appendText("[${now()}] $message\n")
            // Also central log
            appendRaw("[$serverId] $message")
        } catch (_: Exception) {}
    }

    private fun now(): String = LocalDateTime.now().format(formatter)

    private fun appendRaw(text: String) {
        try {
            // Ensure log rotation by size (simple)
            if (_logFile.exists() && _logFile.length() > 10 * 1024 * 1024) {
                val rotated = File(_logFile.parent, "pockethost.${System.currentTimeMillis()}.log")
                _logFile.renameTo(rotated)
            }
            _logFile.appendText("$text\n")
        } catch (_: Exception) {}
    }

    fun getLogFile(): File = _logFile
}
