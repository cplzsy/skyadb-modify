package com.sky22333.skyadb.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbPrivilegeCommandsTest {

    @Test
    fun grantCommandsContainsAllThirteenEntries() {
        assertEquals(13, AdbPrivilegeCommands.grantCommands.size)
    }

    @Test
    fun grantCommandsAllTargetOrchardTv() {
        AdbPrivilegeCommands.grantCommands.forEach { command ->
            assertEquals(true, command.contains("app.orchard.tv"))
        }
    }

    @Test
    fun appendsWithColonWhenOriginalPresent() {
        val result = AdbPrivilegeCommands.buildAccessibilityServicesValue("com.example.Service")
        assertEquals(
            "com.example.Service:app.orchard.tv/app.orchard.tv.guard.HomeGuardService",
            result,
        )
    }

    @Test
    fun usesOnlyNewComponentWhenOriginalBlank() {
        assertEquals(
            "app.orchard.tv/app.orchard.tv.guard.HomeGuardService",
            AdbPrivilegeCommands.buildAccessibilityServicesValue(""),
        )
    }

    @Test
    fun usesOnlyNewComponentWhenOriginalIsNullLiteral() {
        assertEquals(
            "app.orchard.tv/app.orchard.tv.guard.HomeGuardService",
            AdbPrivilegeCommands.buildAccessibilityServicesValue("null"),
        )
    }

    @Test
    fun trimsOriginalBeforeAppending() {
        assertEquals(
            "com.example.Service:app.orchard.tv/app.orchard.tv.guard.HomeGuardService",
            AdbPrivilegeCommands.buildAccessibilityServicesValue("  com.example.Service  "),
        )
    }

    @Test
    fun displayCommandAddsAdbShellPrefix() {
        assertEquals(
            "adb shell pm grant app.orchard.tv android.permission.ACCESS_FINE_LOCATION",
            AdbPrivilegeCommands.displayCommand(
                "pm grant app.orchard.tv android.permission.ACCESS_FINE_LOCATION",
            ),
        )
    }
}
