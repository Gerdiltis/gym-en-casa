package com.gymencasa.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.update.AvailableUpdate
import com.gymencasa.update.GitHubUpdater
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
    data class Failed(val message: String) : UpdateStatus
}

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: GitHubUpdater) : ViewModel() {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status

    fun check() {
        if (!updater.isConfigured) { _status.value = UpdateStatus.NotConfigured; return }
        _status.value = UpdateStatus.Checking
        viewModelScope.launch {
            _status.value = runCatching { updater.check() }.fold(
                onSuccess = { it?.let(UpdateStatus::Available) ?: UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed(it.message ?: "?") },
            )
        }
    }

    fun offer(update: AvailableUpdate) { _status.value = UpdateStatus.Available(update) }

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
                .onFailure { _status.value = UpdateStatus.Failed(it.message ?: "?") }
        }
    }

    fun dismiss() { _status.value = UpdateStatus.Idle }
}

/** Dialog for an update found at start-up or from Settings. */
@Composable
fun UpdateDialog(initial: AvailableUpdate?, onDismissInitial: () -> Unit, vm: UpdateViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val shown = when (val s = status) {
        UpdateStatus.Idle -> initial?.let { UpdateStatus.Available(it) }
        is UpdateStatus.Available, is UpdateStatus.Downloading, is UpdateStatus.NeedsPermission, is UpdateStatus.Failed -> s
        else -> null
    } ?: return
    val dismiss = { vm.dismiss(); onDismissInitial() }
    when (shown) {
        is UpdateStatus.Available -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_available_title, shown.update.version)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_available_text))
                    if (shown.update.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(shown.update.notes.take(600), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.install(shown.update) }) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) } },
        )
        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, (shown.progress * 100).toInt())) },
            text = { LinearProgressIndicator(progress = { shown.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is UpdateStatus.NeedsPermission -> AlertDialog(
            onDismissRequest = dismiss,
            text = { Text(stringResource(R.string.update_permission)) },
            confirmButton = { TextButton(onClick = { vm.install(shown.update) }) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) } },
        )
        is UpdateStatus.Failed -> AlertDialog(
            onDismissRequest = dismiss,
            text = { Text(stringResource(R.string.update_error, shown.message)) },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } },
        )
        else -> Unit
    }
}
