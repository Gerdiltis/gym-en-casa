package com.gymencasa.data.repository

import android.content.Context
import android.util.Log
import com.gymencasa.data.local.ExerciseDao
import com.gymencasa.data.local.ExerciseEntity
import com.gymencasa.data.remote.ExerciseDto
import com.gymencasa.data.remote.SupabaseProvider
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Pregnancy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exercise catalog. Room is the local cache; it is seeded from the catalog bundled in the APK
 * (works offline / before the backend exists) and refreshed from Supabase when online.
 */
@Singleton
class ExerciseRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ExerciseDao,
    private val supabase: SupabaseProvider,
    private val json: Json,
) {
    val exercises: Flow<List<Exercise>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<Exercise> = dao.getAll().map { it.toDomain() }

    private val bundledVideos: Set<String> by lazy {
        runCatching { context.assets.list("videos")?.toSet() }.getOrNull().orEmpty()
    }
    private val bundledThumbs: Set<String> by lazy {
        runCatching { context.assets.list("thumbs")?.toSet() }.getOrNull().orEmpty()
    }

    /** Bundled video first (instant, offline, no bandwidth); otherwise Supabase Storage. */
    fun videoUri(ex: Exercise): String? =
        if ("${ex.slug}.mp4" in bundledVideos) "asset:///videos/${ex.slug}.mp4"
        else ex.videoPath?.let(supabase::publicMediaUrl)

    fun thumbUri(ex: Exercise): String? =
        if ("${ex.slug}.webp" in bundledThumbs) "file:///android_asset/thumbs/${ex.slug}.webp"
        else ex.thumbPath?.let(supabase::publicMediaUrl)

    /** Upserts the catalog bundled in this APK (new versions may add exercises), then the cloud refresh overrides it. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.assets.open("catalog/exercises.json").bufferedReader().use { it.readText() }
            val items = json.decodeFromString<List<ExerciseDto>>(text)
            dao.upsertAll(items.map { it.toEntity() })
        }.onFailure { Log.w(TAG, "No bundled catalog", it) }
    }

    suspend fun refreshFromRemote() = withContext(Dispatchers.IO) {
        val client = supabase.client ?: return@withContext
        runCatching { client.from("exercises").select().decodeList<ExerciseDto>() }
            .onSuccess { if (it.isNotEmpty()) dao.upsertAll(it.map { dto -> dto.toEntity() }) }
            .onFailure { Log.w(TAG, "refreshFromRemote failed", it) }
    }

    private fun ExerciseDto.toEntity() = ExerciseEntity(
        slug, name, description, muscleGroups, equipment.ifEmpty { listOf("ninguno") }, type, difficulty, unilateral,
        videoPath ?: "videos/$slug.mp4", thumbPath ?: "thumbs/$slug.webp", pregnancy, tags,
    )

    private fun ExerciseEntity.toDomain() = Exercise(
        slug, name, description, muscleGroups, equipment.toSet(), ExerciseType.fromSlug(type), difficulty, unilateral,
        videoPath, thumbPath, Pregnancy.fromSlug(pregnancy), tags.toSet(),
    )

    private companion object { const val TAG = "ExerciseRepository" }
}
