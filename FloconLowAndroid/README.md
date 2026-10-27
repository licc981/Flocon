# FloconLowAndroid

面向旧版 Android 工具链的轻量级 Flocon 调试客户端工程，包含：

- `flocon-lite`：Android Library，包名为 `com.cc.floconlite`。
- `app`：示例应用工程，目前尚未初始化调试客户端。

接入方式、功能说明和 AAR 使用方法见 [flocon-lite README](flocon-lite/README.md)。

## 构建 AAR

使用 JDK 11 或 JDK 17，安装 Android SDK 33 后，在本目录执行：

```powershell
.\gradlew.bat :flocon-lite:assembleRelease
```

产物：`flocon-lite/build/outputs/aar/flocon-lite-release.aar`。

当前工具链为 Gradle 7.3.3、Android Gradle Plugin 7.2.2、Kotlin 1.7.0；库的最低 Android 版本为 API 21，Java / Kotlin 字节码目标为 11。
