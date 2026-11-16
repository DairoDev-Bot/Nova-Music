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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
        // El contenido queda entre la barra de estado y la barra de navegación del teléfono
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NovaHeader(onSettingsClick = { /* Ajustes llegará en un paso posterior */ })
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
                onTrackClick = { currentIndex = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 24.dp),
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
