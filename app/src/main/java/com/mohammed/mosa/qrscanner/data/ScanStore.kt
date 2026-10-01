package com.mohammed.mosa.qrscanner.data


import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val format: String,
    val type: String,
    val isLink: Boolean,
    val isFavorite: Boolean = false,
    val isGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: ScanEntity): Long

    /** Re-inserts previous rows (with their ids) — used by the delete undo flow. */
    @Insert
    suspend fun insertAll(scans: List<ScanEntity>)

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ScanEntity>

    @Query("UPDATE scans SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT value FROM scans WHERE createdAt >= :since ORDER BY createdAt DESC LIMIT 1")
    suspend fun recentValue(since: Long): String?

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scans WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM scans")
    suspend fun clearAll()
}

@Database(entities = [ScanEntity::class], version = 2, exportSchema = false)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        /** Preserves existing history when upgrading from v1. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scans ADD COLUMN isGenerated INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: ScannerDatabase? = null
        fun get(context: Context): ScannerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScannerDatabase::class.java,
                    "scanner.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}