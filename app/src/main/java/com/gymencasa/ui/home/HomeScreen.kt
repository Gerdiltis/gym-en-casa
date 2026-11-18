package com.gymencasa.ui.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraft
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.repository.AiRoutineRepository
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.ExerciseFilter
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.SuggestionEngine
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.UserSettings
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.ui.components.SectionTitle
import com.gymencasa.ui.labelRes
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUi(
    val settings: UserSettings = UserSettings(),
    val draft: DayDraft = DayDraft(),
    val availableCount: Int = 0,
    val aiLoading: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val ai: AiRoutineRepository,
    private val draftStore: DayDraftStore,
) : ViewModel() {
    private val aiLoading = MutableStateFlow(false)

    val ui: StateFlow<HomeUi> = combine(settings.settings, draftStore.draft, exercises.exercises, aiLoading) { s, d, all, loading ->
        HomeUi(s, d, ExerciseFilter.available(all, s).size, loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        // Start from the profile goal; in pregnancy mode make sure the goal is one offered there.
        viewModelScope.launch {
            val s = settings.current()
            draftStore.update { d ->
                when {
                    !d.goalSynced -> d.copy(goal = if (s.pregnant) Goal.PARTO else s.goal.takeIf { it != Goal.PARTO } ?: Goal.TONIFICAR, goalSynced = true)
                    d.goal !in Goal.available(s.pregnant) -> d.copy(goal = Goal.available(s.pregnant).first())
                    else -> d
                }
            }
        }
    }

    fun setDuration(min: Int) = draftStore.update { it.copy(durationMin = min) }
    fun setGoal(goal: Goal) = draftStore.update { it.copy(goal = goal) }
    fun setFocus(focus: Focus) = draftStore.update { it.copy(focus = focus) }

    fun startManual(onReady: () -> Unit) {
        draftStore.update { it.copy(selectedSlugs = emptyList(), plan = null) }
        onReady()
    }

    fun generateWithAi(defaultTitle: String, onReady: () -> Unit) {
        if (aiLoading.value) return
        aiLoading.value = true
        viewModelScope.launch {
            try {
                val s = settings.current()
                val d = draftStore.draft.value
                val available = ExerciseFilter.available(exercises.all(), s)
                if (available.isEmpty()) return@launch
                val recent = workouts.lastWorkoutSlugs()
                val bySlug = available.associateBy { it.slug }

                var title = defaultTitle
                var fallback = false
                val chosen = runCatching {
                    ai.generate(d.goal, d.durationMin, d.focus, s.level, available.map { it.slug }, recent.toList(), s.pregnant)
                }.onFailure { Log.w("Home", "AI failed", it) }.getOrNull()?.let { resp ->
                    if (resp.title.isNotBlank()) title = resp.title
                    resp.exerciseSlugs.mapNotNull(bySlug::get)
                }.orEmpty().ifEmpty {
                    fallback = true
                    SuggestionEngine.suggest(available, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, d.focus, recent, pregnant = s.pregnant)
                }
                val plan = PlanAlgorithm.build(chosen, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, WorkoutMode.AI, title, pregnant = s.pregnant)
                draftStore.update { it.copy(plan = plan, usedFallback = fallback) }
                onReady()
            } finally {
                aiLoading.value = false
            }
        }
    }
}

private val durations = listOf(10, 15, 20, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Navigator, vm: HomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var customOpen by remember { mutableStateOf(false) }
    val goalLabel = stringResource(ui.draft.goal.labelRes)
    val defaultTitle = stringResource(R.string.ai_title_default, goalLabel)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(
            (if (ui.settings.displayName.isNotBlank()) stringResource(R.string.home_greeting, ui.settings.displayName)
            else stringResource(R.string.home_greeting_anon)).uppercase(),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(stringResource(R.string.home_available_count, ui.availableCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.settings.pregnant) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PregnantWoman, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.pregnancy_banner), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionTitle(stringResource(R.string.home_how_long))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            durations.forEach { min ->
                RedChip(selected = ui.draft.durationMin == min, text = stringResource(R.string.minutes_short, min)) { vm.setDuration(min) }
            }
            val custom = ui.draft.durationMin !in durations
            RedChip(
                selected = custom,
                text = if (custom) stringResource(R.string.minutes_short, ui.draft.durationMin) else stringResource(R.string.home_custom_minutes),
            ) { customOpen = true }
        }

        SectionTitle(stringResource(R.string.home_goal))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.available(ui.settings.pregnant).forEach { g -> RedChip(ui.draft.goal == g, stringResource(g.labelRes)) { vm.setGoal(g) } }
        }

        SectionTitle(stringResource(R.string.home_focus))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Focus.entries.forEach { f -> RedChip(ui.draft.focus == f, stringResource(f.labelRes)) { vm.setFocus(f) } }
        }

        Spacer(Modifier.height(24.dp))
        if (ui.availableCount == 0) {
            Text(stringResource(R.string.home_no_exercises), color = MaterialTheme.colorScheme.error)
        }
        ActionCard(
            title = stringResource(R.string.home_generate_ai),
            subtitle = if (ui.aiLoading) stringResource(R.string.home_ai_working) else stringResource(R.string.home_generate_ai_desc),
            icon = Icons.Filled.AutoAwesome,
            primary = true,
            loading = ui.aiLoading,
            enabled = ui.availableCount > 0 && !ui.aiLoading,
        ) { vm.generateWithAi(defaultTitle) { nav.go(Route.Plan) } }
        Spacer(Modifier.height(12.dp))
        ActionCard(
            title = stringResource(R.string.home_choose_manual),
            subtitle = stringResource(R.string.home_choose_manual_desc),
            icon = Icons.Filled.TouchApp,
            primary = false,
            enabled = ui.availableCount > 0 && !ui.aiLoading,
        ) { vm.startManual { nav.go(Route.ManualSelect) } }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { nav.go(Route.Routines) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.List, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.home_my_routines).uppercase())
        }
        Spacer(Modifier.height(24.dp))
    }

    if (customOpen) {
        var value by remember { mutableFloatStateOf(ui.draft.durationMin.toFloat().coerceIn(5f, 90f)) }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text(stringResource(R.string.home_custom_minutes_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.minutes_short, value.roundToInt()), style = MaterialTheme.typography.headlineMedium)
                    Slider(value = value, onValueChange = { value = it }, valueRange = 5f..90f, steps = 16)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setDuration(value.roundToInt()); customOpen = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun RedChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(text, modifier = Modifier.padding(vertical = 8.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun ActionCard(
    title: String, subtitle: String, icon: ImageVector, primary: Boolean,
    enabled: Boolean, loading: Boolean = false, onClick: () -> Unit,
) {
    val container = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f), disabledContentColor = content.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = content, strokeWidth = 3.dp)
            else Icon(icon, null, Modifier.size(32.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title.uppercase(), style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
            }
        }
    }
}
