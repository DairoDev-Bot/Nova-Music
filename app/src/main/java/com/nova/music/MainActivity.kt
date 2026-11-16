package com.nova.music

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// Canciones de ejemplo, solo para ver el diseño. Se quitan al conectar tus canciones.
private val SampleTracks = listOf(
    Track(title = "BIRDS OF A FEATHER", artist = "Billie Eilish"),
    Track(title = "Not Like Us", artist = "Kendrick Lamar"),
    Track(title = "Blinding Lights", artist = "The Weeknd"),
    Track(title = "HUMBLE.", artist = "Kendrick Lamar"),
    Track(title = "Die For You", artist = "The Weeknd"),
    Track(title = "Snooze", artist = "SZA"),
)

@Composable
private fun NovaScreen() {
    // Empieza con "Snooze" sonando, como en tu diseño
    var currentIndex by rememberSaveable { mutableStateOf(SampleTracks.lastIndex) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val selectedTab = NovaTab.entries[selectedTabIndex]
    // Guarda el estado de cada pestaña al cambiar de una a otra
    val stateHolder = rememberSaveableStateHolder()

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
                            currentIndex = currentIndex,
                            onTrackClick = { currentIndex = it },
                        )
                        NovaTab.Explore -> ComingSoon(
                            title = "Explorar",
                            message = "Próximamente: búsqueda de canciones, artistas y álbumes.",
                        )
                        NovaTab.Profile -> ComingSoon(
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
private fun ColumnScope.HomeContent(currentIndex: Int, onTrackClick: (Int) -> Unit) {
    key(currentIndex) {
        NowPlayingCard(
            track = SampleTracks[currentIndex],
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 24.dp),
        )
    }
    PlayerControls(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
    )
    SongList(
        tracks = SampleTracks,
        onTrackClick = onTrackClick,
        modifier = Modifier
            .weight(1f)
            .padding(top = 24.dp),
    )
}

@Composable
private fun ComingSoon(title: String, message: String) {
    Box(
        modifier = Modifier
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
