package com.shinjikai.dictionary

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.shinjikai.dictionary.data.Meaning
import com.shinjikai.dictionary.data.RelatedWordItem
import com.shinjikai.dictionary.data.SentenceExample
import com.shinjikai.dictionary.ui.DetailUiState
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.ShinjikaiViewModel
import java.text.DateFormat
import java.util.Date

private data class CategoryChipModel(val id: Int, val label: String)
private data class MeaningEntry(val definition: String, val note: String)
private data class GlossaryReference(val id: Int, val label: String)
private data class DefinitionContent(val text: String, val references: List<GlossaryReference>)

private const val RELATED_WORDS_PAGE_SIZE = 5
private const val EXAMPLES_PAGE_SIZE = 3
private val MEANING_BULLET_PREFIX_REGEX =
    Regex("(?m)^\\s*[\\uD83D\\uDD39\\u25AA\\u2022\\u25CF\\u25E6]\\s*")
private val MEANING_MULTISPACE_REGEX = Regex("""[ \t]{2,}""")
private val MEANING_EMPTY_BRACES_REGEX = Regex("""\{\s*\}""")
private val MEANING_TRAILING_SPACES_REGEX = Regex("""(?m)^\s+$""")
private val MEANING_CONTROL_MARKS_REGEX = Regex("""[\u200E\u200F\u202A-\u202E\u2066-\u2069]""")
private val MEANING_ARABIC_SEMICOLON_REGEX = Regex("""\s*؛\s*""")
private val MEANING_PAREN_BAR_REGEX = Regex("""\(\|\s*(.*?)\s*\|\)""")
private val MEANING_INLINE_TAG_REGEX =
    Regex("""\[\{\s*([^:{}\[\]]+)\s*:\s*([^{}\[\]]+)\s*\}\]|\{\s*([^:{}\[\]]+)\s*:\s*([^{}\[\]]+)\s*\}""")
private val GLOSSARY_REFERENCE_REGEX = Regex("""\{([^:{}]+):(\d+)\}""")
private val JAPANESE_NUMERIC_REFERENCE_REGEX =
    Regex("""([\p{IsHan}\p{IsHiragana}\p{IsKatakana}ー々ヶ]+)\s*:\s*\d+""")
private val API_IMAGE_FILENAME_REGEX = Regex("""(?i)^[^/\\?#]+\.(png|jpe?g|webp|gif|bmp|svg)$""")

