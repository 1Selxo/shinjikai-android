package com.shinjikai.dictionary.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.ichi2.anki.api.AddContentApi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
const val ANKIDROID_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
private const val SHINJIKAI_DECK_NAME = "Shinjikai"
private const val SHINJIKAI_MODEL_NAME = "com.shinjikai.dictionary.dark.v5"
private val SHINJIKAI_MODEL_FIELDS = arrayOf("Expression", "Reading", "Meaning", "Example", "Audio")
private val SHINJIKAI_CARD_NAMES = arrayOf("Meaning")
private val SHINJIKAI_QUESTION_TEMPLATES = arrayOf(
    """
    <div class="screen">
      <div class="card-shell">
        <div class="expression">{{Expression}}</div>
        {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
      </div>
    </div>
    """.trimIndent()
)
private val SHINJIKAI_ANSWER_TEMPLATES = arrayOf(
    """
    <div class="screen">
      <div class="card-shell">
        {{#Reading}}<div class="reading">{{Reading}}</div>{{/Reading}}
        <div class="expression">{{Expression}}</div>
        {{#Audio}}<div class="audio">{{Audio}}</div>{{/Audio}}
      </div>
    </div>
    <div class="answer-divider"></div>
    <div class="meaning-shell">
      <div class="section-label">المعنى</div>
      <div class="meaning">{{Meaning}}</div>
      {{#Example}}
      <div class="example-block">
        <div class="section-label">مثال</div>
        <div class="example">{{Example}}</div>
      </div>
      {{/Example}}
    </div>
    """.trimIndent()
)
private const val SHINJIKAI_CARD_CSS = """
    html, body, #qa, #answer, .card, .night_mode, .nightMode {
      margin: 0;
      padding: 0;
      background: #05070b !important;
      color: #e8eaed !important;
    }
    .card {
      padding: 24px 16px;
      min-height: 100vh;
      box-sizing: border-box;
      font-family: "Noto Sans Arabic", "Noto Sans JP", sans-serif;
    }
    .screen {
      max-width: 640px;
      margin: 0 auto;
    }
    .card-shell,
    .meaning-shell {
      background: #171b22;
      border: 1px solid #222733;
      border-radius: 24px;
      padding: 24px 20px;
      box-shadow: 0 16px 48px rgba(0, 0, 0, 0.28);
      text-align: center;
    }
    .expression {
      font-size: 38px;
      font-weight: 700;
      line-height: 1.25;
      color: #ffffff;
    }
    .reading {
      font-size: 23px;
      color: #8ab4f8;
      margin-bottom: 14px;
      letter-spacing: 0.03em;
    }
    .audio {
      margin-top: 16px;
    }
    .replaybutton {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 52px;
      height: 52px;
      border-radius: 999px;
      background: #2a5ea8;
      text-decoration: none;
      box-shadow: 0 8px 18px rgba(0, 0, 0, 0.25);
    }
    .replaybutton svg {
      width: 24px;
      height: 24px;
      fill: #ffffff;
    }
    .section-label {
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0.12em;
      color: #80cbc4;
      margin-top: 16px;
    }
    .answer-divider {
      width: 72px;
      height: 3px;
      border-radius: 999px;
      margin: 18px auto;
      background: linear-gradient(90deg, #2a5ea8 0%, #80cbc4 100%);
    }
    .meaning {
      margin-top: 14px;
      font-size: 26px;
      line-height: 1.9;
      direction: rtl;
      text-align: right;
      white-space: pre-wrap;
      color: #e8eaed;
    }
    .example-block {
      margin-top: 22px;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
    }
    .example {
      margin-top: 12px;
      font-size: 22px;
      line-height: 1.7;
      white-space: pre-wrap;
      color: #dce4f0;
    }
"""

data class AnkiNoteContent(
    val expression: String,
    val reading: String,
    val meaning: String,
    val example: String,
    val speechText: String
) {
    fun fields(audio: String = ""): Array<String> = arrayOf(expression, reading, meaning, example, audio)

    val shareSubject: String
        get() = expression

    val shareBody: String
        get() = buildString {
            append(expression)
            if (reading.isNotBlank()) {
                append("\n")
                append(reading)
            }
            append("\n\n")
            append(meaning)
            if (example.isNotBlank()) {
                append("\n\n")
                append(example)
            }
        }.trim()
}

