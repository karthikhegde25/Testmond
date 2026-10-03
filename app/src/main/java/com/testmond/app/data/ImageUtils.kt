package com.testmond.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Colour filters offered by the image editor. [matrix] is a 4x5 colour matrix in the layout
 *  shared by android.graphics.ColorMatrix and Compose's ColorMatrix (null = no change), so the
 *  live preview and the exported picture always look identical. */
enum class ImageFilter(val label: String, val matrix: FloatArray?) {
    ORIGINAL("Original", null),
    BLACK_WHITE(
        "B&W", floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    SEPIA(
        "Sepia", floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VIVID(
        "Vivid", floatArrayOf(
            1.35f, -0.25f, -0.10f, 0f, -10f,
            -0.15f, 1.25f, -0.10f, 0f, -10f,
            -0.15f, -0.25f, 1.40f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    BRIGHT(
        "Bright", floatArrayOf(
            1.1f, 0f, 0f, 0f, 30f,
            0f, 1.1f, 0f, 0f, 30f,
            0f, 0f, 1.1f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    // Grayscale with strong contrast -- makes photos of printed/handwritten pages easier to read.
    DOCUMENT(
        "Document", floatArrayOf(
            0.478f, 0.939f, 0.182f, 0f, -25f,
            0.478f, 0.939f, 0.182f, 0f, -25f,
            0.478f, 0.939f, 0.182f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/** What the user did in the image editor. Crop edges are fractions (0..1) of the picture AFTER it
 *  has been rotated by [quarterTurns] clockwise quarter turns. */
data class ImageEdits(
    val quarterTurns: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val filter: ImageFilter
)

object ImageUtils {
    private const val MAX_DIMENSION = 900
    private const val JPEG_QUALITY = 80

    /** Longest side of the copy of the picture handed to the editor. */
    const val EDITOR_MAX_DIMENSION = 2048

    /** Reads a picked image, applies its EXIF orientation, flattens transparency onto white and
     *  returns it scaled so its longest side is at most [maxDimension]. Used to feed the image
     *  editor, and by [encodeForQuestion].
     *
     *  Blocking (decodes) -- call it from a background thread.
     *
     *  - The picture is decoded already shrunk (`inSampleSize`); decoding a 48-megapixel photo at
     *    full size needs ~190 MB and used to crash the app with an out-of-memory error.
     *  - The camera's EXIF orientation is applied, so a portrait photo isn't shown sideways.
     *  - Transparent pixels (a PNG diagram) are flattened onto white; JPEG has no transparency and
     *    they used to turn black. */
    fun decodeForEditing(context: Context, uri: Uri, maxDimension: Int = EDITOR_MAX_DIMENSION): Bitmap? {
        val resolver = context.contentResolver

        // 1. Only read the size. (With inJustDecodeBounds the returned Bitmap is always null --
        //    the size lands in `bounds` -- so the return value must not be used to detect failure.)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2. Decode at a power-of-two reduction that keeps the result at least maxDimension big.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        // 3. Orientation from the photo's EXIF data (if it has any).
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        // 4. Scale down to maxDimension and apply the orientation in one step.
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
        }
        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        var result = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }

        // 5. JPEG can't hold transparency: put the picture on white first.
        if (result.hasAlpha()) {
            val flat = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(result, 0f, 0f, null)
            }
            result = flat
        }
        return result
    }

    /** Applies the editor's rotation, crop and filter to [source] and returns the new picture.
     *  Blocking -- call it from a background thread. */
    fun applyEdits(source: Bitmap, edits: ImageEdits): Bitmap {
        val turns = ((edits.quarterTurns % 4) + 4) % 4
        val rotated = if (turns == 0) {
            source
        } else {
            val m = Matrix().apply { postRotate(90f * turns) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        }

        val x = (edits.left.coerceIn(0f, 1f) * rotated.width).toInt().coerceIn(0, rotated.width - 1)
        val y = (edits.top.coerceIn(0f, 1f) * rotated.height).toInt().coerceIn(0, rotated.height - 1)
        val w = ((edits.right - edits.left).coerceIn(0f, 1f) * rotated.width).toInt().coerceIn(1, rotated.width - x)
        val h = ((edits.bottom - edits.top).coerceIn(0f, 1f) * rotated.height).toInt().coerceIn(1, rotated.height - y)

        val cropped = if (x == 0 && y == 0 && w == rotated.width && h == rotated.height) {
            rotated
        } else {
            Bitmap.createBitmap(rotated, x, y, w, h)
        }

        val filterMatrix = edits.filter.matrix ?: return cropped
        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(filterMatrix))
        }
        Canvas(out).drawBitmap(cropped, 0f, 0f, paint)
        return out
    }

    /** Scales [bitmap] down to at most MAX_DIMENSION on its longest side and returns it as a
     *  base64 JPEG string suitable for embedding directly in a question. Keeps the test file
     *  small and portable even if the original photo was several megabytes.
     *  Blocking -- call it from a background thread. */
    fun encodeBitmap(bitmap: Bitmap): String {
        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val result = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Reads a picked image and returns it as a base64 JPEG string with no editing step.
     *  Blocking -- call it from a background thread. */
    fun encodeForQuestion(context: Context, uri: Uri): String? =
        decodeForEditing(context, uri, MAX_DIMENSION)?.let { encodeBitmap(it) }
}
