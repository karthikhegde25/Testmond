package com.testmond.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MAX_PREVIEW_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen image preview, like opening an image in a browser: the picture fills the screen on
 * a black background; pinch to zoom (up to 6x), drag to pan while zoomed, double-tap to zoom in
 * or back out. The X in the top-right corner (or the back button) closes it.
 */
@Composable
internal fun ImagePreviewDialog(image: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var boxSize by remember { mutableStateOf(IntSize.Zero) }

        // Keeps a zoomed picture from being dragged completely off screen.
        fun clampOffset(value: Offset, forScale: Float): Offset {
            val maxX = boxSize.width * (forScale - 1f) / 2f
            val maxY = boxSize.height * (forScale - 1f) / 2f
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { boxSize = it }
        ) {
            Image(
                bitmap = image,
                contentDescription = "Image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, MAX_PREVIEW_ZOOM)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            // Zoom around the pinch point (so it stays under the fingers), then pan.
                            val moved = (centroid - center) -
                                ((centroid - center) - offset) * (newScale / scale) + pan
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else clampOffset(moved, newScale)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                    scale = DOUBLE_TAP_ZOOM
                                    offset = clampOffset((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
            }
        }
    }
}
