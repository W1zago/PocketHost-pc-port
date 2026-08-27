package com.pockethost.common.util

enum class Platform {
    WINDOWS, LINUX, MACOS
}

fun getPlatform(): Platform {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> Platform.WINDOWS
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> Platform.LINUX
        os.contains("mac") -> Platform.MACOS
        else -> Platform.WINDOWS
    }
}

fun isWindows(): Boolean = getPlatform() == Platform.WINDOWS
