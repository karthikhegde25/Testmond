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
    /** The test has been finished and not started again since -- shows the green tick, and its
     *  card offers View / Reattempt (FileStorage.loadResult holds the answers). */
    val isCompleted: Boolean,
    /** When progress was last saved (QuizProgress.timestampMillis), or null if there is none.
     *  Used to put the most recently worked-on in-progress test at the very top of that group,
     *  regardless of whichever SortOption is otherwise chosen -- see HomeScreen's displayedTestFiles. */
    val progressTimestampMillis: Long?,
    val hasSolution: Boolean,
    val questionCount: Int
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
private const val QUESTION_KEY = "\"question\":"

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

/** How many questions a test file has, found the same streamed, no-JSON-parsing way as
 *  [fileHasSolution]: each question object is written with a `"question":` key (the plural
 *  `"questions":` array key never matches, since the character right after "question" there is
 *  "s", not the closing quote), so counting that key's occurrences is exact without ever touching
 *  a question's image data. Cheap enough to run for every file up front, unlike a full parse. */
internal fun countQuestionsQuick(file: File): Int = runCatching {
    file.bufferedReader().use { reader ->
        val buffer = CharArray(16 * 1024)
        var carry = ""
        var count = 0
        var n = reader.read(buffer)
        while (n >= 0) {
            val window = carry + String(buffer, 0, n)
            var from = 0
            while (true) {
                val idx = window.indexOf(QUESTION_KEY, from)
                if (idx < 0) break
                count++
                from = idx + QUESTION_KEY.length
            }
            carry = window.takeLast(QUESTION_KEY.length - 1)
            n = reader.read(buffer)
        }
        count
    }
}.getOrDefault(0)

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
            val progress = FileStorage.loadProgress(context, file)
            TestCardInfo(
                stamp = stamp,
                attempts = FileStorage.loadAttempts(context, file),
                hasProgress = progress != null,
                isCompleted = progress == null && FileStorage.hasResult(context, file),
                progressTimestampMillis = progress?.timestampMillis,
                hasSolution = fileHasSolution(file),
                questionCount = countQuestionsQuick(file)
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
