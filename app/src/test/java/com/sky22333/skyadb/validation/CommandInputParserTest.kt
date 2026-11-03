package com.sky22333.skyadb.validation

import com.sky22333.skyadb.validation.CommandInputParser.Error
import com.sky22333.skyadb.validation.CommandInputParser.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandInputParserTest {
    private fun ready(input: String, output: String, fastboot: Boolean = false) {
        assertEquals(input, Result.Ready(output), CommandInputParser.parse(input, fastboot))
    }

    private fun rejected(input: String, error: Error, fastboot: Boolean = false) {
        assertEquals(input, Result.Rejected(error), CommandInputParser.parse(input, fastboot))
    }

    @Test
    fun stripsOnlyTheLeadingAdbExecutableAndOptionalShell() {
        ready("  adb shell getprop ro.product.model  ", "getprop ro.product.model")
        ready("adb\tshell\tpm list packages", "pm list packages")
        ready("adb.exe shell id", "id")
        ready("adb reboot bootloader", "reboot bootloader")
        ready("adb getprop ro.product.model", "getprop ro.product.model")
    }

    @Test
    fun preservesShellArgumentsQuotesOperatorsAndEmbeddedPrefixes() {
        val shell = "printf '%s' 'adb fastboot' && echo \"a  b\" | cat"
        ready(shell, shell)
        ready("adb shell $shell", shell)
        ready("adbd --help", "adbd --help")
        ready("fastboot-helper", "fastboot-helper")
        ready("echo adb shell id", "echo adb shell id")
        ready("adb shell adb version", "adb version") // Never recursively strip payload.
    }

    @Test
    fun rejectsEmptyCommandsAfterPrefixRemoval() {
        listOf("", "  ", "adb", "adb.exe", "adb shell").forEach { rejected(it, Error.Empty) }
        rejected("fastboot", Error.Empty, true)
    }

    @Test
    fun rejectsMismatchedTransportInsteadOfExecutingAnotherOperation() {
        rejected("fastboot reboot", Error.WrongMode)
        rejected("adb reboot bootloader", Error.WrongMode, true)
    }

    @Test
    fun distinguishesHostEnumerationFromTargetCommands() {
        rejected("adb devices", Error.Devices)
        rejected("adb devices -l", Error.Devices)
        rejected("fastboot devices", Error.Devices, true)
        rejected("devices", Error.Devices, true)
        ready("adb shell devices", "devices") // Explicit target command remains untouched.
    }

    @Test
    fun doesNotSilentlyIgnoreHostDeviceSelectionOrShellOptions() {
        rejected("adb -s another-device shell reboot", Error.ClientOptions)
        rejected("fastboot -s another-device reboot", Error.ClientOptions, true)
        rejected("adb shell -t id", Error.ClientOptions)
        ready("adb shell echo -n hello", "echo -n hello")
    }

    @Test
    fun rejectsDesktopAdbOperationsButPreservesRawTargetCommands() {
        listOf("adb push a b", "adb install app.apk", "adb connect 1.2.3.4", "adb wait-for-device")
            .forEach { rejected(it, Error.HostCommand) }
        ready("pm uninstall example.app", "pm uninstall example.app")
        ready("adb shell pm list packages", "pm list packages")
    }

    @Test
    fun translatesFastbootCliUsingLibraryCommandBuilders() {
        ready("fastboot getvar product", "getvar:product", true)
        ready("fastboot.exe\tgetvar\tpartition-size:boot", "getvar:partition-size:boot", true)
        ready("getvar current-slot", "getvar:current-slot", true)
        ready("fastboot erase cache", "erase:cache", true)
        ready("fastboot set_active b", "set_active:b", true)
        ready("fastboot reboot", "reboot", true)
        ready("fastboot reboot bootloader", "reboot-bootloader", true)
        ready("fastboot reboot fastboot", "reboot-fastboot", true)
        ready("fastboot reboot recovery", "reboot-recovery", true)
    }

    @Test
    fun preservesFastbootProtocolAndOemCommands() {
        listOf(
                "getvar:product",
                "reboot",
                "reboot-bootloader",
                "oem device-info",
                "flashing get_unlock_ability",
            )
            .forEach {
                ready(it, it, true)
                ready("fastboot $it", it, true)
            }
    }

    @Test
    fun rejectsUnsupportedFileWorkflowsAndInvalidFastbootArguments() {
        listOf("fastboot flash boot boot.img", "fastboot boot boot.img", "fastboot update rom.zip")
            .forEach { rejected(it, Error.FileTransfer, true) }
        listOf("fastboot getvar", "getvar product extra", "reboot system", "getvar product\nreboot")
            .forEach { rejected(it, Error.FastbootSyntax, true) }
        rejected("fastboot --version", Error.ClientOptions, true)
    }
}
