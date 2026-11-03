package com.sky22333.skyadb.validation

/** Untrusted names must never introduce path components or control characters. */
object FileNamePolicy {
    fun sanitize(value: String): String =
        value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .map { if (it.code < 32 || it in ":*?\"<>|") '_' else it }
            .joinToString("")
            .trim()
            .let { name ->
                var end = name.length.coerceAtMost(180)
                while (
                    end > 0 && name.substring(0, end).toByteArray(Charsets.UTF_8).size > 240
                ) end--
                if (end > 0 && name[end - 1].isHighSurrogate()) end--
                name.substring(0, end)
            }
            .takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "download"

    fun isValid(value: String): Boolean =
        value.isNotBlank() &&
            value != "." &&
            value != ".." &&
            value.none { it == '/' || it == '\\' || it.code < 32 }
}
