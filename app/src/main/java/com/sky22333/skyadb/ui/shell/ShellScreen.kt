package com.sky22333.skyadb.ui.shell

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.launch

@Composable
fun ShellScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.shell_output_title)
    val copied = stringResource(R.string.command_result_copied)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    ShellContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onCommandChanged = viewModel::onCommandChanged,
        onExecuteClick = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.onExecuteClick()
        },
        onHistoryCommandClick = viewModel::onHistoryCommandClick,
        onRefreshSummary = viewModel::refreshSummary,
        onReboot = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.onQuickCommand(it)
        },
        onCopy = { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text)))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellContent(
    bottomPadding: Dp = 0.dp,
    uiState: ShellUiState,
    onBackClick: () -> Unit,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
    onHistoryCommandClick: (String) -> Unit,
    onRefreshSummary: () -> Unit = {},
    onReboot: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (uiState.fastboot) R.string.fastboot_console_title
                        else R.string.shell_title
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            if (uiState.fastboot)
                item {
                    FastbootControls(uiState, onRefreshSummary, onReboot, onCopy)
                }
            item {
                ShellCommandCard(
                    uiState = uiState,
                    onCommandChanged = onCommandChanged,
                    onExecuteClick = onExecuteClick,
                )
            }
            item { ShellOutputCard(output = uiState.output, onCopy = { onCopy(uiState.output) }) }
            item {
                ShellHistoryCard(
                    history = uiState.history,
                    onHistoryCommandClick = onHistoryCommandClick,
                )
            }
        }
    }
}

@Composable
private fun ShellCommandCard(
    uiState: ShellUiState,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_command_input_title))
            OutlinedTextField(
                value = uiState.command,
                enabled =
                    uiState.operationStatus !is OperationStatus.Running && !uiState.summaryLoading,
                onValueChange = onCommandChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(
                            if (uiState.fastboot) R.string.fastboot_console_title
                            else R.string.shell_command_label
                        )
                    )
                },
                placeholder = {
                    Text(
                        stringResource(
                            if (uiState.fastboot) R.string.fastboot_command_placeholder
                            else R.string.shell_command_placeholder
                        )
                    )
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (uiState.fastboot) R.string.fastboot_command_input_hint
                            else R.string.adb_command_input_hint
                        )
                    )
                },
                minLines = 1,
                maxLines = 4,
            )
            ShellStatusMessage(status = uiState.operationStatus)
            Button(
                onClick = onExecuteClick,
                enabled = uiState.executeEnabled && !uiState.summaryLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.action_execute))
            }
        }
    }
}

@Composable
private fun ShellOutputCard(output: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.shell_output_title),
                trailing = {
                    IconButton(onClick = onCopy, enabled = output.isNotBlank()) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            stringResource(R.string.command_copy_result),
                        )
                    }
                },
            )
            if (output.isBlank()) {
                EmptyState(title = stringResource(R.string.shell_no_output))
            } else {
                Text(
                    text = output,
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 160.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(AppDimens.CardRadius),
                            )
                            .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ShellHistoryCard(
    history: List<String>,
    onHistoryCommandClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_history_title))
            if (history.isEmpty()) {
                EmptyState(title = stringResource(R.string.shell_no_history))
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { command ->
                        AssistChip(
                            onClick = { onHistoryCommandClick(command) },
                            label = { Text(command) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        is OperationStatus.Success ->
            Text(text = status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed ->
            Text(
                text =
                    stringResource(
                        R.string.device_status_error_format,
                        status.text,
                        status.suggestion,
                    ),
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Preview(name = "Shell - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentEmptyPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ShellContent(
            uiState = ShellUiState(),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}

@Preview(name = "Shell - 输出态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentOutputPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ShellContent(
            uiState =
                ShellUiState(
                    command = "getprop ro.product.model",
                    output = "Android TV",
                    history = listOf("getprop ro.product.model", "wm size", "dumpsys battery"),
                    operationStatus = OperationStatus.Success("命令执行完成，退出码 0"),
                    executeEnabled = true,
                ),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}
