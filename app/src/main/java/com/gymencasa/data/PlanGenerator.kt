package com.gymencasa.data

import android.util.Log
import com.gymencasa.data.repository.AiRoutineRepository
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.ExerciseFilter
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.SuggestionEngine
import com.gymencasa.domain.model.WorkoutMode
import javax.inject.Inject
import javax.inject.Singleton

/** Builds today's AI plan (with local fallback) from the current draft and stores it in [DayDraftStore]. */
@Singleton
class PlanGenerator @Inject constructor(
    private val settings: SettingsRepository,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val ai: AiRoutineRepository,
    private val draftStore: DayDraftStore,
) {
    /** Returns false when no exercise is available with the user's equipment / mode. */
    suspend fun generateAi(defaultTitle: String, avoid: Set<String> = emptySet()): Boolean {
        val s = settings.current()
        val d = draftStore.draft.value
        val available = ExerciseFilter.available(exercises.all(), s)
        if (available.isEmpty()) return false
        val recent = workouts.lastWorkoutSlugs() + avoid
        val bySlug = available.associateBy { it.slug }

        var title = defaultTitle
        var fallback = false
        val chosen = runCatching {
            ai.generate(d.goal, d.durationMin, d.focus, s.level, available.map { it.slug }, recent.toList(), s.pregnant)
        }.onFailure { Log.w(TAG, "AI failed", it) }.getOrNull()?.let { resp ->
            if (resp.title.isNotBlank()) title = resp.title
            resp.exerciseSlugs.mapNotNull(bySlug::get)
        }.orEmpty().ifEmpty {
            fallback = true
            SuggestionEngine.suggest(available, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, d.focus, recent, pregnant = s.pregnant)
        }
        val plan = PlanAlgorithm.build(chosen, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, WorkoutMode.AI, title, pregnant = s.pregnant)
        draftStore.update { it.copy(plan = plan, usedFallback = fallback, generatedAt = System.currentTimeMillis()) }
        return true
    }

    private companion object { const val TAG = "PlanGenerator" }
}
