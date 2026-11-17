package com.nova.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

private val TimeGray = Color(0xFF9A9A9A)
private val TrackGray = Color(0xFF5A5A5A)
private val ThumbRadius = 6.dp

/** Barra de progreso y los 5 controles. No guarda estado: todo viene del reproductor. */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    position: State<Long>,
    durationMs: Long,
    shuffleOn: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionMs = position.value.coerceIn(0L, maxOf(durationMs, 0L))
    val progress = if (durationMs > 0L) positionMs.toFloat() / durationMs else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        SeekBar(progress = progress, onSeek = onSeek)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatTime(positionMs), color = TimeGray, fontSize = 16.sp)
            Text(text = formatTime(durationMs), color = TimeGray, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(Icons.Outlined.Shuffle, "Aleatorio", shuffleOn, onShuffle)
            ControlIcon(Icons.Filled.SkipPrevious, "Anterior", true, onPrevious)
            PlayPauseButton(isPlaying, onPlayPause)
            ControlIcon(Icons.Filled.SkipNext, "Siguiente", true, onNext)
            ControlIcon(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                description = "Repetir",
                active = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onRepeat,
            )
        }
    }
}

@Composable
private fun SeekBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latestOnSeek by rememberUpdatedState(onSeek)
    val safeProgress = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    latestOnSeek(seekFraction(offset.x, size.width, ThumbRadius.toPx()))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestOnSeek(seekFraction(offset.x, size.width, ThumbRadius.toPx()))
                    },
                ) { change, _ ->
                    change.consume()
                    latestOnSeek(seekFraction(change.position.x, size.width, ThumbRadius.toPx()))
                }
            },
    ) {
        val trackHeight = 3.dp.toPx()
        val inset = ThumbRadius.toPx()
        val startX = inset
        val endX = size.width - inset
        val centerY = size.height / 2f
        val thumbX = startX + (endX - startX) * safeProgress

        drawLine(
            color = TrackGray,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(startX, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color.White, radius = inset, center = Offset(thumbX, centerY))
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(76.dp)
            .background(Color(0xFF0B0B0B), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** Convierte la posición del dedo en un valor de 0 a 1 a lo largo de la barra. */
private fun seekFraction(x: Float, widthPx: Int, insetPx: Float): Float {
    val usable = widthPx - 2 * insetPx
    return if (usable <= 0f) 0f else ((x - insetPx) / usable).coerceIn(0f, 1f)
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).toInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
