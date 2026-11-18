package com.gymencasa.domain

import com.gymencasa.domain.model.Equipment
import com.gymencasa.domain.model.Exercise

/** An exercise is available only when the user owns every piece of equipment it needs. */
object EquipmentFilter {
    private val alwaysAvailable = setOf(Equipment.NINGUNO.slug)

    fun missing(exercise: Exercise, owned: Set<String>): Set<String> =
        exercise.equipment.filterNot { it in alwaysAvailable || it in owned }.toSet()

    fun isAvailable(exercise: Exercise, owned: Set<String>): Boolean = missing(exercise, owned).isEmpty()

    fun available(exercises: List<Exercise>, owned: Set<String>): List<Exercise> =
        exercises.filter { isAvailable(it, owned) }
}
