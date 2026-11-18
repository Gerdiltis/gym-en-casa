package com.gymencasa.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymencasa.data.remote.ProfileDto
import com.gymencasa.data.remote.SupabaseProvider
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import com.gymencasa.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User settings: cached locally in DataStore, mirrored to the Supabase `profiles` row. */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val supabase: SupabaseProvider,
    private val auth: AuthRepository,
) {
    private object Keys {
        val name = stringPreferencesKey("display_name")
        val level = stringPreferencesKey("level")
        val goal = stringPreferencesKey("goal")
        val equipment = stringSetPreferencesKey("equipment")
        val rest = intPreferencesKey("rest_between_sets_s")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val onboarded = booleanPreferencesKey("onboarded")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val pregnant = booleanPreferencesKey("pregnant")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.darkTheme] ?: true }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val updated = transform(current())
        context.dataStore.edit { p ->
            p[Keys.name] = updated.displayName
            p[Keys.level] = updated.level.slug
            p[Keys.goal] = updated.goal.slug
            p[Keys.equipment] = updated.equipment
            p[Keys.rest] = updated.restBetweenSetsSeconds.coerceIn(UserSettings.MIN_REST_SECONDS, UserSettings.MAX_REST_SECONDS)
            p[Keys.sound] = updated.soundEnabled
            p[Keys.vibration] = updated.vibrationEnabled
            p[Keys.onboarded] = updated.onboarded
            p[Keys.pregnant] = updated.pregnant
        }
        pushProfile(updated)
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.darkTheme] = dark }
    }

    /** After signing in on a new phone, restore the profile saved in the cloud. */
    suspend fun pullProfile() {
        val client = supabase.client ?: return
        val uid = auth.userId ?: return
        runCatching {
            client.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull<ProfileDto>()
        }.onSuccess { remote ->
            if (remote != null && remote.equipment.isNotEmpty()) {
                context.dataStore.edit { p ->
                    remote.displayName?.let { p[Keys.name] = it }
                    p[Keys.level] = Level.fromSlug(remote.level).slug
                    p[Keys.goal] = Goal.fromSlug(remote.goal).slug
                    p[Keys.equipment] = remote.equipment.toSet()
                    p[Keys.rest] = remote.restBetweenSetsSeconds
                    p[Keys.pregnant] = remote.pregnant
                    p[Keys.onboarded] = true
                }
            }
        }.onFailure { Log.w(TAG, "pullProfile failed", it) }
    }

    private suspend fun pushProfile(s: UserSettings) {
        val client = supabase.client ?: return
        val uid = auth.userId ?: return
        runCatching {
            client.from("profiles").upsert(
                ProfileDto(uid, s.displayName, s.level.slug, s.goal.slug, s.equipment.toList(), s.restBetweenSetsSeconds, s.pregnant),
            )
        }.onFailure { Log.w(TAG, "pushProfile failed", it) }
    }

    private fun Preferences.toSettings() = UserSettings(
        displayName = this[Keys.name] ?: "",
        level = Level.fromSlug(this[Keys.level]),
        goal = Goal.fromSlug(this[Keys.goal]),
        equipment = this[Keys.equipment] ?: emptySet(),
        restBetweenSetsSeconds = this[Keys.rest] ?: UserSettings.DEFAULT_REST_SECONDS,
        soundEnabled = this[Keys.sound] ?: true,
        vibrationEnabled = this[Keys.vibration] ?: true,
        onboarded = this[Keys.onboarded] ?: false,
        pregnant = this[Keys.pregnant] ?: false,
    )

    private companion object { const val TAG = "SettingsRepository" }
}
