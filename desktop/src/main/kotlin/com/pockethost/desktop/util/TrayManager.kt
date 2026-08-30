package com.pockethost.desktop.util

import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object TrayManager {
    private var trayIcon: TrayIcon? = null
    private var tray: SystemTray? = null

    fun isSupported(): Boolean = SystemTray.isSupported()

    fun getWindowIcon(): Image? = loadTrayImage()

    fun install(
        onOpen: () -> Unit,
        onStartServers: () -> Unit = {},
        onSettings: () -> Unit = {},
        onExit: () -> Unit = {}
    ): Boolean {
        if (!isSupported()) return false
        if (trayIcon != null) return true
        try {
            tray = SystemTray.getSystemTray()
            val image = loadTrayImage() ?: createFallbackImage()
            val traySize = tray?.trayIconSize ?: Dimension(16, 16)
            val scaled = image.getScaledInstance(traySize.width, traySize.height, Image.SCALE_SMOOTH)

            val popup = PopupMenu()
            val openItem = MenuItem("Відкрити")
            openItem.addActionListener { onOpen() }
            popup.add(openItem)

            val startItem = MenuItem("Запустити сервери")
            startItem.addActionListener { onStartServers(); onOpen() }
            popup.add(startItem)

            val settingsItem = MenuItem("Налаштування")
            settingsItem.addActionListener { onSettings(); onOpen() }
            popup.add(settingsItem)

            popup.addSeparator()

            val exitItem = MenuItem("Закрити")
            exitItem.addActionListener { onExit() }
            popup.add(exitItem)

            trayIcon = TrayIcon(scaled, "PocketHost", popup).apply {
                isImageAutoSize = true
                addActionListener { onOpen() }
            }
            tray?.add(trayIcon)
            trayIcon?.displayMessage("PocketHost", "Згорнуто в трей", TrayIcon.MessageType.INFO)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun uninstall() {
        try {
            trayIcon?.let { tray?.remove(it) }
        } catch (_: Exception) {}
        trayIcon = null
    }

    fun showMessage(title: String, text: String) {
        try { trayIcon?.displayMessage(title, text, TrayIcon.MessageType.INFO) } catch (_: Exception) {}
    }

    private fun loadTrayImage(): Image? {
        val candidates = listOf(
            "icon.png",
            "icon.ico",
            "/icon.png",
            "icons/icon.png",
            "/icons/icon.png"
        )
        for (name in candidates) {
            try {
                val url = TrayManager::class.java.classLoader.getResource(name)
                if (url != null) {
                    return ImageIO.read(url)
                }
            } catch (_: Exception) {}
        }
        try {
            val cwdIcon = File("desktop/src/main/resources/icon.png")
            if (cwdIcon.exists()) return ImageIO.read(cwdIcon)
            val cwdIcon2 = File("src/main/resources/icon.png")
            if (cwdIcon2.exists()) return ImageIO.read(cwdIcon2)
        } catch (_: Exception) {}
        return null
    }

    private fun createFallbackImage(): Image {
        val size = 32
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(12, 20, 33)
        g.fillOval(0, 0, size, size)
        g.color = Color(66, 133, 255)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(6, 14, 20, 14, 6, 6)
        g.color = Color(99, 179, 255)
        g.fillRoundRect(9, 6, 14, 4, 2, 2)
        g.fillRoundRect(9, 11, 14, 4, 2, 2)
        g.dispose()
        return img
    }
}
