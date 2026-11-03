package com.sky22333.skyadb.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.download.DownloadResult
import com.sky22333.skyadb.download.FnosShareClient
import com.sky22333.skyadb.download.NasDownloadResult
import com.sky22333.skyadb.download.NasFetchResult
import com.sky22333.skyadb.download.NasShareFile
import com.sky22333.skyadb.download.NetworkDownloadManager
import com.sky22333.skyadb.download.ReleaseAsset
import com.sky22333.skyadb.download.ReleaseFetchResult
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AdbPrivilegeReport
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Aerial 远程安装候选文件（NAS 或 GitHub 来源）。 */
sealed interface AerialFileOption {
    val fileName: String

    data class Nas(
        val file: NasShareFile,
        val shareId: String,
        val token: String,
        val cookie: String,
    ) : AerialFileOption {
        override val fileName: String get() = file.fileName
    }

    data class Github(
        val asset: ReleaseAsset,
    ) : AerialFileOption {
        override val fileName: String get() = asset.fileName
    }
}

/** Aerial 来源类型（用于来源确认弹窗与加载分派）。 */
enum class AerialSourceKind { Nas, Github }

data class DeviceUiState(
    val deviceName: String = appString(R.string.device_no_device_selected),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessionKind: AdbSessionKind = AdbSessionKind.None,
    val info: DeviceInfo = DeviceInfo(),
    val refreshing: Boolean = false,
    val refreshStatus: OperationStatus = OperationStatus.Idle,
    val infoExpanded: Boolean = false,
    val aerialInstallStatus: OperationStatus = OperationStatus.Idle,
    val aerialSourceDialogVisible: Boolean = false,
    val aerialSourceLoading: Boolean = false,
    val aerialSourceError: String? = null,
    val aerialSourceConfirm: AerialSourceKind? = null,
    val aerialFilesDialogVisible: Boolean = false,
    val aerialCandidateFiles: List<AerialFileOption> = emptyList(),
    val privilegeConfirmVisible: Boolean = false,
    val privilegeKeepAdb: Boolean = true,
    val privilegeRunning: Boolean = false,
    val privilegeReport: AdbPrivilegeReport? = null,
)

class DeviceViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val downloadManager: NetworkDownloadManager = AppServices.downloadManager,
    private val fnosShareClient: FnosShareClient = FnosShareClient(NasShareUrl),
) : ViewModel() {
    private val state = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = state.asStateFlow()

    private var downloadJob: Job? = null
    @Volatile private var generation = 0L

    init {
        viewModelScope.launch {
            adbRepository.selectedDeviceInfo.collect { info ->
                state.value = state.value.copy(info = info)
            }
        }

        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                val connected = devices.firstOrNull { it.connectionState == ConnectionState.Connected }
                if (connected != null) {
                    state.value = state.value.copy(
                        deviceName = connected.name,
                        connectionState = connected.connectionState,
                        sessionKind = adbRepository.sessionKind(),
                    )
                } else {
                    state.value = state.value.copy(
                        deviceName = appString(R.string.device_no_device_selected),
                        connectionState = ConnectionState.Disconnected,
                        sessionKind = AdbSessionKind.None,
                        info = DeviceInfo(),
                        refreshing = false,
                        refreshStatus = OperationStatus.Idle,
                    )
                }
            }
        }
    }

    fun toggleInfoExpanded() {
        state.value = state.value.copy(infoExpanded = !state.value.infoExpanded)
    }

    fun refreshDeviceInfo() {
        state.value = state.value.copy(
            refreshing = true,
            refreshStatus = OperationStatus.Running(appString(R.string.device_refreshing)),
        )
        viewModelScope.launch {
            when (val result = adbRepository.refreshDeviceInfo()) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        info = result.data,
                        refreshStatus = OperationStatus.Success(appString(R.string.device_refresh_success)),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        refreshStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun onPrivilegeClick() {
        if (state.value.privilegeRunning) return
        state.value = state.value.copy(privilegeConfirmVisible = true)
    }

    fun onPrivilegeDismiss() {
        state.value = state.value.copy(privilegeConfirmVisible = false)
    }

    fun onPrivilegeKeepAdbChange(enabled: Boolean) {
        state.value = state.value.copy(privilegeKeepAdb = enabled)
    }

    fun onPrivilegeConfirm() {
        if (state.value.privilegeRunning) return
        val keepAdb = state.value.privilegeKeepAdb
        state.value = state.value.copy(
            privilegeConfirmVisible = false,
            privilegeRunning = true,
            privilegeReport = null,
        )
        viewModelScope.launch {
            when (val result = adbRepository.runAdbPrivilege(keepAdb)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        privilegeRunning = false,
                        privilegeReport = result.data,
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        privilegeRunning = false,
                        privilegeReport = null,
                        refreshStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun onPrivilegeResultDismiss() {
        state.value = state.value.copy(privilegeReport = null)
    }

    // ---------- Aerial 安装（三来源） ----------

    /** 点击「Aerial安装」：先弹来源选择弹窗。 */
    fun onAerialInstallClick() {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        if (state.value.aerialSourceLoading) return
        state.value =
            state.value.copy(
                aerialSourceDialogVisible = true,
                aerialSourceError = null,
            )
    }

    fun onAerialSourceDismiss() {
        state.value = state.value.copy(aerialSourceDialogVisible = false)
    }

    /** 来源 1：NAS 定制版 —— 先弹来源确认，用户同意后再加载列表。 */
    fun onAerialNasSelected() {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        if (state.value.aerialSourceLoading) return
        state.value = state.value.copy(
            aerialSourceDialogVisible = false,
            aerialSourceConfirm = AerialSourceKind.Nas,
            aerialSourceError = null,
        )
    }

    /** 用户同意来源确认：关闭确认框并开始加载对应来源列表。 */
    fun onAerialSourceConfirmAccept() {
        val kind = state.value.aerialSourceConfirm ?: return
        state.value = state.value.copy(aerialSourceConfirm = null)
        when (kind) {
            AerialSourceKind.Nas -> loadNasAerialOptions()
            AerialSourceKind.Github -> loadGithubAerialOptions()
        }
    }

    fun onAerialSourceConfirmDismiss() {
        state.value = state.value.copy(aerialSourceConfirm = null)
    }

    /** 加载 GitHub 定制版列表：优先 Release APK 资产，失败回退内网飞牛 NAS 分享。 */
    private fun loadNasAerialOptions() {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        if (state.value.aerialSourceLoading) return
        state.value = state.value.copy(
            aerialSourceLoading = true,
            aerialSourceError = null,
        )
        viewModelScope.launch {
            // 1) 优先：GitHub 定制版 Release（与 GitHub 官方版一致的 Release 获取逻辑）。
            when (val result = downloadManager.fetchLatestReleaseApks(CustomGithubReleaseUrl)) {
                is ReleaseFetchResult.Success -> {
                    val options = result.assets.map { AerialFileOption.Github(it) }
                    state.value = state.value.copy(aerialSourceLoading = false)
                    showOrStartAerialOptions(options)
                }
                is ReleaseFetchResult.Failure -> {
                    // 2) 回退：内网飞牛 NAS 分享。
                    when (val nasResult = fnosShareClient.fetchApkFiles()) {
                        is NasFetchResult.Success -> {
                            val options =
                                nasResult.files.map {
                                    AerialFileOption.Nas(
                                        file = it,
                                        shareId = nasResult.shareId,
                                        token = nasResult.token,
                                        cookie = nasResult.cookie,
                                    )
                                }
                            state.value = state.value.copy(aerialSourceLoading = false)
                            showOrStartAerialOptions(options)
                        }
                        is NasFetchResult.Failure -> {
                            state.value = state.value.copy(
                                aerialSourceLoading = false,
                                aerialSourceError = nasResult.message,
                            )
                        }
                    }
                }
            }
        }
    }

    /** 来源 3：GitHub 官方版 —— 先弹来源确认，用户同意后再加载列表。 */
    fun onAerialGithubSelected() {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        if (state.value.aerialSourceLoading) return
        state.value = state.value.copy(
            aerialSourceDialogVisible = false,
            aerialSourceConfirm = AerialSourceKind.Github,
            aerialSourceError = null,
        )
    }

    /** 加载 GitHub 官方版列表：获取 Release APK 资产后选择下载安装。 */
    private fun loadGithubAerialOptions() {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        if (state.value.aerialSourceLoading) return
        state.value = state.value.copy(
            aerialSourceLoading = true,
            aerialSourceError = null,
        )
        viewModelScope.launch {
            when (val result = downloadManager.fetchLatestReleaseApks(GithubReleaseUrl)) {
                is ReleaseFetchResult.Success -> {
                    val options = result.assets.map { AerialFileOption.Github(it) }
                    state.value = state.value.copy(aerialSourceLoading = false)
                    showOrStartAerialOptions(options)
                }
                is ReleaseFetchResult.Failure -> {
                    state.value = state.value.copy(
                        aerialSourceLoading = false,
                        aerialSourceError = result.message,
                    )
                }
            }
        }
    }

    fun onAerialFilesDialogDismiss() {
        state.value = state.value.copy(
            aerialFilesDialogVisible = false,
            aerialCandidateFiles = emptyList(),
        )
    }

    /** 选中某个远程 Aerial 文件：NAS 先换下载地址，随后统一下载 → 安装。 */
    fun onAerialFileSelected(option: AerialFileOption) {
        if (state.value.aerialInstallStatus is OperationStatus.Running) return
        state.value = state.value.copy(
            aerialFilesDialogVisible = false,
            aerialCandidateFiles = emptyList(),
        )
        when (option) {
            is AerialFileOption.Nas -> {
                val taskId = ++generation
                viewModelScope.launch {
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Running(
                                appString(R.string.aerial_preparing_download, option.fileName)
                            ),
                    )
                    when (
                        val result =
                            fnosShareClient.fetchDownloadUrl(
                                file = option.file,
                                token = option.token,
                                cookie = option.cookie,
                            )
                    ) {
                        is NasDownloadResult.Success -> {
                            if (generation != taskId) return@launch
                            startRemoteDownload(
                                fileName = option.fileName,
                                url = result.downloadUrl,
                                cookie = result.cookie,
                            )
                        }
                        is NasDownloadResult.Failure -> {
                            if (generation != taskId) return@launch
                            state.value = state.value.copy(
                                aerialInstallStatus =
                                    OperationStatus.Failed(
                                        result.message,
                                        result.suggestion
                                            ?: appString(R.string.download_failed_generic_suggestion),
                                    ),
                            )
                        }
                    }
                }
            }
            is AerialFileOption.Github ->
                startRemoteDownload(
                    fileName = option.fileName,
                    url = option.asset.downloadUrl,
                    cookie = null,
                )
        }
    }

    /** 远程下载（NAS 带 Cookie），实时进度。 */
    private fun startRemoteDownload(
        fileName: String,
        url: String,
        cookie: String?,
    ) {
        val taskId = ++generation
        downloadJob?.cancel()
        state.value = state.value.copy(
            aerialInstallStatus =
                OperationStatus.Running(appString(R.string.aerial_download_starting, fileName)),
        )
        downloadJob = viewModelScope.launch {
            val headers = cookie?.let { mapOf("Cookie" to it) } ?: emptyMap()
            val result =
                downloadManager.download(url, fileName, headers) { task ->
                    if (generation != taskId) return@download
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Running(task.message, task.progress),
                    )
                }
            if (generation != taskId) return@launch
            when (result) {
                is DownloadResult.Success -> installDownloadedApk(result)
                is DownloadResult.Failure -> {
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
                DownloadResult.Canceled -> {
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Failed(
                                appString(R.string.download_canceled),
                                appString(R.string.download_canceled_hint),
                            ),
                    )
                }
            }
        }
    }

    /** 下载完成后推送到目标设备并安装，实时进度；签名冲突时自动卸载旧应用后重装。 */
    private suspend fun installDownloadedApk(result: DownloadResult.Success) {
        val file = File(result.localPath)
        val transferCallback: (Long, Long) -> Unit = { transferred, total ->
            val status =
                adbTransferRunning(
                    transferringLabel =
                        appString(R.string.common_transferring_arg, result.fileName),
                    finishingLabel =
                        appString(R.string.common_installing_arg, result.fileName),
                    transferred = transferred,
                    total = total,
                )
            state.value = state.value.copy(aerialInstallStatus = status)
        }
        try {
            state.value = state.value.copy(
                aerialInstallStatus =
                    OperationStatus.Running(appString(R.string.download_complete_installing_apk)),
            )
            val firstInstall = adbRepository.install(file, transferCallback)
            val installResult =
                if (firstInstall is AdbOperationResult.Failure &&
                    isSignatureConflict(firstInstall.message)
                ) {
                    // 签名不一致无法覆盖安装：先卸载电视上的对应应用，再重新安装。
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Running(
                                appString(R.string.aerial_signature_conflict_uninstalling)
                            ),
                    )
                    when (
                        val uninstall =
                            adbRepository.uninstall(AERIAL_PACKAGE)
                    ) {
                        is AdbOperationResult.Success -> adbRepository.install(file, transferCallback)
                        is AdbOperationResult.Failure ->
                            AdbOperationResult.Failure(
                                message = appString(R.string.aerial_signature_conflict_uninstall_failed),
                                suggestion = uninstall.message,
                            )
                    }
                } else {
                    firstInstall
                }
            when (installResult) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Success(appString(R.string.common_apk_install_success)),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        aerialInstallStatus =
                            OperationStatus.Failed(installResult.message, installResult.suggestion),
                    )
                }
            }
        } finally {
            file.delete()
        }
    }

    /** 判断安装失败信息是否属于签名不一致（无法覆盖安装）。 */
    private fun isSignatureConflict(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
            text.contains("INCONSISTENT_CERTIFICATES", ignoreCase = true) ||
            text.contains("signatures do not match", ignoreCase = true)
    }

    private fun showOrStartAerialOptions(options: List<AerialFileOption>) {
        when {
            options.isEmpty() -> {
                state.value = state.value.copy(
                    aerialSourceError = appString(R.string.aerial_no_apk_found),
                )
            }
            options.size == 1 -> onAerialFileSelected(options.first())
            else -> {
                state.value = state.value.copy(
                    aerialFilesDialogVisible = true,
                    aerialCandidateFiles = options,
                )
            }
        }
    }

    private companion object {
        const val NasShareUrl = "http://192.168.5.218:5666/s/41412d18807c4e8499"
        const val GithubReleaseUrl = "https://github.com/pengchujin/aerial-tv/releases/latest"
        const val CustomGithubReleaseUrl = "https://github.com/cplzsy/aerial-tv-1.78adb/releases/latest"
        /** 目标应用包名：签名冲突时先卸载它再重装。 */
        const val AERIAL_PACKAGE = "app.orchard.tv"
    }
}
