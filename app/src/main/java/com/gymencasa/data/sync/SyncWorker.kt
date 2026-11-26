package com.gymencasa.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gymencasa.data.local.RoutineDao
import com.gymencasa.data.local.WorkoutDao
import com.gymencasa.data.remote.RoutineDto
import com.gymencasa.data.remote.RoutineItemDto
import com.gymencasa.data.remote.SessionSetDto
import com.gymencasa.data.remote.SupabaseProvider
import com.gymencasa.data.remote.WorkoutSessionDto
import com.gymencasa.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.postgrest.from
import java.time.Instant

/** Pushes locally saved workouts and routines to Supabase. Retries with backoff when offline. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val supabase: SupabaseProvider,
    private val auth: AuthRepository,
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val client = supabase.client ?: return Result.success()
        val uid = auth.userId ?: return Result.success()
        return try {
            for (w in workoutDao.unsynced()) {
                client.from("workout_sessions").upsert(
                    WorkoutSessionDto(
                        w.id, uid, w.title, w.mode, w.goal, w.durationTargetMin,
                        Instant.ofEpochMilli(w.startedAt).toString(), Instant.ofEpochMilli(w.finishedAt).toString(), w.completed,
                        w.format, w.rounds,
                    ),
                )
                val sets = workoutDao.sets(w.id).map {
                    SessionSetDto(w.id, uid, it.position, it.exerciseSlug, it.setIndex, it.plannedSeconds, it.skipped)
                }
                if (sets.isNotEmpty()) client.from("session_sets").upsert(sets)
                workoutDao.markSynced(w.id)
            }
            for (r in routineDao.unsynced()) {
                client.from("routines").upsert(RoutineDto(r.id, uid, r.name, r.goal, r.durationTargetMin, r.format, r.rounds, r.roundRestSeconds))
                val items = routineDao.items(r.id).map {
                    RoutineItemDto(r.id, uid, it.position, it.exerciseSlug, it.sets, it.workSeconds)
                }
                if (items.isNotEmpty()) client.from("routine_items").upsert(items)
                routineDao.markSynced(r.id)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(NAME, "sync failed", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object { const val NAME = "sync" }
}
