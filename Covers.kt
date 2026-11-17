package com.nova.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Guarda las portadas ya cargadas para no leerlas otra vez. Usa poca memoria. */
private object CoverCache {

    private val bitmaps = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Canciones sin portada, para no volver a buscarla
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())

    private fun maxCacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(48L * 1024 * 1024).toInt()

    private fun keyOf(song: Song, px: Int): String = "${song.id}:$px"

    fun peek(song: Song, px: Int): Bitmap? = bitmaps.get(keyOf(song, px))

    suspend fun load(context: Context, song: Song, px: Int): Bitmap? {
        val key = keyOf(song, px)
        bitmaps.get(key)?.let { return it }
        if (missing.contains(key)) return null

        val bitmap = withContext(Dispatchers.IO) { read(context.applicationContext, song, px) }
        if (bitmap != null) {
            bitmaps.put(key, bitmap)
        } else {
            missing.add(key)
        }
        return bitmap
    }

    private fun read(context: Context, song: Song, px: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
        } else {
            readLegacy(context, song.albumId, px)
        }
    } catch (e: Exception) {
        null
    }

    /** Android 9 o anterior: la portada del álbum, reducida al tamaño necesario. */
    private fun readLegacy(context: Context, albumId: Long, px: Int): Bitmap? {
        val uri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}

/** Portada de una canción. Si no tiene, muestra el cuadro oscuro con la nota musical. */
@Composable
fun SongCover(song: Song?, sizeDp: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = remember(sizeDp, density) { with(density) { sizeDp.roundToPx() }.coerceIn(64, 1024) }

    val bitmap by produceState(song?.let { CoverCache.peek(it, px) }, song?.id, px) {
        value = song?.let { CoverCache.load(context, it, px) }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3340), Color(0xFF0E1116)))),
        contentAlignment = Alignment.Center,
    ) {
        val image = remember(bitmap) { bitmap?.asImageBitmap() }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(sizeDp / 2.5f),
            )
        }
    }
}
