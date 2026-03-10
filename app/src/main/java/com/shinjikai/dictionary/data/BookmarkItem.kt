package com.shinjikai.dictionary.data

data class BookmarkItem(
    val item: SearchItem,
    val createdAt: Long
) {
    val id: Int
        get() = item.id
}