package com.gymencasa.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraft
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.PlanGenerator
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.model.SessionFormat
import com.gymencasa.ui.components.PillShape
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.ExerciseFilter
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.UserSettings
import com.gymencasa.ui.components.GymCard
import com.gymencasa.ui.components.HeroCard
import com.gymencasa.ui.components.IconBadge
import com.gymencasa.ui.components.PillChip
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Weekly target shown in the hero ring. */
const val WEEKLY_TARGET = 3

data class HomeUi(
    val settings: UserSettings = UserSettings(),
    val draft: DayDraft = DayDraft(),
    val availableCount: Int = 0,
    val weekSessions: Int = 0,
    val aiLoading: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    exercises: ExerciseRepository,
    workouts: WorkoutRepository,
    private val generator: PlanGenerator,
    private val draftStore: DayDraftStore,
) : ViewModel() {
    private val aiLoading = MutableStateFlow(false)

    val ui: StateFlow<HomeUi> = combine(settings.settings, draftStore.draft, exercises.exercises, workouts.workouts, aiLoading) { s, d, all, ws, loading ->
        val weekStart = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        HomeUi(s, d, ExerciseFilter.available(all, s).size, ws.count { it.startedAt >= weekStart }, loading)
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
    fun setFormat(f: SessionFormat) = draftStore.update { it.copy(format = f) }
    fun setRounds(n: Int) = draftStore.update { it.copy(rounds = n) }
    fun setPerRound(n: Int) = draftStore.update { it.copy(exercisesPerRound = n) }
    fun setCircuitWork(sec: Int) = draftStore.update { it.copy(circuitWorkSeconds = sec) }
    fun setRoundRest(sec: Int) = draftStore.update { it.copy(roundRestSeconds = sec) }
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
                if (generator.generateAi(defaultTitle)) onReady()
            } finally {
                aiLoading.value = false
            }
        }
    }
}