@Composable
fun DetailScreenBody(
    modifier: Modifier = Modifier,
    useOfflineMode: Boolean,
    detailState: DetailUiState,
    canSpeakJapanese: Boolean,
    textToSpeech: TextToSpeech?,
    context: Context,
    clipboardManager: ClipboardManager,
    focusManager: androidx.compose.ui.focus.FocusManager,
    viewModel: ShinjikaiViewModel
) {
    val item = detailState.selectedItem
    val noReadingMessage = stringResource(R.string.detail_no_reading)
    val pronounceLabel = stringResource(R.string.detail_pronounce)
    val japaneseAudioUnavailableMessage = stringResource(R.string.detail_japanese_audio_unavailable)
    val wordCopiedMessage = stringResource(R.string.detail_word_copied)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (detailState.loading) {
            DetailLoadingSkeleton()
            return@Column
        }

        detailState.error?.let {
            DetailStateCard(
                title = stringResource(R.string.error_details_load),
                message = it,
                actionLabel = stringResource(R.string.detail_retry),
                onAction = viewModel::retryDetailsLoad
            )
        }

        if (item == null) {
            Text(stringResource(R.string.detail_not_selected))
            return@Column
        }

        val kanji = detailState.details?.word?.writings?.firstOrNull { it.text.isNotBlank() }?.text
            .orEmpty()
            .ifBlank { item.primaryWriting.ifBlank { "-" } }
        val kana = detailState.details?.word?.kana.orEmpty().ifBlank { item.kana.ifBlank { "-" } }
        val definitionContent = formatDefinition(
            meanings = detailState.details?.word?.meanings,
            notePrefix = stringResource(R.string.detail_note_prefix),
            enableGlossaryLinks = !useOfflineMode
        ).takeIf { it.text.isNotBlank() }
            ?: run {
                if (useOfflineMode) {
                    DefinitionContent(
                        text = item.meaningSummary.ifBlank { "-" },
                        references = emptyList()
                    )
                } else {
                    val fallbackReferences = linkedMapOf<Int, GlossaryReference>()
                    val fallbackText = stripGlossaryReferences(
                        raw = normalizeMeaningText(item.meaningSummary),
                        enableGlossaryLinks = true,
                        references = fallbackReferences
                    ).replace("\n", " ").replace(Regex("""\s{2,}"""), " ").trim()

                    DefinitionContent(
                        text = fallbackText.ifBlank { "-" },
                        references = fallbackReferences.values.toList()
                    )
                }
            }
        val jlptLevel = detailState.details?.word?.jlpt?.takeIf { it in 1..5 } ?: item.jlpt.takeIf { it in 1..5 }
        val commonnessLevel = detailState.details?.word?.difficulty?.takeIf { it in 1..5 }
            ?: item.difficulty.takeIf { it in 1..5 }
        val categoryChips = detailState.details?.word?.categoryIds.orEmpty()
            .map { id ->
                CategoryChipModel(
                    id = id,
                    label = detailState.categoryNameById[id]
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_category_fallback, id)
                )
            }
            .distinctBy { it.id }
        val metadataChips = buildList {
            jlptLevel?.let { add(CategoryChipModel(-it, "JLPT N$it")) }
            commonnessLevel?.let {
                add(CategoryChipModel(-(100 + it), stringResource(R.string.detail_commonness_label, commonnessStars(it))))
            }
            addAll(categoryChips)
        }

        DetailWordHeaderCard(
            kanji = kanji,
            kana = kana,
            chips = metadataChips,
            pronounceLabel = pronounceLabel,
            onSpeakKana = {
                val speakText = kana.trim().takeIf { it.isNotEmpty() && it != "-" }
                when {
                    speakText == null -> Toast.makeText(context, noReadingMessage, Toast.LENGTH_SHORT).show()
                    !canSpeakJapanese || textToSpeech == null ->
                        Toast.makeText(context, japaneseAudioUnavailableMessage, Toast.LENGTH_SHORT).show()
                    else -> textToSpeech.speak(
                        speakText,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "word-kana-${item.id}"
                    )
                }
            },
            onCategoryClick = { chip ->
                if (useOfflineMode) return@DetailWordHeaderCard
                viewModel.navigateTo(Screen.Search)
                focusManager.clearFocus()
                viewModel.runCategorySearch(chip.id, chip.label)
            },
            onKanjiClick = {
                kanji.trim().takeIf { it.isNotEmpty() && it != "-" }?.let {
                    clipboardManager.setText(AnnotatedString(it))
                    Toast.makeText(context, wordCopiedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )

        DefinitionsCard(
            title = stringResource(R.string.detail_definitions_title),
            content = definitionContent,
            onGlossaryReferenceClick = { referenceId ->
                if (!useOfflineMode) {
                    focusManager.clearFocus()
                    viewModel.openOnlineGlossaryReference(referenceId)
                }
            }
        )

        val pictureUrls = detailState.details?.word?.meanings.orEmpty()
            .flatMap(::extractMeaningPictureUrls)
            .mapNotNull(::normalizeApiImageUrl)
            .distinct()
        if (pictureUrls.isNotEmpty()) {
            PicturesCard(title = stringResource(R.string.detail_pictures_title), imageUrls = pictureUrls)
        }

        val relatedItems = (
            detailState.details?.similarWords.orEmpty().map {
                RelatedWordItem(wordId = it.id, text = it.primaryWriting, kana = it.kana)
            } + detailState.details?.word?.meanings.orEmpty().flatMap { meaning ->
                meaning.related.flatMap { group -> group.items }
            }
        )
            .filter { it.text.isNotBlank() || it.kana.isNotBlank() }
            .distinctBy { "${it.wordId}|${it.meaningNo}|${it.text.trim()}|${it.kana.trim()}" }
        if (relatedItems.isNotEmpty()) {
            RelatedWordsCard(title = stringResource(R.string.detail_related_words_title), items = relatedItems, onWordClick = {
                focusManager.clearFocus()
                viewModel.openDetailsByRelatedItem(it)
            })
        }

        val examples = detailState.details?.sentenceSearch.orEmpty()
            .filter { it.text.isNotBlank() || it.kana.isNotBlank() || it.arabic.isNotBlank() }
            .distinctBy { "${it.id}|${it.text.trim()}|${it.kana.trim()}|${it.arabic.trim()}" }
        if (examples.isNotEmpty()) {
            ExamplesCard(title = stringResource(R.string.detail_examples_title), items = examples)
        }
    }
}

@Composable
private fun DetailLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(82.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DetailStateCard(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun normalizeMeaningText(raw: String): String = raw.trim()
    .replace("$", "")
    .replace(MEANING_CONTROL_MARKS_REGEX, "")
    .replace(MEANING_ARABIC_SEMICOLON_REGEX, " ")
    .replace(MEANING_PAREN_BAR_REGEX, "( $1 )")
    .replace(MEANING_INLINE_TAG_REGEX) { match ->
        val label = (match.groups[1]?.value ?: match.groups[3]?.value).orEmpty().trim()
        val value = (match.groups[2]?.value ?: match.groups[4]?.value).orEmpty().trim()
        if (label.isEmpty() || value.all(Char::isDigit)) match.value else "$label:"
    }
    .replace(Regex("""\{\s*([^:{}][^{}]*?)\s*\}""")) { match ->
        val label = match.groupValues[1].trim()
        if (label.isEmpty()) "" else "$label:"
    }
    .replace(JAPANESE_NUMERIC_REFERENCE_REGEX, "$1")
    .replace(MEANING_BULLET_PREFIX_REGEX, "")
    .replace(MEANING_EMPTY_BRACES_REGEX, "")
    .replace(MEANING_TRAILING_SPACES_REGEX, "")
    .replace(MEANING_MULTISPACE_REGEX, " ")
    .trim()

private fun normalizeMeaningNote(raw: String): String {
    val cleaned = normalizeMeaningText(raw)
    return cleaned.takeUnless { it.equals("no", true) || it == "-" }.orEmpty()
}

private fun formatDefinition(
    meanings: List<Meaning>?,
    notePrefix: String,
    enableGlossaryLinks: Boolean
): DefinitionContent {
    val entries = formatMeaningEntries(meanings)
    if (entries.isEmpty()) return DefinitionContent(text = "", references = emptyList())

    val references = linkedMapOf<Int, GlossaryReference>()
    val text = entries.joinToString("\n\n") { entry ->
        buildString {
            append("- ")
            append(stripGlossaryReferences(entry.definition, enableGlossaryLinks, references))
            if (entry.note.isNotBlank()) {
                append("\n")
                append(notePrefix)
                append(stripGlossaryReferences(entry.note, enableGlossaryLinks, references))
            }
        }
    }

    return DefinitionContent(text = text, references = references.values.toList())
}

private fun formatMeaningEntries(meanings: List<Meaning>?): List<MeaningEntry> {
    if (meanings.isNullOrEmpty()) return emptyList()
    return meanings.mapNotNull { meaning ->
        val definition = normalizeMeaningText(meaning.arabic)
        val note = normalizeMeaningNote(meaning.note)
        if (definition.isEmpty() && note.isEmpty()) null else MeaningEntry(definition.ifBlank { "-" }, note)
    }
}

private fun normalizeApiImageUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val normalized = trimmed.replace('\\', '/')
    return when {
        normalized.startsWith("https://", true) -> normalized
        normalized.startsWith("http://", true) -> "https://${normalized.removePrefix("http://")}"
        normalized.startsWith("//") -> "https:$normalized"
        normalized.startsWith("/") -> "https://shinjikai.app$normalized"
        API_IMAGE_FILENAME_REGEX.matches(normalized) -> "https://shinjikai.app/static/word_pictures/$normalized"
        else -> "https://shinjikai.app/$normalized"
    }
}

private fun extractMeaningPictureUrls(meaning: Meaning): List<String> {
    return meaning.pictures.mapNotNull(::extractApiPictureUrl)
}

private fun extractApiPictureUrl(element: JsonElement): String? {
    return when {
        element.isJsonNull -> null
        element.isJsonPrimitive -> element.asJsonPrimitive.takeIf { it.isString }?.asString
        element.isJsonObject -> extractApiPictureUrlFromObject(element.asJsonObject)
        else -> null
    }
}

private fun extractApiPictureUrlFromObject(obj: JsonObject): String? {
    val keys = listOf("Filename", "FileName", "filename", "Url", "url", "Src", "src", "Path", "path")
    for (key in keys) {
        val value = obj.get(key) ?: continue
        val asString = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: continue
        return if (key.equals("Filename", true) || key.equals("FileName", true)) {
            "/static/word_pictures/${asString.trim()}"
        } else {
            asString
        }
    }
    return null
}

internal fun forceRtlText(text: String): String = "\u202B$text\u202C"

internal fun formatOfflineSearchPreview(raw: String): String {
    return normalizeMeaningText(raw).replace("\n", " ").replace(Regex("""\s{2,}"""), " ").trim()
}

internal fun formatOnlineSearchPreview(raw: String): String {
    return normalizeMeaningText(raw)
        .replace(GLOSSARY_REFERENCE_REGEX) { match -> match.groupValues[1].trim() }
        .replace("\n", " ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}

internal fun formatEpochAsLocal(epochMs: Long): String {
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
    }.getOrDefault("-")
}

@Composable
internal fun ModeBadge(useOfflineMode: Boolean) {
    val label = if (useOfflineMode) stringResource(R.string.mode_offline) else stringResource(R.string.mode_online)
    val bg = if (useOfflineMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (useOfflineMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun commonnessStars(difficulty: Int): String {
    if (difficulty !in 1..5) return ""
    return buildString {
        repeat(difficulty) { append('★') }
        repeat(5 - difficulty) { append('☆') }
    }
}

@Composable
internal fun CommonnessBadge(difficulty: Int, modifier: Modifier = Modifier) {
    val stars = commonnessStars(difficulty)
    if (stars.isEmpty()) return
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stringResource(R.string.detail_commonness_label, stars),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun CategorySearchBanner(label: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.detail_category_label), style = MaterialTheme.typography.labelMedium)
                Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun DetailWordHeaderCard(
    kanji: String,
    kana: String,
    chips: List<CategoryChipModel>,
    pronounceLabel: String,
    onSpeakKana: () -> Unit,
    onCategoryClick: (CategoryChipModel) -> Unit,
    onKanjiClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = kana, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onSpeakKana) {
                    Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = pronounceLabel)
                }
            }
            Text(
                text = kanji,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onKanjiClick)
            )
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { chip ->
                        val isCategory = chip.id > 0
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isCategory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = if (isCategory) Modifier.clickable { onCategoryClick(chip) } else Modifier
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCategory) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                }
                                Text(text = chip.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DefinitionsCard(
    title: String,
    content: DefinitionContent,
    onGlossaryReferenceClick: (Int) -> Unit
) {
    var expanded by remember(content.text) { mutableStateOf(false) }
    val definition = content.text
    val canExpand = definition.length > 260 || definition.count { it == '\n' } >= 4
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            SelectionContainer {
                Text(
                    text = forceRtlText(definition),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textDirection = TextDirection.Rtl
                    ),
                    textAlign = TextAlign.Right,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            if (content.references.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content.references.forEach { reference ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onGlossaryReferenceClick(reference.id) }
                        ) {
                            Text(
                                text = reference.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            if (canExpand) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more))
                }
            }
        }
    }
}

