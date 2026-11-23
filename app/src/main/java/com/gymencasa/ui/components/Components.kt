package com.gymencasa.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.gymencasa.ui.theme.GymColors
import com.gymencasa.ui.theme.LocalGymExtras
import com.gymencasa.ui.theme.SectionStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// ---------------------------------------------------------------- shapes & surfaces

val CardShape = RoundedCornerShape(22.dp)
val HeroShape = RoundedCornerShape(26.dp)
val PillShape = RoundedCornerShape(50)

/** Soft drop shadow used by every floating card (the reference's "lifted white" look). */
@Composable
fun Modifier.softShadow(shape: Shape = CardShape, elevation: Dp = 14.dp): Modifier {
    val c = LocalGymExtras.current.shadow
    return this.shadow(elevation, shape, clip = false, ambientColor = c, spotColor = c)
}

/** Page background: a faint vertical gradient behind the content. */
@Composable
fun Modifier.pageBackground(): Modifier = this.background(LocalGymExtras.current.pageGradient)

/** White rounded card with a soft shadow. */
@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = CardShape,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .softShadow(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Red gradient hero card (the "Today" card of the reference). Content is white. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(18.dp, HeroShape, clip = false, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
            .clip(HeroShape)
            .background(LocalGymExtras.current.heroGradient)
            .padding(18.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

// ---------------------------------------------------------------- pills & buttons

/** Selectable pill. [onHero] = translucent white style for use inside the red hero card. */
@Composable
fun PillChip(
    text: String, selected: Boolean, onHero: Boolean = false, modifier: Modifier = Modifier,
    icon: ImageVector? = null, onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg, border) = when {
        onHero && selected -> Triple(Color.White, cs.primary, Color.Transparent)
        onHero -> Triple(Color.White.copy(alpha = 0.16f), Color.White, Color.Transparent)
        selected -> Triple(cs.secondary, cs.onSecondary, Color.Transparent)
        else -> Triple(cs.surface, cs.onSurface, cs.outlineVariant)
    }
    Box(
        modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                Box(Modifier.size(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}

/** List row presented as a floating white card (library, history, routines…). */
@Composable
fun CardListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
) {
    GymCard(Modifier.fillMaxWidth().padding(outerPadding), padding = PaddingValues(0.dp), shape = RoundedCornerShape(20.dp)) {
        androidx.compose.material3.ListItem(
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = modifier,
        )
    }
}

/** Main call to action: red gradient pill. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(height)
            .then(if (enabled) Modifier.shadow(12.dp, PillShape, clip = false, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary) else Modifier)
            .clip(PillShape)
            .background(if (enabled) LocalGymExtras.current.heroGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Text("  ")
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary action: white pill with hairline border. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 48.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .height(height)
            .clip(PillShape)
            .background(cs.surface)
            .border(1.dp, cs.outlineVariant, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
            Text(" ")
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, maxLines = 1)
    }
}

/** Round grey icon button (back, more, close…). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = LocalGymExtras.current.chip,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(container).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size * 0.5f))
    }
}

/** Floating white pill holding a row of controls (top bar, session controls, bottom actions). */
@Composable
fun FloatingPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.softShadow(PillShape, 16.dp).clip(PillShape).background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Top bar as a floating pill: back · title/subtitle · trailing action. */
@Composable
fun TopPillBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { Box(Modifier.size(40.dp)) },
) {
    FloatingPill(modifier.fillMaxWidth()) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onClick = onBack)
        else Box(Modifier.size(40.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        trailing()
    }
}

// ---------------------------------------------------------------- text

/** Small uppercase section label, e.g. "PARA TI". */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SectionStyle, color = MaterialTheme.colorScheme.onBackground, modifier = modifier.padding(top = 22.dp, bottom = 10.dp))
}

/** Big sentence-case headline for top-level screens. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Coloured rounded-square icon used on quick-action tiles. */
@Composable
fun IconBadge(icon: ImageVector, background: Color, modifier: Modifier = Modifier, size: Dp = 38.dp, tint: Color = Color.White) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// ---------------------------------------------------------------- media

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
    corner: Dp = 28.dp,
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

    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .aspectRatio(1f)
            .softShadow(shape, 16.dp)
            .clip(shape)
            .background(GymColors.Graphite),
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
                Text(stringResource(R.string.video_unavailable), style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ExerciseThumb(thumbUri: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(16.dp)).background(GymColors.Graphite),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUri != null) {
            AsyncImage(model = thumbUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
