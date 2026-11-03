package com.sky22333.skyadb.model

/** 提权流程中的固定步骤，用于 UI 端映射标题。 */
enum class PrivilegeStep {
    GrantPermissions,
    CompileOptimize,
    Screensaver,
    KeepAdb,
    LaunchAerial,
    DefaultHome,
    HomeGuard,
}

/** 单条命令的执行状态。 */
enum class PrivilegeCommandState {
    SUCCESS,
    FAILED,
    SKIPPED,
}

/** 单条命令的执行结果。 */
data class PrivilegeCommandResult(
    val command: String,
    val state: PrivilegeCommandState,
    val output: String = "",
)

/** 单个步骤的执行结果；整组跳过时 [skippedReason] 说明原因。 */
data class PrivilegeStepResult(
    val step: PrivilegeStep,
    val commands: List<PrivilegeCommandResult>,
    val skippedReason: String? = null,
) {
    val skipped: Boolean get() = skippedReason != null
}

/** ADB 提权整体执行报告。 */
data class AdbPrivilegeReport(
    val steps: List<PrivilegeStepResult>,
) {
    val failedCount: Int
        get() = steps.sumOf { step ->
            step.commands.count { it.state == PrivilegeCommandState.FAILED }
        }

    val succeededCount: Int
        get() = steps.sumOf { step ->
            step.commands.count { it.state == PrivilegeCommandState.SUCCESS }
        }

    val skippedCount: Int
        get() = steps.sumOf { step ->
            if (step.skipped) 1 else 0
        }
}
