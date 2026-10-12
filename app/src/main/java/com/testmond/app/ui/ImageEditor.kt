package com.testmond.app.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.testmond.app.data.ImageEdits
import com.testmond.app.data.ImageFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Crop rectangle as fractions (0..1) of the (rotated) picture. */
private data class CropFractions(val l: Float, val t: Float, val r: Float, val b: Float)

private const val EDGE_LEFT = 1
private const val EDGE_TOP = 2
private const val EDGE_RIGHT = 4
private const val EDGE_BOTTOM = 8
private const val MOVE_ALL = 16

private fun ImageFilter.toComposeFilter(): ColorFilter? =
    matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/**
 * Full-screen image editor shown after a picture is picked: drag the crop frame's corners/edges
 * (or drag inside it to move it), rotate in 90-degree steps, choose a colour filter, then
 * Confirm (returns the chosen edits) or Cancel (discards the picture).
 *
 * [source] is never modified; the caller turns the returned [ImageEdits] into the final picture
 * with ImageUtils.applyEdits on a background thread.
 */
@Composable
fun ImageEditorDialog(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (ImageEdits) -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        var quarterTurns by remember { mutableStateOf(0) }
        var crop by remember { mutableStateOf(CropFractions(0f, 0f, 1f, 1f)) }
        var filter by remember { mutableStateOf(ImageFilter.ORIGINAL) }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

        val density = LocalDensity.current
        val rotated = remember(source, quarterTurns) {
            if (quarterTurns % 4 == 0) source
            else Bitmap.createBitmap(
                source, 0, 0, source.width, source.height,
                Matrix().apply { postRotate(90f * (quarterTurns % 4)) }, true
            )
        }
        val rotatedImage = remember(rotated) { rotated.asImageBitmap() }
        val thumb = remember(source) {
            val s = 120f / max(source.width, source.height)
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * s).toInt()),
                max(1, (source.height * s).toInt()),
                true
            ).asImageBitmap()
        }

        fun rotate(clockwise: Boolean) {
            val c = crop
            // Carry the crop frame along with the picture.
            crop = if (clockwise) CropFractions(1f - c.b, c.l, 1f - c.t, c.r)
            else CropFractions(c.t, 1f - c.r, c.b, 1f - c.l)
            quarterTurns = (quarterTurns + (if (clockwise) 1 else 3)) % 4
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                "Edit image",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // ---- Picture + crop frame ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(rotated, canvasSize) {
                        val marginPx = with(density) { 24.dp.toPx() }
                        val slopPx = with(density) { 32.dp.toPx() }
                        val minSizePx = with(density) { 48.dp.toPx() }
                        var mode = 0

                        // Where the picture is drawn inside the canvas.
                        fun imageRect(): Rect {
                            val availW = canvasSize.width - 2 * marginPx
                            val availH = canvasSize.height - 2 * marginPx
                            val scale = min(availW / rotated.width, availH / rotated.height)
                            val w = rotated.width * scale
                            val h = rotated.height * scale
                            val left = (canvasSize.width - w) / 2f
                            val top = (canvasSize.height - h) / 2f
                            return Rect(left, top, left + w, top + h)
                        }

                        detectDragGestures(
                            onDragStart = { p ->
                                val img = imageRect()
                                val c = crop
                                val cl = img.left + c.l * img.width
                                val ct = img.top + c.t * img.height
                                val cr = img.left + c.r * img.width
                                val cb = img.top + c.b * img.height
                                mode = 0
                                if (p.x in (cl - slopPx)..(cr + slopPx) && p.y in (ct - slopPx)..(cb + slopPx)) {
                                    val nearL = abs(p.x - cl) < slopPx
                                    val nearR = abs(p.x - cr) < slopPx
                                    val nearT = abs(p.y - ct) < slopPx
                                    val nearB = abs(p.y - cb) < slopPx
                                    // On a very small frame both sides can be "near": pick the closer.
                                    if (nearL && (!nearR || abs(p.x - cl) <= abs(p.x - cr))) mode = mode or EDGE_LEFT
                                    else if (nearR) mode = mode or EDGE_RIGHT
                                    if (nearT && (!nearB || abs(p.y - ct) <= abs(p.y - cb))) mode = mode or EDGE_TOP
                                    else if (nearB) mode = mode or EDGE_BOTTOM
                                    if (mode == 0) mode = MOVE_ALL
                                }
                            },
                            onDragEnd = { mode = 0 },
                            onDragCancel = { mode = 0 },
                            onDrag = { change, drag ->
                                if (mode == 0) return@detectDragGestures
                                change.consume()
                                val img = imageRect()
                                val dx = drag.x / img.width
                                val dy = drag.y / img.height
                                val minW = min(minSizePx / img.width, 1f)
                                val minH = min(minSizePx / img.height, 1f)
                                var (l, t, r, b) = crop
                                if (mode == MOVE_ALL) {
                                    val w = r - l
                                    val h = b - t
                                    l = (l + dx).coerceIn(0f, 1f - w)
                                    t = (t + dy).coerceIn(0f, 1f - h)
                                    r = l + w
                                    b = t + h
                                } else {
                                    if (mode and EDGE_LEFT != 0) l = (l + dx).coerceIn(0f, r - minW)
                                    if (mode and EDGE_RIGHT != 0) r = (r + dx).coerceIn(l + minW, 1f)
                                    if (mode and EDGE_TOP != 0) t = (t + dy).coerceIn(0f, b - minH)
                                    if (mode and EDGE_BOTTOM != 0) b = (b + dy).coerceIn(t + minH, 1f)
                                }
                                crop = CropFractions(l, t, r, b)
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val marginPx = 24.dp.toPx()
                    val availW = size.width - 2 * marginPx
                    val availH = size.height - 2 * marginPx
                    if (availW <= 0f || availH <= 0f) return@Canvas
                    val scale = min(availW / rotated.width, availH / rotated.height)
                    val w = rotated.width * scale
                    val h = rotated.height * scale
                    val left = (size.width - w) / 2f
                    val top = (size.height - h) / 2f

                    drawImage(
                        image = rotatedImage,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(rotated.width, rotated.height),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                        colorFilter = filter.toComposeFilter()
                    )

                    val cl = left + crop.l * w
                    val ct = top + crop.t * h
                    val cr = left + crop.r * w
                    val cb = top + crop.b * h

                    // Dim everything outside the crop frame.
                    val dim = Color.Black.copy(alpha = 0.6f)
                    drawRect(dim, Offset(left, top), Size(w, ct - top))
                    drawRect(dim, Offset(left, cb), Size(w, top + h - cb))
                    drawRect(dim, Offset(left, ct), Size(cl - left, cb - ct))
                    drawRect(dim, Offset(cr, ct), Size(left + w - cr, cb - ct))

                    // Frame, rule-of-thirds grid, corner handles.
                    drawRect(Color.White, Offset(cl, ct), Size(cr - cl, cb - ct), style = Stroke(2.dp.toPx()))
                    val grid = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        val gx = cl + (cr - cl) * i / 3f
                        val gy = ct + (cb - ct) * i / 3f
                        drawLine(grid, Offset(gx, ct), Offset(gx, cb), 1.dp.toPx())
                        drawLine(grid, Offset(cl, gy), Offset(cr, gy), 1.dp.toPx())
                    }
                    val hl = 18.dp.toPx()
                    val hw = 4.dp.toPx()
                    fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                        drawLine(Color.White, Offset(x - sx * hw / 2, y), Offset(x + sx * hl, y), hw)
                        drawLine(Color.White, Offset(x, y - sy * hw / 2), Offset(x, y + sy * hl), hw)
                    }
                    corner(cl, ct, 1f, 1f)
                    corner(cr, ct, -1f, 1f)
                    corner(cl, cb, 1f, -1f)
                    corner(cr, cb, -1f, -1f)
                }
            }

            // ---- Tools: rotate + reset crop ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { rotate(clockwise = false) }) {
                    Icon(Icons.Default.RotateLeft, contentDescription = "Rotate left", tint = Color.White)
                }
                IconButton(onClick = { rotate(clockwise = true) }) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate right", tint = Color.White)
                }
                IconButton(onClick = { crop = CropFractions(0f, 0f, 1f, 1f) }) {
                    Icon(Icons.Default.Crop, contentDescription = "Reset crop", tint = Color.White)
                }
            }

            // ---- Filters ----
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ImageFilter.entries.toList()) { f ->
                    val selected = f == filter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { filter = f }
                    ) {
                        Image(
                            bitmap = thumb,
                            contentDescription = f.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = f.toComposeFilter(),
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            f.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }

            // ---- Cancel / Confirm ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = {
                        onConfirm(
                            ImageEdits(
                                quarterTurns = quarterTurns,
                                left = crop.l,
                                top = crop.t,
                                right = crop.r,
                                bottom = crop.b,
                                filter = filter
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}
