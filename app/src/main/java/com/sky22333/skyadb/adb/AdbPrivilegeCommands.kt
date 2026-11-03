package com.sky22333.skyadb.adb

/**
 * ADB 提权命令清单与纯逻辑。
 *
 * 命令均为 KadbManager.shell 可执行的正文（不含 `adb shell` 前缀）；
 * 展示时由结果模型统一加上 `adb shell ` 前缀。
 */
object AdbPrivilegeCommands {

    const val TARGET_PACKAGE = "app.orchard.tv"
    const val MAIN_ACTIVITY = "app.orchard.tv/.MainActivity"
    const val CONFIG_RECEIVER = "app.orchard.tv/.guard.ConfigReceiver"
    const val REVIVAL_LISTENER = "app.orchard.tv/app.orchard.tv.guard.RevivalListener"
    const val AERIAL_DREAM = "app.orchard.tv/app.orchard.tv.dream.AerialDream"
    const val HOME_GUARD_SERVICE = "app.orchard.tv/app.orchard.tv.guard.HomeGuardService"

    /** 组① 授予 adb 专属权限。 */
    val grantCommands: List<String> = listOf(
        "pm grant $TARGET_PACKAGE android.permission.ACCESS_FINE_LOCATION",
        "pm grant $TARGET_PACKAGE android.permission.WRITE_SECURE_SETTINGS",
        "appops set $TARGET_PACKAGE SYSTEM_ALERT_WINDOW allow",
        "appops set $TARGET_PACKAGE WRITE_SETTINGS allow",
        "appops set $TARGET_PACKAGE REQUEST_INSTALL_PACKAGES allow",
        "appops set $TARGET_PACKAGE GET_USAGE_STATS allow",
        "appops set $TARGET_PACKAGE android:get_usage_stats allow",
        "appops set $TARGET_PACKAGE ACCESS_RESTRICTED_SETTINGS allow",
        "cmd notification allow_listener $REVIVAL_LISTENER",
        "appops set $TARGET_PACKAGE APP_AUTO_START allow",
        "appops set $TARGET_PACKAGE APP_ASSOC_START allow",
        "appops set $TARGET_PACKAGE AUTO_START allow",
        "appops set $TARGET_PACKAGE ASSOC_START allow",
    )

    /** 组② 启动一次并编译优化。 */
    const val LAUNCH_COMMAND = "am start -W -n $MAIN_ACTIVITY"
    const val COMPILE_COMMAND = "cmd package compile -m speed -f $TARGET_PACKAGE"

    /** 组③ 屏保设置前置查询与命令。 */
    const val SCREENSAVER_QUERY_COMMAND = "settings get secure screensaver_enabled"
    const val SCREENSAVER_COMPONENT_COMMAND =
        "settings put secure screensaver_components $AERIAL_DREAM"
    const val SCREENSAVER_CONFIG_COMMAND =
        "am broadcast -a app.orchard.tv.CONFIG -n $CONFIG_RECEIVER --ez own_screensaver true"

    /** 组④ 保持 ADB 常开。 */
    const val KEEP_ADB_COMMAND =
        "am broadcast -a app.orchard.tv.CONFIG -n $CONFIG_RECEIVER --ez keep_adb true"

    /** 组⑥ 设为默认桌面及回退命令。 */
    const val DEFAULT_HOME_ROLE_COMMAND =
        "cmd role add-role-holder --user 0 android.app.role.HOME $TARGET_PACKAGE"
    const val DEFAULT_HOME_FALLBACK_COMMAND =
        "cmd package set-home-activity --user 0 $MAIN_ACTIVITY"

    /** 组⑦ 桌面守护。 */
    const val ACCESSIBILITY_QUERY_COMMAND =
        "settings get secure enabled_accessibility_services"
    const val ACCESSIBILITY_ENABLE_COMMAND = "settings put secure accessibility_enabled 1"

    /** 等待 5 秒后编译的间隔。 */
    const val COMPILE_WAIT_MILLIS = 5_000L

    /** 组⑤ 判定 `am start -W` 成功的标记。 */
    const val STATUS_OK_MARKER = "Status: ok"

    /**
     * 组⑦ 拼接无障碍服务值：原值非空（且非 "null"）时用冒号追加，
     * 否则直接使用新组件。
     */
    fun buildAccessibilityServicesValue(original: String): String {
        val trimmed = original.trim()
        return if (trimmed.isBlank() || trimmed == "null") {
            HOME_GUARD_SERVICE
        } else {
            "$trimmed:$HOME_GUARD_SERVICE"
        }
    }

    /** 展示命令：统一加上 `adb shell ` 前缀。 */
    fun displayCommand(command: String): String = "adb shell $command"
}
