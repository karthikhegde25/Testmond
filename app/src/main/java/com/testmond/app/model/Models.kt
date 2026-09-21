package com.testmond.app.model

import kotlinx.serialization.Serializable

/**
 * The .mcqz file format. Plain JSON, versioned so future app updates
 * can migrate older files. Any text editor can open it if needed --
 * that's the point of keeping it open and simple.
 *
 * version 1 files had MCQ-only questions with no `type` field; the
 * default below makes them load fine as MCQ questions in version 2+.
 */
@Serializable
enum class QuestionType {
    MCQ,
    FILL_BLANK
}

@Serializable
data class McqOption(
    val letter: String,
    val text: String
)

@Serializable
data class McqQuestion(
    val type: QuestionType = QuestionType.MCQ,
    val question: String,
    val options: List<McqOption> = emptyList(),  // empty for FILL_BLANK
    val answer: String,                           // option letter for MCQ, expected text for FILL_BLANK
    val imageBase64: String? = null,              // optional image shown with the question during the test
    val solution: String? = null,                 // optional explanation shown after the question is submitted
    val solutionImageBase64: String? = null       // optional image attached to the solution (shown below its text)
)

@Serializable
data class McqSet(
    val version: Int = 2,
    val title: String,
    val questions: List<McqQuestion>
)

/** Which paste format the user is using on the Create screen. */
enum class PasteFormat {
    COMBINED,       // Q + (options for MCQ) + ANSWER: all together, per block
    SEPARATE_KEY    // numbered questions in one box, numbered answer key in another
}

/** A parse failure the user should see, tied to a line/question number where possible. */
data class ParseIssue(val reference: String, val message: String)

data class ParseResult(
    val questions: List<McqQuestion>,
    val issues: List<ParseIssue>
)

/** In-progress answers for a set, persisted so leaving mid-test and coming back resumes it. */
@Serializable
data class QuizProgress(
    val currentIndex: Int,
    val answers: List<String?>,
    val timestampMillis: Long,
    val drafts: List<String?> = emptyList(),
    /** How long each already-submitted question took (milliseconds), so a resumed test still has them. */
    val questionTimesMillis: List<Long?> = emptyList()
)

/** One completed attempt at a set, kept in a running history per set. */
@Serializable
data class QuizAttempt(
    val timestampMillis: Long,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val total: Int
)

@Serializable
data class QuizAttemptHistory(
    val attempts: List<QuizAttempt> = emptyList()
)

/** App-wide appearance preference. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide test-taking behavior, applies to every set.
 *  QUIZ: answer is revealed (correct/incorrect) the moment it's submitted.
 *  PRACTICE: no reveal during the test; correctness only shows on the review page after Finish. */
enum class QuizMode { QUIZ, PRACTICE }

/** A folder groups existing tests by referencing their filenames in the library.
 *  Deleting a folder never deletes the tests themselves -- it's just a grouping. */
@Serializable
data class TestFolder(
    val name: String,
    val testFileNames: List<String> = emptyList()
)

/** Portable export format for a folder: the folder name plus the FULL content of every
 *  test in it (not just filenames), so the single exported file is self-contained and
 *  can rebuild the folder and its tests on another device. */
@Serializable
data class FolderBundle(
    val folderName: String,
    val sets: List<McqSet> = emptyList()
)

/** The tests the user has added to the Exam tab, by library filename. Just a reference
 *  list -- removing a test from it never touches the test itself. */
@Serializable
data class ExamModeList(
    val testFileNames: List<String> = emptyList()
)

/** One finished (or timed-out) exam. Unlike a normal QuizAttempt it keeps every answer plus a
 *  text-only snapshot of the questions as they were, so the review can be reopened any time and
 *  still shows the right/wrong result even if the test is edited afterwards. Images are not
 *  snapshotted (they'd bloat every attempt) -- the review borrows them from the live test when
 *  the question at that position is unchanged. */
@Serializable
data class ExamAttempt(
    val timestampMillis: Long,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val total: Int,
    val answers: List<String?>,
    val questions: List<McqQuestion> = emptyList(),
    val durationMillis: Long = 0L,      // the time limit that was set
    val timeTakenMillis: Long = 0L      // how much of it was actually used
)

@Serializable
data class ExamAttemptHistory(
    val attempts: List<ExamAttempt> = emptyList()
)

/** Custom marking for one Exam Mode test: marks awarded per correct, wrong and skipped question
 *  (e.g. 4 / -1 / 0). All zero means "no custom rule" -- the review then shows the normal
 *  correct-out-of-total score. Applied when a review is displayed, so changing it re-scores
 *  every past attempt of that test. */
@Serializable
data class ExamRules(
    val correct: Double = 0.0,
    val wrong: Double = 0.0,
    val skipped: Double = 0.0
) {
    fun isActive(): Boolean = correct != 0.0 || wrong != 0.0 || skipped != 0.0
}

/** Custom rules for every test that has them, keyed by library filename. */
@Serializable
data class ExamRulesStore(
    val rules: Map<String, ExamRules> = emptyMap()
)
