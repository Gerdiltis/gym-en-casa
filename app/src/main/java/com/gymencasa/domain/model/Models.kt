package com.gymencasa.domain.model

/** Equipment slugs shared with the Supabase `equipment` table and the exercise catalog. */
enum class Equipment(val slug: String) {
    NINGUNO("ninguno"),
    GOMAS("gomas"),
    ESTERILLA("esterilla"),
    MANCUERNA("mancuerna"),
    FOAM("foam"),
    SILLA("silla"),
    PARED("pared"),
    ESCALON("escalon"),
    SOFA("sofa"),
    TOALLA("toalla"),
    MOCHILA("mochila"),
    BOTELLAS("botellas"),
    PELOTA("pelota"),
    BANDAS_LARGAS("bandas_largas");

    companion object {
        /** Gym-style equipment, shown first in the picker. */
        val gym = listOf(GOMAS, BANDAS_LARGAS, ESTERILLA, MANCUERNA, FOAM, PELOTA)
        val household = listOf(SILLA, PARED, ESCALON, SOFA, TOALLA, MOCHILA, BOTELLAS)
        fun fromSlug(slug: String): Equipment? = entries.firstOrNull { it.slug == slug }
    }
}

enum class ExerciseType(val slug: String) {
    FUERZA("fuerza"), CARDIO("cardio"), CORE("core"), MOVILIDAD("movilidad"), RECUPERACION("recuperacion");

    companion object {
        fun fromSlug(slug: String): ExerciseType = entries.firstOrNull { it.slug == slug } ?: FUERZA
    }
}

enum class Goal(val slug: String) {
    FUERZA("fuerza"), CARDIO("cardio"), TONIFICAR("tonificar"), MOVILIDAD("movilidad"),

    /** Labour preparation, only offered in pregnancy mode. */
    PARTO("parto");

    companion object {
        /** Goals offered on Home: PARTO only in pregnancy mode. */
        fun available(pregnant: Boolean): List<Goal> = if (pregnant) listOf(PARTO, TONIFICAR, MOVILIDAD, FUERZA) else entries - PARTO
        fun fromSlug(slug: String?): Goal = entries.firstOrNull { it.slug == slug } ?: TONIFICAR
    }
}

enum class Level(val slug: String) {
    PRINCIPIANTE("principiante"), INTERMEDIO("intermedio"), AVANZADO("avanzado");

    companion object {
        fun fromSlug(slug: String?): Level = entries.firstOrNull { it.slug == slug } ?: INTERMEDIO
    }
}

/** Suitability during pregnancy (conservative). NO = not shown in pregnancy mode. */
enum class Pregnancy(val slug: String?) {
    NO(null), APTO("apto"), PARTO("parto");

    companion object {
        fun fromSlug(slug: String?): Pregnancy = entries.firstOrNull { it.slug != null && it.slug == slug } ?: NO
    }
}

/** Body area the user wants to focus on today. */
enum class Focus(val slug: String, val muscleGroups: Set<String>) {
    CUERPO_COMPLETO("cuerpo_completo", emptySet()),
    TREN_SUPERIOR("tren_superior", setOf("pecho", "espalda", "hombros", "brazos", "biceps", "triceps")),
    TREN_INFERIOR("tren_inferior", setOf("piernas", "cuadriceps", "isquios", "gluteos", "gemelos", "aductores")),
    CORE("core", setOf("core", "abdominales", "oblicuos", "lumbar")),
}

data class Exercise(
    val slug: String,
    val name: String,
    val description: String,
    val muscleGroups: List<String>,
    /** Every item the exercise needs. "ninguno" means no equipment. */
    val equipment: Set<String>,
    val type: ExerciseType,
    val difficulty: Int,
    val unilateral: Boolean,
    val videoPath: String?,
    val thumbPath: String?,
    val pregnancy: Pregnancy = Pregnancy.NO,
)

data class PlannedExercise(
    val exercise: Exercise,
    val sets: Int,
    /** Work seconds per set (already doubled for unilateral exercises). */
    val workSeconds: Int,
)

data class DayPlan(
    val title: String,
    val goal: Goal,
    val mode: WorkoutMode,
    val durationTargetMin: Int,
    /** Global rest between sets, always taken from the user's settings. */
    val restSeconds: Int,
    val items: List<PlannedExercise>,
)

enum class WorkoutMode(val slug: String) { AI("ai"), MANUAL("manual") }

data class UserSettings(
    val displayName: String = "",
    val level: Level = Level.INTERMEDIO,
    val goal: Goal = Goal.TONIFICAR,
    val equipment: Set<String> = emptySet(),
    val restBetweenSetsSeconds: Int = DEFAULT_REST_SECONDS,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val onboarded: Boolean = false,
    val pregnant: Boolean = false,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 30
        const val MIN_REST_SECONDS = 10
        const val MAX_REST_SECONDS = 120
        const val REST_STEP_SECONDS = 5
    }
}