private val durations = listOf(10, 15, 20, 30, 45)
private val roundOptions = listOf(2, 3, 4, 5)
private val workOptions = listOf(20, 30, 40, 45, 60)
private val roundRestOptions = listOf(30, 60, 90, 120)
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-ES"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Navigator, vm: HomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var customOpen by remember { mutableStateOf(false) }
    var roundsOpen by remember { mutableStateOf(false) }
    val goalLabel = stringResource(ui.draft.goal.labelRes)
    val defaultTitle = stringResource(R.string.ai_title_default, goalLabel)
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when (hour) {
            in 6..13 -> R.string.home_greeting_morning
            in 14..20 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    val name = ui.settings.displayName.trim()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        // Brand row
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.FitnessCenter, MaterialTheme.colorScheme.primary, size = 30.dp)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "G" },
                    color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Greeting
        Text(
            if (name.isNotEmpty()) "$greeting," else greeting,
            style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 18.dp),
        )
        if (name.isNotEmpty()) Text(name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "${LocalDate.now().format(dateFormat).replaceFirstChar { it.uppercase() }} · " +
                stringResource(R.string.home_available_count, ui.availableCount),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (ui.settings.pregnant) {
            GymCard(Modifier.fillMaxWidth().padding(top = 14.dp), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PregnantWoman, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.pregnancy_banner), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Hero: today's session
        HeroCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                        // Session type: Series (current flow) or Circuit (rounds of one pass per exercise)
                        Row(
                            Modifier.padding(top = 10.dp).clip(PillShape).background(Color.White.copy(alpha = 0.16f)).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            SessionFormat.entries.forEach { f ->
                                PillChip(
                                    stringResource(if (f == SessionFormat.SERIES) R.string.format_series else R.string.format_circuit),
                                    ui.draft.format == f, onHero = true,
                                ) { vm.setFormat(f) }
                            }
                        }
                    }
                    WeekRing(ui.weekSessions, WEEKLY_TARGET)
                }
                val circuit = ui.draft.format == SessionFormat.CIRCUIT
                if (!circuit) {
                    HeroLabel(stringResource(R.string.home_how_long))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        durations.forEach { min ->
                            PillChip(stringResource(R.string.minutes_compact, min), ui.draft.durationMin == min, onHero = true) { vm.setDuration(min) }
                        }
                        val custom = ui.draft.durationMin !in durations
                        PillChip(if (custom) stringResource(R.string.minutes_compact, ui.draft.durationMin) else "+", custom, onHero = true) { customOpen = true }
                    }
                } else {
                    val maxRounds = if (ui.settings.pregnant) PlanAlgorithm.PREGNANCY_MAX_ROUNDS else PlanAlgorithm.MAX_ROUNDS
                    HeroLabel(stringResource(R.string.circuit_rounds))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        roundOptions.filter { it <= maxRounds }.forEach { n -> PillChip("$n", ui.draft.rounds == n, onHero = true) { vm.setRounds(n) } }
                        val custom = ui.draft.rounds !in roundOptions
                        PillChip(if (custom) "${ui.draft.rounds}" else "+", custom, onHero = true) { roundsOpen = true }
                    }
                    HeroLabel(stringResource(R.string.circuit_per_round))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (3..8).forEach { n -> PillChip("$n", ui.draft.exercisesPerRound == n, onHero = true) { vm.setPerRound(n) } }
                    }
                    HeroLabel(stringResource(R.string.circuit_work_seconds))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        workOptions.filter { !ui.settings.pregnant || it <= PlanAlgorithm.PREGNANCY_MAX_WORK_SECONDS }.forEach { sec ->
                            PillChip(stringResource(R.string.seconds_short, sec), ui.draft.circuitWorkSeconds == sec, onHero = true) { vm.setCircuitWork(sec) }
                        }
                    }
                    HeroLabel(stringResource(R.string.circuit_round_rest))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        roundRestOptions.forEach { sec ->
                            PillChip(stringResource(R.string.seconds_short, sec), ui.draft.roundRestSeconds == sec, onHero = true) { vm.setRoundRest(sec) }
                        }
                    }
                    Text(
                        stringResource(R.string.circuit_estimate, PlanGenerator.estimateCircuitMinutes(ui.draft, ui.settings.restBetweenSetsSeconds), ui.settings.restBetweenSetsSeconds),
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 10.dp),
                    )
                }
                HeroLabel(stringResource(R.string.home_goal))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Goal.available(ui.settings.pregnant).forEach { g ->
                        PillChip(stringResource(g.labelRes), ui.draft.goal == g, onHero = true) { vm.setGoal(g) }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.home_focus))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Focus.entries.forEach { f -> PillChip(stringResource(f.labelRes), ui.draft.focus == f) { vm.setFocus(f) } }
        }

        SectionTitle(stringResource(R.string.home_for_you))
        if (ui.availableCount == 0) {
            Text(stringResource(R.string.home_no_exercises), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        val canStart = ui.availableCount > 0 && !ui.aiLoading
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                Modifier.weight(1f), Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary,
                stringResource(R.string.home_generate_ai),
                if (ui.aiLoading) stringResource(R.string.home_ai_working) else stringResource(R.string.home_generate_ai_desc),
                loading = ui.aiLoading, enabled = canStart,
            ) { vm.generateWithAi(defaultTitle) { nav.go(Route.Plan) } }
            ActionTile(
                Modifier.weight(1f), Icons.Filled.TouchApp, MaterialTheme.colorScheme.secondary,
                stringResource(R.string.home_choose_manual), stringResource(R.string.home_choose_manual_desc), enabled = canStart,
                iconTint = MaterialTheme.colorScheme.onSecondary,
            ) { vm.startManual { nav.go(Route.ManualSelect) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                Modifier.weight(1f), Icons.AutoMirrored.Filled.List, MaterialTheme.colorScheme.onSurfaceVariant,
                stringResource(R.string.home_my_routines), stringResource(R.string.home_my_routines_desc),
            ) { nav.go(Route.Routines) }
            ActionTile(
                Modifier.weight(1f), Icons.Filled.FitnessCenter, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                stringResource(R.string.tab_library), stringResource(R.string.home_library_desc),
            ) { nav.resetTo(Route.Library) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (roundsOpen) {
        val maxR = if (ui.settings.pregnant) PlanAlgorithm.PREGNANCY_MAX_ROUNDS else PlanAlgorithm.MAX_ROUNDS
        var value by remember { mutableFloatStateOf(ui.draft.rounds.toFloat().coerceIn(1f, maxR.toFloat())) }
        AlertDialog(
            onDismissRequest = { roundsOpen = false },
            title = { Text(stringResource(R.string.circuit_rounds)) },
            text = {
                Column {
                    Text("${value.roundToInt()}", style = MaterialTheme.typography.headlineMedium)
                    Slider(value = value, onValueChange = { value = it }, valueRange = 1f..maxR.toFloat(), steps = maxR - 2)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setRounds(value.roundToInt()); roundsOpen = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { roundsOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
private fun HeroLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

/** Kept for other screens that still use it. */
@Composable
fun RedChip(selected: Boolean, text: String, onClick: () -> Unit) = PillChip(text, selected, onClick = onClick)

@Composable
private fun WeekRing(done: Int, target: Int) {
    val progress = (done.toFloat() / target).coerceIn(0f, 1f)
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.22f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(Color.White, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$target", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(stringResource(R.string.home_week_label), color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier, icon: ImageVector, iconBg: Color, title: String, subtitle: String,
    loading: Boolean = false, enabled: Boolean = true, iconTint: Color = Color.White, onClick: () -> Unit,
) {
    GymCard(modifier.height(138.dp), onClick = if (enabled) onClick else null) {
        Column(Modifier.fillMaxSize()) {
            Row {
                if (loading) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp, color = iconBg)
                    }
                } else IconBadge(icon, if (enabled) iconBg else iconBg.copy(alpha = 0.4f), tint = iconTint)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.NorthEast, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

