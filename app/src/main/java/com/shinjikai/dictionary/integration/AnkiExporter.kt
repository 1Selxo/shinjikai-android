package com.shinjikai.dictionary.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ichi2.anki.api.AddContentApi

private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
const val ANKIDROID_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
private const val SHINJIKAI_DECK_NAME = "Shinjikai"
private const val SHINJIKAI_MODEL_NAME = "com.shinjikai.dictionary.basic"

data class AnkiNoteContent(
    val front: String,
    val back: String
)

sealed interface AnkiAddResult {
    data object Added : AnkiAddResult
    data object PermissionRequired : AnkiAddResult
    data object OpenedShareFallback : AnkiAddResult
    data object AnkiNotInstalled : AnkiAddResult
    data object Failed : AnkiAddResult
}

object AnkiExporter {
    fun canRequestDirectAdd(context: Context): Boolean =
        AddContentApi.getAnkiDroidPackageName(context) != null

    fun hasDatabasePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, ANKIDROID_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun addNote(context: Context, note: AnkiNoteContent): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled
        if (!canRequestDirectAdd(context)) return shareToAnki(context, note)
        if (!hasDatabasePermission(context)) return AnkiAddResult.PermissionRequired

        return runCatching {
            val api = AddContentApi(context)
            val deckId = api.deckList.entries.firstOrNull { it.value == SHINJIKAI_DECK_NAME }?.key
                ?: api.addNewDeck(SHINJIKAI_DECK_NAME)
            val modelId = api.modelList.entries.firstOrNull { it.value == SHINJIKAI_MODEL_NAME }?.key
                ?: api.addNewBasicModel(SHINJIKAI_MODEL_NAME)

            if (deckId <= 0L || modelId <= 0L) {
                shareToAnki(context, note)
            } else {
                val noteId = api.addNote(modelId, deckId, arrayOf(note.front, note.back), null)
                if (noteId > 0L) AnkiAddResult.Added else shareToAnki(context, note)
            }
        }.getOrElse {
            if (it is SecurityException) {
                AnkiAddResult.PermissionRequired
            } else {
                shareToAnki(context, note)
            }
        }
    }

    fun shareToAnki(context: Context, note: AnkiNoteContent): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = ANKIDROID_PACKAGE
            putExtra(Intent.EXTRA_SUBJECT, note.front)
            putExtra(Intent.EXTRA_TEXT, note.back)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(shareIntent)
            AnkiAddResult.OpenedShareFallback
        } catch (_: ActivityNotFoundException) {
            AnkiAddResult.AnkiNotInstalled
        } catch (_: SecurityException) {
            AnkiAddResult.Failed
        }
    }

    private fun isAnkiDroidInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ANKIDROID_PACKAGE, 0)
        true
    }.getOrDefault(false)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
