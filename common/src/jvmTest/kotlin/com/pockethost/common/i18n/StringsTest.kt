package com.pockethost.common.i18n

import kotlin.test.*

class StringsTest {
    @Test
    fun testEnglishDefault() {
        Strings.setLanguage(Language.EN)
        assertEquals("Servers", Strings.tr("nav.servers"))
        assertEquals("PocketHost", Strings.tr("app.title"))
    }

    @Test
    fun testUkrainianTranslation() {
        Strings.setLanguage(Language.UK)
        assertEquals("Сервери", Strings.tr("nav.servers"))
        assertEquals("Створити новий сервер", Strings.tr("server.create.title"))
        // reset
        Strings.setLanguage(Language.EN)
    }

    @Test
    fun testFallbackToKey() {
        Strings.setLanguage(Language.EN)
        assertEquals("unknown.key", Strings.tr("unknown.key"))
    }

    @Test
    fun testLanguageSwitchFlow() {
        Strings.setLanguage(Language.EN)
        assertEquals(Language.EN, Strings.language.value)
        Strings.setLanguage(Language.UK)
        assertEquals(Language.UK, Strings.language.value)
        Strings.setLanguage(Language.EN)
    }
}
