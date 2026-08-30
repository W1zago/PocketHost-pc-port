package com.pockethost.desktop.java

import kotlin.test.*

class JavaManagerTest {
    @Test
    fun testRequiredJavaForMinecraft() {
        assertEquals(21, JavaManager.requiredJavaForMinecraft("1.21.4"))
        assertEquals(21, JavaManager.requiredJavaForMinecraft("1.20.5"))
        assertEquals(17, JavaManager.requiredJavaForMinecraft("1.20.4"))
        assertEquals(17, JavaManager.requiredJavaForMinecraft("1.18.2"))
        assertEquals(16, JavaManager.requiredJavaForMinecraft("1.17.1"))
        assertEquals(8, JavaManager.requiredJavaForMinecraft("1.16.5"))
        assertEquals(21, JavaManager.requiredJavaForMinecraft(null))
    }

    @Test
    fun testFindJavaNotNullOnSystem() {
        val runtime = JavaManager.findJava()
        assertNotNull(runtime, "Java should be found on this system")
        assertTrue(runtime.version >= 17)
    }

    @Test
    fun testGetJavaVersionParsing() {
        val runtime = JavaManager.findJava() ?: return
        val file = java.io.File(runtime.path)
        val ver = JavaManager.getJavaVersion(file)
        assertNotNull(ver)
        assertTrue(ver.major >= 17)
    }
}
