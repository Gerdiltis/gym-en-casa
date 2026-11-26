package com.gymencasa.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import com.gymencasa.ui.components.CardListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.local.WorkoutEntity
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(workouts: WorkoutRepository) : ViewModel() {
    val workouts: StateFlow<List<WorkoutEntity>> = workouts.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.forLanguageTag("es-ES"))

@Composable
fun HistoryScreen(nav: Navigator, vm: HistoryViewModel = hiltViewModel()) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(
                stringResource(R.string.history_title),
                subtitle = workouts.count { it.startedAt >= weekAgo }.let { pluralStringResource(R.plurals.history_week, it, it) },
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            if (workouts.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(workouts, key = { it.id }) { w ->
                val minutes = ((w.finishedAt - w.startedAt) / 60_000).toInt().coerceAtLeast(1)
                val date = dateFormat.format(Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()))
                CardListItem(
                    headlineContent = { Text(w.title) },
                    supportingContent = {
                        Text("$date · ${stringResource(R.string.minutes_short, minutes)}" +
                            (if (w.format == "circuit") " · ${stringResource(R.string.circuit_rounds_short, w.rounds)}" else "") +
                            if (!w.synced) " · ${stringResource(R.string.history_pending_sync)}" else "")
                    },
                    leadingContent = {
                        Icon(when { w.format == "circuit" -> Icons.Filled.Loop; w.mode == "ai" -> Icons.Filled.AutoAwesome; else -> Icons.Filled.TouchApp }, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = { if (!w.synced) Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.go(Route.Summary(w.id)) }
                )
            }
        }
    }
}
