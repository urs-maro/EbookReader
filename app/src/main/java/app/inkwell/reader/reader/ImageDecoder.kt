package app.inkwell.reader.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes image bytes into an [ImageBitmap], downsampled so it never exceeds the page size. */
object ImageDecoder {

    suspend fun decode(bytes: ByteArray, maxWidth: Int, maxHeight: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@runCatching null
                var sample = 1
                if (maxWidth > 0 && maxHeight > 0) {
                    while (w / (sample * 2) >= maxWidth && h / (sample * 2) >= maxHeight) sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }.getOrNull()
        }
}
