package tachiyomi.domain.updates.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import java.util.Calendar

class GetAnimeUpdates(
    private val repository: AnimeUpdatesRepository,
) {

    suspend fun await(seen: Boolean, after: Long): List<AnimeUpdatesWithRelations> {
        return repository.awaitWithSeen(seen, after, limit = 500)
    }

    fun subscribe(calendar: Calendar): Flow<List<AnimeUpdatesWithRelations>> {
        return repository.subscribeAllAnimeUpdates(calendar.time.time, limit = 500)
    }

    fun subscribe(seen: Boolean, after: Long): Flow<List<AnimeUpdatesWithRelations>> {
        return repository.subscribeWithSeen(seen, after, limit = 500)
    }
}
