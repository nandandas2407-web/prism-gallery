package com.prism.gallery.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Favorite(@PrimaryKey val mediaId: Long)

@Entity data class SavedSearch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val saved: Boolean, // false = just a "recent" entry
    val createdAt: Long = System.currentTimeMillis(),
)

/** Non-destructive edit: only parameters are stored; the original file is never touched. */
@Entity data class EditRecord(@PrimaryKey val mediaId: Long, val paramsJson: String)

@Dao
interface GalleryDao {
    @Query("SELECT mediaId FROM Favorite") fun favorites(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addFavorite(f: Favorite)
    @Query("DELETE FROM Favorite WHERE mediaId = :id") suspend fun removeFavorite(id: Long)

    @Query("SELECT * FROM SavedSearch WHERE saved = :saved ORDER BY createdAt DESC LIMIT 30")
    fun searches(saved: Boolean): Flow<List<SavedSearch>>
    @Insert suspend fun addSearch(s: SavedSearch)
    @Query("DELETE FROM SavedSearch WHERE id = :id") suspend fun deleteSearch(id: Long)

    @Upsert suspend fun upsertEdit(e: EditRecord)
    @Query("SELECT * FROM EditRecord WHERE mediaId = :id") suspend fun edit(id: Long): EditRecord?
}

@Database(entities = [Favorite::class, SavedSearch::class, EditRecord::class], version = 1, exportSchema = false)
abstract class GalleryDb : RoomDatabase() { abstract fun dao(): GalleryDao }
