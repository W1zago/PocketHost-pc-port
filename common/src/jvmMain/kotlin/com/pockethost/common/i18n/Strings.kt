package com.pockethost.common.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Language { EN, UK }

object Strings {
    private val _language = MutableStateFlow(Language.EN)
    val language: StateFlow<Language> = _language

    fun setLanguage(lang: Language) {
        _language.value = lang
    }

    fun get(key: String, lang: Language = _language.value): String {
        return translations[lang]?.get(key) ?: translations[Language.EN]?.get(key) ?: key
    }

    // Helper for composable
    fun tr(key: String): String = get(key)

    private val translations: Map<Language, Map<String, String>> = mapOf(
        Language.EN to mapOf(
            "app.title" to "PocketHost",
            "app.subtitle" to "Minecraft Server Manager",
            "nav.servers" to "Servers",
            "nav.newServer" to "New Server",
            "nav.settings" to "Settings",
            "servers.title" to "Servers",
            "servers.total" to "total",
            "servers.noServers" to "No servers yet",
            "servers.createFirst" to "Create your first Minecraft server",
            "servers.select" to "Select a server",
            "servers.choose" to "Choose a server from the list to view details",
            "server.create.title" to "Create New Server",
            "server.create.step" to "Step",
            "server.create.of" to "of",
            "server.name" to "Server Name",
            "server.software" to "Choose server software:",
            "server.version" to "Minecraft Version",
            "server.port" to "Port",
            "server.memory.min" to "Min Memory (MB)",
            "server.memory.max" to "Max Memory (MB)",
            "server.create" to "Create Server",
            "server.start" to "Start",
            "server.stop" to "Stop",
            "server.restart" to "Restart",
            "server.openFolder" to "Open Server Folder",
            "server.delete" to "Delete Server",
            "server.console.placeholder" to "Enter command...",
            "server.console.send" to "Send",
            "server.console.requiresRunning" to "Server must be running to send commands",
            "settings.title" to "Settings",
            "settings.general" to "General",
            "settings.language" to "Language",
            "common.cancel" to "Cancel",
            "common.next" to "Next",
            "common.back" to "Back",
            "common.save" to "Save",
            "common.edit" to "Edit",
            "common.delete" to "Delete",
            "error.portInUse" to "Port already in use",
            "error.creationFailed" to "Failed to create server. Check logs."
        ),
        Language.UK to mapOf(
            "app.title" to "PocketHost",
            "app.subtitle" to "Менеджер Minecraft серверів",
            "nav.servers" to "Сервери",
            "nav.newServer" to "Новий сервер",
            "nav.settings" to "Налаштування",
            "servers.title" to "Сервери",
            "servers.total" to "всього",
            "servers.noServers" to "Ще немає серверів",
            "servers.createFirst" to "Створи свій перший Minecraft сервер",
            "servers.select" to "Вибери сервер",
            "servers.choose" to "Вибери сервер зі списку для перегляду деталей",
            "server.create.title" to "Створити новий сервер",
            "server.create.step" to "Крок",
            "server.create.of" to "з",
            "server.name" to "Назва сервера",
            "server.software" to "Вибери програмне забезпечення:",
            "server.version" to "Версія Minecraft",
            "server.port" to "Порт",
            "server.memory.min" to "Мін. пам'ять (MB)",
            "server.memory.max" to "Макс. пам'ять (MB)",
            "server.create" to "Створити сервер",
            "server.start" to "Запустити",
            "server.stop" to "Зупинити",
            "server.restart" to "Перезапустити",
            "server.openFolder" to "Відкрити теку сервера",
            "server.delete" to "Видалити сервер",
            "server.console.placeholder" to "Введи команду...",
            "server.console.send" to "Надіслати",
            "server.console.requiresRunning" to "Сервер має працювати для відправки команд",
            "settings.title" to "Налаштування",
            "settings.general" to "Загальне",
            "settings.language" to "Мова",
            "common.cancel" to "Скасувати",
            "common.next" to "Далі",
            "common.back" to "Назад",
            "common.save" to "Зберегти",
            "common.edit" to "Редагувати",
            "common.delete" to "Видалити",
            "error.portInUse" to "Порт вже зайнятий",
            "error.creationFailed" to "Не вдалося створити сервер. Перевір логи."
        )
    )
}
