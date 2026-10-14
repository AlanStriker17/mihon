package eu.kanade.tachiyomi.data.saver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@Inject
@SingleIn(AppScope::class)
class PanelImageExporter(
    private val imageSaver: ImageSaver,
) {
    suspend fun export(
        inputStream: InputStream,
        cropRect: Rect,
        filename: String,
    ): Uri = withContext(Dispatchers.IO) {
        inputStream.use { stream ->
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            } ?: error("Unable to decode reader page")
            try {
                val bounds = Rect(0, 0, decoder.width, decoder.height)
                require(bounds.contains(cropRect) && !cropRect.isEmpty) {
                    "Crop rectangle must fit within the reader page"
                }
                require(cropRect.width().toLong() * cropRect.height() <= MAX_CROP_PIXELS) {
                    "Panel crop exceeds the supported image size"
                }
                val bitmap = decoder.decodeRegion(cropRect, BitmapFactory.Options())
                    ?: error("Unable to decode selected panel")
                try {
                    imageSaver.save(
                        Image.Cover(
                            bitmap = bitmap,
                            name = filename,
                            location = Location.Pictures.create(),
                        ),
                    )
                } finally {
                    bitmap.recycle()
                }
            } finally {
                decoder.recycle()
            }
        }
    }

    private companion object {
        const val MAX_CROP_PIXELS = 16_000_000L
    }
}
