# PocketHost Desktop — Windows MVP (Kotlin Multiplatform + Compose Desktop)

Port of **ANServer / PocketHost** (Android) to **Windows 10/11** desktop for managing Minecraft servers locally.

- **Stack:** Kotlin 1.9.22 · Compose Desktop 1.6.10 · SQLDelight 2.0.1 · Adoptium JDK 17 · ProcessBuilder + JNA
- **Source:** `E:\Projects\baza-for-port` (Android, Kotlin + C++ NDK + PRoot) → new clean KMP project `E:\Projects\PocketHost-pc-port`
- **Prompt:** `E:\Promts\PocketHost(PS port)\PocketHost_to_PC_conversion.md` (recommended: KMP + Compose Desktop)

## What's implemented (MVP Phase 1 — Windows only)

- **Project structure:** `common` (models, SQLDelight, Repository) + `desktop` (ProcessManager, JavaManager, MinecraftServerManager, Compose UI)
- **DB:** SQLDelight `~/.pockethost/config/pockethost.db` (`servers`, `server_logs`, `settings`) — persistence via `ServerRepository:common/src/jvmMain/kotlin/com/pockethost/common/repository/ServerRepository.kt`
- **Server types:** `MINECRAFT` only (8 loaders: VANILLA, PAPER, FABRIC, FORGE, NEOFORGE, SPIGOT, BUKKIT, PURPUR). Auto-download for **VANILLA/PAPER/FABRIC/PURPUR**, manual for FORGE/NEOFORGE/SPIGOT/BUKKIT with warning.
- **Java:** `JAVA_HOME`/`PATH`/`~/.pockethost/java` detection → Adoptium auto-download per MC version (`JavaManager:desktop/src/main/kotlin/com/pockethost/desktop/java/JavaManager.kt:86`) — Temurin 17/21 mappings `requiredJavaForMinecraft`.
- **Processes:** `ProcessBuilder` + coroutines reader, stdin/out, graceful `stop` → `SIGKILL` after 10s `ProcessManager:desktop/src/main/kotlin/com/pockethost/desktop/process/ProcessManager.kt:18`. Stats via PowerShell `WorkingSet64` (Phase 2: polling graphs).
- **Files:** `~/.pockethost/servers/<name>/` (`AppPaths:common/src/jvmMain/kotlin/com/pockethost/common/util/AppPaths.kt`), `NetworkUtils:common/.../NetworkUtils.kt:10` for `isPortAvailable`/`getAvailablePort`.
- **UI (Compose Desktop):** `Sidebar:desktop/.../ui/components/Sidebar.kt:20` + `ServerListScreen:desktop/.../screens/ServerListScreen.kt:26` + `ServerDetailScreen:desktop/.../screens/ServerDetailScreen.kt:27` (Overview/Console/Files/Settings tabs) + `CreateServerScreen:desktop/.../screens/CreateServerScreen.kt:18` (3-step wizard: name/type → config → create) + `SettingsScreen:desktop/.../screens/SettingsScreen.kt:10`.
- **Phase 2 deferred:** UBUNTU_SERVER/Docker/WSL2, bundled JRE via `jlink`, system tray, autostart, themes (dark only MVP), backup, monitoring graphs, installers (MSI/DEB/DMG via jpackage + CI), Ukrainian locale.

## Project layout

```
PocketHost-pc-port/
├── build.gradle.kts / settings.gradle.kts (FOOJAY, KMP + Compose)
├── common/
│   ├── src/commonMain/kotlin/com/pockethost/common/Dummy.kt
│   ├── src/commonMain/sqldelight/com/pockethost/database/servers.sq
│   └── src/jvmMain/kotlin/com/pockethost/common/
│       ├── model/Server.kt (Server, ServerConfig, MinecraftLoader:common/.../model/Server.kt:12)
│       ├── util/{FileUtils,NetworkUtils,AppPaths,Platform}.kt
│       └── database/DatabaseFactory.kt + repository/ServerRepository.kt
├── desktop/
│   ├── src/main/kotlin/com/pockethost/desktop/
│   │   ├── Main.kt (1280x800 Window:desktop/.../Main.kt:7)
│   │   ├── process/ProcessManager.kt
│   │   ├── java/JavaManager.kt
│   │   ├── minecraft/MinecraftServerManager.kt (8 loaders:desktop/.../minecraft/MinecraftServerManager.kt:20)
│   │   └── ui/{App.kt,components/Sidebar.kt,screens/*}
│   └── build.gradle.kts (compose.desktop.currentOs + material-icons-extended)
└── gradle/wrapper (8.13)
```

