package com.chap.zrec.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class RecordingItem(
    val id: Long, val uri: Uri, val displayName: String, val size: Long,
    val dateAdded: Long, val duration: Long, val width: Int, val height: Int
)

class RecordingRepository(private val context: Context) {
    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    suspend fun refresh() {
        _recordings.value = withContext(Dispatchers.IO) {
            val items = mutableListOf<RecordingItem>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.RELATIVE_PATH
            )
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%${Environment.DIRECTORY_MOVIES}/ZRecorder%")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items += RecordingItem(id, uri, cursor.getString(nameColumn) ?: "Recording", cursor.getLong(sizeColumn), cursor.getLong(dateColumn), cursor.getLong(durationColumn), cursor.getInt(widthColumn), cursor.getInt(heightColumn))
                }
            }
            items
        }
    }

    suspend fun delete(item: RecordingItem): Boolean = withContext(Dispatchers.IO) {
        try { context.contentResolver.delete(item.uri, null, null) > 0 } catch (e: Exception) { false }
    }
}
