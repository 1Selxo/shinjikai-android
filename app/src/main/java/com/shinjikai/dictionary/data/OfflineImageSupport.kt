package com.shinjikai.dictionary.data

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.io.File

const val OFFLINE_IMAGE_DIR_META_KEY = "offline_image_dir"

internal fun WordDetailsResponse.withResolvedOfflineImages(imageDirectory: File?): WordDetailsResponse {
    if (imageDirectory == null) return this
    return copy(
        word = word.copy(
            meanings = word.meanings.map { meaning ->
                meaning.copy(
                    pictures = meaning.pictures.mapNotNull { picture ->
                        resolveStoredPictureElement(picture, imageDirectory)
                    }
                )
            }
        )
    )
}

internal fun resolveStoredPictureElement(
    picture: JsonElement,
    imageDirectory: File?
): JsonElement? {
    if (picture.isJsonNull) return null
    if (picture.isJsonPrimitive) {
        val value = picture.asString.trim()
        if (value.isBlank()) return null
        return JsonPrimitive(resolveOfflineImagePath(value, imageDirectory))
    }
    if (!picture.isJsonObject) return picture

    val obj = picture.asJsonObject
    val value = sequenceOf("Filename", "FileName", "filename", "Url", "url", "Src", "src", "Path", "path")
        .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return picture

    return JsonPrimitive(resolveOfflineImagePath(value, imageDirectory))
}

internal fun resolveOfflineImagePath(reference: String, imageDirectory: File?): String {
    val normalized = reference.replace('\\', '/').trim()
    if (normalized.isBlank()) return normalized
    if (normalized.matches(Regex("""^[A-Za-z]:/.*"""))) return normalized
    if (normalized.startsWith("file:/", ignoreCase = true)) return normalized
    if (normalized.startsWith("https://", ignoreCase = true)) return normalized
    if (normalized.startsWith("http://", ignoreCase = true)) return normalized
    if (normalized.startsWith("//")) return normalized
    if (normalized.startsWith("/")) return normalized
    return imageDirectory?.resolve(normalized)?.absolutePath?.replace('\\', '/') ?: normalized
}
