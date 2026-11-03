package com.sky22333.skyadb.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppTopBar
import com.sky22333.skyadb.ui.theme.AdbManagerTheme

@Composable
fun RemoteControlScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: RemoteControlViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RemoteControlContent(bottomPadding, uiState, onBackClick, viewModel::sendKey)
}

@Composable
internal fun RemoteControlContent(
    bottomPadding: Dp = 0.dp,
    uiState: RemoteControlUiState,
    onBackClick: () -> Unit,
    onKeyClick: (RemoteKey) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        AppTopBar(
            title = { Text(stringResource(R.string.remote_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.action_back),
                    )
                }
            },
        )
        RemoteStatus(uiState.status)
        RemotePanel(Modifier.weight(1f).fillMaxWidth(), onKeyClick)
    }
}

/**
 * Measure the available space rather than the display: navigation and system insets are already
 * removed.
 */
@Composable
internal fun RemotePanel(modifier: Modifier = Modifier, onKeyClick: (RemoteKey) -> Unit) {
    BoxWithConstraints(
        modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
        val directionSize = (maxHeight - if (landscape) 80.dp else 272.dp).coerceIn(144.dp, 224.dp)
        Surface(
            modifier =
                if (landscape) Modifier.widthIn(max = 640.dp).fillMaxSize()
                else Modifier.widthIn(max = 320.dp).fillMaxHeight().fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            if (landscape) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RemoteRow(powerKeys, onKeyClick)
                        DirectionPad(directionSize, onKeyClick)
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        RemoteRow(navigationKeys, onKeyClick)
                        RemoteRow(volumeKeys, onKeyClick)
                        RemoteRow(mediaKeys, onKeyClick)
                    }
                }
            } else {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RemoteRow(powerKeys, onKeyClick)
                    DirectionPad(directionSize, onKeyClick)
                    RemoteRow(navigationKeys, onKeyClick)
                    RemoteRow(volumeKeys, onKeyClick)
                    RemoteRow(mediaKeys, onKeyClick)
                }
            }
        }
    }
}

@Composable
private fun DirectionPad(diameter: Dp, onKeyClick: (RemoteKey) -> Unit) {
    // Round down so pixel rounding never makes adjacent touch targets overlap.
    val keySize = (diameter.value / 3).toInt().dp
    Box(
        Modifier.size(diameter)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
    ) {
        DirectionKey(
            RemoteKey.Up,
            Icons.Outlined.KeyboardArrowUp,
            Modifier.align(Alignment.TopCenter).size(keySize),
            onKeyClick,
        )
        DirectionKey(
            RemoteKey.Left,
            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            Modifier.align(Alignment.CenterStart).size(keySize),
            onKeyClick,
        )
        DirectionKey(
            RemoteKey.Right,
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            Modifier.align(Alignment.CenterEnd).size(keySize),
            onKeyClick,
        )
        DirectionKey(
            RemoteKey.Down,
            Icons.Outlined.KeyboardArrowDown,
            Modifier.align(Alignment.BottomCenter).size(keySize),
            onKeyClick,
        )
        RemoteKeySurface(
            RemoteKey.Center,
            Modifier.align(Alignment.Center).size(keySize),
            onKeyClick,
            CircleShape,
            MaterialTheme.colorScheme.surface,
        ) {
            Text(
                stringResource(RemoteKey.Center.labelRes),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DirectionKey(
    key: RemoteKey,
    icon: ImageVector,
    modifier: Modifier,
    onKeyClick: (RemoteKey) -> Unit,
) {
    RemoteKeySurface(key, modifier, onKeyClick, CircleShape, Color.Transparent) {
        Icon(icon, null, Modifier.size(28.dp))
    }
}

@Composable
private fun RemoteRow(keys: List<KeyAction>, onKeyClick: (RemoteKey) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { (key, icon) ->
            RemoteKeySurface(
                key,
                Modifier.weight(1f).height(56.dp),
                onKeyClick,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(icon, null, Modifier.size(22.dp))
                    Text(
                        stringResource(key.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteKeySurface(
    key: RemoteKey,
    modifier: Modifier,
    onKeyClick: (RemoteKey) -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    color: Color,
    content: @Composable () -> Unit,
) {
    val label = stringResource(key.labelRes)
    Surface(
        onClick = { onKeyClick(key) },
        modifier = modifier.semantics { contentDescription = label },
        shape = shape,
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun RemoteStatus(status: OperationStatus) {
    var showDetails by remember(status) { mutableStateOf(false) }
    val text =
        when (status) {
            OperationStatus.Idle -> stringResource(R.string.remote_subtitle)
            is OperationStatus.Running -> status.text
            is OperationStatus.Success -> status.text
            is OperationStatus.Failed ->
                stringResource(R.string.device_status_error_format, status.text, status.suggestion)
        }
    Box(
        Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 20.dp).clickable(
            enabled = status is OperationStatus.Failed,
            role = Role.Button,
        ) {
            showDetails = true
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color =
                if (status is OperationStatus.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (showDetails && status is OperationStatus.Failed) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(status.text) },
            text = { Text(status.suggestion) },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }
}

private data class KeyAction(val key: RemoteKey, val icon: ImageVector)

private val powerKeys =
    listOf(
        KeyAction(RemoteKey.Wakeup, Icons.Outlined.LightMode),
        KeyAction(RemoteKey.Power, Icons.Outlined.PowerSettingsNew),
        KeyAction(RemoteKey.Sleep, Icons.Outlined.Bedtime),
    )
private val navigationKeys =
    listOf(
        KeyAction(RemoteKey.Back, Icons.AutoMirrored.Outlined.ArrowBack),
        KeyAction(RemoteKey.Home, Icons.Outlined.Home),
        KeyAction(RemoteKey.Menu, Icons.Outlined.Menu),
    )
private val volumeKeys =
    listOf(
        KeyAction(RemoteKey.VolumeDown, Icons.AutoMirrored.Outlined.VolumeDown),
        KeyAction(RemoteKey.Mute, Icons.AutoMirrored.Outlined.VolumeOff),
        KeyAction(RemoteKey.VolumeUp, Icons.AutoMirrored.Outlined.VolumeUp),
    )
private val mediaKeys =
    listOf(
        KeyAction(RemoteKey.Previous, Icons.Outlined.SkipPrevious),
        KeyAction(RemoteKey.PlayPause, Icons.Outlined.PlayArrow),
        KeyAction(RemoteKey.Next, Icons.Outlined.SkipNext),
    )

@Preview(name = "紧凑遥控器", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(name = "遥控器", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "横屏遥控器", showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun RemoteControlContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        RemoteControlContent(uiState = RemoteControlUiState(), onBackClick = {}, onKeyClick = {})
    }
}
