package com.gymencasa.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Lists are stored as JSON: values may contain "|" (equipment alternatives such as "gomas|bandas_largas"). */
class Converters {
    @TypeConverter fun fromList(value: List<String>): String = kotlinx.serialization.json.Json.encodeToString(value)
    @TypeConverter fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else kotlinx.serialization.json.Json.decodeFromString(value)
}

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val description: String,
    val muscleGroups: List<String>,
    val equipment: List<String>,
    val type: String,
    val difficulty: Int,
    val unilateral: Boolean,
    val videoPath: String?,
    val thumbPath: String?,
    /** "apto", "parto" or null (not suitable in pregnancy). */
    val pregnancy: String? = null,
    /** Free labels such as "calistenia" (JSON list). */
    val tags: List<String> = emptyList(),
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val title: String,
    val mode: String,
    val goal: String,
    val durationTargetMin: Int,
    val restSeconds: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val completed: Boolean,
    val synced: Boolean = false,
)

@Entity(tableName = "workout_sets", primaryKeys = ["workoutId", "position"], indices = [Index("workoutId")])
data class WorkoutSetEntity(
    val workoutId: String,
    val position: Int,
    val exerciseSlug: String,
    val setIndex: Int,
    val plannedSeconds: Int,
    val skipped: Boolean,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val name: String,
    val goal: String,
    val durationTargetMin: Int,
    val createdAt: Long,
    val synced: Boolean = false,
)

@Entity(tableName = "routine_items", primaryKeys = ["routineId", "position"], indices = [Index("routineId")])
data class RoutineItemEntity(
    val routineId: String,
    val position: Int,
    val exerciseSlug: String,
    val sets: Int,
    val workSeconds: Int,
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(items: List<ExerciseEntity>)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: String): WorkoutEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutId = :id ORDER BY position")
    suspend fun sets(id: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workouts WHERE synced = 0")
    suspend fun unsynced(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): WorkoutEntity?

    @Query("UPDATE workouts SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(w: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Transaction
    suspend fun insert(w: WorkoutEntity, sets: List<WorkoutSetEntity>) {
        insertWorkout(w); insertSets(sets)
    }
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_items WHERE routineId = :id ORDER BY position")
    suspend fun items(id: String): List<RoutineItemEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE synced = 0")
    suspend fun unsynced(): List<RoutineEntity>

    @Query("UPDATE routines SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM routine_items WHERE routineId = :id")
    suspend fun deleteItems(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(r: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RoutineItemEntity>)

    @Transaction
    suspend fun insert(r: RoutineEntity, items: List<RoutineItemEntity>) {
        insertRoutine(r); deleteItems(r.id); insertItems(items)
    }

    @Transaction
    suspend fun delete(id: String) {
        deleteItems(id); deleteRoutine(id)
    }
}

@Database(
    entities = [ExerciseEntity::class, WorkoutEntity::class, WorkoutSetEntity::class, RoutineEntity::class, RoutineItemEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        /** v2: pregnancy suitability per exercise. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN pregnancy TEXT")
                // v1 stored lists joined with "|"; the catalog is reloaded from the APK / Supabase on start.
                db.execSQL("DELETE FROM exercises")
            }
        }

        /** v3: free tags per exercise (e.g. "calistenia"). */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
                // The catalog is reloaded from the APK / Supabase on start.
                db.execSQL("DELETE FROM exercises")
            }
        }
    }

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
}
