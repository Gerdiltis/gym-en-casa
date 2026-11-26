package com.gymencasa.ui.settings

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gymencasa.ui.components.PillButton
import com.gymencasa.ui.components.GymCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.BuildConfig
import com.gymencasa.R
import com.gymencasa.data.repository.AuthRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import com.gymencasa.ui.equipment.PregnancySwitch
import com.gymencasa.domain.model.UserSettings
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.components.SectionTitle
import com.gymencasa.ui.equipment.LevelPicker
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import com.gymencasa.ui.update.UpdateDialog
import com.gymencasa.ui.update.UpdateStatus
import com.gymencasa.ui.update.UpdateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    private val draftStore: com.gymencasa.data.DayDraftStore,
) : ViewModel() {
    val current: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val dark: StateFlow<Boolean> = settings.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val email: String? get() = auth.email

    fun update(f: (UserSettings) -> UserSettings) { viewModelScope.launch { settings.update(f) } }
    /** Pregnancy mode: labour preparation becomes the default goal; turning it off leaves that goal. */
    fun setPregnant(v: Boolean) {
        viewModelScope.launch {
            settings.update { s ->
                s.copy(pregnant = v, goal = if (v) Goal.PARTO else if (s.goal == Goal.PARTO) Goal.TONIFICAR else s.goal)
            }
            draftStore.update { it.copy(goalSynced = false, selectedSlugs = emptyList(), plan = null) }
        }
    }

    fun setDark(v: Boolean) { viewModelScope.launch { settings.setDarkTheme(v) } }
    fun signOut() { viewModelScope.launch { auth.signOut() } }
}

@Composable
fun SettingsScreen(nav: Navigator, vm: SettingsViewModel = hiltViewModel(), updateVm: UpdateViewModel = hiltViewModel()) {
    val s by vm.current.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val updateStatus by updateVm.status.collectAsStateWithLifecycle()
    val settings = s ?: return

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(stringResource(R.string.settings_title))

        SectionTitle(stringResource(R.string.settings_training))
        GymCard(Modifier.fillMaxWidth()) { Column {
        // Global rest between sets: applies to every workout.
        var rest by remember(settings.restBetweenSetsSeconds) { mutableFloatStateOf(settings.restBetweenSetsSeconds.toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_rest), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_rest_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.seconds_short, rest.roundToInt()), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        val steps = (UserSettings.MAX_REST_SECONDS - UserSettings.MIN_REST_SECONDS) / UserSettings.REST_STEP_SECONDS - 1
        Slider(
            value = rest,
            onValueChange = { rest = it },
            onValueChangeFinished = { vm.update { it.copy(restBetweenSetsSeconds = rest.roundToInt()) } },
            valueRange = UserSettings.MIN_REST_SECONDS.toFloat()..UserSettings.MAX_REST_SECONDS.toFloat(),
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )

        PregnancySwitch(settings.pregnant, vm::setPregnant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_level), style = MaterialTheme.typography.titleMedium)
        LevelPicker(settings.level) { level: Level -> vm.update { it.copy(level = level) } }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).clickable { nav.go(Route.EquipmentEdit) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_equipment), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_equipment_count, settings.equipment.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
        SwitchRow(stringResource(R.string.settings_sound), settings.soundEnabled) { v -> vm.update { it.copy(soundEnabled = v) } }
        SwitchRow(stringResource(R.string.settings_vibration), settings.vibrationEnabled) { v -> vm.update { it.copy(vibrationEnabled = v) } }
        } }

        SectionTitle(stringResource(R.string.settings_appearance))
        GymCard(Modifier.fillMaxWidth()) { SwitchRow(stringResource(R.string.settings_dark_theme), dark, vm::setDark) }

        SectionTitle(stringResource(R.string.settings_account))
        GymCard(Modifier.fillMaxWidth()) { Column {
        var name by remember { mutableStateOf(settings.displayName) }
        LaunchedEffect(settings.displayName) { if (name.isEmpty()) name = settings.displayName }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) }, singleLine = true,
            label = { Text(stringResource(R.string.settings_name)) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (name.trim() != settings.displayName) {
                    androidx.compose.material3.TextButton(onClick = { vm.update { it.copy(displayName = name.trim()) } }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            },
        )
        Text(vm.email ?: stringResource(R.string.settings_local_mode), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        PillButton(stringResource(R.string.settings_sign_out), Modifier.fillMaxWidth(), onClick = vm::signOut)
        } }

        SectionTitle(stringResource(R.string.settings_app))
        GymCard(Modifier.fillMaxWidth()) { Column {
        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PillButton(stringResource(R.string.settings_check_update), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = updateVm::check)
        when (updateStatus) {
            UpdateStatus.UpToDate -> Text(stringResource(R.string.settings_up_to_date), color = MaterialTheme.colorScheme.primary)
            UpdateStatus.NotConfigured -> Text(stringResource(R.string.settings_updates_not_configured), color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateStatus.Checking -> Text(stringResource(R.string.loading))
            else -> Unit
        }
        } }
        Spacer(Modifier.height(24.dp))
    }
    UpdateDialog(updateVm)
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
    }
}
