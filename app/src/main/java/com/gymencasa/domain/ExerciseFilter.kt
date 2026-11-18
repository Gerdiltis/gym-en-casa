package com.gymencasa.domain

import com.gymencasa.domain.model.Equipment
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.Pregnancy
import com.gymencasa.domain.model.UserSettings

/**
 * Decides which exercises a user can do.
 * - Equipment: every requirement must be met. A requirement may list alternatives with `|`
 *   ("gomas|bandas_largas" = either one). "ninguno" is always met.
 * - Pregnancy mode: exercises marked [Pregnancy.NO] are hidden.
 */
object ExerciseFilter {
    private const val NONE = "ninguno"

    fun alternatives(requirement: String): List<String> = requirement.split('|').map(String::trim).filter(String::isNotEmpty)

    private fun isMet(requirement: String, owned: Set<String>): Boolean =
        alternatives(requirement).any { it == NONE || it in owned }

    /** Unmet requirements (each may contain alternatives separated by `|`). */
    fun missing(exercise: Exercise, owned: Set<String>): Set<String> =
        exercise.equipment.filterNot { isMet(it, owned) }.toSet()

    fun hasEquipment(exercise: Exercise, owned: Set<String>): Boolean = missing(exercise, owned).isEmpty()

    fun suitableForPregnancy(exercise: Exercise): Boolean = exercise.pregnancy != Pregnancy.NO

    fun isAvailable(exercise: Exercise, settings: UserSettings): Boolean =
        hasEquipment(exercise, settings.equipment) && (!settings.pregnant || suitableForPregnancy(exercise))

    fun available(exercises: List<Exercise>, settings: UserSettings): List<Exercise> =
        exercises.filter { isAvailable(it, settings) }

    /** Every equipment slug an exercise can use (for labels). */
    fun allSlugs(exercise: Exercise): List<String> = exercise.equipment.flatMap(::alternatives).filter { it != Equipment.NINGUNO.slug }
}
