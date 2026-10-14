package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import mihon.domain.extension.interactor.UpdateExtensionStores
import tachiyomi.i18n.MR
import java.io.File
import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
@AssistedInject
class BackupRestorer(
    @Assisted private val notifier: BackupNotifier,
    @Assisted private val isSync: Boolean,
    private val context: Context,
    private val downloadCache: DownloadCache,
    private val categoriesRestorer: CategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val mangaRestorer: MangaRestorer,
    private val backupDecoder: BackupDecoder,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    private val extensionManager: ExtensionManager,
    private val updateExtensionStores: UpdateExtensionStores,
) {

    @AssistedFactory
    fun interface Factory {
        fun create(notifier: BackupNotifier, isSync: Boolean): BackupRestorer
    }

    private var restoreAmount = 0
    private val restoreProgress = AtomicInt(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()
    private var hasMissingExtensionsToInstall = false
    private val missingExtensionSourceIds = mutableSetOf<Long>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        // Invalidate download cache to ensure UI reflects any restored downloads
        if (options.libraryEntries) {
            try {
                downloadCache.invalidateCache()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after restore" }
            }
        }

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
            hasMissingExtensionsToInstall,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val (entryCount, backup) = backupDecoder.decodeMetadata(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreSourceNames(backupMaps)
            restoreAmount += entryCount
        }
        if (options.categories) {
            restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }

        coroutineScope {
            val restoreCategoriesJob = if (options.categories) {
                restoreCategories(backup.backupCategories)
            } else {
                null
            }
            if (options.appSettings) {
                restoreAppPreferences(
                    backup.backupPreferences,
                    backup.backupCategories.takeIf { options.categories },
                    restoreCategoriesJob,
                )
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreManga(
                    uri,
                    if (options.categories) backup.backupCategories else emptyList(),
                    restoreCategoriesJob,
                )
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupExtensionStores)
            }

            // TODO: optionally trigger online library + tracker update
        }

        if (options.libraryEntries) {
            restoreMissingExtensions(backupMaps)
        }
    }

    private suspend fun restoreMissingExtensions(backupSources: List<BackupSource>) {
        val sourceIds = backupSources.map { it.sourceId }.toSet()
        val installedSourceIds = sourceIds.filterTo(mutableSetOf()) { sourceManager.get(it) != null }
        val missingSourceIds = sourceIds - installedSourceIds
        if (missingSourceIds.isEmpty()) return

        val availableExtensions = try {
            updateExtensionStores()
            withTimeoutOrNull(45.seconds) {
                extensionManager.availableExtensionsFlow.first { available ->
                    available.any { extension -> extension.sources.any { it.id in missingSourceIds } }
                }
            }.orEmpty()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(LogPriority.WARN, e) { "Failed to refresh extension indexes during backup restore" }
            errors.add(Date() to "Failed to refresh extension indexes: ${e.message}")
            hasMissingExtensionsToInstall = true
            missingExtensionSourceIds += missingSourceIds
            persistMissingExtensionSourceIds()
            return
        }

        val installCandidates = availableExtensions
            .filter { extension -> extension.sources.any { it.id in missingSourceIds } }
            .distinctBy(Extension.Available::pkgName)
        val matchedSourceIds = installCandidates
            .flatMapTo(mutableSetOf()) { extension -> extension.sources.map { it.id } }
        if (missingSourceIds.any { it !in matchedSourceIds }) {
            hasMissingExtensionsToInstall = true
            val unmatchedSourceIds = missingSourceIds - matchedSourceIds
            missingExtensionSourceIds += unmatchedSourceIds
            errors.add(Date() to "No configured extension repository contains all missing backup sources")
        }

        installCandidates.forEach { extension ->
            try {
                extensionManager.installExtension(extension).collect()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                hasMissingExtensionsToInstall = true
                missingExtensionSourceIds += extension.sources.map { it.id }.filter { it in missingSourceIds }
                errors.add(Date() to "Failed to install extension ${extension.name}: ${e.message}")
                logcat(LogPriority.WARN, e) { "Failed to install extension ${extension.pkgName} during backup restore" }
            }
        }
        persistMissingExtensionSourceIds()
    }

    private fun persistMissingExtensionSourceIds() {
        if (missingExtensionSourceIds.isEmpty()) return
        context.getSharedPreferences("backup_extension_restore", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("missing_source_ids", missingExtensionSourceIds.mapTo(mutableSetOf()) { it.toString() })
            .apply()
    }

    // Without a stub, a source that isn't installed has no name for the next backup to write
    private suspend fun restoreSourceNames(backupSources: List<BackupSource>) {
        backupSources
            .filter { it.name.isNotBlank() }
            .filter {
                sourceManager.get(it.sourceId) == null && stubSourceRepository.getStubSource(it.sourceId) == null
            }
            .forEach { stubSourceRepository.upsertStubSource(it.sourceId, lang = "", name = it.name) }
    }

    private fun CoroutineScope.restoreCategories(backupCategories: List<BackupCategory>) = launch {
        ensureActive()
        categoriesRestorer(backupCategories)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreManga(
        uri: Uri,
        backupCategories: List<BackupCategory>,
        categoriesRestoreJob: Job?,
    ) = launch {
        categoriesRestoreJob?.join()

        // New entries go first. Rather than hold the others back in memory, a second pass reads them again.
        val isExisting = mangaRestorer.getExistingMatcher()
        val existingIndices = BitSet()
        val newMangas = backupDecoder.decodeEntries(uri)
            .withIndex()
            .filter { (index, manga) ->
                val exists = isExisting(manga)
                if (exists) existingIndices.set(index)
                !exists
            }
            .map { it.value }
        val backupMangas = flow {
            emitAll(newMangas)
            emitAll(backupDecoder.decodeEntries(uri, existingIndices::get))
        }

        backupMangas
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    ensureActive()
                    mangaRestorer.restore(chunk, backupCategories)
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndFetch(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()

                        try {
                            mangaRestorer.restore(listOf(it), backupCategories)
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = sourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndFetch()
                    }
                }

                notifier.showRestoreProgress(chunk.last().title, restoreProgress.load(), restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
        categoriesRestoreJob: Job?,
    ) = launch {
        ensureActive()
        categoriesRestoreJob?.join()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch {
        backupExtensionStores
            .chunked(100)
            .forEach { chunk ->
                chunk.forEach {
                    ensureActive()

                    try {
                        extensionStoreRestorer(it)
                    } catch (e: Exception) {
                        errors.add(Date() to "Error Adding Repo: ${it.name} : ${e.message}")
                    }

                    restoreProgress.incrementAndFetch()
                }
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionStores),
                    restoreProgress.load(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("mihon_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
