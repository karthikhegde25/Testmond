package com.testmond.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.testmond.app.ActiveBookmarksFileHolder
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.model.McqSet
import com.testmond.app.safeNavigate
import com.testmond.app.safePopBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists every question bookmarked in one test (reached from that test's three-dot menu ->
 * "Bookmarks"), whichever file [ActiveBookmarksFileHolder] currently points at. Tapping a
 * question opens the test's normal quiz screen straight at that question (via
 * [ActiveSetHolder.jumpToIndex], consumed once by QuizScreen). This list is read-only --
 * unbookmarking only happens from inside the test itself (the same toggle used to add one), so a
 * bookmark can't be lost by an accidental tap here. Test tab only -- there is no equivalent in
 * Exam Mode, so nothing here is reachable from an exam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(navController: NavHostController) {
    val context = LocalContext.current
    val file = ActiveBookmarksFileHolder.currentFile.value
    if (file == null) {
        navController.safePopBackStack()
        return
    }

    var loading by remember(file) { mutableStateOf(true) }
    var set by remember(file) { mutableStateOf<McqSet?>(null) }
    var bookmarkedIndexes by remember(file) {
        mutableStateOf(FileStorage.loadBookmarks(context, file).sorted())
    }

    LaunchedEffect(file) {
        loading = true
        set = runCatching { withContext(Dispatchers.IO) { FileStorage.loadFromFile(file) } }.getOrNull()
        loading = false
    }

    // Refresh the list whenever this screen comes back into view -- e.g. after tapping a
    // bookmarked question, taking it (or un-bookmarking it) inside the quiz, and returning here --
    // so a question just un-bookmarked doesn't linger in this read-only list until it's reopened.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, file) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bookmarkedIndexes = FileStorage.loadBookmarks(context, file).sorted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val currentSet = set
            // Bookmarks pointing past the end of the question list (a stale entry from before
            // questions were deleted) are ignored rather than showing an empty, blank list.
            val validIndexes = remember(bookmarkedIndexes, currentSet) {
                bookmarkedIndexes.filter { index -> currentSet != null && index in currentSet.questions.indices }
            }
            when {
                loading -> LoadingOverlayDialog()
                currentSet == null -> Text(
                    "Couldn't open \"${file.nameWithoutExtension}\".",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                validIndexes.isEmpty() -> Text(
                    "No bookmarks yet. While taking this test, tap the bookmark icon next to " +
                        "the timer on any question to add it here.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(validIndexes, key = { it }) { index ->
                        val q = currentSet.questions.getOrNull(index)
                        if (q != null) {
                            ElevatedCard(
                                onClick = {
                                    ActiveSetHolder.current.value = currentSet
                                    ActiveSetHolder.currentFile.value = file
                                    ActiveSetHolder.jumpToIndex.value = index
                                    navController.safeNavigate("quiz")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Question ${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        // A cheap Unicode preview of the question (CH_3 -> CH₃, x^2 -> x²,
                                        // \frac{a}{b} -> a/b ...) instead of a KaTeX WebView per row, which
                                        // made this list stutter while scrolling. Computed once per question.
                                        val preview = remember(q.question) { mathToPlainPreview(q.question) }
                                        Text(
                                            preview,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
