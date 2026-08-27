# Виявлені проблеми та рекомендації
**Дата аудиту:** 27.08.2026  
**Версія:** 1.0.0 MVP

---

## Пріоритет 1: Потребують виправлення перед релізом

### 1. Deprecated Material3 APIs (12 warnings)

**Файли:**
- `CreateServerScreen.kt:67` - LinearProgressIndicator
- `ServerDetailScreen.kt:54` - Icons.Default.ArrowBack
- `ServerDetailScreen.kt:210, 297, 361` - Divider
- `ServerDetailScreen.kt:227` - Icons.Default.Send
- `ServerListScreen.kt:56` - Divider

**Проблема:**
Використання застарілих API Material3, які будуть видалені в наступних версіях.

**Рішення:**
```kotlin
// Замість
LinearProgressIndicator(progress = 0.5f)
// Використати
LinearProgressIndicator(progress = { 0.5f })

// Замість
Icon(Icons.Default.ArrowBack, "Back")
// Використати
Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")

// Замість
Divider()
// Використати
HorizontalDivider()
```

**Час виправлення:** 30 хвилин  
**Складність:** Низька

---

### 2. Error handling без UI feedback

**Файли:**
- `ServerListScreen.kt:78, 83` - тільки printStackTrace()

**Проблема:**
Помилки запуску/зупинки серверів не відображаються користувачу. Тільки в console.

**Рішення:**
```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

// При помилці
scope.launch {
    snackbarHostState.showSnackbar(
        message = "Failed to start server: ${e.message}",
        duration = SnackbarDuration.Long
    )
}
```

**Час виправлення:** 1 година  
**Складність:** Середня

---

### 3. Невикористані змінні (compiler warnings)

**Файли:**
- `JavaManager.kt:60, 86` - Condition 'javaExec != null' is always 'true'
- `MinecraftServerManager.kt:70` - Variable 'javaRequired' is never used
- `App.kt:24` - Parameter 'onExit' is never used
- `ServerDetailScreen.kt:254` - Variable 'scope' is never used

**Рішення:**
```kotlin
// MinecraftServerManager.kt:70 - видалити або використати
val javaRequired = JavaManager.requiredJavaForMinecraft(version)
// Якщо не потрібно - видалити рядок

// App.kt:24 - або використати для cleanup, або видалити parameter
@Composable
fun App() { // без onExit якщо не використовується

// ServerDetailScreen.kt:254 - видалити якщо не використовується
```

**Час виправлення:** 15 хвилин  
**Складність:** Низька

---

## Пріоритет 2: Рекомендовано виправити

### 4. ProcessStats.threadCount завжди 0

**Файл:** `ProcessManager.kt:159`

**Проблема:**
```kotlin
ProcessStats(cpuPercent = cpu, memoryBytes = mem, threadCount = 0)
```

**Рішення:**
```kotlin
// Додати в PowerShell query
val process = ProcessBuilder(
    "powershell", "-NoProfile", "-Command",
    "Get-Process -Id $pid | Select-Object CPU,WorkingSet64,@{l='ThreadCount';e={`$_.Threads.Count}} | ConvertTo-Json"
).start()

val threadRegex = Regex("\"ThreadCount\"\\s*:\\s*(\\d+)")
val threads = threadRegex.find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
ProcessStats(cpuPercent = cpu, memoryBytes = mem, threadCount = threads)
```

**Час виправлення:** 30 хвилин  
**Складність:** Низька

---

### 5. Немає unit тестів

**Проблема:**
Критична бізнес-логіка не покрита тестами.

**Рекомендовані тести:**
```kotlin
// ServerRepositoryTest.kt
class ServerRepositoryTest {
    @Test
    fun `test add and retrieve server`() { }
    @Test
    fun `test update server status`() { }
    @Test
    fun `test delete server cascades logs`() { }
}

// NetworkUtilsTest.kt
class NetworkUtilsTest {
    @Test
    fun `test port validation`() { }
    @Test
    fun `test available port detection`() { }
}

// JavaManagerTest.kt
class JavaManagerTest {
    @Test
    fun `test Java version parsing`() { }
    @Test
    fun `test required Java for MC version`() { }
}
```

**Час реалізації:** 4-6 годин  
**Складність:** Середня

---

### 6. Hardcoded strings (немає локалізації)

**Проблема:**
Всі UI тексти hardcoded англійською.

**Рішення Phase 2:**
```kotlin
// Використати Compose resources або strings
// common/src/commonMain/composeResources/values/strings.xml
<resources>
    <string name="app_name">PocketHost</string>
    <string name="create_server">Create Server</string>
    <string name="start">Start</string>
    <string name="stop">Stop</string>
</resources>

// common/src/commonMain/composeResources/values-uk/strings.xml (українська)
<resources>
    <string name="app_name">PocketHost</string>
    <string name="create_server">Створити сервер</string>
    <string name="start">Запустити</string>
    <string name="stop">Зупинити</string>
