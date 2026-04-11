package com.shinjikai.dictionary.data

import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

internal enum class OfflineArchiveKind {
    ZIP,
    TAR,
    TAR_XZ
}

internal fun detectOfflineArchiveKind(fileName: String): OfflineArchiveKind? {
    val normalized = fileName.trim().lowercase(Locale.ROOT)
    return when {
        normalized.endsWith(".tar.xz") || normalized.endsWith(".txz") -> OfflineArchiveKind.TAR_XZ
        normalized.endsWith(".tar") -> OfflineArchiveKind.TAR
        normalized.endsWith(".zip") -> OfflineArchiveKind.ZIP
        else -> null
    }
}

internal fun extractOfflineArchive(
    archiveFile: File,
    targetDir: File
) {
    archiveFile.inputStream().buffered().use { input ->
        when (detectOfflineArchiveKind(archiveFile.name)) {
            OfflineArchiveKind.ZIP -> extractZipStream(input, targetDir)
            OfflineArchiveKind.TAR -> extractTarStream(input, targetDir)
            OfflineArchiveKind.TAR_XZ -> extractTarXzStream(input, targetDir)
            null -> error("Unsupported archive: ${archiveFile.name}")
        }
    }
}

internal data class OfflineImportPayload(
    val jsonlFile: File? = null,
    val extractedImagesDir: File? = null,
    val yomitanDirectory: File? = null
)

internal fun findOfflineImportPayload(rootDir: File): OfflineImportPayload {
    val jsonlFile = rootDir
        .walkTopDown()
        .filter { it.isFile && it.name.equals("raw_shinjikai_data.jsonl", ignoreCase = true) }
        .minByOrNull { it.absolutePath.length }
    val extractedImagesDir = rootDir
        .walkTopDown()
        .filter { it.isDirectory && it.name.equals("yomitan_images", ignoreCase = true) }
        .minByOrNull { it.absolutePath.length }
    val yomitanDirectory = rootDir
        .walkTopDown()
        .filter { file ->
            file.isFile &&
                file.name.lowercase().startsWith("term_bank_") &&
                file.extension.equals("json", ignoreCase = true)
        }
        .map { it.parentFile }
        .firstOrNull()

    return OfflineImportPayload(
        jsonlFile = jsonlFile,
        extractedImagesDir = extractedImagesDir,
        yomitanDirectory = yomitanDirectory
    )
}

internal fun extractZipStream(
    input: InputStream,
    targetDir: File
) {
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val outFile = safeResolveArchiveEntry(targetDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output ->
                    zip.copyTo(output)
                }
            }
            zip.closeEntry()
        }
    }
}

internal fun extractTarStream(
    input: InputStream,
    targetDir: File
) {
    TarArchiveInputStream(input).use { tar ->
        while (true) {
            val entry = tar.nextEntry ?: break
            val outFile = safeResolveArchiveEntry(targetDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output ->
                    tar.copyTo(output)
                }
            }
        }
    }
}

internal fun extractTarXzStream(
    input: InputStream,
    targetDir: File
) {
    XZCompressorInputStream(input).use { xz ->
        extractTarStream(xz, targetDir)
    }
}

internal fun safeResolveArchiveEntry(rootDir: File, entryName: String): File {
    val candidate = File(rootDir, entryName)
    val rootPath = rootDir.canonicalPath
    val candidatePath = candidate.canonicalPath
    require(candidatePath.startsWith(rootPath)) { "Invalid archive entry path: $entryName" }
    return candidate
}
