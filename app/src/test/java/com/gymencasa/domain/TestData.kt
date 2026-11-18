package com.gymencasa.domain

import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Pregnancy

fun ex(
    slug: String,
    equipment: Set<String> = setOf("ninguno"),
    type: ExerciseType = ExerciseType.FUERZA,
    muscles: List<String> = listOf("piernas"),
    unilateral: Boolean = false,
    pregnancy: Pregnancy = Pregnancy.NO,
) = Exercise(slug, slug, "", muscles, equipment, type, 1, unilateral, null, null, pregnancy)

val catalog = listOf(
    ex("sentadilla", muscles = listOf("piernas")),
    ex("flexion", muscles = listOf("pecho")),
    ex("plancha", type = ExerciseType.CORE, muscles = listOf("core")),
    ex("goblet_squat", equipment = setOf("mancuerna"), muscles = listOf("piernas")),
    ex("remo_goma", equipment = setOf("gomas"), muscles = listOf("espalda")),
    ex("fondos_silla", equipment = setOf("silla"), muscles = listOf("triceps")),
    ex("zancada", muscles = listOf("gluteos"), unilateral = true),
    ex("jumping_jacks", type = ExerciseType.CARDIO, muscles = listOf("cardio")),
    ex("burpee", type = ExerciseType.CARDIO, muscles = listOf("cuerpo_completo")),
    ex("press_hombro_mancuerna", equipment = setOf("mancuerna"), muscles = listOf("hombros")),
    ex("puente_gluteo", muscles = listOf("gluteos")),
    ex("superman", type = ExerciseType.CORE, muscles = listOf("lumbar")),
    ex("foam_cuadriceps", equipment = setOf("foam"), type = ExerciseType.RECUPERACION, muscles = listOf("cuadriceps")),
    ex("gato_camello", type = ExerciseType.MOVILIDAD, muscles = listOf("espalda")),
)
