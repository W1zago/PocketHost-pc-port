package com.pockethost.desktop.minecraft

import com.pockethost.common.model.MinecraftLoader
import com.pockethost.common.model.Server
import com.pockethost.common.model.ServerConfig
import com.pockethost.common.model.ServerType
import com.pockethost.common.util.AppPaths
import com.pockethost.common.util.FileUtils
import com.pockethost.common.util.NetworkUtils
import com.pockethost.desktop.java.JavaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MinecraftServerManager {
    private val serversDir: File = AppPaths.serversDir

    suspend fun createServer(
        name: String,
        version: String,
        loader: MinecraftLoader,
        port: Int = 25565,
        maxMemory: Int = 2048,
        minMemory: Int = 1024,
        customDir: String? = null,
        onProgress: (String) -> Unit = {}
    ): Server? = withContext(Dispatchers.IO) {
        try {
            val serverDir = if (!customDir.isNullOrBlank()) File(customDir) else File(serversDir, name)
            if (serverDir.exists() && serverDir.listFiles()?.isNotEmpty() == true) {
                onProgress("Warning: Directory ${serverDir.absolutePath} already exists and not empty")
            }
            serverDir.mkdirs()
            if (!serverDir.exists() || !serverDir.canWrite()) {
                onProgress("ERROR: Cannot write to ${serverDir.absolutePath} - check permissions/disk")
                return@withContext null
            }

            val availablePort = if (NetworkUtils.isPortAvailable(port)) port else NetworkUtils.getAvailablePort(port)
            if (availablePort == -1) {
                onProgress("ERROR: No available port")
                return@withContext null
            }
            // P3: attempt firewall rule (best-effort, no Admin = logged)
            try {
                val fwOk = com.pockethost.desktop.network.FirewallManager.addFirewallRule(availablePort, "PocketHost-$name")
                if (fwOk) onProgress("Firewall rule added for port $availablePort")
                else onProgress("Firewall rule not added (run as Admin to allow port $availablePort)")
                com.pockethost.desktop.util.AppLogger.info("Firewall check for $name port $availablePort: $fwOk")
            } catch (e: Exception) { onProgress("Firewall check failed: ${e.message}") }

            val jarName = getJarName(loader)
            val jarFile = File(serverDir, jarName)
            val genericJar = File(serverDir, "server.jar")
            val hasJar = (jarFile.exists() && jarFile.length() > 1024 * 1024) || (genericJar.exists() && genericJar.length() > 1024 * 1024)
            if (!hasJar) {
                onProgress("Downloading $loader $version...")
                val downloaded = downloadServerJar(serverDir, version, loader, onProgress)
                if (downloaded == null) {
                    onProgress("ERROR: Failed to download server JAR for $loader $version")
                    // Do not return null immediately; still create server entry but will provision later
                    // For FORGE/NEOFORGE/SPIGOT we allow manual install, so continue
                    if (loader == MinecraftLoader.VANILLA || loader == MinecraftLoader.PAPER || loader == MinecraftLoader.PURPUR || loader == MinecraftLoader.FABRIC) {
                        return@withContext null
                    } else {
                        onProgress("Warning: $loader requires manual JAR install. Place JAR as $jarName in $serverDir")
                    }
                } else {
                    onProgress("Downloaded $downloaded")
                }
            } else {
                onProgress("Server JAR already exists")
            }

            createEula(serverDir)
            createServerProperties(serverDir, availablePort)
            val effectiveJar = if (File(serverDir, jarName).exists()) jarName else if (genericJar.exists()) "server.jar" else jarName
            createStartScript(serverDir, effectiveJar, maxMemory, minMemory)

            val javaPath = JavaManager.findJava()?.path ?: "java"

            val config = ServerConfig(
                startCommand = "$javaPath -Xmx${maxMemory}M -Xms${minMemory}M -XX:+UseG1GC -jar $effectiveJar nogui",
                stopCommand = "stop",
                maxMemory = maxMemory,
                minMemory = minMemory,
                javaArgs = listOf("-XX:+UseG1GC"),
                minecraftVersion = version,
                minecraftLoader = loader
            )

            val server = Server(
                name = name,
                type = ServerType.MINECRAFT,
                port = availablePort,
                workingDirectory = serverDir.absolutePath,
                config = config
            )
            onProgress("Server created successfully!")
            server
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("EXCEPTION: ${e.message}")
            null
        }
    }

    suspend fun provisionIfNeeded(
        serverDir: String,
        version: String,
        loader: MinecraftLoader?,
        maxMemory: Int = 1024,
        minMemory: Int = 512,
        port: Int = 25565,
        log: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(serverDir)
            dir.mkdirs()
            val software = loader ?: MinecraftLoader.PAPER
            val jarName = getJarName(software)
            val jarFile = File(dir, jarName)
            val genericJar = File(dir, "server.jar")
            val hasJar = (jarFile.exists() && jarFile.length() > 1024 * 1024) || (genericJar.exists() && genericJar.length() > 1024 * 1024)
            if (!hasJar && (software == MinecraftLoader.VANILLA || software == MinecraftLoader.PAPER || software == MinecraftLoader.PURPUR || software == MinecraftLoader.FABRIC)) {
                log("Minecraft jar not found, downloading $software $version...")
                val downloaded = downloadServerJar(dir, version, software, log)
                if (downloaded == null) {
                    log("ERROR: Download failed. Check internet and try Paper/Vanilla.")
                    return@withContext false
                }
            } else if (!hasJar) {
                log("Manual install required for $software: place $jarName in $serverDir")
                // Don't fail, allow start attempt
            } else {
                log("Found existing jar: ${if (jarFile.exists()) jarName else "server.jar"}")
            }
            val eulaFile = File(dir, "eula.txt")
            if (!eulaFile.exists()) {
                createEula(dir)
                log("Created eula.txt")
            }
            val propFile = File(dir, "server.properties")
            if (!propFile.exists()) {
                createServerProperties(dir, port)
                log("Created server.properties (port $port)")
            }
            val startSh = File(dir, "start.bat")
            val startSh2 = File(dir, "start.sh")
            if (!startSh.exists() && !startSh2.exists()) {
                val effectiveJar = if (jarFile.exists()) jarName else if (genericJar.exists()) "server.jar" else jarName
                createStartScript(dir, effectiveJar, maxMemory, minMemory)
                log("Created start script")
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            log("Provision error: ${e.message}")
            return@withContext false
        }
    }

    fun getJarName(loader: MinecraftLoader): String {
        return when (loader) {
            MinecraftLoader.VANILLA -> "server.jar"
            MinecraftLoader.PAPER -> "paper.jar"
            MinecraftLoader.SPIGOT -> "spigot.jar"
            MinecraftLoader.FABRIC -> "fabric-server-launch.jar"
            MinecraftLoader.FORGE -> "forge.jar"
            MinecraftLoader.NEOFORGE -> "neoforge.jar"
            MinecraftLoader.PURPUR -> "purpur.jar"
            MinecraftLoader.BUKKIT -> "craftbukkit.jar"
        }
    }

    private suspend fun downloadServerJar(
        serverDir: File,
        version: String,
        loader: MinecraftLoader,
        log: ((String) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val jarName = getJarName(loader)
            val jarPath = File(serverDir, jarName)
            val downloadUrl = getDownloadUrl(version, loader, log)
            if (downloadUrl == null) {
                log?.invoke("No download URL for $loader $version. Spigot/Forge/NeoForge/Bukkit require manual BuildTools/installer.")
                return@withContext null
            }
            log?.invoke("Fetching $downloadUrl")
            downloadFile(downloadUrl, jarPath, log)
            if (!jarPath.exists() || jarPath.length() < 1024) {
                log?.invoke("Downloaded file invalid")
                return@withContext null
            }
            jarName
        } catch (e: Exception) {
            e.printStackTrace()
            log?.invoke("Download error: ${e.message}")
            null
        }
    }

    private suspend fun getDownloadUrl(version: String, loader: MinecraftLoader, log: ((String) -> Unit)?): String? = withContext(Dispatchers.IO) {
        return@withContext when (loader) {
            MinecraftLoader.VANILLA -> getVanillaUrl(version, log)
            MinecraftLoader.PAPER -> getPaperUrl(version, log)
            MinecraftLoader.PURPUR -> getPurpurUrl(version, log)
            MinecraftLoader.FABRIC -> getFabricUrl(version, log)
            MinecraftLoader.SPIGOT -> null // requires BuildTools
            MinecraftLoader.FORGE -> null // requires installer
            MinecraftLoader.NEOFORGE -> null
            MinecraftLoader.BUKKIT -> null
        }
    }

    private fun getVanillaUrl(version: String, log: ((String) -> Unit)?): String? {
        return try {
            log?.invoke("Resolving Vanilla $version manifest...")
            var manifestJson: String? = null
            val manifestUrls = listOf(
                "https://piston-meta.mojang.com/mc/game/version_manifest.json",
                "https://launchermeta.mojang.com/mc/game/version_manifest.json",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
            )
            var attempts = 0
            while (manifestJson == null && attempts < 6) {
                for (u in manifestUrls) {
                    try {
                        log?.invoke("Trying manifest: $u (attempt ${attempts + 1})")
                        manifestJson = httpGet(u)
                        if (manifestJson != null) break
                    } catch (e: Exception) {
                        log?.invoke("Manifest $u failed: ${e.message}")
                    }
                }
                if (manifestJson == null) {
                    attempts++
                    if (attempts < 6) {
                        log?.invoke("Retrying manifest in ${attempts * 2}s... ($attempts/6)")
                        Thread.sleep((attempts * 2000).toLong())
                    }
                }
            }
            if (manifestJson == null) {
                log?.invoke("All manifest URLs failed")
                return null
            }
            val manifest = JSONObject(manifestJson)
            val versions = manifest.getJSONArray("versions")
            var versionUrl: String? = null
            for (i in 0 until versions.length()) {
                val obj = versions.getJSONObject(i)
                if (obj.getString("id") == version) {
                    versionUrl = obj.getString("url")
                    break
                }
            }
            if (versionUrl == null) {
                log?.invoke("Version $version not found, trying latest release")
                val latest = manifest.getJSONObject("latest")
                val release = latest.getString("release")
                for (i in 0 until versions.length()) {
                    val obj = versions.getJSONObject(i)
                    if (obj.getString("id") == release) {
                        versionUrl = obj.getString("url")
                        break
                    }
                }
            }
            if (versionUrl == null) return null
            log?.invoke("Fetching version meta: $versionUrl")
            var versionMetaJson: String? = null
            repeat(3) { attempt ->
                try {
                    versionMetaJson = httpGet(versionUrl)
                    if (versionMetaJson != null) return@repeat
                } catch (e: Exception) {
                    log?.invoke("Version meta attempt ${attempt + 1} failed: ${e.message}")
                }
                if (versionMetaJson == null && attempt < 2) Thread.sleep(2000)
            }
            if (versionMetaJson == null) return null
            val versionMeta = JSONObject(versionMetaJson)
            val downloads = versionMeta.getJSONObject("downloads")
            val server = downloads.getJSONObject("server")
            val url = server.getString("url")
            log?.invoke("Vanilla URL: $url")
            url
        } catch (e: Exception) {
            e.printStackTrace()
            log?.invoke("Vanilla resolve failed: ${e.message}")
            null
        }
    }

    private fun getPaperUrl(version: String, log: ((String) -> Unit)?): String? {
        return try {
            log?.invoke("Resolving Paper $version via fill.papermc.io...")
            val buildsJson = httpGet("https://fill.papermc.io/v3/projects/paper/versions/$version/builds")
            if (buildsJson == null) {
                log?.invoke("Paper version $version not found on fill.papermc.io (try Vanilla or check version exists).")
                return null
            }
            val arr = JSONArray(buildsJson)
            if (arr.length() == 0) {
                log?.invoke("No Paper builds for $version")
                return null
            }
            // Prefer STABLE channel
            var target: JSONObject? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("channel") == "STABLE") { target = obj; break }
            }
            if (target == null) target = arr.getJSONObject(0)
            val downloads = target!!.getJSONObject("downloads")
            // server:default is preferred for Paper
            val dl = downloads.optJSONObject("server:default") ?: downloads.optJSONObject("server") ?: downloads.optJSONObject("application")
            val url = dl?.optString("url")
            if (!url.isNullOrEmpty()) {
                log?.invoke("Paper URL: $url")
                return url
            }
            log?.invoke("No stable download URL for Paper $version")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            log?.invoke("Paper resolve failed: ${e.message}")
            null
        }
    }

    private fun getPurpurUrl(version: String, log: ((String) -> Unit)?): String? {
        return try {
            log?.invoke("Resolving Purpur $version ...")
            val verJson = httpGet("https://api.purpurmc.org/v2/purpur/$version")
            if (verJson != null) {
                val obj = JSONObject(verJson)
                val builds = obj.getJSONObject("builds")
                val latest = builds.getString("latest")
                val url = "https://api.purpurmc.org/v2/purpur/$version/$latest/download"
                log?.invoke("Purpur URL: $url")
                return url
            }
            "https://api.purpurmc.org/v2/purpur/$version/latest/download"
        } catch (e: Exception) {
            e.printStackTrace()
            log?.invoke("Purpur resolve failed: ${e.message}")
            "https://api.purpurmc.org/v2/purpur/$version/latest/download"
        }
    }

    private fun getFabricUrl(version: String, log: ((String) -> Unit)?): String? {
        return try {
            log?.invoke("Resolving Fabric for $version ...")
            val loadersJson = httpGet("https://meta.fabricmc.net/v2/versions/loader/$version")
            if (loadersJson != null) {
                val arr = JSONArray(loadersJson)
                if (arr.length() > 0) {
                    val latest = arr.getJSONObject(0)
                    val loader = latest.getJSONObject("loader")
                    val loaderVersion = loader.getString("version")
                    log?.invoke("Fabric loader $loaderVersion for $version")
                    val url = "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVersion/1.0.0/server/jar"
                    log?.invoke("Fabric URL: $url")
                    return url
                }
            }
            val globalLoaders = httpGet("https://meta.fabricmc.net/v2/versions/loader")
            if (globalLoaders != null) {
                val arr = JSONArray(globalLoaders)
                if (arr.length() > 0) {
                    val loaderVersion = arr.getJSONObject(0).getString("version")
                    val url = "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVersion/1.0.0/server/jar"
                    return url
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            log?.invoke("Fabric resolve failed: ${e.message}")
            null
        }
    }

    private fun httpGet(urlStr: String): String? {
        var current = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(current)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "PocketHost/1.0.0 (https://github.com/pockethost)")
            conn.instanceFollowRedirects = false
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    current = loc
                    redirects++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                return conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun downloadFile(urlStr: String, target: File, log: ((String) -> Unit)?) {
        var current = urlStr
        var redirects = 0
        while (true) {
            val url = URL(current)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 90000
            conn.setRequestProperty("User-Agent", "PocketHost/1.0.0 (https://github.com/pockethost)")
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location")
                if (++redirects > 5) throw IllegalStateException("Too many redirects")
                current = loc
                conn.disconnect()
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw IllegalStateException("HTTP $code for $current")
            }
            val total = conn.contentLengthLong
            log?.invoke("Downloading ${target.name} (${if (total > 0) "${total / 1024 / 1024} MB" else "unknown size"})...")
            target.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var downloaded = 0L
                    var lastLog = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0 && downloaded - lastLog > 2 * 1024 * 1024) {
                            val percent = (downloaded * 100 / total).toInt()
                            log?.invoke("Download $percent% (${downloaded / 1024 / 1024} MB)")
                            lastLog = downloaded
                        }
                    }
                }
            }
            conn.disconnect()
            log?.invoke("Download complete: ${target.length() / 1024 / 1024} MB")
            return
        }
    }

    private fun createEula(serverDir: File) {
        val eulaContent = """
            #By changing the setting below to TRUE you are indicating your agreement to our EULA (https://account.mojang.com/documents/minecraft_eula).
            eula=true
        """.trimIndent()
        FileUtils.writeFile(File(serverDir, "eula.txt").absolutePath, eulaContent)
    }

    private fun createServerProperties(serverDir: File, port: Int) {
        val properties = """
            server-port=$port
            max-players=20
            gamemode=survival
            difficulty=normal
            pvp=true
            online-mode=true
            motd=A Minecraft Server powered by PocketHost
            view-distance=10
            spawn-protection=16
            enable-command-block=false
            allow-flight=false
            white-list=false
            enforce-whitelist=false
            spawn-monsters=true
            spawn-animals=true
            generate-structures=true
        """.trimIndent()
        FileUtils.writeFile(File(serverDir, "server.properties").absolutePath, properties)
    }

    private fun createStartScript(serverDir: File, jarFile: String, maxMem: Int, minMem: Int) {
        // Windows batch
        val batContent = """
            @echo off
            java -Xmx${maxMem}M -Xms${minMem}M -XX:+UseG1GC -jar $jarFile nogui
            pause
        """.trimIndent()
        FileUtils.writeFile(File(serverDir, "start.bat").absolutePath, batContent)
        // Also shell for compatibility
        val shContent = """
            #!/bin/bash
            java -Xmx${maxMem}M -Xms${minMem}M -XX:+UseG1GC -jar $jarFile nogui
        """.trimIndent()
        val shFile = File(serverDir, "start.sh")
        FileUtils.writeFile(shFile.absolutePath, shContent)
        shFile.setExecutable(true)
    }

    suspend fun fetchAvailableVersions(loader: MinecraftLoader, onProgress: (String) -> Unit = {}): List<String> = withContext(Dispatchers.IO) {
        try {
            when (loader) {
                MinecraftLoader.VANILLA -> {
                    val json = httpGet("https://piston-meta.mojang.com/mc/game/version_manifest.json") ?: return@withContext emptyList()
                    val obj = JSONObject(json)
                    val arr = obj.getJSONArray("versions")
                    val versions = mutableListOf<String>()
                    for (i in 0 until minOf(arr.length(), 50)) {
                        val v = arr.getJSONObject(i)
                        if (v.getString("type") == "release") versions.add(v.getString("id"))
                    }
                    versions
                }
                MinecraftLoader.PAPER -> {
                    // Use fill.papermc.io v3
                    val json = httpGet("https://fill.papermc.io/v3/projects/paper") ?: return@withContext listOf("1.21.4", "1.21.1", "1.20.4", "1.19.4")
                    try {
                        val obj = JSONObject(json)
                        val versionsObj = obj.getJSONObject("versions")
                        val allVersions = mutableListOf<String>()
                        for (key in versionsObj.keys()) {
                            val arr = versionsObj.getJSONArray(key)
                            for (i in 0 until arr.length()) allVersions.add(arr.getString(i))
                        }
                        // Distinct and sort descending (simple)
                        allVersions.distinct().sortedWith(compareByDescending { it }).take(30).ifEmpty { listOf("1.21.4", "1.21.1") }
                    } catch (e: Exception) {
                        // Fallback: try old format
                        try {
                            val obj = JSONObject(json)
                            val arr = obj.getJSONArray("versions")
                            (0 until arr.length()).map { arr.getString(it) }.reversed()
                        } catch (_: Exception) { listOf("1.21.4", "1.21.1", "1.20.4") }
                    }
                }
                MinecraftLoader.PURPUR -> {
                    val json = httpGet("https://api.purpurmc.org/v2/purpur") ?: return@withContext listOf("1.21.4", "1.21.1")
                    val obj = JSONObject(json)
                    val arr = obj.getJSONArray("versions")
                    (0 until arr.length()).map { arr.getString(it) }.reversed()
                }
                MinecraftLoader.FABRIC -> {
                    // Fabric uses same versions as vanilla
                    val json = httpGet("https://meta.fabricmc.net/v2/versions/game") ?: return@withContext listOf("1.21.4", "1.21.1")
                    val arr = JSONArray(json)
                    (0 until minOf(arr.length(), 20)).map { arr.getJSONObject(it).getString("version") }
                }
                else -> listOf("1.21.4", "1.21.1", "1.20.4", "1.19.4", "1.18.2", "1.16.5")
            }
        } catch (e: Exception) {
            onProgress("Failed to fetch versions: ${e.message}")
            listOf("1.21.4", "1.21.1", "1.20.4", "1.19.4")
        }
    }
}