</resources>
```

**Час реалізації:** 2-3 години  
**Складність:** Низька

---

## Пріоритет 3: Enhancement (майбутнє)

### 7. Gradle 9.0 deprecation warnings

**Рекомендація:**
```bash
./gradlew build --warning-mode all
# Проаналізувати всі warnings
# Оновити застарілі Gradle API
```

**Час:** 1 година  
**Складність:** Низька

---

### 8. Відсутність логування у файл

**Поточний стан:**
Logback dependency є, але не налаштовано.

**Рішення:**
```xml
<!-- desktop/src/main/resources/logback.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>${user.home}/.pockethost/logs/pockethost.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <appender name="ROLLING" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${user.home}/.pockethost/logs/pockethost.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${user.home}/.pockethost/logs/pockethost.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="ROLLING" />
    </root>
</configuration>
```

**Час:** 30 хвилин  
**Складність:** Низька

---

### 9. Firewall rules не налаштовуються автоматично

**Проблема:**
При створенні сервера порт може бути заблокований Windows Firewall.

**Рішення Phase 2:**
```kotlin
// WindowsFirewallManager.kt
object WindowsFirewallManager {
    suspend fun addRule(port: Int, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = listOf(
                "netsh", "advfirewall", "firewall", "add", "rule",
                "name=$name",
                "dir=in",
                "action=allow",
                "protocol=TCP",
                "localport=$port"
            )
            val process = ProcessBuilder(command).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun removeRule(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = listOf(
                "netsh", "advfirewall", "firewall", "delete", "rule",
                "name=$name"
            )
            val process = ProcessBuilder(command).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
```

**Увага:** Потребує адміністраторських прав!

**Час:** 2 години  
**Складність:** Середня

---

### 10. Відсутність CI/CD

**Рекомендація:**
```yaml
# .github/workflows/build.yml
name: Build

on: [push, pull_request]

jobs:
  build-windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build
        run: ./gradlew build
      - name: Package MSI
        run: ./gradlew packageMsi
      - uses: actions/upload-artifact@v3
        with:
          name: PocketHost-Windows
          path: desktop/build/compose/binaries/main/msi/*.msi
  
  build-linux:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build
        run: ./gradlew build
      - name: Package DEB
        run: ./gradlew packageDeb
```

**Час:** 2-3 години  
**Складність:** Середня

---

## Безпека

### 11. Input validation для server names

**Поточний стан:**
Базова перевірка є, але можна покращити.

**Рекомендація:**
```kotlin
fun validateServerName(name: String): String? {
    return when {
        name.isBlank() -> "Name cannot be empty"
        name.length < 3 -> "Name must be at least 3 characters"
        name.length > 32 -> "Name must be at most 32 characters"
        !name.matches(Regex("^[a-zA-Z0-9_-]+$")) -> 
            "Name can only contain letters, numbers, hyphens and underscores"
        name.startsWith("-") || name.startsWith("_") ->
            "Name cannot start with hyphen or underscore"
        else -> null // Valid
    }
}
```

**Час:** 30 хвилин  
**Складність:** Низька

---

### 12. Path traversal protection

**Рекомендація:**
```kotlin
// FileUtils.kt
fun validatePath(path: File, baseDir: File): Boolean {
    val canonical = path.canonicalPath
    val base = baseDir.canonicalPath
    return canonical.startsWith(base)
}

// Використання в ServerFilesTab
if (!validatePath(selectedFile, File(server.workingDirectory))) {
    // Show error: "Access denied: path outside server directory"
    return
}
```

**Час:** 1 година  
**Складність:** Низька

---

## Підсумок пріоритетів

### Перед релізом MVP (критичні):
1. ✅ Виправити deprecated Material3 APIs - 30 хв
2. ✅ Додати Snackbar error handling - 1 год
3. ✅ Видалити unused variables - 15 хв

**Загальний час:** ~2 години

### Phase 1.5 (рекомендовано):
4. ProcessStats threadCount - 30 хв
5. Basic unit tests - 4-6 год
6. Input validation - 1 год
7. Path traversal protection - 1 год

**Загальний час:** ~7-9 годин

### Phase 2 (enhancement):
8. Локалізація (українська) - 2-3 год
9. Logback налаштування - 30 хв
10. Firewall manager - 2 год
11. CI/CD setup - 2-3 год
12. Gradle 9.0 migration - 1 год

**Загальний час:** ~8-10 годин

---

## Як застосувати виправлення

```bash
# 1. Створити гілку для виправлень
git checkout -b fix/mvp-issues

# 2. Виправити deprecated APIs
# Відредагувати файли згідно рекомендацій вище

# 3. Додати error handling
# Додати SnackbarHost і обробку помилок

# 4. Прибрати warnings
# Видалити unused variables

# 5. Перевірити компіляцію
./gradlew clean build

# 6. Тестування
./gradlew :desktop:run

# 7. Commit
git add .
git commit -m "fix: resolve deprecated APIs and add error handling"

# 8. Merge до main
git checkout main
git merge fix/mvp-issues
```

---

**Статус:** 📋 Всі проблеми задокументовані  
**Наступний крок:** Виправлення критичних проблем перед релізом  
**Оцінка готовності:** 92% (після виправлення Пріоритет 1 → 98%)
