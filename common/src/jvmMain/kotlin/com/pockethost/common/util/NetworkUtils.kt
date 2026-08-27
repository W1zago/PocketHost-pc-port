package com.pockethost.common.util

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

object NetworkUtils {

    fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    fun isValidPort(port: Int): Boolean = port in 1024..65535

    fun getAvailablePort(startPort: Int = 25565): Int {
        var port = startPort
        while (port < 65535) {
            if (isPortAvailable(port)) return port
            port++
        }
        return -1
    }

    fun isHostReachable(host: String, timeout: Int = 3000): Boolean {
        return try {
            InetAddress.getByName(host).isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }
}
