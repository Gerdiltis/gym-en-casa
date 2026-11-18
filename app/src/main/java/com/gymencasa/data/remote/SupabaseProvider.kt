package com.gymencasa.data.remote

import com.gymencasa.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Supabase client, or null when the build has no Supabase URL / anon key
 * (the app then works in local-only mode, useful before the backend exists).
 */
@Singleton
class SupabaseProvider @Inject constructor() {
    val isConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) null
        else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }

    fun publicMediaUrl(path: String): String? =
        if (!isConfigured) null
        else "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$MEDIA_BUCKET/${path.trimStart('/')}"

    companion object {
        const val MEDIA_BUCKET = "exercise-media"
    }
}
