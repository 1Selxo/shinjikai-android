package com.shinjikai.dictionary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: Int,
    val primaryWriting: String,
    val kana: String,
    val meaningSummary: String,
    val detailsJson: String? = null,
    val detailsSavedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
