package com.testmond.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// A test that has been finished (and not started again since) is "completed": its card shows a green
// tick next to the three-dot menu, and tapping it offers View (the finished attempt exactly as it was
// answered, with solutions) or Reattempt (start over, after a confirmation). Shared by the Tests tab
// and by a folder's test list.

/** The green tick shown on a completed test's card, just before its three-dot menu. */
@Composable
internal fun CompletedTick(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = "Completed",
        tint = AnswerColors.correctBg,
        modifier = modifier.size(22.dp)
    )
}

/** Shown when a completed test is tapped: View its finished attempt, or Reattempt it. */
@Composable
internal fun CompletedTestDialog(
    title: String,
    onView: () -> Unit,
    onReattempt: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "You've completed this test.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text("View") }
                OutlinedButton(onClick = onReattempt, modifier = Modifier.fillMaxWidth()) { Text("Reattempt") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Confirmation before a completed test is started over. */
@Composable
internal fun ReattemptConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reattempt this test?") },
        text = {
            Text(
                "Your current test progress will be erased and you'll start over from question 1. " +
                    "This can't be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reattempt", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
