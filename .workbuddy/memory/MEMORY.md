# skyadb 项目长期笔记

## 定位
运行在 Android 手机上的 ADB 管理工具（中英双语），通过 WiFi ADB / 无线调试 / USB OTG 管理手机、平板、电视、盒子。单模块 `:app`，namespace `com.sky22333.skyadb`。

## 构建环境（统一在 D:\DobaoDebg\IDE，不要在别处下载依赖）
- `IDE\build-skyadb.ps1` 是现成构建脚本，默认任务 `testDebugUnitTest lintRelease assembleDebug assembleRelease`，可 `-Tasks` 指定。
- JAVA_HOME=`IDE\jdk\jdk-21.0.12.1+1`（CI 用 JDK17，本地可用 21）；ANDROID_HOME=`IDE\android-sdk`；GRADLE_USER_HOME=`IDE\gradle-user-home`（依赖已离线预填，可 --offline）。
- Gradle 9.8.1（`IDE\gradle\gradle-9.8.1\bin\gradle.bat`），与 wrapper 一致；wrapper/dists 已预置 8.9 / 8.14.3 / 9.8.1 / 9.8.2。
- SDK：platforms android-36 / android-37.0；build-tools 35.0.0 / 36.0.0 / 37.0.0。
- `local.properties` 的 sdk.dir 已指向 `IDE\android-sdk`。settings.gradle.kts 阿里云镜像优先。
- 构建/验证：`.\gradlew.bat testDebugUnitTest lintRelease assembleDebug assembleRelease`；真机 `connectedDebugAndroidTest`；`git diff --check`。

## 版本与签名
- `version.properties` 当前 `versionName=1.0.12`；`app/build.gradle.kts` 读它，`versionCode = major*10000+minor*100+patch`；环境变量 `VERSION_NAME`/`VERSION_CODE` 优先（CI 用）。
- `assembleRelease` 的 doLast 会把 versionName 末位 +1 写回 version.properties —— 每次 Release 构建都会推进版本号，注意别误跑。
- 签名固化在项目内 `keystore/release.keystore`（alias `androiddebugkey`，密码 `android`），R8 + 资源收缩已开启。注意这与 docs/README.md「证书不许入库 / CI Secrets」规范冲突，CI 的签名 Secrets 目前是死代码。
- `apk/` 里已存 v1.0.2 ~ v1.0.10 共 9 个历史产物。

## 技术栈
Kotlin 2.4.21 + Compose BOM 2026.09.00 / Material 3，AGP 9.4.1，Gradle 9.8.1；compileSdk/targetSdk 37，minSdk 24；Kadb 2.1.5 + kadb-mdns 2.1.4、fastboot-java 1.2.0、scrcpy-server 5.0.1、OkHttp 5.5.0、DataStore、Coroutines、Timber。版本目录在 `gradle/libs.versions.toml`。

## 架构与关键入口
- 分层：UI(Compose Screen + ViewModel) → `repository/AdbRepository` → `adb/KadbManager`（约 840 行）→ Kadb；`AppServices` 是手工 DI 容器（object，全 lazy 单例）。
- 入口链：`AdbManagerApplication` → `AppServices.initialize` → `MainActivity`（唯一 Activity，USB 广播动态注册、主题、Android 17 `ACCESS_LOCAL_NETWORK` 权限）→ `ui/AdbManagerApp`（NavHost，17 个路由，底部三 tab：Home/Device/Settings）。
- 主要分组：`adb/`(Kadb 封装 + Fastboot 协议/USB 通道 + ADB 提权命令)、`repository/`(708 行，连接串行化 + 七组提权流水线)、`usb/`(OTG)、`discovery/`(mDNS + 网段扫描 + 协议探测)、`scrcpy/`(会话/协议/MediaCodec 解码/坐标映射)、`download/`(OkHttp 下载 + 飞牛 fnOS NAS 分享客户端 + GitHub Release 资产)、`files/`、`localapps/`、`apps/`、`data/`(设置/最近设备)、`i18n/`、`validation/`、`diagnostics/`、`model/`、`ui/`(每页 Screen + ViewModel)。
- 业务耦合点（个人定制，移植要替换）：`AdbPrivilegeCommands` 面向 `app.orchard.tv` Aerial 的 7 组提权命令；`DeviceViewModel` 里 Aerial 三来源安装（GitHub 定制版 `cplzsy/aerial-tv-1.78adb` 优先 → 飞牛内网回退 → 官方 `pengchujin/aerial-tv`）、签名冲突自动卸载重装；在线下载常用任务「短剧之家」「HGTV」；`DeviceViewModel.kt` 硬编码内网 NAS 地址 `http://192.168.5.218:5666/s/...`；`FnosShareClient` 硬编码 SignSecret/ApiKey。
- assets 只有 `scrcpy/scrcpy-server-v5.0.1`（约 717KB，SHA-256 `764eb6f7...bc536`）；没有内置 adb 二进制，ADB 由 Kadb 纯 Kotlin 实现。
- AndroidManifest：INTERNET / ACCESS_NETWORK_STATE / ACCESS_LOCAL_NETWORK / MANAGE_EXTERNAL_STORAGE 等；`network_security_config` 全局允许明文 HTTP；USB_DEVICE_ATTACHED intent-filter + `@xml/usb_device_filter`（ADB protocol 1 / Fastboot protocol 3）。

## 规范与注意
- 权威文档 `docs/README.md`（251 行）：架构、UI/性能/开发/验收/维护规范。根目录三份 md（在线下载、ADB 提权、Aerial 安装）是「拷到别的项目复用」的说明，与当前代码有漂移（例如文档里的 `FileBrowserShareClient` 已不存在）。
- 规范要求：小步可验证、不提交死代码/临时计划文档、失败必须进诊断日志、业务逻辑不塞进 Composable、耗时操作放 Dispatchers.IO、新增 ADB 命令封装在 KadbManager/Repository。
- 已知限制：本机应用导出仅单 APK；Shell 非交互终端；无断线心跳/自动重连；mDNS 与扫描受网络拓扑限制；`ACCESS_LOCAL_NETWORK` 只请求一次，拒绝后无引导。
