package com.sky22333.skyadb.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.PrivilegeCommandState
import com.sky22333.skyadb.model.PrivilegeCommandResult
import com.sky22333.skyadb.model.PrivilegeStep
import com.sky22333.skyadb.model.PrivilegeStepResult
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.OperationProgressIndicator
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.components.ToolActionCard
import com.sky22333.skyadb.ui.shared.SharedToolKeys
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.ui.theme.AdbManagerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    bottomPadding: Dp = 0.dp,
    onAppsClick: () -> Unit = {},
    onLocalAppsClick: () -> Unit = {},
    onInstallClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onFilesClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    onShellClick: () -> Unit = {},
    onRemoteClick: () -> Unit = {},
    onMirrorClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    viewModel: DeviceViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeviceContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onAppsClick = onAppsClick,
        onLocalAppsClick = onLocalAppsClick,
        onInstallClick = onInstallClick,
        onDownloadClick = onDownloadClick,
        onFilesClick = onFilesClick,
        onScreenshotClick = onScreenshotClick,
        onShellClick = onShellClick,
        onRemoteClick = onRemoteClick,
        onMirrorClick = onMirrorClick,
        onLogsClick = onLogsClick,
        onRefreshClick = viewModel::refreshDeviceInfo,
        onToggleInfoClick = viewModel::toggleInfoExpanded,
        onAerialInstallClick = viewModel::onAerialInstallClick,
        onAerialSourceDismiss = viewModel::onAerialSourceDismiss,
        onAerialNasSelected = viewModel::onAerialNasSelected,
        onAerialGithubSelected = viewModel::onAerialGithubSelected,
        onAerialSourceConfirmAccept = viewModel::onAerialSourceConfirmAccept,
        onAerialSourceConfirmDismiss = viewModel::onAerialSourceConfirmDismiss,
        onAerialFilesDialogDismiss = viewModel::onAerialFilesDialogDismiss,
        onAerialFileSelected = viewModel::onAerialFileSelected,
        onPrivilegeClick = viewModel::onPrivilegeClick,
        onPrivilegeDismiss = viewModel::onPrivilegeDismiss,
        onPrivilegeKeepAdbChange = viewModel::onPrivilegeKeepAdbChange,
        onPrivilegeConfirm = viewModel::onPrivilegeConfirm,
        onPrivilegeResultDismiss = viewModel::onPrivilegeResultDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceContent(
    bottomPadding: Dp = 0.dp,
    uiState: DeviceUiState,
    onAppsClick: () -> Unit,
    onLocalAppsClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFilesClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onShellClick: () -> Unit,
    onRemoteClick: () -> Unit,
    onMirrorClick: () -> Unit,
    onLogsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleInfoClick: () -> Unit,
    onAerialInstallClick: () -> Unit,
    onAerialSourceDismiss: () -> Unit,
    onAerialNasSelected: () -> Unit,
    onAerialGithubSelected: () -> Unit,
    onAerialSourceConfirmAccept: () -> Unit,
    onAerialSourceConfirmDismiss: () -> Unit,
    onAerialFilesDialogDismiss: () -> Unit,
    onAerialFileSelected: (AerialFileOption) -> Unit,
    onPrivilegeClick: () -> Unit,
    onPrivilegeDismiss: () -> Unit,
    onPrivilegeKeepAdbChange: (Boolean) -> Unit,
    onPrivilegeConfirm: () -> Unit,
    onPrivilegeResultDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.device_details_title)) },
            actions = {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !uiState.refreshing && uiState.connectionState == ConnectionState.Connected,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (uiState.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.device_refresh_info_desc),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.CardRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.deviceName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.device_connect_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            AppStatusBadge(state = uiState.connectionState)
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.section_quick_actions)) }
            item {
                QuickActionGrid(
                    sessionKind = uiState.sessionKind,
                    onAppsClick = onAppsClick,
                    onLocalAppsClick = onLocalAppsClick,
                    onInstallClick = onInstallClick,
                    onDownloadClick = onDownloadClick,
                    onFilesClick = onFilesClick,
                    onScreenshotClick = onScreenshotClick,
                    onShellClick = onShellClick,
                    onRemoteClick = onRemoteClick,
                    onMirrorClick = onMirrorClick,
                    onLogsClick = onLogsClick,
                    onAerialInstallClick = onAerialInstallClick,
                    onPrivilegeClick = onPrivilegeClick,
                )
            }
            when (val status = uiState.aerialInstallStatus) {
                OperationStatus.Idle -> Unit
                is OperationStatus.Running -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = status.text,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OperationProgressIndicator(progress = status.progress)
                    }
                }
                is OperationStatus.Success -> item {
                    Text(
                        text = status.text,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is OperationStatus.Failed -> item {
                    Text(
                        text = stringResource(
                            R.string.device_status_error_format,
                            status.text,
                            status.suggestion,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (uiState.privilegeRunning) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.adb_privilege_running),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (uiState.aerialSourceLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.aerial_fetching_files),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            uiState.aerialSourceError?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.section_system_info),
                    description = stringResource(
                        if (uiState.infoExpanded) R.string.device_info_expanded_desc else R.string.device_info_collapsed_desc,
                    ),
                    trailing = {
                        IconButton(
                            onClick = onToggleInfoClick,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(
                                    if (uiState.infoExpanded) R.string.device_info_collapse_desc else R.string.device_info_expand_desc,
                                ),
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.infoExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                )
            }
            if (uiState.infoExpanded) {
                item { DeviceRefreshStatus(status = uiState.refreshStatus) }
                item {
                    InfoGrid(
                        items = listOf(
                            stringResource(R.string.device_info_brand) to uiState.info.brand,
                            stringResource(R.string.device_info_model) to uiState.info.model,
                            stringResource(R.string.device_info_android_version) to uiState.info.androidVersion,
                            "SDK" to uiState.info.sdk,
                            "ABI" to uiState.info.abi,
                            stringResource(R.string.device_info_resolution) to uiState.info.resolution,
                            stringResource(R.string.device_info_battery) to uiState.info.battery,
                        ),
                    )
                }
            }
        }
    }

    if (uiState.privilegeConfirmVisible) {
        PrivilegeConfirmDialog(
            keepAdbEnabled = uiState.privilegeKeepAdb,
            onKeepAdbChange = onPrivilegeKeepAdbChange,
            onConfirm = onPrivilegeConfirm,
            onDismiss = onPrivilegeDismiss,
        )
    }

    uiState.privilegeReport?.let { report ->
        PrivilegeResultDialog(
            report = report,
            onDismiss = onPrivilegeResultDismiss,
        )
    }

    if (uiState.aerialSourceDialogVisible) {
        AerialSourceDialog(
            onNasSelected = onAerialNasSelected,
            onGithubSelected = onAerialGithubSelected,
            onDismiss = onAerialSourceDismiss,
        )
    }

    uiState.aerialSourceConfirm?.let { kind ->
        AerialSourceConfirmDialog(
            kind = kind,
            onAccept = onAerialSourceConfirmAccept,
            onDismiss = onAerialSourceConfirmDismiss,
        )
    }

    if (uiState.aerialFilesDialogVisible) {
        AerialFileSelectDialog(
            files = uiState.aerialCandidateFiles,
            onSelect = onAerialFileSelected,
            onDismiss = onAerialFilesDialogDismiss,
        )
    }
}

