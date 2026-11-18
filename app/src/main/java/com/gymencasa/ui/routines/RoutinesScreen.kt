package com.gymencasa.ui.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.local.RoutineEntity
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val draftStore: DayDraftStore,
) : ViewModel() {
    val routines: StateFlow<List<RoutineEntity>> = workouts.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Loads a saved routine as today's plan. Rest always comes from the current global setting. */
    fun use(id: String, onReady: () -> Unit) {
        viewModelScope.launch {
            val saved = workouts.routine(id) ?: return@launch
            val bySlug = exercises.all().associateBy { it.slug }
            val items = saved.items.mapNotNull { i -> bySlug[i.exerciseSlug]?.let { PlannedExercise(it, i.sets, i.workSeconds) } }
            if (items.isEmpty()) return@launch
            val plan = DayPlan(saved.routine.name, Goal.fromSlug(saved.routine.goal), WorkoutMode.MANUAL,
                saved.routine.durationTargetMin, settings.current().restBetweenSetsSeconds, items)
            draftStore.update { it.copy(plan = plan, durationMin = plan.durationTargetMin, goal = plan.goal, usedFallback = false) }
            onReady()
        }
    }

    fun delete(id: String) { viewModelScope.launch { workouts.deleteRoutine(id) } }
}

@Composable
fun RoutinesScreen(nav: Navigator, vm: RoutinesViewModel = hiltViewModel()) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) { ScreenHeader(stringResource(R.string.routines_title)) }
        LazyColumn(Modifier.weight(1f)) {
            if (routines.isEmpty()) {
                item { Text(stringResource(R.string.routines_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(routines, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = { Text(stringResource(R.string.minutes_short, r.durationTargetMin)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.delete(r.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                            Button(onClick = { vm.use(r.id) { nav.go(Route.Plan) } }) { Text(stringResource(R.string.routines_use)) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
