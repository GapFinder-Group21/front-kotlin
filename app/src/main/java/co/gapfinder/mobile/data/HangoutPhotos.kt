package co.gapfinder.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fotos de las open tables. El backend exige una foto JPG o PNG de máximo 5 MB al crear la mesa,
 * así que cada foto (de la cámara o de la galería) se reescala y se guarda como JPEG en filesDir.
 * Se guarda en filesDir y no en caché para que el borrador del wizard la conserve si la app se cierra.
 */
object HangoutPhotos {
    private const val FOLDER = "hangout_photos"
    private const val MAX_SIDE = 1600
    private const val QUALITY = 85

    private fun folder(context: Context): File = File(context.filesDir, FOLDER).apply { mkdirs() }

    /** Archivo vacío donde la cámara escribe la foto, con su Uri del FileProvider. */
    fun cameraTarget(context: Context): Pair<File, Uri> {
        val file = File(folder(context), "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        return file to uri
    }

    /**
     * Lee la imagen de [source], la endereza según el EXIF, la reduce a [MAX_SIDE] px por lado
     * y la escribe como JPEG nuevo. Cada foto tiene nombre propio para que Coil no muestre una vieja.
     */
    suspend fun normalize(context: Context, source: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Primero solo las dimensiones, para decodificar ya reducida y no gastar memoria
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("The file is not an image")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(source).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("The file is not an image")

        val degrees = resolver.openInputStream(source).use { stream ->
            if (stream == null) 0 else when (
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val target = File(folder(context), "photo_${System.currentTimeMillis()}.jpg")
        target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        target
    }

    /** Borra una foto que ya no se usa (se ignora si no existe). */
    fun discard(file: File?) {
        file?.delete()
    }
}
