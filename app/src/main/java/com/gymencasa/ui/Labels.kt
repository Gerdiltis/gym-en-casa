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
import androidx.compose.ui.graphics.vector.ImageVector
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
    }

/** Label for an equipment slug coming from the catalog (unknown slugs fall back to the slug). */
@StringRes
fun equipmentLabelRes(slug: String): Int? = Equipment.fromSlug(slug)?.labelRes

val unknownIcon = Icons.Filled.Inventory2

@get:StringRes
val Goal.labelRes: Int
    get() = when (this) {
        Goal.FUERZA -> R.string.goal_fuerza
        Goal.CARDIO -> R.string.goal_cardio
        Goal.TONIFICAR -> R.string.goal_tonificar
        Goal.MOVILIDAD -> R.string.goal_movilidad
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

/** Muscle group slugs → readable Spanish (catalog data, not UI chrome). */
fun muscleLabel(slug: String): String = slug.replace('_', ' ').replaceFirstChar { it.uppercase() }
