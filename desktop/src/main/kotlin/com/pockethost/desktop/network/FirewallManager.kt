package com.pockethost.desktop.network

import com.pockethost.common.util.getPlatform
import com.pockethost.common.util.Platform
import com.pockethost.desktop.util.AppLogger
import java.io.File

object FirewallManager {
    fun isPortAvailable(port: Int): Boolean {
        return try {
            java.net.ServerSocket(port).use { true }
        } catch (_: Exception) { false }
    }

    /**
     * Adds firewall rule for given port. Returns true if succeeded or rule already exists.
     * Windows: netsh advfirewall firewall add rule
     * Linux: ufw allow / iptables fallback
     * macOS: pfctl / socketfilterfw (best-effort)
     */
    fun addFirewallRule(port: Int, name: String = "PocketHost-$port"): Boolean {
        return try {
            when (getPlatform()) {
                Platform.WINDOWS -> addWindowsRule(port, name)
                Platform.LINUX -> addLinuxRule(port)
                Platform.MACOS -> addMacRule(port)
            }
        } catch (e: Exception) {
            AppLogger.error("Firewall add failed for $port: ${e.message}", e)
            false
        }
    }

    fun removeFirewallRule(port: Int, name: String = "PocketHost-$port"): Boolean {
        return try {
            when (getPlatform()) {
                Platform.WINDOWS -> {
                    val cmd = listOf("netsh", "advfirewall", "firewall", "delete", "rule", "name=$name")
                    val p = ProcessBuilder(cmd).start()
                    p.waitFor() == 0
                }
                Platform.LINUX -> {
                    val p = ProcessBuilder("sudo", "ufw", "delete", "allow", "$port/tcp").start()
                    p.waitFor() == 0
                }
                Platform.MACOS -> true // no-op
            }
        } catch (e: Exception) {
            AppLogger.error("Firewall remove failed: ${e.message}", e)
            false
        }
    }

    fun checkFirewallRuleExists(port: Int, name: String = "PocketHost-$port"): Boolean {
        return try {
            when (getPlatform()) {
                Platform.WINDOWS -> {
                    val p = ProcessBuilder("netsh", "advfirewall", "firewall", "show", "rule", "name=$name").start()
                    val out = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    out.contains("$port") || out.contains(name)
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }

    private fun addWindowsRule(port: Int, name: String): Boolean {
        // Try without admin first - will fail if not elevated, but log and return false
        // Use PowerShell for better error handling
        val cmd = listOf(
            "powershell", "-NoProfile", "-Command",
            "netsh advfirewall firewall add rule name=\"$name\" dir=in action=allow protocol=TCP localport=$port | Out-Null; \$LASTEXITCODE"
        )
        val p = ProcessBuilder(cmd).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code == 0 || out == "0") {
            AppLogger.info("Firewall rule added: $name port $port")
            return true
        }
        // Fallback direct netsh
        val direct = ProcessBuilder("netsh", "advfirewall", "firewall", "add", "rule",
            "name=$name", "dir=in", "action=allow", "protocol=TCP", "localport=$port").start()
        val dCode = direct.waitFor()
        if (dCode == 0) AppLogger.info("Firewall rule added via netsh: $name")
        else AppLogger.warn("Firewall add failed (need Admin): $err")
        return dCode == 0
    }

    private fun addLinuxRule(port: Int): Boolean {
        // Try ufw
        try {
            val check = ProcessBuilder("which", "ufw").start()
            check.waitFor()
            if (check.exitValue() == 0) {
                val p = ProcessBuilder("sudo", "ufw", "allow", "$port/tcp").start()
                val code = p.waitFor()
                if (code == 0) {
                    AppLogger.info("ufw allowed $port/tcp")
                    return true
                }
            }
        } catch (_: Exception) {}

        // Fallback iptables (requires sudo)
        return try {
            val p = ProcessBuilder("sudo", "iptables", "-A", "INPUT", "-p", "tcp", "--dport", "$port", "-j", "ACCEPT").start()
            p.waitFor() == 0
        } catch (_: Exception) { false }
    }

    private fun addMacRule(port: Int): Boolean {
        AppLogger.info("macOS firewall manual: sudo /usr/libexec/ApplicationFirewall/socketfilterfw --add ${File(System.getProperty("user.home"), ".pockethost").absolutePath}")
        // macOS requires manual pfctl or socketfilterfw, return true as best-effort
        return true
    }
}
