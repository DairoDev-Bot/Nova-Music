package com.nova.music

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { NovaScreen() }
    }
}

// Android 13 en adelante usa un permiso propio para el audio; antes se usaba el de almacenamiento
private val AudioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(AudioPermission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    runCatching { context.startActivity(intent) }
}

@Composable
private fun NovaScreen() {
    val context = LocalContext.current
    val library: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(this[APPLICATION_KEY] as Application) }
        }
    )
    val player: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlayerViewModel(this[APPLICATION_KEY] as Application) }
        }
    )
    val libraryState by library.state.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val selectedTab = NovaTab.entries[selectedTabIndex]
    // Guarda el estado de cada pestaña al cambiar de una a otra
    val stateHolder = rememberSaveableStateHolder()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) library.load() else deniedOnce = true
    }

    // El permiso de avisos solo hace falta para ver los controles en la notificación; la música suena igual sin él
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !askedNotifications &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Al volver a la app (por ejemplo desde Ajustes) se revisa el permiso y se actualiza la lista
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = hasAudioPermission(context)
        if (hasPermission) library.load()
    }

    // La barra de progreso solo se mueve mientras la app está a la vista
    LifecycleEventEffect(Lifecycle.Event.ON_START) { player.setUiVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.setUiVisible(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Fondo con textura: llena la pantalla y mantiene la luz arriba a la derecha
        Image(
            painter = painterResource(R.drawable.nova_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            // Contenido: empieza debajo de la barra de estado del teléfono
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                NovaHeader(onSettingsClick = { /* Ajustes llegará en un paso posterior */ })
                stateHolder.SaveableStateProvider(selectedTab.name) {
                    when (selectedTab) {
                        NovaTab.Home -> HomeContent(
                            hasPermission = hasPermission,
                            deniedOnce = deniedOnce,
                            libraryState = libraryState,
                            playerState = playerState,
                            player = player,
                            onPlayStarted = { askNotificationPermissionOnce() },
                            onAllow = { permissionLauncher.launch(AudioPermission) },
                            onOpenSettings = { openAppSettings(context) },
                            onRetry = { library.load() },
                        )
                        NovaTab.Explore -> MessagePanel(
                            title = "Explorar",
                            message = "Próximamente: búsqueda de canciones, artistas y álbumes.",
                        )
                        NovaTab.Profile -> MessagePanel(
                            title = "Perfil",
                            message = "Próximamente: aquí estará tu Nova Wrapped, con tus canciones más escuchadas.",
                        )
                    }
                }
            }
            NovaBottomBar(
                selected = selectedTab,
                onSelect = { selectedTabIndex = it.ordinal },
            )
        }
    }
}

@Composable
private fun ColumnScope.HomeContent(
    hasPermission: Boolean,
    deniedOnce: Boolean,
    libraryState: LibraryState,
    playerState: PlayerUiState,
    player: PlayerViewModel,
    onPlayStarted: () -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!hasPermission) {
        MessagePanel(
            modifier = Modifier.weight(1f),
            title = "Tu música",
            message = if (deniedOnce) {
                "Sin ese permiso no puedo mostrar tus canciones. Si ya no te lo pide, actívalo en los ajustes de la app."
            } else {
                "Para mostrar tu música, Nova necesita permiso para leer las canciones de tu teléfono. Todo se queda en tu teléfono."
            },
            actionLabel = if (deniedOnce) "Intentar de nuevo" else "Permitir acceso",
            onAction = onAllow,
            secondaryLabel = if (deniedOnce) "Abrir ajustes" else null,
            onSecondary = onOpenSettings,
        )
        return
    }

    when (libraryState) {
        LibraryState.Loading -> Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        LibraryState.Empty -> MessagePanel(
            modifier = Modifier.weight(1f),
            title = "Sin canciones",
            message = "No encontré música en tu teléfono. Copia o descarga canciones y vuelve a abrir Nova.",
        )
        LibraryState.Failed -> MessagePanel(
            modifier = Modifier.weight(1f),
            title = "No pude leer tu música",
            message = "Algo falló al leer las canciones.",
            actionLabel = "Reintentar",
            onAction = onRetry,
        )
        is LibraryState.Ready -> LibraryContent(
            songs = libraryState.songs,
            playerState = playerState,
            player = player,
            onPlayStarted = onPlayStarted,
        )
    }
}

@Composable
private fun ColumnScope.LibraryContent(
    songs: List<Song>,
    playerState: PlayerUiState,
    player: PlayerViewModel,
    onPlayStarted: () -> Unit,
) {
    // Si todavía no suena nada, se muestra la primera canción de la lista
    val current = songs.firstOrNull { it.id == playerState.currentSongId } ?: songs.first()
    val durationMs = if (playerState.durationMs > 0L) playerState.durationMs else current.durationMs
    // Solo la barra lee la posición, así no se redibuja toda la pantalla cada vez que avanza
    val position = player.position.collectAsStateWithLifecycle()

    key(current.id) {
        NowPlayingCard(
            song = current,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 24.dp),
        )
    }
    PlayerControls(
        isPlaying = playerState.isPlaying,
        position = position,
        durationMs = durationMs,
        shuffleOn = playerState.shuffleOn,
        repeatMode = playerState.repeatMode,
        onPlayPause = {
            onPlayStarted()
            player.togglePlayPause(songs, current.id)
        },
        onPrevious = player::previous,
        onNext = player::next,
        onShuffle = player::toggleShuffle,
        onRepeat = player::cycleRepeat,
        onSeek = player::seekToFraction,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
    )
    SongList(
        songs = songs,
        currentSongId = current.id,
        onSongClick = { index ->
            onPlayStarted()
            player.playFrom(songs, index)
        },
        modifier = Modifier
            .weight(1f)
            .padding(top = 24.dp),
    )
}

@Composable
private fun MessagePanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                color = Color(0xFF9A9A9A),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            if (secondaryLabel != null) {
                TextButton(onClick = onSecondary) {
                    Text(text = secondaryLabel, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NovaHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Nova",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Ajustes",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
