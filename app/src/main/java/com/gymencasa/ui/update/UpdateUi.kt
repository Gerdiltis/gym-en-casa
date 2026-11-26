package com.gymencasa.ui.update

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.BuildConfig
import com.gymencasa.R
import com.gymencasa.ui.components.GradientButton
import com.gymencasa.ui.components.GymCard
import com.gymencasa.ui.components.PillButton
import com.gymencasa.ui.components.SectionTitle
import com.gymencasa.ui.theme.LocalGymExtras
import com.gymencasa.update.AvailableUpdate
import com.gymencasa.update.GitHubUpdater
import com.gymencasa.update.UpdateCheckPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data object NotConfigured : UpdateStatus
    data class Available(val update: AvailableUpdate) : UpdateStatus
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateStatus
    data class NeedsPermission(val update: AvailableUpdate) : UpdateStatus
    data class Failed(val message: String, val update: AvailableUpdate? = null) : UpdateStatus
}

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: GitHubUpdater) : ViewModel() {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status

    /** Last update found, so "Más tarde" + reopening shows it again even without a new network call. */
    private var known: AvailableUpdate? = null
    private var lastCheckMs: Long? = null

    /** Called every time the app comes to the foreground. */
    fun onAppForeground() {
        if (!updater.isConfigured) return
        when (val s = _status.value) {
            // Back from "install unknown apps" settings: continue on our own if it was granted.
            is UpdateStatus.NeedsPermission -> { if (updater.canInstall()) install(s.update); return }
            is UpdateStatus.Downloading, UpdateStatus.Checking -> return
            else -> Unit
        }
        known?.let { _status.value = UpdateStatus.Available(it) }
        val now = SystemClock.elapsedRealtime()
        if (!UpdateCheckPolicy.shouldCheck(lastCheckMs, now)) return
        lastCheckMs = now
        viewModelScope.launch {
            // Silent check: a network error at start-up must not bother the user.
            val found = runCatching { updater.check() }.getOrElse { return@launch }
            known = found
            val current = _status.value
            if (current is UpdateStatus.Downloading || current is UpdateStatus.NeedsPermission) return@launch
            _status.value = found?.let(UpdateStatus::Available) ?: if (current is UpdateStatus.Available) UpdateStatus.Idle else current
        }
    }

    /** Manual check from Settings. */
    fun check() {
        if (!updater.isConfigured) { _status.value = UpdateStatus.NotConfigured; return }
        _status.value = UpdateStatus.Checking
        viewModelScope.launch {
            lastCheckMs = SystemClock.elapsedRealtime()
            _status.value = runCatching { updater.check() }.fold(
                onSuccess = { known = it; it?.let(UpdateStatus::Available) ?: UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed(it.message ?: "?") },
            )
        }
    }

    fun install(update: AvailableUpdate) {
        if (!updater.canInstall()) {
            _status.value = UpdateStatus.NeedsPermission(update)
            updater.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch {
            _status.value = UpdateStatus.Downloading(update, 0f)
            runCatching { updater.download(update) { p -> _status.value = UpdateStatus.Downloading(update, p) } }
                .onSuccess { updater.install(it); _status.value = UpdateStatus.Idle }
                .onFailure { _status.value = UpdateStatus.Failed(it.message ?: "?", update) }
        }
    }

    /** "Más tarde": hidden until the next time the app is opened. */
    fun dismiss() { _status.value = UpdateStatus.Idle }
}

/** Update notice: shown when the app opens (and from Settings) whenever a newer release exists. */
@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val s = status
    val update = when (s) {
        is UpdateStatus.Available -> s.update
        is UpdateStatus.Downloading -> s.update
        is UpdateStatus.NeedsPermission -> s.update
        is UpdateStatus.Failed -> s.update
        else -> return
    }
    val downloading = s is UpdateStatus.Downloading
    Dialog(
        onDismissRequest = { if (!downloading) vm.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !downloading, dismissOnBackPress = !downloading),
    ) {
        GymCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), padding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(LocalGymExtras.current.heroGradient),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(14.dp))
                Text(
                    update?.let { stringResource(R.string.update_available_title, it.version) } ?: stringResource(R.string.update_error_title),
                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                val message = when (s) {
                    is UpdateStatus.NeedsPermission -> stringResource(R.string.update_permission)
                    is UpdateStatus.Failed -> stringResource(R.string.update_error, s.message)
                    else -> stringResource(R.string.update_available_text)
                }
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                if (s is UpdateStatus.Available && update != null && update.notes.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        SectionTitle(stringResource(R.string.update_whats_new))
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant).verticalScroll(rememberScrollState()).padding(12.dp),
                        ) { Text(update.notes.take(600), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (s is UpdateStatus.Downloading) {
                    Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(CircleShape))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (update != null) {
                            GradientButton(stringResource(R.string.update_now), Modifier.fillMaxWidth(), icon = Icons.Filled.Download) { vm.install(update) }
                        }
                        PillButton(stringResource(if (update != null) R.string.update_later else R.string.action_close), Modifier.fillMaxWidth(), onClick = vm::dismiss)
                    }
                }
            }
        }
    }
}