@Composable
private fun PrivilegeConfirmDialog(
    keepAdbEnabled: Boolean,
    onKeepAdbChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adb_privilege_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.adb_privilege_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.adb_privilege_keep_adb),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.adb_privilege_keep_adb_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = keepAdbEnabled, onCheckedChange = onKeepAdbChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.adb_privilege_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AerialSourceDialog(
    onNasSelected: () -> Unit,
    onGithubSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.aerial_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onNasSelected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.aerial_source_nas))
                }
                OutlinedButton(
                    onClick = onGithubSelected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.aerial_source_github))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AerialSourceConfirmDialog(
    kind: AerialSourceKind,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.aerial_source_confirm_title)) },
        text = {
            Text(
                text =
                    stringResource(
                        if (kind == AerialSourceKind.Nas) {
                            R.string.aerial_source_confirm_nas
                        } else {
                            R.string.aerial_source_confirm_github
                        }
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.aerial_source_confirm_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AerialFileSelectDialog(
    files: List<AerialFileOption>,
    onSelect: (AerialFileOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.aerial_select_file_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                files.forEach { file ->
                    OutlinedButton(
                        onClick = { onSelect(file) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = file.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PrivilegeResultDialog(
    report: com.sky22333.skyadb.model.AdbPrivilegeReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.adb_privilege_result_title))
                Text(
                    text = stringResource(
                        R.string.adb_privilege_summary_format,
                        report.succeededCount,
                        report.failedCount,
                        report.skippedCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                report.steps.forEach { step ->
                    PrivilegeStepBlock(step = step)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.adb_privilege_close))
            }
        },
    )
}

@Composable
private fun PrivilegeStepBlock(step: PrivilegeStepResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(stepTitleRes(step.step)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        step.skippedReason?.let { reason ->
            Text(
                text = reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        step.commands.forEach { command ->
            PrivilegeCommandRow(command = command)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
private fun PrivilegeCommandRow(command: PrivilegeCommandResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(statusTextRes(command.state)),
            color = statusColor(command.state),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (command.output.isNotBlank()) {
                Text(
                    text = command.output,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun statusColor(state: PrivilegeCommandState): androidx.compose.ui.graphics.Color =
    when (state) {
        PrivilegeCommandState.SUCCESS -> MaterialTheme.colorScheme.primary
        PrivilegeCommandState.FAILED -> MaterialTheme.colorScheme.error
        PrivilegeCommandState.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun statusTextRes(state: PrivilegeCommandState): Int =
    when (state) {
        PrivilegeCommandState.SUCCESS -> R.string.adb_privilege_status_success
        PrivilegeCommandState.FAILED -> R.string.adb_privilege_status_failed
        PrivilegeCommandState.SKIPPED -> R.string.adb_privilege_status_skipped
    }

private fun stepTitleRes(step: PrivilegeStep): Int =
    when (step) {
        PrivilegeStep.GrantPermissions -> R.string.adb_privilege_step_grant
        PrivilegeStep.CompileOptimize -> R.string.adb_privilege_step_compile
        PrivilegeStep.Screensaver -> R.string.adb_privilege_step_screensaver
        PrivilegeStep.KeepAdb -> R.string.adb_privilege_step_keep_adb
        PrivilegeStep.LaunchAerial -> R.string.adb_privilege_step_launch
        PrivilegeStep.DefaultHome -> R.string.adb_privilege_step_home
        PrivilegeStep.HomeGuard -> R.string.adb_privilege_step_guard
    }

@Composable
private fun DeviceRefreshStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimens.CardRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.CardPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    sessionKind: AdbSessionKind,
    onAppsClick: () -> Unit,
    onLocalAppsClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFilesClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onShellClick: () -> Unit,
    onRemoteClick: () -> Unit,
    onMirrorClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAerialInstallClick: () -> Unit,
    onPrivilegeClick: () -> Unit,
) {
    val shellLabel = stringResource(R.string.nav_shell)
    val actions = when (sessionKind) {
        AdbSessionKind.UsbFastboot -> listOf(
            QuickActionSpec(stringResource(R.string.fastboot_console_title), Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
        )
        AdbSessionKind.None, AdbSessionKind.Wifi, AdbSessionKind.UsbAdb -> listOf(
            QuickActionSpec(stringResource(R.string.action_apps), Icons.Outlined.Apps, onAppsClick, SharedToolKeys.Apps),
            QuickActionSpec(stringResource(R.string.nav_local_apps), Icons.Outlined.Apps, onLocalAppsClick, SharedToolKeys.LocalApps),
            QuickActionSpec(stringResource(R.string.action_install_apk), Icons.Outlined.Android, onInstallClick, SharedToolKeys.Install),
            QuickActionSpec(stringResource(R.string.action_download_online), Icons.Outlined.Download, onDownloadClick, SharedToolKeys.Download),
            QuickActionSpec(stringResource(R.string.action_aerial_install), Icons.Outlined.Android, onAerialInstallClick),
            QuickActionSpec(stringResource(R.string.action_adb_privilege), Icons.Outlined.Apps, onPrivilegeClick),
            QuickActionSpec(stringResource(R.string.action_file_manage), Icons.Outlined.FolderOpen, onFilesClick, SharedToolKeys.Files),
            QuickActionSpec(shellLabel, Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
            QuickActionSpec(stringResource(R.string.action_mirror), Icons.Outlined.Android, onMirrorClick),
            QuickActionSpec(stringResource(R.string.action_remote), Icons.Outlined.Android, onRemoteClick, SharedToolKeys.Remote),
            QuickActionSpec(stringResource(R.string.action_system_log), Icons.Outlined.Code, onLogsClick, SharedToolKeys.Logs),
            QuickActionSpec(stringResource(R.string.nav_screenshot), Icons.Outlined.PhotoCamera, onScreenshotClick, SharedToolKeys.Screenshot),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowActions.forEach { action ->
                    ToolActionCard(
                        title = action.label,
                        icon = action.icon,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        sharedContentKey = action.sharedContentKey,
                    )
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val sharedContentKey: String? = null,
)

@Preview(name = "设备详情 - 未连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentDisconnectedPreview() {
    AdbManagerTheme(dynamicColor = false) {
        DeviceContent(
            uiState = DeviceUiState(),
            onAppsClick = {},
            onLocalAppsClick = {},
            onInstallClick = {},
            onDownloadClick = {},
            onFilesClick = {},
            onScreenshotClick = {},
            onShellClick = {},
            onRemoteClick = {},
            onMirrorClick = {},
            onLogsClick = {},
            onRefreshClick = {},
            onToggleInfoClick = {},
            onAerialInstallClick = {},
            onAerialSourceDismiss = {},
            onAerialNasSelected = {},
            onAerialGithubSelected = {},
            onAerialSourceConfirmAccept = {},
            onAerialSourceConfirmDismiss = {},
            onAerialFilesDialogDismiss = {},
            onAerialFileSelected = {},
            onPrivilegeClick = {},
            onPrivilegeDismiss = {},
            onPrivilegeKeepAdbChange = {},
            onPrivilegeConfirm = {},
            onPrivilegeResultDismiss = {},
        )
    }
}

@Preview(name = "设备详情 - 已连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentConnectedPreview() {
    AdbManagerTheme(dynamicColor = false) {
        DeviceContent(
            uiState = DeviceUiState(
                deviceName = "客厅电视",
                connectionState = ConnectionState.Connected,
                sessionKind = AdbSessionKind.Wifi,
                info = DeviceInfo(
                    brand = "Google",
                    model = "Android TV",
                    androidVersion = "14",
                    sdk = "34",
                    abi = "arm64-v8a",
                    resolution = "3840 x 2160",
                    battery = "未知",
                ),
            ),
            onAppsClick = {},
            onLocalAppsClick = {},
            onInstallClick = {},
            onDownloadClick = {},
            onFilesClick = {},
            onScreenshotClick = {},
            onShellClick = {},
            onRemoteClick = {},
            onMirrorClick = {},
            onLogsClick = {},
            onRefreshClick = {},
            onToggleInfoClick = {},
            onAerialInstallClick = {},
            onAerialSourceDismiss = {},
            onAerialNasSelected = {},
            onAerialGithubSelected = {},
            onAerialSourceConfirmAccept = {},
            onAerialSourceConfirmDismiss = {},
            onAerialFilesDialogDismiss = {},
            onAerialFileSelected = {},
            onPrivilegeClick = {},
            onPrivilegeDismiss = {},
            onPrivilegeKeepAdbChange = {},
            onPrivilegeConfirm = {},
            onPrivilegeResultDismiss = {},
        )
    }
}
