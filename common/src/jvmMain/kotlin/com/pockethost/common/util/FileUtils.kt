package com.pockethost.common.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {

    fun createDirectory(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteDirectory(path: String): Boolean {
        return try {
            File(path).deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    fun copyFile(source: String, destination: String): Boolean {
        return try {
            File(source).copyTo(File(destination), overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readFile(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun zipDirectory(sourceDir: String, outputZip: String): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                File(sourceDir).walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val zipEntry = ZipEntry(file.relativeTo(File(sourceDir)).path)
                        zos.putNextEntry(zipEntry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getFileSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            0L
        }
    }

    fun listFiles(path: String): List<File> {
        return try {
            File(path).listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    fun validatePath(path: File, baseDir: File): Boolean {
        return try {
            val canonical = path.canonicalPath
            val base = baseDir.canonicalPath
            canonical.startsWith(base)
        } catch (_: Exception) { false }
    }

    fun validateServerName(name: String): String? {
        return when {
            name.isBlank() -> "Name cannot be empty"
            name.length < 3 -> "Name must be at least 3 characters"
            name.length > 32 -> "Name must be at most 32 characters"
            !name.matches(Regex("^[a-zA-Z0-9_-]+$")) -> "Name can only contain letters, numbers, hyphens and underscores"
            name.startsWith("-") || name.startsWith("_") -> "Name cannot start with hyphen or underscore"
            else -> null
        }
    }
}
