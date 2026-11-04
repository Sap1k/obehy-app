package cz.obehy.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import cz.obehy.core.model.Mode
import cz.obehy.core.model.Place
import cz.obehy.core.model.PlaceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The file name, also listed in the app's backup rules: history never leaves the phone. */
internal const val HISTORY_DATABASE = "search-history.db"

@Database(entities = [JourneySearchEntity::class], version = 1)
internal abstract class HistoryDatabase : RoomDatabase() {
    abstract fun searches(): JourneySearchDao
}

@Entity(tableName = "journey_search", indices = [Index("provider", "at")])
internal data class JourneySearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    /** Epoch milliseconds. */
    val at: Long,
    @Embedded(prefix = "from_") val from: PlaceColumns,
    @Embedded(prefix = "to_") val to: PlaceColumns,
)

internal data class PlaceColumns(
    @ColumnInfo(name = "id") val placeId: String,
    val name: String,
    val area: String?,
    val kind: String,
    val lat: Double,
    val lon: Double,
    /** Mode names, comma-separated. */
    val modes: String,
)

@Dao
internal interface JourneySearchDao {
    @Query("SELECT * FROM journey_search WHERE provider = :provider ORDER BY at DESC, id DESC")
    fun searches(provider: String): Flow<List<JourneySearchEntity>>

    @Insert
    suspend fun add(search: JourneySearchEntity)

    @Query(
        "DELETE FROM journey_search WHERE at < :oldest OR id NOT IN " +
            "(SELECT id FROM journey_search ORDER BY at DESC, id DESC LIMIT :keep)",
    )
    suspend fun trim(
        oldest: Long,
        keep: Int,
    )

    @Query("DELETE FROM journey_search WHERE id IN (:ids)")
    suspend fun delete(ids: Set<Long>)

    @Query("DELETE FROM journey_search")
    suspend fun clear()
}

@Singleton
internal class RoomSearchHistoryStore
    @Inject
    constructor(
        private val dao: JourneySearchDao,
    ) : SearchHistoryStore {
        override fun searches(provider: String): Flow<List<PastSearch>> =
            dao.searches(provider).map { rows ->
                rows.mapNotNull { row ->
                    val from = row.from.toPlace() ?: return@mapNotNull null
                    val to = row.to.toPlace() ?: return@mapNotNull null
                    PastSearch(row.id, Instant.ofEpochMilli(row.at), from, to)
                }
            }

        override suspend fun add(
            provider: String,
            search: PastSearch,
        ) = dao.add(
            JourneySearchEntity(
                provider = provider,
                at = search.at.toEpochMilli(),
                from = search.from.toColumns(),
                to = search.to.toColumns(),
            ),
        )

        override suspend fun trim(
            oldest: Instant,
            keep: Int,
        ) = dao.trim(oldest.toEpochMilli(), keep)

        override suspend fun delete(ids: Set<Long>) = dao.delete(ids)

        override suspend fun clear() = dao.clear()
    }

private fun Place.toColumns() = PlaceColumns(id, name, area, kind.name, lat, lon, modes.joinToString(",") { it.name })

/** A place from the table, or null when a later version's kind isn't known here. */
private fun PlaceColumns.toPlace(): Place? {
    val kind = PlaceKind.entries.firstOrNull { it.name == kind } ?: return null
    val modes = modes.split(",").mapNotNullTo(mutableSetOf()) { name -> Mode.entries.firstOrNull { it.name == name } }
    return Place(placeId, name, area, kind, lat, lon, modes)
}
