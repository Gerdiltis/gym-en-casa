package com.gymencasa.ui.summary

import com.gymencasa.ui.components.GymCard
import com.gymencasa.ui.components.GradientButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.gymencasa.data.local.WorkoutEntity
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.ui.components.SectionTitle
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUi(val workout: WorkoutEntity? = null, val doneSets: Int = 0, val exerciseLines: List<String> = emptyList())

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SummaryUi())
    val ui: StateFlow<SummaryUi> = _ui

    fun load(id: String) {
        if (_ui.value.workout?.id == id) return
        viewModelScope.launch {
            val w = workouts.workout(id) ?: return@launch
            val sets = workouts.workoutSets(id)
            val names = exercises.all().associate { it.slug to it.name }
            val lines = sets.groupBy { it.exerciseSlug }.map { (slug, s) ->
                "${names[slug] ?: slug} · ${s.count { !it.skipped }}/${s.size}"
            }
            _ui.value = SummaryUi(w, sets.count { !it.skipped }, lines)
        }
    }
}

@Composable
fun SummaryScreen(workoutId: String, nav: Navigator, vm: SummaryViewModel = hiltViewModel()) {
    vm.load(workoutId)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val w = ui.workout
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Text(
            stringResource(if (w?.completed != false) R.string.summary_title else R.string.summary_incomplete),
            style = MaterialTheme.typography.displaySmall,
        )
        if (w != null) {
            val minutes = ((w.finishedAt - w.startedAt) / 60_000).toInt().coerceAtLeast(1)
            Text(w.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.summary_stats, ui.doneSets, minutes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.summary_exercises))
            GymCard(Modifier.fillMaxWidth()) {
                Column { ui.exerciseLines.forEach { Text(it, Modifier.padding(vertical = 5.dp)) } }
            }
        }
        Spacer(Modifier.height(32.dp))
        GradientButton(stringResource(R.string.summary_done), modifier = Modifier.fillMaxWidth()) { nav.resetTo(Route.Home) }
    }
}
