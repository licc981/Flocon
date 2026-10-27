# flocon-lite

面向旧版 Android 工具链的轻量级 [Flocon](https://github.com/openflocon/Flocon) 调试客户端，通过本机 WebSocket 与 Flocon Desktop 通信，不依赖主工程的 FloconAndroid 模块。

- 包名 / namespace：`com.cc.floconlite`
- 最低系统版本：Android 5.0（API 21）
- compileSdk：33；targetSdk：34
- 工具链：AGP 7.2.2、Gradle 7.3.3、Kotlin 1.7.0
- Java / Kotlin 字节码目标：11

## 功能

当前源码包含设备注册、OkHttp 网络记录、SQLite 数据库查询、应用文件浏览与删除、SharedPreferences 查看与修改，以及 Analytics、CrashReporter、Deeplink、FeatureFlags、Table、Dashboard 等功能。

这是 Flocon 协议的精简实现，不保证与所有桌面端版本或完整 SDK 功能兼容；设备图标上报尚未实现。

## 接入

### 以源码模块依赖

在当前工程的应用模块中添加：

```kotlin
dependencies {
    debugImplementation(project(":flocon-lite"))
}
```

使用 `debugImplementation` 时，相关初始化和导入应放在 `src/debug` 源集中，避免 release 编译引用不存在的类。

### 以 AAR 依赖

将 `flocon-lite-release.aar` 复制到应用的 `libs/` 目录，然后添加：

```kotlin
dependencies {
    debugImplementation(files("libs/flocon-lite-release.aar"))

    // 本地 AAR 不包含 Maven 传递依赖信息，也不会内嵌以下依赖。
    debugImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")
    debugImplementation("com.squareup.okhttp3:okhttp:4.10.0")
    debugImplementation("com.google.code.gson:gson:2.9.1")
    debugImplementation("androidx.appcompat:appcompat:1.4.2")
    debugImplementation("androidx.core:core-ktx:1.8.0")
}
```

应用已有相关依赖时，请统一版本，避免重复或版本冲突。

## 初始化

在调试版 `Application.onCreate()` 中调用：

```kotlin
import com.cc.floconlite.FloconLite

override fun onCreate() {
    super.onCreate()
    FloconLite.install(this, enabled = BuildConfig.DEBUG)
}
```

确保该 Application 已在应用清单中注册。库清单声明了 `INTERNET` 权限。由于客户端连接 `ws://127.0.0.1:9023/`，如果应用禁止明文流量，需要在**调试版**清单或网络安全配置中允许此连接，例如在 `src/debug/AndroidManifest.xml` 中配置：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="true" />
</manifest>
```

若应用已经设置 `android:networkSecurityConfig`，请在对应的调试版网络安全配置中允许本机明文连接。

打开 Flocon Desktop，并通过 ADB 连接设备：

```shell
adb reverse tcp:9023 tcp:9023
```

客户端会自动连接，并在断开后尝试重连。

## OkHttp 网络记录

```kotlin
import com.cc.floconlite.FloconInterceptor
import okhttp3.OkHttpClient

val client = OkHttpClient.Builder()
    .addInterceptor(FloconInterceptor())
    .build()
```

仅给需要调试的业务 OkHttpClient 添加拦截器。网络记录可能包含请求头、请求体和响应体，请勿用于生产环境或记录敏感信息；大响应体的读取也可能增加内存开销。

## 其他接口示例

```kotlin
import com.cc.floconlite.FloconAnalytics
import com.cc.floconlite.FloconCrashReporter
import com.cc.floconlite.FloconSharedPrefs
import com.cc.floconlite.FloconTables

FloconSharedPrefs.registerPreference("app_settings")
FloconAnalytics.logEvent("events", "screen_open", mapOf("screen" to "home"))
FloconTables.addRow("debug_log", mapOf("message" to "hello"))
FloconCrashReporter.install(this)
```

以上调用应位于调试代码中。数据库、文件和 SharedPreferences 功能可读取或修改应用数据，仅在受控的本地开发环境启用，不要将该库集成到正式发布包。

## 构建 AAR

在 `FloconLowAndroid` 目录执行：

```powershell
.\gradlew.bat :flocon-lite:assembleRelease
```

输出文件：

```text
flocon-lite/build/outputs/aar/flocon-lite-release.aar
```

这里的 `release` 仅指库的构建变体，不代表建议在生产应用中启用调试能力。
