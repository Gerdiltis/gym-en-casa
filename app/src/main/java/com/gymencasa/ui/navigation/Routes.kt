package com.gymencasa.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data object Library : Route
    @Serializable data object History : Route
    @Serializable data object Settings : Route

    @Serializable data object ManualSelect : Route
    /** Library opened from the manual selection to add more exercises. */
    @Serializable data object LibraryPicker : Route
    @Serializable data class ExerciseDetail(val slug: String) : Route
    @Serializable data object Plan : Route
    @Serializable data object Session : Route
    @Serializable data class Summary(val workoutId: String) : Route
    @Serializable data object Routines : Route
    @Serializable data object EquipmentEdit : Route
}

val topLevelRoutes: List<Route> = listOf(Route.Home, Route.Library, Route.History, Route.Settings)
