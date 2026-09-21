package com.testmond.app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Decodes and shows an embedded image (a question's, or a solution's), if there is one. Used
 *  while taking a test, on the review page and in the editors, so an image renders identically
 *  wherever it appears. A small zoom badge sits on the image's bottom-right corner; tapping it
 *  (or the image) opens a full-screen preview with pinch-to-zoom and an X to close. */
@Composable
fun QuestionImage(imageBase64: String?, modifier: Modifier = Modifier) {
    if (imageBase64.isNullOrBlank()) return
    val bitmap = remember(imageBase64) {
        runCatching {
            val bytes = Base64.decode(imageBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    } ?: return
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var showPreview by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // This inner Box wraps the picture itself, so the badge sits on the picture's own corner
        // rather than the corner of the (possibly wider) area around it.
        Box {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Question image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPreview = true }
            )
            // 40dp touch target around a 28dp visible badge.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { showPreview = true },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "View full screen",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showPreview) {
        ImagePreviewDialog(image = imageBitmap, onDismiss = { showPreview = false })
    }
}
