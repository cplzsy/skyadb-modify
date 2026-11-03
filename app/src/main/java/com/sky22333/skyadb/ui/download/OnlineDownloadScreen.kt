package com.sky22333.skyadb.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.download.DownloadState
import com.sky22333.skyadb.download.DownloadTask
import com.sky22333.skyadb.download.ReleaseAsset
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun OnlineDownloadScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: OnlineDownloadViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnlineDownloadContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onModeChanged = viewModel::onModeChanged,
        onUrlChanged = viewModel::onUrlChanged,
        onTargetPathChanged = viewModel::onTargetPathChanged,
        onStartClick = viewModel::onStartClick,
        onCancelClick = viewModel::onCancelClick,
        onFavoriteTaskClick = viewModel::onFavoriteTaskClick,
        onHgvClick = viewModel::onHgvClick,
        onReleaseDialogDismiss = viewModel::onReleaseDialogDismiss,
        onReleaseAssetSelected = viewModel::onReleaseAssetSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineDownloadContent(
    bottomPadding: Dp = 0.dp,
    uiState: OnlineDownloadUiState,
    onBackClick: () -> Unit,
    onModeChanged: (OnlineDownloadMode) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTargetPathChanged: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
    onFavoriteTaskClick: () -> Unit,
    onHgvClick: () -> Unit,
    onReleaseDialogDismiss: () -> Unit,
    onReleaseAssetSelected: (ReleaseAsset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.online_download_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            DownloadFormCard(
                uiState = uiState,
                onModeChanged = onModeChanged,
                onUrlChanged = onUrlChanged,
                onTargetPathChanged = onTargetPathChanged,
                onStartClick = onStartClick,
                onCancelClick = onCancelClick,
            )
            FavoriteTaskCard(
                onShortDramaClick = onFavoriteTaskClick,
                onHgvClick = onHgvClick,
            )
            DownloadTaskCard(task = uiState.task)
            DownloadStatusMessage(status = uiState.operationStatus)

            if (uiState.releaseDialogVisible) {
                ReleaseAssetsDialog(
                    loading = uiState.releaseLoading,
                    assets = uiState.releaseAssets,
                    error = uiState.releaseError,
                    onAssetSelected = onReleaseAssetSelected,
                    onDismiss = onReleaseDialogDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadFormCard(
    uiState: OnlineDownloadUiState,
    onModeChanged: (OnlineDownloadMode) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTargetPathChanged: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.online_download_task_title))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OnlineDownloadMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.mode == mode,
                        onClick = { onModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = OnlineDownloadMode.entries.size,
                        ),
                    ) {
                        Text(stringResource(mode.labelRes))
                    }
                }
            }
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.online_download_url_label)) },
                singleLine = true,
                placeholder = { Text("https://example.com/app.apk") },
                isError = uiState.urlError != null,
                supportingText = uiState.urlError?.let { { Text(it) } },
            )
            if (uiState.mode == OnlineDownloadMode.PushFile) {
                OutlinedTextField(
                    value = uiState.targetPath,
                    onValueChange = onTargetPathChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.online_download_target_path_label)) },
                    singleLine = true,
                    placeholder = { Text("/sdcard/Download/") },
                    isError = uiState.targetPathError != null,
                    supportingText = uiState.targetPathError?.let { { Text(it) } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartClick,
                    enabled = uiState.actionEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_start))
                }
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTask?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.online_download_task_status_title))
            if (task == null) {
                Text(
                    text = stringResource(R.string.online_download_no_task),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(text = task.fileName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = task.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.online_download_status_format,
                        stringResource(task.state.labelRes),
                    ),
                    color = if (task.state == DownloadState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (task.targetPath.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.online_download_target_format, task.targetPath),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteTaskCard(
    onShortDramaClick: () -> Unit,
    onHgvClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.online_download_favorite_tasks))
            FavoriteTaskItem(
                icon = Icons.Outlined.LiveTv,
                title = stringResource(R.string.online_download_favorite_short_drama),
                subtitle = stringResource(R.string.online_download_favorite_subtitle),
                onClick = onShortDramaClick,
            )
            FavoriteTaskItem(
                icon = Icons.Outlined.Tv,
                title = stringResource(R.string.online_download_favorite_hgtv),
                subtitle = stringResource(R.string.online_download_favorite_subtitle),
                onClick = onHgvClick,
            )
        }
    }
}

@Composable
private fun FavoriteTaskItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReleaseAssetsDialog(
    loading: Boolean,
    assets: List<ReleaseAsset>,
    error: String?,
    onAssetSelected: (ReleaseAsset) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_download_release_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loading ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                            )
                            Text(stringResource(R.string.online_download_release_loading))
                        }
                    error != null ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    assets.isEmpty() -> Text(stringResource(R.string.online_download_release_empty))
                    else ->
                        assets.forEach { asset ->
                            OutlinedButton(
                                onClick = { onAssetSelected(asset) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = releaseAssetLabel(asset.fileName),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun releaseAssetLabel(fileName: String): String =
    when {
        fileName.contains("arm64-v8a", ignoreCase = true) ->
            stringResource(R.string.online_download_release_arm64)
        fileName.contains("armeabi-v7a", ignoreCase = true) ->
            stringResource(R.string.online_download_release_arm32)
        else -> fileName
    }

@Composable
private fun DownloadStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(text = status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is OperationStatus.Success -> Text(text = status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(name = "在线下载 - APK", showBackground = true, widthDp = 390)
@Composable
private fun OnlineDownloadApkPreview() {
    AdbManagerTheme(dynamicColor = false) {
        OnlineDownloadContent(
            uiState = OnlineDownloadUiState(
                mode = OnlineDownloadMode.InstallApk,
                url = "https://example.com/app.apk",
                actionEnabled = true,
            ),
            onBackClick = {},
            onModeChanged = {},
            onUrlChanged = {},
            onTargetPathChanged = {},
            onStartClick = {},
            onCancelClick = {},
            onFavoriteTaskClick = {},
            onHgvClick = {},
            onReleaseDialogDismiss = {},
            onReleaseAssetSelected = {},
        )
    }
}

@Preview(name = "在线下载 - 文件推送", showBackground = true, widthDp = 390)
@Composable
private fun OnlineDownloadPushPreview() {
    AdbManagerTheme(dynamicColor = false) {
        OnlineDownloadContent(
            uiState = OnlineDownloadUiState(
                mode = OnlineDownloadMode.PushFile,
                url = "https://example.com/config.json",
                targetPath = "/sdcard/Download/",
                actionEnabled = true,
                task = DownloadTask(
                    url = "https://example.com/config.json",
                    fileName = "config.json",
                    targetPath = "/sdcard/Download/config.json",
                    progress = 0.56f,
                    state = DownloadState.Downloading,
                    message = "已下载 56%",
                ),
            ),
            onBackClick = {},
            onModeChanged = {},
            onUrlChanged = {},
            onTargetPathChanged = {},
            onStartClick = {},
            onCancelClick = {},
            onFavoriteTaskClick = {},
            onHgvClick = {},
            onReleaseDialogDismiss = {},
            onReleaseAssetSelected = {},
        )
    }
}
