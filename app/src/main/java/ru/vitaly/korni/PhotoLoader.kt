package ru.vitaly.korni

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

object PhotoLoader {
    fun loadThumbnail(context: Context, rawUri: String): Bitmap? {
        if (rawUri.isBlank()) return null
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(Uri.parse(rawUri))?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }
}
