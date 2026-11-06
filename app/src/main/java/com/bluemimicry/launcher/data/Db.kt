package com.bluemimicry.launcher.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** A floating neumorphic group container on the Classic home screen. Position is fractional (0..1). */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val x: Float,          // left edge, fraction of home width
    val y: Float,          // top edge, fraction of home height
    val w: Float,          // width, fraction of home width
    val cols: Int = 3,     // big-slot columns
    val sort: Int = 0,
)

/** Membership. `big` apps get prominent slots; the rest are packed into the small cluster. */
@Entity(tableName = "folder_apps", indices = [Index("folderId")])
data class FolderAppEntity(
    @PrimaryKey val key: String,
    val folderId: Long,
    val position: Int,
    val big: Boolean = true,
)

@Entity(tableName = "dock")
data class DockEntity(@PrimaryKey val position: Int, val key: String)

/** Per-app launcher-side overrides. Never touches the real package. */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val customLabel: String? = null,
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunch: Long = 0,
    val iconSizeScale: Float = 1f,
    val customIconPath: String? = null,
)

/** A saved display-engine environment (mode + its tunables serialised as JSON). */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val json: String,
)

/** A real AppWidgetHost widget placed on the widgets page (fractions of page size). */
@Entity(tableName = "widgets")
data class WidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val x: Float, val y: Float, val w: Float, val h: Float,
)

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widgets") fun all(): Flow<List<WidgetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(w: WidgetEntity)
    @Delete suspend fun delete(w: WidgetEntity)
}

@Dao
interface LayoutDao {
    @Query("SELECT * FROM folders ORDER BY sort, id") fun folders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folder_apps ORDER BY folderId, position") fun folderApps(): Flow<List<FolderAppEntity>>
    @Query("SELECT * FROM dock ORDER BY position") fun dock(): Flow<List<DockEntity>>
    @Query("SELECT COUNT(*) FROM folders") suspend fun folderCount(): Int
    @Query("SELECT `key` FROM folder_apps") suspend fun placedKeys(): List<String>
    @Query("SELECT `key` FROM dock") suspend fun dockKeys(): List<String>
    @Query("SELECT * FROM folders ORDER BY sort, id") suspend fun foldersNow(): List<FolderEntity>

    @Insert suspend fun insertFolder(f: FolderEntity): Long
    @Update suspend fun updateFolder(f: FolderEntity)
    @Delete suspend fun deleteFolder(f: FolderEntity)
    @Query("DELETE FROM folder_apps WHERE folderId = :id") suspend fun clearFolder(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFolderApps(items: List<FolderAppEntity>)
    @Query("DELETE FROM folder_apps WHERE `key` = :key") suspend fun removeFromFolders(key: String)

    @Query("DELETE FROM dock") suspend fun clearDock()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDock(items: List<DockEntity>)

    @Transaction
    suspend fun replaceDock(keys: List<String>) {
        clearDock()
        putDock(keys.mapIndexed { i, k -> DockEntity(i, k) })
    }

    @Transaction
    suspend fun moveApp(key: String, toFolder: Long, position: Int, big: Boolean) {
        removeFromFolders(key)
        putFolderApps(listOf(FolderAppEntity(key, toFolder, position, big)))
    }
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM app_meta") fun all(): Flow<List<AppMetaEntity>>
    @Query("SELECT * FROM app_meta WHERE `key` = :key") suspend fun get(key: String): AppMetaEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(m: AppMetaEntity)

    @Transaction
    suspend fun edit(key: String, block: (AppMetaEntity) -> AppMetaEntity) {
        put(block(get(key) ?: AppMetaEntity(key)))
    }

    @Transaction
    suspend fun recordLaunch(key: String, now: Long) = edit(key) {
        it.copy(launchCount = it.launchCount + 1, lastLaunch = now)
    }
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY id DESC") fun all(): Flow<List<PresetEntity>>
    @Insert suspend fun insert(p: PresetEntity): Long
    @Delete suspend fun delete(p: PresetEntity)
}

@Database(
    entities = [FolderEntity::class, FolderAppEntity::class, DockEntity::class, AppMetaEntity::class, PresetEntity::class, WidgetEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LauncherDb : RoomDatabase() {
    abstract fun layout(): LayoutDao
    abstract fun meta(): MetaDao
    abstract fun presets(): PresetDao
    abstract fun widgets(): WidgetDao

    companion object {
        fun build(ctx: Context): LauncherDb =
            Room.databaseBuilder(ctx, LauncherDb::class.java, "launcher.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
