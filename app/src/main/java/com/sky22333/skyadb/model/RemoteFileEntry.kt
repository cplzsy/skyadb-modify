package com.sky22333.skyadb.model

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)