sealed interface AnkiAddResult {
    data class Added(val hasAudio: Boolean) : AnkiAddResult
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

    fun loadDeckNames(context: Context): List<String> {
        if (!isAnkiDroidInstalled(context)) return emptyList()
        if (!canRequestDirectAdd(context)) return emptyList()
        if (!hasDatabasePermission(context)) return emptyList()
        return AddContentApi(context).deckList.values
            .filter { it.isNotBlank() }
            .sorted()
    }

    suspend fun addNote(
        context: Context,
        note: AnkiNoteContent,
        deckName: String = SHINJIKAI_DECK_NAME,
        textToSpeech: TextToSpeech? = null,
        canSpeakJapanese: Boolean = false
    ): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled
        if (!canRequestDirectAdd(context)) return shareToAnki(context, note)
        if (!hasDatabasePermission(context)) return AnkiAddResult.PermissionRequired

        val audioField = if (canSpeakJapanese && textToSpeech != null && note.speechText.isNotBlank()) {
            synthesizeAudioField(
                context = context,
                note = note,
                textToSpeech = textToSpeech
            )
        } else {
            ""
        }

        return runCatching {
            val api = AddContentApi(context)
            val normalizedDeckName = deckName.trim().ifBlank { SHINJIKAI_DECK_NAME }
            val deckId = api.deckList.entries.firstOrNull { it.value == normalizedDeckName }?.key
                ?: api.addNewDeck(normalizedDeckName)
            val modelId = api.modelList.entries.firstOrNull { it.value == SHINJIKAI_MODEL_NAME }?.key
                ?: api.addNewCustomModel(
                    SHINJIKAI_MODEL_NAME,
                    SHINJIKAI_MODEL_FIELDS,
                    SHINJIKAI_CARD_NAMES,
                    SHINJIKAI_QUESTION_TEMPLATES,
                    SHINJIKAI_ANSWER_TEMPLATES,
                    SHINJIKAI_CARD_CSS,
                    null,
                    null
                )

            if (deckId <= 0L || modelId <= 0L) {
                shareToAnki(context, note)
            } else {
                val noteId = api.addNote(modelId, deckId, note.fields(audioField), null)
                if (noteId > 0L) AnkiAddResult.Added(hasAudio = audioField.isNotBlank()) else shareToAnki(context, note)
            }
        }.getOrElse {
            if (it is SecurityException) {
                AnkiAddResult.PermissionRequired
            } else {
                shareToAnki(context, note)
            }
        }
    }

    private suspend fun synthesizeAudioField(
        context: Context,
        note: AnkiNoteContent,
        textToSpeech: TextToSpeech
    ): String = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "anki-audio-${UUID.randomUUID()}.wav")
        val importedName = try {
            val synthResult = synthesizeToFile(
                textToSpeech = textToSpeech,
                text = note.speechText,
                destination = tempFile,
                utteranceId = "anki-audio-${UUID.randomUUID()}"
            )
            if (!synthResult || !tempFile.exists() || tempFile.length() == 0L) {
                ""
            } else {
                val api = AddContentApi(context)
                api.addMediaFromUri(
                    Uri.fromFile(tempFile),
                    tempFile.name,
                    "audio/wav"
                ).orEmpty()
            }
        } finally {
            tempFile.delete()
        }

        if (importedName.isBlank()) "" else "[sound:$importedName]"
    }

    private suspend fun synthesizeToFile(
        textToSpeech: TextToSpeech,
        text: String,
        destination: File,
        utteranceId: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(completedUtteranceId: String?) {
                if (completedUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(errorUtteranceId: String?) {
                if (errorUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(false)
                }
            }

            override fun onError(errorUtteranceId: String?, errorCode: Int) {
                if (errorUtteranceId == utteranceId && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(listener)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = textToSpeech.synthesizeToFile(text, params, destination, utteranceId)
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resume(false)
        }
    }

    fun shareToAnki(context: Context, note: AnkiNoteContent): AnkiAddResult {
        if (!isAnkiDroidInstalled(context)) return AnkiAddResult.AnkiNotInstalled

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = ANKIDROID_PACKAGE
            putExtra(Intent.EXTRA_SUBJECT, note.shareSubject)
            putExtra(Intent.EXTRA_TEXT, note.shareBody)
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
