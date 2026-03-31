package com.shinjikai.dictionary.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shinjikai.dictionary.R
import com.shinjikai.dictionary.data.AppDatabase
import com.shinjikai.dictionary.data.AppSettings
import com.shinjikai.dictionary.data.BookmarkItem
import com.shinjikai.dictionary.data.BookmarkRepository
import com.shinjikai.dictionary.data.ClientIdStore
import com.shinjikai.dictionary.data.DictionarySource
import com.shinjikai.dictionary.data.LocalYomitanSource
import com.shinjikai.dictionary.data.OFFLINE_IMAGE_DIR_META_KEY
import com.shinjikai.dictionary.data.RecentSearchStore
import com.shinjikai.dictionary.data.RelatedWordItem
import com.shinjikai.dictionary.data.RemoteDictionarySource
import com.shinjikai.dictionary.data.RawShinjikaiImporter
import com.shinjikai.dictionary.data.SearchItem
import com.shinjikai.dictionary.data.SearchPagingSource
import com.shinjikai.dictionary.data.SearchRequestSpec
import com.shinjikai.dictionary.data.SettingsStore
import com.shinjikai.dictionary.data.ShinjikaiRepository
import com.shinjikai.dictionary.data.UpdateRepository
import com.shinjikai.dictionary.data.WordDetailsResponse
import com.shinjikai.dictionary.data.YomitanMetaEntity
import com.shinjikai.dictionary.data.YomitanImporter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ShinjikaiViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val settingsStore = SettingsStore(context)
    private val recentSearchStore = RecentSearchStore(context)
    private val bookmarkRepository = BookmarkRepository(database.bookmarkDao(), database.yomitanDao())
    private val clientId = ClientIdStore.getOrCreate(context)
    private val installedVersionCode = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(1L)
    private val updateRepository = UpdateRepository(
        metadataUrl = APP_UPDATE_METADATA_URL,
        fallbackUrl = APP_RELEASES_URL
    )
    private val searchSpec = MutableStateFlow<SearchRequestSpec?>(null)
    private val searchRefreshNonce = MutableStateFlow(0)
    private var detailsLoadJob: Job? = null
    private var categoriesPreloadJob: Job? = null

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.readCached())

    private var dictionarySource: DictionarySource = createDictionarySource(settings.value.useOfflineMode)
        set(value) {
            field = value
            repository = ShinjikaiRepository(source = value)
            searchRefreshNonce.value = searchRefreshNonce.value + 1
        }

    private var repository: ShinjikaiRepository = ShinjikaiRepository(source = dictionarySource)

    val searchResults: Flow<PagingData<SearchItem>> = combine(searchSpec, searchRefreshNonce) { spec, _ -> spec }
        .flatMapLatest { spec ->
            if (spec == null || spec.mode == ResultMode.None) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 15,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = { SearchPagingSource(repository = repository, spec = spec) }
                ).flow
            }
        }
        .cachedIn(viewModelScope)

    val bookmarkPagingFlow: Flow<PagingData<BookmarkItem>> =
        bookmarkRepository.pagedFlow().cachedIn(viewModelScope)

    private val importClient = OkHttpClient()
    private val yomitanImporter = YomitanImporter(database)
    private val rawShinjikaiImporter = RawShinjikaiImporter(database)

    private data class OfflineImportResult(
        val importedCount: Int = 0,
        val installedImages: Boolean = false
    )

    var term by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<SearchItem>>(emptyList())
    var resultHeader by mutableStateOf<String?>(null)
    var activeResultQuery by mutableStateOf("")
    var currentResultsPage by mutableStateOf(0)
    var currentResultsPageCount by mutableStateOf(0)
    var currentResultsTotalCount by mutableStateOf(0)
    var loadingMore by mutableStateOf(false)
    var loadingDetails by mutableStateOf(false)
    var detailsError by mutableStateOf<String?>(null)
    var details by mutableStateOf<WordDetailsResponse?>(null)
    var selectedItem by mutableStateOf<SearchItem?>(null)
    private var detailsOpenedFromBookmarks by mutableStateOf(false)

    var currentScreen by mutableStateOf(Screen.Search)
    val screenStack = mutableStateListOf(Screen.Search)
    private var introDismissedThisSession by mutableStateOf(false)

    var activeCategoryId by mutableStateOf<Int?>(null)
    var activeCategoryName by mutableStateOf<String?>(null)
    var categoryNameById by mutableStateOf<Map<Int, String>>(emptyMap())

    val bookmarkedItems = mutableStateListOf<BookmarkItem>()

    var isBookmarkEditMode by mutableStateOf(false)
    var selectedBookmarkIds by mutableStateOf<Set<Int>>(emptySet())
    var pendingBookmarkDeletionIds by mutableStateOf<Set<Int>?>(null)

    val recentSearches = mutableStateListOf<String>().apply {
        addAll(recentSearchStore.readCached())
    }

    var isImportingOfflineData by mutableStateOf(false)
    var offlineImportProgress by mutableStateOf(0f)
    var offlineImportPhase by mutableStateOf<String?>(null)
    var offlineImportStatus by mutableStateOf<String?>(null)
    var offlineLastImportEpochMs by mutableStateOf<Long?>(null)
    var offlineTermCount by mutableStateOf(0)
    var offlineLastImportSource by mutableStateOf<String?>(null)
    var isCheckingForUpdate by mutableStateOf(false)
    var availableUpdate by mutableStateOf<com.shinjikai.dictionary.data.AppUpdateInfo?>(null)

    val searchUiState: SearchUiState
        get() = SearchUiState(
            term = term,
            activeCategoryId = activeCategoryId,
            activeCategoryName = activeCategoryName,
            resultMode = searchSpec.value?.mode ?: ResultMode.None,
            recentSearches = recentSearches.toList()
        )

    val detailUiState: DetailUiState
        get() = DetailUiState(
            loading = loadingDetails,
            error = detailsError,
            details = details,
            selectedItem = selectedItem,
            isBookmarked = selectedItem?.let { item ->
                bookmarkedItems.any { it.id == item.id }
            } == true,
            categoryNameById = categoryNameById
        )

    val bookmarksUiState: BookmarksUiState
        get() = BookmarksUiState(
            items = bookmarkedItems.toList(),
            isEditMode = isBookmarkEditMode,
            selectedIds = selectedBookmarkIds,
            pendingDeletionIds = pendingBookmarkDeletionIds,
            useOfflineMode = settings.value.useOfflineMode,
            activeCategoryId = activeCategoryId
        )

    val settingsUiState: SettingsUiState
        get() = SettingsUiState(
            settings = settings.value,
            showIntroduction = !settings.value.hasSeenIntroduction && !introDismissedThisSession,
            isImportingOfflineData = isImportingOfflineData,
            offlineImportProgress = offlineImportProgress,
            offlineImportPhase = offlineImportPhase,
            offlineImportStatus = offlineImportStatus,
            offlineLastImportEpochMs = offlineLastImportEpochMs,
            offlineTermCount = offlineTermCount,
            offlineLastImportSource = offlineLastImportSource,
            isCheckingForUpdate = isCheckingForUpdate,
            availableUpdate = availableUpdate
        )

    init {
        refreshOfflineTermCount()

        viewModelScope.launch {
            settings.collect { newSettings ->
                dictionarySource = createDictionarySource(newSettings.useOfflineMode)
                ensureCategoriesPreloadedIfNeeded()
            }
        }

        viewModelScope.launch {
            bookmarkedItems.clear()
            bookmarkedItems.addAll(bookmarkRepository.getAll())
        }

        viewModelScope.launch {
            recentSearchStore.recentSearchesFlow.collect { stored ->
                if (recentSearches.toList() != stored) {
                    recentSearches.clear()
                    recentSearches.addAll(stored)
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        screenStack.add(screen)
        currentScreen = screen
    }

    fun goBack() {
        if (screenStack.size > 1) {
            screenStack.removeLast()
            currentScreen = screenStack.last()
            if (currentScreen != Screen.Detail) {
                detailsError = null
            }
        }
    }

    fun clearCategorySearch() {
        activeCategoryId = null
        activeCategoryName = null
        term = ""
        searchSpec.value = null
    }

    fun runSearchForTerm(rawTerm: String) {
        val query = rawTerm.trim()
        activeCategoryId = null
        activeCategoryName = null
        error = null
        activeResultQuery = query

        if (query.isBlank()) {
            term = ""
            searchSpec.value = null
            return
        }

        rememberRecentSearch(query)
        term = query
        searchSpec.value = SearchRequestSpec(
            mode = ResultMode.Search,
            query = query
        )
    }

    fun runSearch() {
        runSearchForTerm(term)
    }

    fun runCategorySearch(categoryId: Int, categoryName: String) {
        activeCategoryId = categoryId
        activeCategoryName = categoryName
        term = categoryName
        activeResultQuery = categoryName
        searchSpec.value = SearchRequestSpec(
            mode = ResultMode.Category,
            categoryId = categoryId,
            categoryName = categoryName
        )
    }

    fun canLoadMoreResults(): Boolean = false

    fun loadMoreResults() = Unit

    private fun openDetailsInternal(item: SearchItem, navigate: Boolean) {
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = false
        selectedItem = item
        details = null
        detailsError = null
        loadingDetails = true
        if (navigate) {
            navigateTo(Screen.Detail)
        }

        detailsLoadJob = viewModelScope.launch {
            ensureCategoriesPreloadedIfNeeded()
            val result = repository.loadWordDetails(item.id)
            loadingDetails = false
            result.onSuccess { details = it }
                .onFailure { detailsError = it.message ?: context.getString(R.string.error_details_load) }
        }
    }

    fun openDetails(item: SearchItem) {
        openDetailsInternal(item, navigate = true)
    }

    fun openBookmarkedDetails(item: SearchItem) {
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = true
        selectedItem = item
        details = null
        detailsError = null
        loadingDetails = true
        navigateTo(Screen.Detail)

        detailsLoadJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                bookmarkRepository.getSavedDetails(item.id)
            }
            loadingDetails = false
            if (cached != null) {
                details = cached
            } else {
                detailsError = context.getString(R.string.error_bookmark_not_available_offline)
            }
        }
    }

    fun retryDetailsLoad() {
        val item = selectedItem ?: return
        if (detailsOpenedFromBookmarks) {
            detailsLoadJob?.cancel()
            details = null
            detailsError = null
            loadingDetails = true
            detailsLoadJob = viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    bookmarkRepository.getSavedDetails(item.id)
                }
                loadingDetails = false
                if (cached != null) {
                    details = cached
                } else {
                    detailsError = context.getString(R.string.error_bookmark_not_available_offline)
                }
            }
        } else {
            openDetailsInternal(item, navigate = false)
        }
    }

    fun openDetailsById(id: Int) {
        openDetails(SearchItem(id = id))
    }

    fun openOnlineGlossaryReference(id: Int) {
        if (id <= 0) return
        detailsLoadJob?.cancel()
        detailsOpenedFromBookmarks = false
        selectedItem = SearchItem(id = id)
        details = null
        detailsError = null
        loadingDetails = true

        detailsLoadJob = viewModelScope.launch {
            ensureCategoriesPreloadedIfNeeded()
            val result = repository.loadWordDetails(id)
            loadingDetails = false
            result.onSuccess { details = it }
                .onFailure { detailsError = it.message ?: context.getString(R.string.error_details_load) }
        }
    }

    fun openDetailsByRelatedItem(relatedItem: RelatedWordItem) {
        if (relatedItem.wordId > 0) {
            openDetailsById(relatedItem.wordId)
        } else {
            val lookupTerm = relatedItem.text.ifBlank { relatedItem.kana }.trim()
            if (lookupTerm.isNotEmpty()) {
                navigateTo(Screen.Search)
                term = lookupTerm
                runSearchForTerm(lookupTerm)
            }
        }
    }

    fun toggleBookmark(item: SearchItem) {
        viewModelScope.launch {
            val isBookmarked = bookmarkedItems.any { it.id == item.id }
            if (isBookmarked) {
                bookmarkRepository.deleteById(item.id)
                bookmarkedItems.removeAll { it.id == item.id }
            } else {
                val existingDetails = details?.takeIf { it.word.id == item.id }
                val detailsResult = existingDetails?.let { Result.success(it) }
                    ?: repository.loadWordDetails(item.id)

                detailsResult.onSuccess { response ->
                    val existingCreatedAt = bookmarkedItems.firstOrNull { it.id == item.id }?.createdAt
                        ?: System.currentTimeMillis()
                    val word = response.word
                    val summaryFromDetails = word.meanings
                        .asSequence()
                        .map { it.arabic.trim() }
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    val normalizedItem = SearchItem(
                        id = word.id,
                        kana = word.kana,
                        writings = word.writings,
                        meaningSummary = item.meaningSummary.ifBlank { summaryFromDetails },
                        jlpt = word.jlpt,
                        difficulty = word.difficulty
                    )

                    bookmarkRepository.upsertWithDetails(normalizedItem, response)
                    bookmarkedItems.removeAll { it.id == normalizedItem.id }
                    bookmarkedItems.add(
                        0,
                        BookmarkItem(item = normalizedItem, createdAt = existingCreatedAt)
                    )
                }.onFailure { throwable ->
                    Toast.makeText(
                        context,
                        throwable.message ?: context.getString(R.string.error_bookmark_save),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun requestDeleteBookmarks(ids: Set<Int>) {
        pendingBookmarkDeletionIds = ids
    }

    fun updateBookmarkEditMode(enabled: Boolean) {
        isBookmarkEditMode = enabled
        if (!enabled) {
            selectedBookmarkIds = emptySet()
        }
    }

    fun toggleBookmarkSelection(id: Int) {
        selectedBookmarkIds = if (selectedBookmarkIds.contains(id)) {
            selectedBookmarkIds - id
        } else {
            selectedBookmarkIds + id
        }
    }

    fun pruneBookmarkSelection(validIds: Set<Int>) {
        selectedBookmarkIds = selectedBookmarkIds.intersect(validIds)
    }

    fun deleteBookmarks(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookmarkRepository.deleteByIds(ids)
            bookmarkedItems.removeAll { it.id in ids }
            pendingBookmarkDeletionIds = null
            selectedBookmarkIds = emptySet()
        }
    }

    fun clearRecentSearches() {
        recentSearches.clear()
        viewModelScope.launch { recentSearchStore.save(emptyList()) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkMode(enabled) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseDynamicColor(enabled) }
    }

    fun setUseOfflineMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseOfflineMode(enabled) }
    }

    fun dismissIntroduction() {
        if (settings.value.hasSeenIntroduction || introDismissedThisSession) return
        introDismissedThisSession = true
        viewModelScope.launch {
            runCatching { settingsStore.setHasSeenIntroduction(true) }
                .onFailure { introDismissedThisSession = false }
        }
    }

    fun dismissIntroductionAndOpenSettings() {
        dismissIntroduction()
        screenStack.clear()
        screenStack.add(Screen.Search)
        navigateTo(Screen.Settings)
    }

    fun showIntroductionAgain() {
        introDismissedThisSession = false
        viewModelScope.launch { settingsStore.setHasSeenIntroduction(false) }
    }

    fun checkForAppUpdate() {
        if (isCheckingForUpdate) return

        viewModelScope.launch {
            isCheckingForUpdate = true

            val now = System.currentTimeMillis()
            val result = updateRepository.checkForUpdate(installedVersionCode)
            settingsStore.setLastUpdateCheckEpochMs(now)

            result.onSuccess { update ->
                availableUpdate = update
                if (update == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_updates_up_to_date),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_updates_available_version,
                            update.latestVersionName
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { throwable ->
                Toast.makeText(
                    context,
                    throwable.message ?: context.getString(R.string.settings_updates_check_failed),
                    Toast.LENGTH_LONG
                ).show()
            }

            isCheckingForUpdate = false
        }
    }

    fun refreshOfflineTermCount() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val dao = database.yomitanDao()
                val count = dao.countTerms()
                val epoch = dao.getMetaValue("last_import_epoch_ms")?.toLongOrNull()
                val source = dao.getMetaValue("last_import_source")
                Triple(count, epoch, source)
            }
            offlineTermCount = snapshot.first
            offlineLastImportEpochMs = snapshot.second
            offlineLastImportSource = snapshot.third
        }
    }

    fun importOfflineDictionaryFromUri(uri: Uri) {
        if (isImportingOfflineData) return
        viewModelScope.launch {
            isImportingOfflineData = true
            offlineImportStatus = null
            offlineImportProgress = 0f

            val result = runCatching {
                val imported = importFromPickedArchive(uri)
                offlineImportProgress = 1f
                imported
            }

            isImportingOfflineData = false
            offlineImportPhase = null
            result.onSuccess { importResult ->
                offlineImportStatus = when {
                    importResult.importedCount > 0 && importResult.installedImages ->
                        context.getString(R.string.offline_import_success_with_images, importResult.importedCount)
                    importResult.importedCount > 0 ->
                        context.getString(R.string.offline_import_success, importResult.importedCount)
                    importResult.installedImages ->
                        context.getString(R.string.offline_images_install_success)
                    else ->
                        context.getString(R.string.offline_import_failure)
                }
                refreshOfflineTermCount()
            }.onFailure { throwable ->
                offlineImportStatus = throwable.message ?: context.getString(R.string.offline_import_failure)
            }
        }
    }

    private suspend fun ensureCategoriesPreloadedIfNeeded() {
        if (categoryNameById.isNotEmpty()) return
        if (categoriesPreloadJob?.isActive == true) {
            categoriesPreloadJob?.join()
            return
        }
        categoriesPreloadJob = viewModelScope.launch {
            repository.loadCategories().onSuccess { response ->
                categoryNameById = response.categories
                    .associate { it.id to it.name.trim() }
                    .filterValues { it.isNotEmpty() }
            }
        }
        categoriesPreloadJob?.join()
    }

    private suspend fun importFromRemoteZip(): Int {
        return withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "offline-import").apply { mkdirs() }
            val tempFile = File(tempDir, "offline_dictionary_import.zip.part")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(OFFLINE_DICTIONARY_URL)
                .build()

            try {
                importClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("No response body")
                    val totalBytes = body.contentLength().takeIf { it > 0L } ?: -1L

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var copied = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (totalBytes > 0L) {
                                    val ratio = copied.toFloat() / totalBytes.toFloat()
                                    offlineImportProgress = (ratio.coerceIn(0f, 1f) * 0.82f)
                                }
                            }
                        }
                    }
                }

                ensureValidOfflineImportZip(tempFile)

                offlineImportPhase = context.getString(R.string.offline_import_phase_index)
                offlineImportProgress = offlineImportProgress.coerceAtLeast(0.86f)
                val imported = FileInputStream(tempFile).use { stream ->
                    yomitanImporter.importFromZip(
                        zipStream = stream,
                        sourceLabel = OFFLINE_DICTIONARY_SOURCE
                    ).getOrThrow()
                }
                offlineImportProgress = 1f
                imported
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private suspend fun importFromPickedZip(uri: Uri): OfflineImportResult {
        return withContext(Dispatchers.IO) {
            val targetDir = File(context.cacheDir, "picked-import/extracted")
            try {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()

                offlineImportPhase = context.getString(R.string.offline_import_phase_index)
                offlineImportProgress = 0.15f
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val outFile = safeResolveZipEntry(targetDir, entry.name)
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
                } ?: error("Unable to read selected zip.")

                processPickedImport(targetDir, PICKED_ZIP_SHINJIKAI_SOURCE)
            } finally {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
            }
        }
    }

    private suspend fun importFromPickedArchive(uri: Uri): OfflineImportResult {
        val archiveName = resolvePickedArchiveName(uri).orEmpty().lowercase()
        return if (archiveName.endsWith(".tar.xz") || archiveName.endsWith(".txz")) {
            importFromPickedTarXz(uri)
        } else {
            importFromPickedZip(uri)
        }
    }

    private suspend fun importFromPickedTarXz(uri: Uri): OfflineImportResult {
        return withContext(Dispatchers.IO) {
            val targetDir = File(context.cacheDir, "picked-import/extracted")
            try {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()

                offlineImportPhase = context.getString(R.string.offline_import_phase_index)
                offlineImportProgress = 0.15f
                context.contentResolver.openInputStream(uri)?.use { input ->
                    XZCompressorInputStream(input.buffered()).use { xz ->
                        TarArchiveInputStream(xz).use { tar ->
                            while (true) {
                                val entry = tar.nextTarEntry ?: break
                                val outFile = safeResolveZipEntry(targetDir, entry.name)
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
                } ?: error("Unable to read selected archive.")

                processPickedImport(targetDir, PICKED_TXZ_SHINJIKAI_SOURCE)
            } finally {
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
            }
        }
    }

    private suspend fun processPickedImport(targetDir: File, sourceLabel: String): OfflineImportResult {
        val jsonlFile = targetDir.resolve("raw_shinjikai_data.jsonl").takeIf { it.exists() && it.isFile }
        val extractedImagesDir = targetDir.resolve("yomitan_images").takeIf { it.exists() && it.isDirectory }

        require(jsonlFile != null || extractedImagesDir != null) {
            "Selected archive does not contain dictionary text or yomitan_images."
        }

        var installedImages = false
        if (extractedImagesDir != null) {
            offlineImportPhase = context.getString(R.string.offline_import_phase_images)
            offlineImportProgress = offlineImportProgress.coerceAtLeast(0.2f)
            installPickedOfflineImages(extractedImagesDir)
            installedImages = true
        }

        val importedCount = if (jsonlFile != null) {
            offlineImportPhase = context.getString(R.string.offline_import_phase_index)
            offlineImportProgress = offlineImportProgress.coerceAtLeast(0.35f)
            rawShinjikaiImporter.importFromJsonLines(
                jsonlFile = jsonlFile,
                sourceLabel = sourceLabel
            ).getOrThrow()
        } else {
            0
        }

        return OfflineImportResult(
            importedCount = importedCount,
            installedImages = installedImages
        )
    }

    private suspend fun installPickedOfflineImages(sourceDir: File) {
        val targetDir = File(context.filesDir, "offline/yomitan_images")
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.parentFile?.mkdirs()
        sourceDir.copyRecursively(targetDir, overwrite = true)
        database.yomitanDao().upsertMeta(
            YomitanMetaEntity(
                key = OFFLINE_IMAGE_DIR_META_KEY,
                value = targetDir.absolutePath.replace('\\', '/')
            )
        )
    }

    private fun resolvePickedArchiveName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
    }

    private fun safeResolveZipEntry(rootDir: File, entryName: String): File {
        val candidate = File(rootDir, entryName)
        val rootPath = rootDir.canonicalFile.toPath()
        val candidatePath = candidate.canonicalFile.toPath()
        require(candidatePath.startsWith(rootPath)) { "Invalid zip entry path: $entryName" }
        return candidate
    }

    private fun rememberRecentSearch(term: String) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        recentSearches.removeAll { it.equals(normalized, ignoreCase = true) }
        recentSearches.add(0, normalized)
        while (recentSearches.size > MAX_RECENT_SEARCHES) {
            recentSearches.removeAt(recentSearches.lastIndex)
        }
        val snapshot = recentSearches.toList()
        viewModelScope.launch { recentSearchStore.save(snapshot) }
    }

    private fun ensureValidOfflineImportZip(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            throw IOException(context.getString(R.string.offline_import_failure))
        }
        ZipInputStream(FileInputStream(file).buffered()).use { zip ->
            if (zip.nextEntry == null) {
                throw IOException(context.getString(R.string.offline_import_invalid_zip))
            }
        }
    }

    private fun createDictionarySource(useOfflineMode: Boolean): DictionarySource {
        return if (useOfflineMode) {
            LocalYomitanSource(database.yomitanDao())
        } else {
            RemoteDictionarySource(clientId = clientId, cacheDir = context.cacheDir)
        }
    }

    private companion object {
        private const val MAX_RECENT_SEARCHES = 15
        private const val OFFLINE_DICTIONARY_SOURCE = "japanesearabic-yomitan-v2"
        private const val PICKED_ZIP_SHINJIKAI_SOURCE = "raw-shinjikai-jp-ar-picked-zip"
        private const val PICKED_TXZ_SHINJIKAI_SOURCE = "raw-shinjikai-jp-ar-picked-tar-xz"
        private const val LOCAL_RAW_SHINJIKAI_SOURCE = "raw-shinjikai-jp-ar-jsonl"
        private const val LOCAL_RAW_SHINJIKAI_JSONL_PATH =
            "C:\\Users\\almag\\Desktop\\Raw Shinjikai Database\\content\\Raw Shinjikai JP-AR Database\\raw_shinjikai_data.jsonl"
        private const val LOCAL_RAW_SHINJIKAI_IMAGES_PATH =
            "C:\\Users\\almag\\Desktop\\Raw Shinjikai Database\\content\\Raw Shinjikai JP-AR Database\\yomitan_images"
        private const val OFFLINE_DICTIONARY_URL =
            "https://raw.githubusercontent.com/a-hamdi/japanesearabic/main/data/YomitandictionaryV2/%E6%B7%B1%E8%BE%9E%E6%B5%B7_No_Examples_No_%E4%BE%8B%E6%96%87%20-%20JP-AR%20STYLING%20FIX.zip"
        private const val APP_UPDATE_METADATA_URL =
            "https://raw.githubusercontent.com/obj44/shinjikai/main/update.json"
        private const val APP_RELEASES_URL =
            "https://github.com/obj44/shinjikai/releases/latest"
    }
}
