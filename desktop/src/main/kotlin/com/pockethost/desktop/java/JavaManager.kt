package com.pockethost.desktop.java

import com.pockethost.common.util.AppPaths
import com.pockethost.common.util.getPlatform
import com.pockethost.common.util.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class JavaRuntime(
    val path: String,
    val version: Int,
    val fullVersion: String,
    val vendor: String,
    val source: JavaSource
)

enum class JavaSource { SYSTEM, BUNDLED, DOWNLOADED }

object JavaManager {
    private val javaHome = AppPaths.javaDir

    init { javaHome.mkdirs() }

    fun findJava(): JavaRuntime? {
        // 1. JAVA_HOME
        System.getenv("JAVA_HOME")?.let { javaHomePath ->
            val javaExec = getJavaExecutable(File(javaHomePath))
            if (javaExec.exists() && javaExec.canExecute()) {
                val version = getJavaVersion(javaExec)
                if (version != null) {
                    return JavaRuntime(javaExec.absolutePath, version.major, version.full, version.vendor, JavaSource.SYSTEM)
                }
            }
        }
        // 2. PATH via where
        try {
            val which = if (getPlatform() == Platform.WINDOWS) ProcessBuilder("where", "java") else ProcessBuilder("which", "java")
            val process = which.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotEmpty()) {
                val javaPath = output.lines().first().trim()
                val file = File(javaPath)
                if (file.exists()) {
                    val version = getJavaVersion(file)
                    if (version != null) {
                        return JavaRuntime(javaPath, version.major, version.full, version.vendor, JavaSource.SYSTEM)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. bundled/downloaded
        javaHome.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val javaExec = findJavaInDirectory(dir) ?: getJavaExecutable(dir)
                if (javaExec.exists()) {
                    val version = getJavaVersion(javaExec)
                    if (version != null) {
                        return JavaRuntime(javaExec.absolutePath, version.major, version.full, version.vendor, JavaSource.BUNDLED)
                    }
                }
            }
        }
        // also direct bin
        val direct = File(javaHome, if (getPlatform() == Platform.WINDOWS) "bin/java.exe" else "bin/java")
        if (direct.exists()) {
            val version = getJavaVersion(direct)
            if (version != null) return JavaRuntime(direct.absolutePath, version.major, version.full, version.vendor, JavaSource.BUNDLED)
        }
        return null
    }

    suspend fun ensureJava(requiredVersion: Int, onProgress: (String) -> Unit = {}): JavaRuntime? {
        val existing = findJava()
        if (existing != null && existing.version >= requiredVersion) {
            onProgress("Found Java ${existing.version} at ${existing.path}")
            return existing
        }
        val downloadedDir = File(javaHome, "jre-$requiredVersion")
        if (downloadedDir.exists()) {
            val javaExec = findJavaInDirectory(downloadedDir) ?: getJavaExecutable(downloadedDir)
            if (javaExec.exists()) {
                val version = getJavaVersion(javaExec)
                if (version != null && version.major >= requiredVersion) {
                    onProgress("Using downloaded Java ${version.major}")
                    return JavaRuntime(javaExec.absolutePath, version.major, version.full, version.vendor, JavaSource.DOWNLOADED)
                }
            }
        }
        onProgress("Downloading Java $requiredVersion...")
        return downloadJava(requiredVersion, onProgress)
    }

    private suspend fun downloadJava(version: Int, onProgress: (String) -> Unit): JavaRuntime? = withContext(Dispatchers.IO) {
        try {
            val platform = getPlatform()
            val arch = getArchitecture()
            val os = when (platform) {
                Platform.WINDOWS -> "windows"
                Platform.LINUX -> "linux"
                Platform.MACOS -> "mac"
            }
            onProgress("Detecting system: $os $arch")
            val apiUrl = "https://api.adoptium.net/v3/binary/latest/$version/ga/$os/$arch/jre/hotspot/normal/eclipse"
            onProgress("Fetching from Adoptium...")
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "PocketHost/1.0")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            if (connection.responseCode != 200) {
                onProgress("Failed to download: HTTP ${connection.responseCode}")
                // Try fallback: try with jdk instead of jre
                val fallbackUrl = "https://api.adoptium.net/v3/binary/latest/$version/ga/$os/$arch/jdk/hotspot/normal/eclipse"
                onProgress("Trying JDK fallback...")
                val conn2 = URL(fallbackUrl).openConnection() as HttpURLConnection
                conn2.instanceFollowRedirects = true
                conn2.setRequestProperty("User-Agent", "PocketHost/1.0")
                if (conn2.responseCode != 200) {
                    onProgress("Fallback also failed: HTTP ${conn2.responseCode}")
                    return@withContext null
                }
                return@withContext downloadAndExtract(conn2, version, onProgress)
            }
            return@withContext downloadAndExtract(connection, version, onProgress)
        } catch (e: Exception) {
            onProgress("Error: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private suspend fun downloadAndExtract(connection: HttpURLConnection, version: Int, onProgress: (String) -> Unit): JavaRuntime? = withContext(Dispatchers.IO) {
        try {
            val contentLength = connection.contentLengthLong
            val tempFile = File.createTempFile("jre-$version", ".zip")
            if (contentLength > 0) onProgress("Downloading ${contentLength / 1024 / 1024} MB...") else onProgress("Downloading...")

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0 && downloaded % (5 * 1024 * 1024) == 0L) {
                            val percent = (downloaded * 100 / contentLength).toInt()
                            onProgress("Download progress: $percent%")
                        }
                    }
                }
            }
            onProgress("Extracting...")
            val targetDir = File(javaHome, "jre-$version")
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()
            extractArchive(tempFile, targetDir)
            tempFile.delete()
            val javaExec = findJavaInDirectory(targetDir)
            if (javaExec == null) {
                onProgress("Error: java executable not found after extraction")
                return@withContext null
            }
            val javaVersion = getJavaVersion(javaExec)
            if (javaVersion == null) {
                onProgress("Error: Cannot verify Java version")
                return@withContext null
            }
            onProgress("Successfully installed Java ${javaVersion.major}")
            JavaRuntime(javaExec.absolutePath, javaVersion.major, javaVersion.full, javaVersion.vendor, JavaSource.DOWNLOADED)
        } catch (e: Exception) {
            onProgress("Extract error: ${e.message}")
            null
        }
    }

    private fun getJavaExecutable(javaDir: File): File {
        val executable = if (getPlatform() == Platform.WINDOWS) "java.exe" else "java"
        return File(javaDir, "bin/$executable")
    }

    data class JavaVersion(val major: Int, val full: String, val vendor: String)

    fun getJavaVersion(javaExecutable: File): JavaVersion? {
        return try {
            val process = ProcessBuilder(javaExecutable.absolutePath, "-version").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val versionRegex = """"(\d+)\.(\d+)\.(\d+).*"""".toRegex()
            var match = versionRegex.find(output)
            if (match != null) {
                val major = match.groupValues[1].toInt()
                val vendor = when {
                    output.contains("Temurin", ignoreCase = true) || output.contains("Adoptium", ignoreCase = true) -> "Temurin"
                    output.contains("OpenJDK") -> "OpenJDK"
                    output.contains("Oracle") -> "Oracle"
                    output.contains("Azul") -> "Azul Zulu"
                    output.contains("Amazon") -> "Amazon Corretto"
                    output.contains("Microsoft") -> "Microsoft"
                    else -> "Unknown"
                }
                JavaVersion(major, match.groupValues[0].trim('"'), vendor)
            } else {
                val altRegex = """"1\.(\d+)\.(\d+).*"""".toRegex()
                val altMatch = altRegex.find(output)
                if (altMatch != null) {
                    JavaVersion(altMatch.groupValues[1].toInt(), altMatch.groupValues[0].trim('"'), "Unknown")
                } else {
                    // Try parse like 'openjdk version "21.0.3"'
                    val simpleRegex = """"(\d+).*"""".toRegex()
                    val simple = simpleRegex.find(output)
                    if (simple != null) {
                        JavaVersion(simple.groupValues[1].toInt(), simple.groupValues[0].trim('"'), "Unknown")
                    } else null
                }
            }
        } catch (e: Exception) { null }
    }

    private fun extractArchive(archive: File, targetDir: File) {
        when {
            archive.name.endsWith(".zip") -> extractZip(archive, targetDir)
            archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") -> extractTarGz(archive, targetDir)
            else -> extractZip(archive, targetDir) // Adoptium usually zip on Windows
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        // If zip contains single top-level folder, flatten? Keep as is and findJavaInDirectory will handle nested.
    }

    private fun extractTarGz(tarGzFile: File, targetDir: File) {
        ProcessBuilder("tar", "-xzf", tarGzFile.absolutePath, "-C", targetDir.absolutePath).start().waitFor()
    }

    private fun findJavaInDirectory(dir: File): File? {
        val executable = if (getPlatform() == Platform.WINDOWS) "java.exe" else "java"
        // walk
        dir.walkTopDown().firstOrNull { it.isFile && it.name == executable && it.path.contains("bin") }?.let { return it }
        // also check directly
        val direct = getJavaExecutable(dir)
        if (direct.exists()) return direct
        return null
    }

    private fun getArchitecture(): String {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("amd64") || arch.contains("x86_64") -> "x64"
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("arm") -> "arm"
            arch.contains("x86") -> "x86"
            else -> "x64"
        }
    }

    fun requiredJavaForMinecraft(version: String?): Int {
        if (version == null) return 17
        return when {
            version.startsWith("1.21") || version.startsWith("1.20.5") || version.startsWith("1.20.6") -> 21
            version.startsWith("1.20") || version.startsWith("1.19") || version.startsWith("1.18") -> 17
            version.startsWith("1.17") -> 16
            version.startsWith("1.16") -> 8
            else -> 8
        }
    }
}
