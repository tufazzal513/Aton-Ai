package com.atonai.data.model

/** A file inside the app's private workspace. */
data class WorkspaceFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)