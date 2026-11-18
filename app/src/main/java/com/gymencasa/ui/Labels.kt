package com.gymencasa.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.Crop75
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.gymencasa.R
import com.gymencasa.domain.model.Equipment
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level

@get:StringRes
val Equipment.labelRes: Int
    get() = when (this) {
        Equipment.NINGUNO -> R.string.eq_ninguno
        Equipment.GOMAS -> R.string.eq_gomas
        Equipment.ESTERILLA -> R.string.eq_esterilla
        Equipment.MANCUERNA -> R.string.eq_mancuerna
        Equipment.FOAM -> R.string.eq_foam
        Equipment.SILLA -> R.string.eq_silla
        Equipment.PARED -> R.string.eq_pared
        Equipment.ESCALON -> R.string.eq_escalon
        Equipment.SOFA -> R.string.eq_sofa
        Equipment.TOALLA -> R.string.eq_toalla
        Equipment.MOCHILA -> R.string.eq_mochila
        Equipment.BOTELLAS -> R.string.eq_botellas
        Equipment.PELOTA -> R.string.eq_pelota
        Equipment.BANDAS_LARGAS -> R.string.eq_bandas_largas
    }

val Equipment.icon: ImageVector
    get() = when (this) {
        Equipment.NINGUNO -> Icons.Filled.CheckCircle
        Equipment.GOMAS -> Icons.Filled.Cable
        Equipment.ESTERILLA -> Icons.Filled.ViewDay
        Equipment.MANCUERNA -> Icons.Filled.FitnessCenter
        Equipment.FOAM -> Icons.Filled.Crop75
        Equipment.SILLA -> Icons.Filled.Chair
        Equipment.PARED -> Icons.Filled.Straighten
        Equipment.ESCALON -> Icons.Filled.Stairs
        Equipment.SOFA -> Icons.Filled.Weekend
        Equipment.TOALLA -> Icons.Filled.DryCleaning
        Equipment.MOCHILA -> Icons.Filled.Backpack
        Equipment.BOTELLAS -> Icons.Filled.LocalDrink
        Equipment.PELOTA -> Icons.Filled.SportsVolleyball
        Equipment.BANDAS_LARGAS -> Icons.Filled.LinearScale
    }

/** Label for an equipment slug coming from the catalog (unknown slugs fall back to the slug). */
@StringRes
fun equipmentLabelRes(slug: String): Int? = Equipment.fromSlug(slug)?.labelRes

/** "gomas|bandas_largas" -> "Gomas elásticas o Bandas largas". */
@Composable
fun equipmentRequirementLabel(requirement: String): String =
    requirement.split('|').map { s -> equipmentLabelRes(s)?.let { stringResource(it) } ?: s }
        .joinToString(" ${stringResource(R.string.or_word)} ")

val unknownIcon = Icons.Filled.Inventory2

@get:StringRes
val Goal.labelRes: Int
    get() = when (this) {
        Goal.FUERZA -> R.string.goal_fuerza
        Goal.CARDIO -> R.string.goal_cardio
        Goal.TONIFICAR -> R.string.goal_tonificar
        Goal.MOVILIDAD -> R.string.goal_movilidad
        Goal.PARTO -> R.string.goal_parto
    }

@get:StringRes
val Level.labelRes: Int
    get() = when (this) {
        Level.PRINCIPIANTE -> R.string.level_principiante
        Level.INTERMEDIO -> R.string.level_intermedio
        Level.AVANZADO -> R.string.level_avanzado
    }

@get:StringRes
val Focus.labelRes: Int
    get() = when (this) {
        Focus.CUERPO_COMPLETO -> R.string.focus_cuerpo_completo
        Focus.TREN_SUPERIOR -> R.string.focus_tren_superior
        Focus.TREN_INFERIOR -> R.string.focus_tren_inferior
        Focus.CORE -> R.string.focus_core
    }

@get:StringRes
val ExerciseType.labelRes: Int
    get() = when (this) {
        ExerciseType.FUERZA -> R.string.type_fuerza
        ExerciseType.CARDIO -> R.string.type_cardio
        ExerciseType.CORE -> R.string.type_core
        ExerciseType.MOVILIDAD -> R.string.type_movilidad
        ExerciseType.RECUPERACION -> R.string.type_recuperacion
    }

private val muscleRes = mapOf(
    "pecho" to R.string.muscle_pecho, "espalda" to R.string.muscle_espalda, "hombros" to R.string.muscle_hombros,
    "brazos" to R.string.muscle_brazos, "biceps" to R.string.muscle_biceps, "triceps" to R.string.muscle_triceps,
    "piernas" to R.string.muscle_piernas, "cuadriceps" to R.string.muscle_cuadriceps, "isquios" to R.string.muscle_isquios,
    "gluteos" to R.string.muscle_gluteos, "gemelos" to R.string.muscle_gemelos, "aductores" to R.string.muscle_aductores,
    "core" to R.string.muscle_core, "abdominales" to R.string.muscle_abdominales, "oblicuos" to R.string.muscle_oblicuos,
    "lumbar" to R.string.muscle_lumbar, "cardio" to R.string.muscle_cardio, "cuerpo_completo" to R.string.muscle_cuerpo_completo,
)

/** Muscle group slug → Spanish label from strings.xml (unknown slugs are prettified). */
@Composable
fun muscleLabel(slug: String): String =
    muscleRes[slug]?.let { stringResource(it) } ?: slug.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun muscleList(slugs: List<String>): String = slugs.map { muscleLabel(it) }.joinToString(" · ")

/** Muscle filter chips in the library: label → slugs it matches. */
enum class MuscleFilter(@param:StringRes val label: Int, val slugs: Set<String>) {
    PECHO(R.string.muscle_pecho, setOf("pecho")),
    ESPALDA(R.string.muscle_espalda, setOf("espalda", "lumbar")),
    HOMBROS(R.string.muscle_hombros, setOf("hombros")),
    BRAZOS(R.string.muscle_brazos, setOf("brazos", "biceps", "triceps")),
    PIERNAS(R.string.muscle_piernas, setOf("piernas", "cuadriceps", "isquios", "gemelos", "aductores")),
    GLUTEOS(R.string.muscle_gluteos, setOf("gluteos")),
    CORE(R.string.muscle_core, setOf("core", "abdominales", "oblicuos")),
    CARDIO(R.string.muscle_cardio, setOf("cardio", "cuerpo_completo")),
}
