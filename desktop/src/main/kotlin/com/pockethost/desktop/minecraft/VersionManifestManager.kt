package com.pockethost.desktop.minecraft

import com.pockethost.common.util.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class VersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String? = null
)

object VersionManifestManager {
    private const val V2_PRIMARY = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    private const val V2_FALLBACK = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val V1_PRIMARY = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    private const val V1_FALLBACK = "https://piston-meta.mojang.com/mc/game/version_manifest.json"

    private val cacheFile: File get() = File(AppPaths.configDir, "version_manifest_cache.json")

    sealed class ManifestFilter(val label: String, val typeValue: String?) {
        object All : ManifestFilter("Всі", null)
        object Release : ManifestFilter("Release", "release")
        object Snapshot : ManifestFilter("Snapshot", "snapshot")
        object Alpha : ManifestFilter("Alpha", "old_alpha")
        object Beta : ManifestFilter("Beta", "old_beta")
    }

    val allFilters = listOf(ManifestFilter.All, ManifestFilter.Release, ManifestFilter.Snapshot, ManifestFilter.Beta, ManifestFilter.Alpha)

    suspend fun fetchAllVersions(forceRefresh: Boolean = false, onProgress: (String) -> Unit = {}): List<VersionEntry> = withContext(Dispatchers.IO) {
        try {
            var json: String? = null
            if (!forceRefresh && cacheFile.exists()) {
                try {
                    val cached = cacheFile.readText()
                    if (cached.isNotBlank()) {
                        JSONObject(cached)
                        json = cached
                        onProgress("Завантажено з кешу (${cached.length / 1024} KB)")
                    }
                } catch (_: Exception) {}
            }
            if (json == null) {
                json = fetchManifestWithFallback(onProgress)
                if (json != null) {
                    try {
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(json)
                        onProgress("Кеш збережено")
                    } catch (_: Exception) {}
                }
            }
            if (json == null) {
                onProgress("Не вдалося завантажити маніфест")
                return@withContext emptyList()
            }
            parseVersions(json)
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Помилка маніфесту: ${e.message}")
            emptyList()
        }
    }

    fun getFiltered(versions: List<VersionEntry>, filter: ManifestFilter): List<VersionEntry> {
        return when (filter) {
            is ManifestFilter.All -> versions
            else -> versions.filter { it.type == filter.typeValue }
        }
    }

    fun getFilteredWithSearch(versions: List<VersionEntry>, filter: ManifestFilter, query: String): List<VersionEntry> {
        val byType = getFiltered(versions, filter)
        if (query.isBlank()) return byType
        val q = query.trim().lowercase()
        return byType.filter { it.id.lowercase().contains(q) || it.type.lowercase().contains(q) }
    }

    fun countByType(versions: List<VersionEntry>): Map<String, Int> {
        return versions.groupingBy { it.type }.eachCount()
    }

    fun clearCache() {
        try { if (cacheFile.exists()) cacheFile.delete() } catch (_: Exception) {}
    }

    private suspend fun fetchManifestWithFallback(onProgress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val urls = listOf(V2_PRIMARY, V2_FALLBACK, V1_PRIMARY, V1_FALLBACK)
        for (u in urls) {
            try {
                onProgress("Завантаження маніфесту: $u")
                val res = httpGet(u)
                if (res != null && res.contains("\"versions\"")) {
                    onProgress("Успішно з $u")
                    return@withContext res
                } else {
                    onProgress("Порожня відповідь від $u")
                }
            } catch (e: Exception) {
                onProgress("Помилка $u: ${e.message}")
            }
        }
        null
    }

    private fun parseVersions(json: String): List<VersionEntry> {
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("versions")
        val list = mutableListOf<VersionEntry>()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val id = v.optString("id", "")
            val type = v.optString("type", "release")
            val url = v.optString("url", "")
            val time = v.optString("releaseTime", null)
            if (id.isNotBlank()) {
                list.add(VersionEntry(id, type, url, time))
            }
        }
        // Sort by releaseTime descending (newest first)
        return list.sortedByDescending { it.releaseTime ?: "" }
    }

    private fun httpGet(urlStr: String): String? {
        var current = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(current)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "PocketHost/1.0.0")
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
}
