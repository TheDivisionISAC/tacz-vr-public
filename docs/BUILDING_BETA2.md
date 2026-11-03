# 构建 TACZ VR 0.1.0-beta.2

源码ZIP来自对应实际工作树，包含附属源码、资源、测试及构建规则；不含上游模组JAR、游戏资源、Git历史或内部工程材料。gradle.properties已写入对应平台的0.1.0-beta.2版本号。

## Forge 1.20.1

JDK17；首次联网构建按build.gradle固定坐标获取TACZ、Vivecraft及其他依赖。

```powershell
.\gradlew.bat test check reobfJar scopeGlSmokeTest --no-daemon --console=plain
```

产物：build/libs/tacz-vr-0.1.0-beta.2+mc1.20.1.jar。

## NeoForge 1.21.1

JDK21；先将以下外部依赖放入源码根目录compat-artifacts/。附属源码ZIP未内嵌这些文件；发布Neo构建给其他人时，需要同时明确其依赖和对应源码的获取方式，不能假定普通Forge包通用。

| 文件 | SHA-256 |
| --- | --- |
| tacz-neoforge-1.21.1-1.1.8-hotfix-r4.jar | 529D384FB82431DF50CA78318FAAA8EEB161522D818DAC488E8499C897FD2F14 |
| vivecraft-1.21.1-1.3.15-neoforge.jar | 0712A6167421BF337477AB1766293887441514F5186EC94B13E081C0779A6474 |

```powershell
.\gradlew.bat test check scopeGlSmokeTest --no-daemon --console=plain
```

产物：build/libs/tacz-vr-neoforge-1.21.1-0.1.0-beta.2+mc1.21.1.jar。include_iris_runtime默认为false；可选渲染依赖文件名见gradle.properties。

有完整依赖缓存时可附加--offline。scopeGlSmokeTest需要可用的OpenGL环境；它不启动Minecraft或SteamVR，也不能代替头显和实际游戏注入验证。
