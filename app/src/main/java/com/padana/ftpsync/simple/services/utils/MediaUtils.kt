package com.padana.ftpsync.simple.services.utils

import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.padana.ftpsync.MyApp
import java.util.concurrent.TimeUnit

object MediaUtils {

    suspend fun getVideos(): MutableList<Video>? {
        return createVideoQuery()?.let { performVideoQuery(it) }
    }

    suspend fun getImages(): MutableList<Image>? {
        return createImagesQuery()?.let { performImagesQuery(it) }
    }

    fun getImageThumbnail(image: Image): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MyApp.getCtx().contentResolver.loadThumbnail(
                    image.uri, Size(320, 240), null)
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
    }

    private fun createVideoQuery(): Cursor? {
        val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_TAKEN
        )

        // Show only videos that are at least 1 minutes in duration.
        val selection = "${MediaStore.Video.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(
                TimeUnit.MILLISECONDS.convert(1, TimeUnit.MILLISECONDS).toString()
        )

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC"

        return MyApp.getCtx().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )
    }

    private fun performVideoQuery(query: Cursor): MutableList<Video> {
        val videoList = mutableListOf<Video>()
        query.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getInt(sizeColumn)
                val dateTaken = cursor.getString(dateTakenColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                videoList += Video(contentUri, name, duration, size, dateTaken)
            }
        }
        return videoList
    }

    private fun createImagesQuery(): Cursor? {
        val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DURATION,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN
        )
        val selection = "${MediaStore.Images.Media.SIZE} >= ?"
        val selectionArgs = arrayOf("1")

        // Display videos in alphabetical order based on their display name.
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        return MyApp.getCtx().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )
    }

    private fun performImagesQuery(query: Cursor): MutableList<Image> {
        val imageList = mutableListOf<Image>()
        query.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getInt(sizeColumn)
                val dateTaken = cursor.getString(dateTakenColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                imageList += Image(contentUri, name, duration, size, dateTaken)
            }
        }
        return imageList
    }
}

data class Video(val uri: Uri,
                 val name: String,
                 val duration: Int,
                 val size: Int,
                 val dateTaken: String
)

data class Image(val uri: Uri,
                 val name: String,
                 val duration: Int,
                 val size: Int,
                 val dateTaken: String
)