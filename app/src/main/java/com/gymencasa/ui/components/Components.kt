package com.gymencasa.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import com.gymencasa.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoCacheEntryPoint {
    fun cache(): SimpleCache
}

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, uri: String): ExoPlayer {
    val upstream = DefaultDataSource.Factory(context)
    val factory = if (uri.startsWith("http")) {
        val cache = EntryPointAccessors.fromApplication(context, VideoCacheEntryPoint::class.java).cache()
        CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
    } else upstream
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
        .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
}

/** Looping, muted exercise animation (3–5 s clip). Falls back to the thumbnail / a placeholder. */
@Composable
fun ExerciseVideo(
    videoUri: String?,
    thumbUri: String?,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    corner: Dp = 20.dp,
) {
    val context = LocalContext.current
    var failed by remember(videoUri) { mutableStateOf(false) }
    val player = remember(videoUri) { videoUri?.let { buildPlayer(context, it) } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener); player?.release() }
    }
    LaunchedEffect(player, playing) { player?.playWhenReady = playing }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUri != null) {
            AsyncImage(model = thumbUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (player != null && !failed) {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
        } else if (thumbUri == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.video_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ExerciseThumb(thumbUri: String?, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUri != null) {
            AsyncImage(model = thumbUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
