plugins {
    kotlin("multiplatform") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("org.jetbrains.compose") version "1.6.10" apply false
    id("app.cash.sqldelight") version "2.0.1" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