private fun stripGlossaryReferences(
    raw: String,
    enableGlossaryLinks: Boolean,
    references: MutableMap<Int, GlossaryReference>
): String {
    if (!enableGlossaryLinks) {
        return raw
    }

    val result = StringBuilder()
    var cursor = 0
    for (match in GLOSSARY_REFERENCE_REGEX.findAll(raw)) {
        val range = match.range
        if (cursor < range.first) {
            result.append(raw.substring(cursor, range.first))
        }
        val label = match.groupValues[1].trim()
        val id = match.groupValues[2].toIntOrNull()
        if (!label.isNullOrEmpty()) {
            result.append(label)
            if (id != null && id > 0) {
                references.putIfAbsent(id, GlossaryReference(id = id, label = label))
            }
        }
        cursor = range.last + 1
    }
    if (cursor < raw.length) {
        result.append(raw.substring(cursor))
    }
    return result.toString()
}

@Composable
private fun PicturesCard(title: String, imageUrls: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            if (imageUrls.size == 1) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PictureCard(url = imageUrls.first())
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(imageUrls) { url ->
                        PictureCard(url = url)
                    }
                }
            }
        }
    }
}

@Composable
private fun PictureCard(url: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.size(width = 240.dp, height = 170.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    is coil.compose.AsyncImagePainter.State.Error -> Text(stringResource(R.string.detail_image_load_error))
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RelatedWordsCard(title: String, items: List<RelatedWordItem>, onWordClick: (RelatedWordItem) -> Unit) {
    var visibleCount by remember(items) { mutableStateOf(RELATED_WORDS_PAGE_SIZE.coerceAtMost(items.size)) }
    val wordFallback = stringResource(R.string.detail_word_fallback)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.take(visibleCount).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onWordClick(item) }
                    ) {
                        Text(
                            text = item.text.ifBlank { item.kana.ifBlank { wordFallback.format(item.wordId) } },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (visibleCount < items.size) {
                TextButton(onClick = { visibleCount = (visibleCount + RELATED_WORDS_PAGE_SIZE).coerceAtMost(items.size) }) {
                    Text(stringResource(R.string.detail_show_more))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExamplesCard(title: String, items: List<SentenceExample>) {
    var expanded by remember(items) { mutableStateOf(true) }
    var visibleCount by remember(items) { mutableStateOf(EXAMPLES_PAGE_SIZE.coerceAtMost(items.size)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val exampleFallback = stringResource(R.string.detail_example_fallback)
    val exampleCopiedMessage = stringResource(R.string.detail_example_copied)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items.take(visibleCount).forEach { item ->
                        val displayText = item.text.ifBlank { item.kana.ifBlank { exampleFallback } }
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(displayText))
                                    Toast.makeText(context, exampleCopiedMessage, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            if (item.arabic.isNotBlank()) {
                                Text(
                                    text = forceRtlText(item.arabic),
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (visibleCount < items.size) {
                            TextButton(
                                onClick = {
                                    visibleCount = (visibleCount + EXAMPLES_PAGE_SIZE).coerceAtMost(items.size)
                                }
                            ) {
                                Text(stringResource(R.string.detail_show_more))
                            }
                        } else {
                            Spacer(modifier = Modifier)
                        }
                        TextButton(onClick = { expanded = false }) {
                            Text(stringResource(R.string.detail_card_collapse))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.detail_show_more))
                    }
                }
            }
        }
    }
}
