package com.gymencasa.data.repository

import android.content.Context
import android.util.Log
import com.gymencasa.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gymencasa.data.local.RoutineDao
import com.gymencasa.data.local.RoutineEntity
import com.gymencasa.data.local.RoutineItemEntity
import com.gymencasa.data.local.WorkoutDao
import com.gymencasa.data.local.WorkoutEntity
import com.gymencasa.data.local.WorkoutSetEntity
import com.gymencasa.data.sync.SyncWorker
import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.session.CompletedSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SavedRoutine(val routine: RoutineEntity, val items: List<RoutineItemEntity>)

/** Workouts and routines: saved locally first, then pushed to Supabase by [SyncWorker]. */
@Singleton
class WorkoutRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val auth: AuthRepository,
    private val supabase: SupabaseProvider,
) {
    val workouts: Flow<List<WorkoutEntity>> = workoutDao.observeAll()
    val routines: Flow<List<RoutineEntity>> = routineDao.observeAll()

    suspend fun saveWorkout(
        plan: DayPlan,
        startedAtEpochMs: Long,
        finishedAtEpochMs: Long,
        sets: List<CompletedSet>,
        completed: Boolean,
    ): String {
        val id = UUID.randomUUID().toString()
        workoutDao.insert(
            WorkoutEntity(
                id = id, userId = auth.userId, title = plan.title, mode = plan.mode.slug, goal = plan.goal.slug,
                durationTargetMin = plan.durationTargetMin, restSeconds = plan.restSeconds,
                startedAt = startedAtEpochMs, finishedAt = finishedAtEpochMs, completed = completed,
            ),
            sets.mapIndexed { i, s -> WorkoutSetEntity(id, i, s.exerciseSlug, s.setIndex, s.plannedSeconds, s.skipped) },
        )
        requestSync()
        return id
    }

    suspend fun workout(id: String) = workoutDao.get(id)
    suspend fun workoutSets(id: String) = workoutDao.sets(id)

    /** Slugs of the last workout, so suggestions can avoid repeating it. */
    suspend fun lastWorkoutSlugs(): Set<String> =
        workoutDao.latest()?.let { w -> workoutDao.sets(w.id).map { it.exerciseSlug }.toSet() }.orEmpty()

    suspend fun saveRoutine(name: String, plan: DayPlan): String {
        val id = UUID.randomUUID().toString()
        routineDao.insert(
            RoutineEntity(id, auth.userId, name, plan.goal.slug, plan.durationTargetMin, System.currentTimeMillis()),
            plan.items.mapIndexed { i, p -> RoutineItemEntity(id, i, p.exercise.slug, p.sets, p.workSeconds) },
        )
        requestSync()
        return id
    }

    suspend fun routine(id: String): SavedRoutine? =
        routineDao.get(id)?.let { SavedRoutine(it, routineDao.items(id)) }

    suspend fun deleteRoutine(id: String) {
        routineDao.delete(id)
        supabase.client?.let { client ->
            runCatching { client.from("routines").delete { filter { eq("id", id) } } }
                .onFailure { Log.w("WorkoutRepository", "remote routine delete failed", it) }
        }
    }

    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncWorker.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
