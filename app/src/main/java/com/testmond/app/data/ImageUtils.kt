package com.testmond.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val MAX_DIMENSION = 900
    private const val JPEG_QUALITY = 80

    /** Reads an image from a picked Uri, downscales it to a reasonable size, and returns
     *  it as a base64 JPEG string suitable for embedding directly in a question. Keeps the
     *  test file small and portable even if the original photo was several megabytes. */
    fun encodeForQuestion(context: Context, uri: Uri): String? {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null

        val scale = MAX_DIMENSION.toFloat() / maxOf(original.width, original.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
