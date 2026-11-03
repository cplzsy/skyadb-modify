package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.rv882.fastbootjava.FastbootCommand
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.model.operationCatching
import com.sky22333.skyadb.usb.AndroidUsbInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FastbootOtgManager {
    private val mutex = Mutex()
    private var channel: FastbootChannel? = null
    @Volatile private var activeDeviceName: String? = null

    suspend fun connect(usbManager: UsbManager, device: UsbDevice): AdbOperationResult<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                disconnectLocked()
                val usbInterface =
                    AndroidUsbInterface.findFastbootInterface(device)
                        ?: return@withLock AdbOperationResult.Failure(
                            appString(R.string.fastboot_interface_not_found),
                            appString(R.string.fastboot_interface_suggestion),
                        )
                val connection =
                    usbManager.openDevice(device)
                        ?: return@withLock AdbOperationResult.Failure(
                            appString(R.string.usb_cannot_open_device),
                            appString(R.string.usb_replug_allow_access_suggestion),
                        )
                try {
                    val opened =
                        FastbootUsbChannel(connection, usbInterface) {
                            usbManager.deviceList.containsKey(device.deviceName)
                        }
                    channel = opened
                    val context = currentCoroutineContext()
                    // FAIL is a valid terminal probe reply (some devices omit product).
                    FastbootProtocol(opened).execute(
                        FastbootCommand.getVar("product").toString(),
                        3000,
                    ) {
                        context.ensureActive()
                    }
                    activeDeviceName = device.deviceName
                    AdbOperationResult.Success("fastboot:${device.deviceName}")
                } catch (error: Exception) {
                    disconnectLocked()
                    connection.close()
                    if (error is CancellationException) throw error
                    AdbOperationResult.Failure(
                        appString(R.string.fastboot_connect_failed),
                        appString(R.string.fastboot_connect_failed_suggestion),
                        error,
                    )
                }
            }
        }

    suspend fun sendCommand(command: String): AdbOperationResult<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val opened = channel ?: return@withLock notConnected()
                val context = currentCoroutineContext()
                try {
                    operationCatching {
                        val reply =
                            FastbootProtocol(opened).execute(command.trim()) {
                                context.ensureActive()
                            }
                        if (
                            command == "reboot" ||
                                command.startsWith("reboot-") ||
                                command == "continue"
                        ) {
                            if (reply.successful) disconnectLocked()
                        }
                        if (reply.successful) AdbOperationResult.Success(reply.output)
                        else
                            AdbOperationResult.Failure(
                                appString(R.string.fastboot_command_failed),
                                reply.value.ifBlank {
                                    appString(R.string.fastboot_command_failed_suggestion)
                                },
                                output = reply.output,
                            )
                    }
                        .getOrElse { error ->
                            if (error is FastbootExchangeException) disconnectLocked()
                            AdbOperationResult.Failure(
                                appString(R.string.fastboot_command_failed),
                                error.message
                                    ?: appString(R.string.fastboot_command_failed_suggestion),
                                error,
                                (error as? FastbootExchangeException)?.output,
                            )
                        }
                } catch (error: CancellationException) {
                    disconnectLocked()
                    throw error
                }
            }
        }

    suspend fun readSummary(): AdbOperationResult<FastbootDeviceSummary> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val opened = channel ?: return@withLock notConnected()
                val context = currentCoroutineContext()
                try {
                    operationCatching {
                        val protocol = FastbootProtocol(opened)
                        val values =
                            FastbootDeviceSummary.Variables.associateWith { variable ->
                                val reply =
                                    protocol.execute(
                                        FastbootCommand.getVar(variable).toString(),
                                        3000,
                                    ) {
                                        context.ensureActive()
                                    }
                                reply.value.takeIf { reply.successful && it.isNotBlank() }
                            }
                        AdbOperationResult.Success(FastbootDeviceSummary(values))
                    }
                        .getOrElse { error ->
                            disconnectLocked()
                            AdbOperationResult.Failure(
                                appString(R.string.fastboot_summary_failed),
                                error.message
                                    ?: appString(R.string.fastboot_connect_failed_suggestion),
                                error,
                                (error as? FastbootExchangeException)?.output,
                            )
                        }
                } catch (error: CancellationException) {
                    disconnectLocked()
                    throw error
                }
            }
        }

    private fun notConnected() =
        AdbOperationResult.Failure(
            appString(R.string.fastboot_not_connected),
            appString(R.string.fastboot_connect_via_usb_otg_suggestion),
        )

    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { disconnectLocked() } }

    private fun disconnectLocked() {
        runCatching { channel?.close() }
        channel = null
        activeDeviceName = null
    }

    fun currentDeviceName(): String? = activeDeviceName
}
