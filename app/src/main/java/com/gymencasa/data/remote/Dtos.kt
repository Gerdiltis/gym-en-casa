package com.gymencasa.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of the `exercises` table, also the format of the bundled `assets/catalog/exercises.json`. */
@Serializable
data class ExerciseDto(
    val slug: String,
    val name: String,
    val description: String = "",
    @SerialName("muscle_groups") val muscleGroups: List<String> = emptyList(),
    val equipment: List<String> = listOf("ninguno"),
    val type: String = "fuerza",
    val difficulty: Int = 1,
    val unilateral: Boolean = false,
    @SerialName("video_path") val videoPath: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    val pregnancy: String? = null,
)

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val level: String? = null,
    val goal: String? = null,
    val equipment: List<String> = emptyList(),
    @SerialName("rest_between_sets_s") val restBetweenSetsSeconds: Int = 30,
    val pregnant: Boolean = false,
)

@Serializable
data class WorkoutSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val mode: String,
    val goal: String,
    @SerialName("duration_target_min") val durationTargetMin: Int,
    @SerialName("started_at") val startedAt: String,
    @SerialName("finished_at") val finishedAt: String,
    val completed: Boolean,
)

@Serializable
data class SessionSetDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    val position: Int,
    @SerialName("exercise_slug") val exerciseSlug: String,
    @SerialName("set_index") val setIndex: Int,
    @SerialName("planned_s") val plannedSeconds: Int,
    val skipped: Boolean,
)

@Serializable
data class RoutineDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val goal: String,
    @SerialName("duration_target_min") val durationTargetMin: Int,
)

@Serializable
data class RoutineItemDto(
    @SerialName("routine_id") val routineId: String,
    @SerialName("user_id") val userId: String,
    val position: Int,
    @SerialName("exercise_slug") val exerciseSlug: String,
    val sets: Int,
    @SerialName("work_s") val workSeconds: Int,
)

@Serializable
data class GenerateRoutineRequest(
    val goal: String,
    @SerialName("duration_min") val durationMin: Int,
    val focus: String,
    val level: String,
    /** Slugs the app considers available (already filtered by the user's equipment). */
    @SerialName("available_slugs") val availableSlugs: List<String>,
    @SerialName("recent_slugs") val recentSlugs: List<String> = emptyList(),
    val pregnant: Boolean = false,
)

@Serializable
data class GenerateRoutineResponse(
    val title: String = "",
    @SerialName("exercise_slugs") val exerciseSlugs: List<String> = emptyList(),
    val source: String? = null,
)
