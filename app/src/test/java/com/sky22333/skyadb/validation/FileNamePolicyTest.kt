package com.sky22333.skyadb.validation

import org.junit.Assert.*
import org.junit.Test

class FileNamePolicyTest {
    @Test
    fun serverNamesCannotEscapeDownloadDirectory() {
        assertEquals("payload.apk", FileNamePolicy.sanitize("../../files/adb_identity/payload.apk"))
        assertEquals("payload.apk", FileNamePolicy.sanitize("..\\..\\payload.apk"))
        assertEquals("download", FileNamePolicy.sanitize(".."))
        assertEquals("download", FileNamePolicy.sanitize("/"))
    }

    @Test
    fun filenamesRejectTraversalAndControlCharacters() {
        for (name in listOf(".", "..", "../app.apk", "a/b", "a\\b", "a\u0000b", "a\nb")) {
            assertFalse(name, FileNamePolicy.isValid(name))
        }
        assertTrue(FileNamePolicy.isValid("中文应用 1.apk"))
        assertEquals("中文应用.apk", FileNamePolicy.sanitize("中文应用.apk"))
    }

    @Test
    fun longUnicodeNamesRespectFilesystemByteLimits() {
        val name = FileNamePolicy.sanitize("你".repeat(300))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
        assertFalse(name.contains('\uFFFD'))
    }
}
