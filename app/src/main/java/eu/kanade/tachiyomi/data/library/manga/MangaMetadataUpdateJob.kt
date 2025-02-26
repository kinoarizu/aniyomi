package eu.kanade.tachiyomi.data.library.manga

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.copyFrom
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.toMangaUpdate
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class MangaMetadataUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: MangaSourceManager = Injekt.get()
    private val coverCache: MangaCoverCache = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()

    private val notifier = MangaLibraryUpdateNotifier(context)

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        addMangaToQueue()

        return withIOContext {
            try {
                updateMetadata()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = MangaLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
        )
    }

    /**
     * Adds list of manga to be updated.
     */
    private fun addMangaToQueue() {
        mangaToUpdate = runBlocking { getLibraryManga.await() }

        // Warn when excessively checking a single source
        val maxUpdatesFromSource = mangaToUpdate
            .groupBy { it.manga.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxUpdatesFromSource > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotification()
        }
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingManga,
                                    progressCount,
                                    manga,
                                ) {
                                    val source = sourceManager.get(manga.source) ?: return@withUpdateNotification
                                    try {
                                        val networkManga = source.getMangaDetails(manga.toSManga())
                                        val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                                            .copyFrom(networkManga)
                                        try {
                                            updateManga.await(updatedManga.toMangaUpdate())
                                        } catch (e: Exception) {
                                            logcat(LogPriority.ERROR) { "Manga doesn't exist anymore" }
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInteger,
        manga: Manga,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.get(),
            mangaToUpdate.size,
        )
    }

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<MangaMetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)
                }
        }
    }
}