## Requirements

- **JDK 17 Temurin** (`java -version` → 17.0.18)
- **Windows 10/11 64-bit**, 4 GB RAM, 2 GB disk + servers
- Gradle 8.13 (`./gradlew` via wrapper), no system `gradle` needed

## Build & Run (MVP portable)

```powershell
# 1. Build
.\gradlew :common:build :desktop:build

# 2. Run directly (Compose)
.\gradlew :desktop:run

# 3. Uber-jar (portable .zip distribution per prompt)
.\gradlew :desktop:packageUberJarForCurrentOS
# → desktop/build/compose/jars/PocketHost-windows-x64-1.0.0.jar
java -jar desktop\build\compose\jars\PocketHost-windows-x64-1.0.0.jar

# 4. App image with bundled JRE (createDistributable)
.\gradlew :desktop:createDistributable
# → desktop/build/compose/binaries/main/app/PocketHost/PocketHost.exe
#   (runtime at .../runtime, portable folder)

# 5. MSI installer (Phase 2, requires WiX 3.11 which is auto-downloaded)
.\gradlew :desktop:packageMsi
```

## How it works

1. **Create Server:** `CreateServerScreen` fetches versions via Mojang/PaperMC/Purpur/Fabric meta → `MinecraftServerManager.createServer:desktop/.../MinecraftServerManager.kt:15` downloads JAR (`httpGet`/`downloadFile` with redirects), creates `eula.txt`, `server.properties`, `start.bat`/`start.sh`.
2. **Start:** `ServerListScreen.startServer:desktop/.../screens/ServerListScreen.kt:238` → `JavaManager.ensureJava` → `provisionIfNeeded` → `ProcessBuilder(listOf(java.path, "-Xmx...","-jar", jar, "nogui"))` → `ProcessManager.startProcess` with `onOutput` → `ServerRepository.appendLog` (Flow `getServerLogs`).
3. **Console:** `ServerConsoleTab:desktop/.../screens/ServerDetailScreen.kt:175` shows `server_logs` flow, input via `ProcessManager.writeInput(serverId,"cmd\n")`.
4. **Files:** `ServerFilesTab:desktop/.../screens/ServerDetailScreen.kt:244` browse `File(server.workingDirectory)`, edit (1024 KB limit, `readText`/`writeText`), open in Explorer via `ProcessBuilder("explorer", path)`.
5. **DB:** `AppPaths.appDir` → `~/.pockethost/`, `DatabaseFactory.createDriver:common/.../database/DatabaseFactory.kt:12` with `JdbcSqliteDriver("jdbc:sqlite:.../pockethost.db")`.

## Ports & Security

- No Android sandbox — all ports available (`NetworkUtils.isPortAvailable:common/.../NetworkUtils.kt:10` via `ServerSocket`, `getAvailablePort` fallback).
- Firewall Phase 2 (`netsh advfirewall` per prompt).

## Troubleshooting

- **Java not found:** `Settings` tab runs `JavaManager.ensureJava(17)` → Adoptium `https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse`.
- **Port in use:** Wizard validates `NetworkUtils.isValidPort`/`isPortAvailable`, suggests `getAvailablePort`.
- **Server won't start:** check Console logs, `workingDirectory`, JAR existence (`paper.jar`/`server.jar` etc.), `eula=true`.
- **Native C++ removed:** all JNI/PRoot replaced by `ProcessBuilder`; `native/` module dropped (per prompt Phase).
- **DB errors:** delete `~/.pockethost/config/pockethost.db` and restart (schema recreated via `Database.Schema.create`).

## Roadmap (from `PocketHost_deployment_final.md`)

- **Phase 2:** tray (`SystemTrayManager`), auto-start (Registry `HKCU/.../Run`), light/dark theme, backup zip, graphs (CPU/RAM polling 5 s), installers, Bundled JRE (`jlink`), Ubuntu container (Docker/WSL2), Ukrainian.
- **CI:** GitHub Actions (windows `packageMsi`, ubuntu `packageDeb/Rpm`, macOS `packageDmg`) + release.

## License

Provided as-is for educational purposes.

## References

- Prompt: `E:\Promts\PocketHost(PS port)\PocketHost_to_PC_conversion.md:1`
- Specs: `PocketHost_technical_specs.md:1` (ProcessManager/JavaManager/MinecraftManager)
- UI: `PocketHost_UI_components.md:1`, DB: `PocketHost_database_repository_final.md:1`
