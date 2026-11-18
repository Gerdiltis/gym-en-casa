package com.gymencasa.data.repository

import com.gymencasa.data.remote.GenerateRoutineRequest
import com.gymencasa.data.remote.GenerateRoutineResponse
import com.gymencasa.data.remote.SupabaseProvider
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Calls the `generate-routine` Edge Function, which holds the OpenRouter key server-side. */
@Singleton
class AiRoutineRepository @Inject constructor(
    private val supabase: SupabaseProvider,
    private val json: Json,
) {
    val isAvailable: Boolean get() = supabase.isConfigured

    suspend fun generate(
        goal: Goal,
        durationMin: Int,
        focus: Focus,
        level: Level,
        availableSlugs: List<String>,
        recentSlugs: List<String>,
        pregnant: Boolean = false,
    ): GenerateRoutineResponse {
        val client = supabase.client ?: error("IA no disponible: Supabase no configurado")
        val response = withTimeout(45_000) {
            client.functions.invoke(
                function = "generate-routine",
                body = GenerateRoutineRequest(goal.slug, durationMin, focus.slug, level.slug, availableSlugs, recentSlugs, pregnant),
            )
        }
        val parsed = json.decodeFromString<GenerateRoutineResponse>(response.bodyAsText())
        // Never trust the model blindly: keep only exercises the user can actually do.
        val allowed = availableSlugs.toSet()
        return parsed.copy(exerciseSlugs = parsed.exerciseSlugs.filter { it in allowed }.distinct())
    }
}
