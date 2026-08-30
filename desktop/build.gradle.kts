plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":common"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.6.10")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.material3)
    implementation(compose.components.resources)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "com.pockethost.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "PocketHost"
            packageVersion = "1.0.0"
            description = "Server management platform for PC - SERVER ANYWHERE"
            vendor = "PocketHost"
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "PocketHost"
                upgradeUuid = "9a4e2e1c-7f3d-4b8a-9c1e-5f6a7b8c9d0e"
            }
        }
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}
