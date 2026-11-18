package com.gymencasa.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import com.gymencasa.data.local.AppDatabase
import com.gymencasa.data.local.ExerciseDao
import com.gymencasa.data.local.RoutineDao
import com.gymencasa.data.local.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gym.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides fun exerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun routineDao(db: AppDatabase): RoutineDao = db.routineDao()

    /** Disk cache for exercise videos streamed from Supabase Storage (saves free-tier bandwidth). */
    @androidx.annotation.OptIn(UnstableApi::class)
    @Provides @Singleton
    fun videoCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "videos"),
            LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        )
}
