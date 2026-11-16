package com.nova.music

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una canción guardada en el teléfono. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
)

/** Estados posibles de la biblioteca de música. */
sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data object Failed : LibraryState
    data class Ready(val songs: List<Song>) : LibraryState
}

object MusicRepository {

    // Se ignoran los audios de menos de 30 segundos (tonos, notas de voz, efectos)
    private const val MIN_DURATION_MS = 30_000L

    // Así marca Android al artista cuando la canción no lo tiene
    private const val ANDROID_UNKNOWN = "<unknown>"

    fun loadSongs(context: Context): List<Song> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())

        val songs = ArrayList<Song>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.trim().orEmpty()
                    val fileName = cursor.getString(nameCol)?.substringBeforeLast('.')?.trim().orEmpty()
                    val artist = cursor.getString(artistCol)?.trim().orEmpty()

                    songs.add(
                        Song(
                            id = id,
                            title = title.ifEmpty { fileName }.ifEmpty { "Sin título" },
                            artist = if (artist.isEmpty() || artist == ANDROID_UNKNOWN) {
                                "Artista desconocido"
                            } else {
                                artist
                            },
                            albumId = cursor.getLong(albumCol),
                            durationMs = cursor.getLong(durationCol),
                            uri = ContentUris.withAppendedId(collection, id),
                        )
                    )
                }
            }

        // Orden alfabético que respeta acentos y no distingue mayúsculas
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        return songs.sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app.applicationContext
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Lee las canciones fuera del hilo principal, para que la app no se trabe. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value !is LibraryState.Ready) _state.value = LibraryState.Loading
            _state.value = try {
                val songs = withContext(Dispatchers.IO) { MusicRepository.loadSongs(appContext) }
                if (songs.isEmpty()) LibraryState.Empty else LibraryState.Ready(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryState.Failed
            }
        }
    }
}
