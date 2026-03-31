package com.shinjikai.dictionary.data

data class AppUpdateMetadata(
    val latestVersionCode: Long = 0L,
    val latestVersionName: String = "",
    val required: Boolean = false,
    val title: String? = null,
    val notes: String? = null,
    val url: String? = null
)

data class AppUpdateInfo(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val required: Boolean,
    val title: String,
    val notes: String,
    val url: String
)
