package com.testmond.app.ui

import android.content.Context
import com.testmond.app.data.FileStorage
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.ExamRules
import com.testmond.app.model.QuizAttempt
import java.io.File

// Everything the home screen's cards show besides the file name -- attempt history, "in
// progress", whether a test has solutions, a folder's contents, exam history and rules -- lives
// in files that have to be read and JSON-parsed. The cards used to do that themselves, on the main
// thread, the moment each one scrolled into view (for the Tests tab that even meant parsing the
// WHOLE test file, images included, just to see whether any question has a solution). Fast
// scrolling composed dozens of cards in a row, so it stuttered.
//
// Now the reading happens once, on a background thread, ahead of time; the cards only look the
// values up in memory. The last result is also kept for the life of the process, so coming back to
// the home screen shows the previous values immediately and refreshes them in the background.

internal data class TestCardInfo(
    val stamp: String,               // FileStorage.cardStamp when this was read
    val attempts: List<QuizAttempt>,
    val hasProgress: Boolean,
    val hasSolution: Boolean
)

internal data class ExamCardInfo(
    val stamp: String,
    val attempts: List<ExamAttempt>,
    val rules: ExamRules?
)

internal object HomeCardCache {
    @Volatile var tests: Map<String, TestCardInfo> = emptyMap()
    @Volatile var folders: Map<String, Int> = emptyMap()   // folder file name -> tests it holds
    @Volatile var exams: Map<String, ExamCardInfo> = emptyMap()
}

private const val SOLUTION_KEY = "\"solution\":"

/** Whether any question in the test file has a solution. A question's `solution` is only written to
 *  the file when it is set, so this is a plain text search -- no JSON parsing -- and it streams the
 *  file in small chunks so a test full of images never has to be held in memory as one huge string
 *  (which would cause garbage-collection pauses that stutter scrolling even on a background thread). */
private fun fileHasSolution(file: File): Boolean = runCatching {
    file.bufferedReader().use { reader ->
        val buffer = CharArray(16 * 1024)
        var carry = ""   // the tail of the previous chunk, in case the key straddles two chunks
        var found = false
        var n = reader.read(buffer)
        while (n >= 0 && !found) {
            val window = carry + String(buffer, 0, n)
            found = window.contains(SOLUTION_KEY)
            carry = window.takeLast(SOLUTION_KEY.length - 1)
            n = reader.read(buffer)
        }
        found
    }
}.getOrDefault(false)

/** Blocking -- call from a background dispatcher. Keys are the files' names. Entries whose files
 *  haven't changed since [previous] (same [FileStorage.cardStamp]) are reused as they are, so
 *  coming back to the home screen only re-reads what actually changed. */
internal fun loadTestCardInfo(
    context: Context,
    files: List<File>,
    previous: Map<String, TestCardInfo>
): Map<String, TestCardInfo> =
    files.associate { file ->
        val stamp = FileStorage.cardStamp(context, file)
        val old = previous[file.name]
        file.name to if (old != null && old.stamp == stamp) {
            old
        } else {
            TestCardInfo(
                stamp = stamp,
                attempts = FileStorage.loadAttempts(context, file),
                hasProgress = FileStorage.loadProgress(context, file) != null,
                hasSolution = fileHasSolution(file)
            )
        }
    }

/** Blocking -- call from a background dispatcher. Maps each folder file's name to how many of its
 *  tests still exist (a reference to a deleted test is not counted). Unreadable folders are left out. */
internal fun loadFolderCardInfo(context: Context, files: List<File>): Map<String, Int> {
    val result = HashMap<String, Int>(files.size)
    for (file in files) {
        val folder = runCatching { FileStorage.loadFolder(file) }.getOrNull() ?: continue
        result[file.name] = FileStorage.resolveFolderSets(context, folder).size
    }
    return result
}

/** Blocking -- call from a background dispatcher. Unchanged entries are reused (see above). */
internal fun loadExamCardInfo(
    context: Context,
    files: List<File>,
    previous: Map<String, ExamCardInfo>
): Map<String, ExamCardInfo> =
    files.associate { file ->
        val stamp = FileStorage.cardStamp(context, file)
        val old = previous[file.name]
        file.name to if (old != null && old.stamp == stamp) {
            old
        } else {
            ExamCardInfo(
                stamp = stamp,
                attempts = FileStorage.loadExamAttempts(context, file),
                rules = FileStorage.loadExamRules(context, file)
            )
        }
    }
