package com.testmond.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.AppSettings
import com.testmond.app.data.FileStorage
import com.testmond.app.model.McqQuestion
import com.testmond.app.model.QuestionType
import com.testmond.app.model.QuizAttempt
import com.testmond.app.model.QuizMode
import com.testmond.app.model.QuizProgress
import com.testmond.app.model.QuizResult
import com.testmond.app.model.allImages
import com.testmond.app.model.answerLetters
import com.testmond.app.model.isMultiAnswer

private val WHITESPACE_RUN = Regex("\\s+")

/** What a fill-in-the-blank answer is compared as: ignoring capitalisation, leading/trailing
 *  spaces, runs of spaces, and LaTeX `$` delimiters -- so an answer key written as `$100$` (the
 *  way an AI-formatted bank often has it) is matched by typing plain `100`. */
private fun normalizeBlankAnswer(text: String): String =
    text.replace("\$", "").trim().replace(WHITESPACE_RUN, " ").lowercase()

/** Forgiving match for fill-blank (see [normalizeBlankAnswer]); exact letter match for a normal
 *  MCQ; for a multi-answer MCQ (see [isMultiAnswer]), every correct letter must be picked and no
 *  extra one -- order in [userAnswer] doesn't matter. */
internal fun isCorrect(question: McqQuestion, userAnswer: String?): Boolean {
    if (userAnswer == null) return false
    return when {
        question.type == QuestionType.FILL_BLANK ->
            normalizeBlankAnswer(userAnswer) == normalizeBlankAnswer(question.answer)
        question.isMultiAnswer -> {
            val picked = userAnswer.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            picked.isNotEmpty() && picked == question.answerLetters.toSet()
        }
        else -> userAnswer == question.answer
    }
}

/** Correct/incorrect tints that adapt to light/dark theme instead of fixed hex colors. */
internal object AnswerColors {
    // ForestGreen / Firebrick, used consistently everywhere correct/wrong is shown: the answer
    // background at reveal time, the question-number strip, and Grid View.
    val correctBg: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFF258A01)
    val incorrectBg: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFFC00402)
    // Exam Mode's "marked for review" flag -- deliberately a color used nowhere else in the
    // answered/correct/incorrect scheme, so a marked question is unmistakable at a glance.
    val reviewMark: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFF7C4DFF)
    val correctChip: androidx.compose.ui.graphics.Color
        @Composable get() = correctBg
    val incorrectChip: androidx.compose.ui.graphics.Color
        @Composable get() = incorrectBg
    val skippedChip: androidx.compose.ui.graphics.Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val answeredNeutralChip: androidx.compose.ui.graphics.Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val selectedTint: androidx.compose.ui.graphics.Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
}

/**
 * Stopwatch for the question currently on screen in a normal test. It starts from zero when the
 * question opens (also when you come back to an unsubmitted one -- nothing is carried over), counts
 * only while the app is in the foreground, and is frozen once the question is submitted.
 * [shownMillis] is what the label displays (null = nothing to show).
 */
internal class QuestionClock {
    val shownMillis = mutableStateOf<Long?>(null)
    private var elapsed = 0L
    private var lastTick = 0L

    fun restart() {
        elapsed = 0L
        lastTick = SystemClock.elapsedRealtime()
        shownMillis.value = 0L
    }

    /** Adds the time since the previous call (only while [active]) and returns the total so far. */
    fun tick(active: Boolean): Long {
        val now = SystemClock.elapsedRealtime()
        if (active) elapsed += now - lastTick
        lastTick = now
        shownMillis.value = elapsed
        return elapsed
    }

    fun showFixed(millis: Long?) {
        shownMillis.value = millis
    }
}

@Composable
private fun QuestionTimeLabel(clock: QuestionClock) {
    val millis = clock.shownMillis.value ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Timer,
            contentDescription = "Time on this question",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            formatClock(millis / 1000),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal enum class QuestionStatus { CORRECT, WRONG, SKIPPED }

internal fun statusOf(question: McqQuestion, answer: String?): QuestionStatus = when {
    answer == null -> QuestionStatus.SKIPPED
    isCorrect(question, answer) -> QuestionStatus.CORRECT
    else -> QuestionStatus.WRONG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(navController: NavHostController) {
    val context = LocalContext.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    if (set == null) {
        navController.popBackStack()
        return
    }

    val questions = set.questions
    val quizMode = AppSettings.quizMode

    // Resume from saved progress if any exists for this set.
    val savedProgress = remember(setFile) { setFile?.let { FileStorage.loadProgress(context, it) } }
    // A tap on a bookmarked question (see BookmarksScreen) sets this just before navigating here,
    // so the quiz opens straight to that question instead of wherever saved progress left off.
    // Consumed once: cleared right after being read so it doesn't stick on a later revisit.
    val jumpTo = ActiveSetHolder.jumpToIndex.value
    LaunchedEffect(Unit) { ActiveSetHolder.jumpToIndex.value = null }
    // "View" on a completed test (see CompletedTestUi.kt) opens this screen straight onto that
    // finished attempt -- the results and review, with every answer as it was given -- instead of
    // starting the test. A bookmarked question of a completed test opens the same way (on that
    // question) so merely looking at it can't wipe the finished attempt: any new progress would.
    val viewRequested = ActiveSetHolder.viewResult.value
    LaunchedEffect(Unit) { ActiveSetHolder.viewResult.value = false }
    val savedResult = remember(setFile) {
        if (savedProgress == null && setFile != null) {
            FileStorage.loadResult(context, setFile)?.takeIf { it.answers.size == questions.size }
        } else null
    }
    val openAsResult = savedResult != null && (viewRequested || jumpTo != null)
    var current by remember {
        mutableStateOf(
            jumpTo?.coerceIn(0, questions.size - 1)
                ?: savedProgress?.currentIndex?.coerceIn(0, questions.size - 1)
                ?: 0
        )
    }
    val answers = remember {
        mutableStateListOf<String?>().apply {
            val restored = savedProgress?.answers ?: savedResult?.takeIf { openAsResult }?.answers
            if (restored != null && restored.size == questions.size) addAll(restored)
            else addAll(List(questions.size) { null })
        }
    }
    // Draft = picked/typed but not yet submitted. Kept separate so selecting an option
    // doesn't lock it in or reveal correctness until Submit is pressed.
    val drafts = remember {
        mutableStateListOf<String?>().apply {
            val restored = savedProgress?.drafts
            if (restored != null && restored.size == questions.size) addAll(restored)
            else addAll(List(questions.size) { null })
        }
    }
    // Time each submitted question took (ms; null = unknown / not submitted yet).
    val times = remember {
        mutableStateListOf<Long?>().apply {
            val restored = savedProgress?.questionTimesMillis
                ?: savedResult?.takeIf { openAsResult }?.questionTimesMillis
            if (restored != null && restored.size == questions.size) addAll(restored)
            else addAll(List(questions.size) { null })
        }
    }
    val clock = remember { QuestionClock() }
    var showResults by remember { mutableStateOf(openAsResult) }
    var restartCount by remember { mutableStateOf(0) }   // bumped on Reset / Retake so the clock restarts
    // Which question is opened from the review list (null = the list itself).
    var reviewIndex by remember {
        mutableStateOf<Int?>(if (openAsResult && jumpTo != null) jumpTo.coerceIn(0, questions.size - 1) else null)
    }
    var showRetakeConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    // The "Grid View" screen: every question number at once (with a correct/incorrect/not
    // answered tally in Quiz mode, just answered/not answered in Practice), tapping one jumps
    // straight to it. Only offered while actually taking the test, not on the results screen.
    var showGridView by remember { mutableStateOf(false) }
    // Bookmarks: a personal marker on a question, independent of answering it, so it can be found
    // again later from this test's three-dot menu ("Bookmarks"). Persisted per test, Test tab only
    // (never in Exam Mode -- see ExamQuizScreen, which never passes onToggleBookmark to QuizBody).
    var bookmarked by remember(setFile) {
        mutableStateOf(setFile?.let { FileStorage.loadBookmarks(context, it) } ?: emptySet())
    }
    fun toggleBookmark(index: Int) {
        val file = setFile ?: return
        bookmarked = FileStorage.setBookmarked(context, file, index, index !in bookmarked)
    }

    fun persistProgress() {
        setFile?.let {
            FileStorage.saveProgress(
                context, it,
                QuizProgress(current, answers.toList(), System.currentTimeMillis(), drafts.toList(), times.toList())
            )
        }
    }

    fun finishQuiz() {
        setFile?.let {
            val correct = questions.indices.count { i -> statusOf(questions[i], answers[i]) == QuestionStatus.CORRECT }
            val wrong = questions.indices.count { i -> statusOf(questions[i], answers[i]) == QuestionStatus.WRONG }
            val skipped = questions.indices.count { i -> statusOf(questions[i], answers[i]) == QuestionStatus.SKIPPED }
            FileStorage.appendAttempt(
                context, it,
                QuizAttempt(System.currentTimeMillis(), correct, wrong, skipped, questions.size)
            )
            FileStorage.clearProgress(context, it)
            // Keep this attempt's answers so the test can be viewed again as completed (green tick).
            FileStorage.saveResult(
                context, it,
                QuizResult(answers.toList(), times.toList(), System.currentTimeMillis())
            )
        }
        showResults = true
    }

    fun resetQuiz() {
        for (i in answers.indices) answers[i] = null
        for (i in drafts.indices) drafts[i] = null
        for (i in times.indices) times[i] = null
        restartCount += 1
        current = 0
        setFile?.let { FileStorage.clearProgress(context, it) }
    }

    // Confirm before leaving mid-test, whether via system back gesture or the top bar arrow.
    BackHandler(enabled = showGridView) { showGridView = false }
    BackHandler(enabled = !showResults && !showGridView) {
        showExitConfirm = true
    }
    // On the review, back first closes an opened question and only then leaves.
    BackHandler(enabled = showResults && reviewIndex != null) {
        reviewIndex = null
    }

    // Per-question timer. Time only counts while the app is in the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    var appActive by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appActive = true
            if (event == Lifecycle.Event.ON_PAUSE) appActive = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Runs whenever a question opens (or the user comes back to one): a submitted question just
    // shows the time it took; an unsubmitted one starts counting from zero until it is submitted.
    LaunchedEffect(current, showResults, restartCount) {
        if (showResults) return@LaunchedEffect
        if (answers[current] != null) {
            clock.showFixed(times.getOrNull(current))
            return@LaunchedEffect
        }
        clock.restart()
        while (answers[current] == null) {
            delay(250)
            if (answers[current] != null) break
            clock.tick(appActive)
        }
    }

    Scaffold(
        topBar = {
            if (showGridView) {
                TopAppBar(
                    title = { Text("Grid View") },
                    navigationIcon = {
                        IconButton(onClick = { showGridView = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close grid view")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            set.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showResults) {
                                if (reviewIndex != null) {
                                    reviewIndex = null
                                } else {
                                    navController.safePopBackStack()
                                }
                            } else {
                                showExitConfirm = true
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!showResults) {
                            IconButton(onClick = { showResetConfirm = true }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset test")
                            }
                            TextButton(onClick = { showFinishConfirm = true }) { Text("Finish") }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (showGridView) {
            GridViewBody(
                questions = questions,
                answers = answers,
                current = current,
                revealCorrectness = quizMode == QuizMode.QUIZ,
                onJump = { index ->
                    current = index
                    persistProgress()
                    showGridView = false
                },
                modifier = Modifier.padding(padding)
            )
        } else if (showResults) {
            ResultView(
                questions = questions,
                answers = answers,
                times = times,
                openIndex = reviewIndex,
                onOpenIndex = { reviewIndex = it },
                onRetake = { showRetakeConfirm = true },
                onDone = { navController.safePopBackStack() },
                modifier = Modifier.padding(padding)
            )
        } else {
            QuizBody(
                questions = questions,
                answers = answers,
                drafts = drafts,
                quizMode = quizMode,
                current = current,
                onJump = { current = it; persistProgress() },
                onSelect = { text -> drafts[current] = text },
                onSubmit = {
                    val picked = drafts[current]
                    if (picked != null) {
                        // Freeze this question's time at the moment it is submitted.
                        times[current] = clock.tick(appActive)
                        clock.showFixed(times[current])
                        answers[current] = picked
                        persistProgress()
                    }
                },
                onPrev = { if (current > 0) { current--; persistProgress() } },
                onNext = { if (current < questions.size - 1) { current++; persistProgress() } },
                modifier = Modifier.padding(padding),
                clock = clock,
                bookmarked = questions.indices.map { it in bookmarked },
                onToggleBookmark = { toggleBookmark(current) },
                onOpenGridView = { showGridView = true }
            )
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit this test?") },
            text = { Text("Your progress will be saved, and you can pick up right where you left off.") },
            confirmButton = {
                TextButton(onClick = {
                    val hasProgress = current > 0 || answers.any { it != null } || drafts.any { it != null }
                    if (hasProgress) {
                        persistProgress()
                    } else {
                        setFile?.let { FileStorage.clearProgress(context, it) }
                    }
                    showExitConfirm = false
                    navController.safePopBackStack()
                }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Keep going") }
            }
        )
    }

    if (showRetakeConfirm) {
        ReattemptConfirmDialog(
            onConfirm = {
                showRetakeConfirm = false
                for (i in answers.indices) answers[i] = null
                for (i in drafts.indices) drafts[i] = null
                for (i in times.indices) times[i] = null
                restartCount += 1
                current = 0
                reviewIndex = null
                showResults = false
                setFile?.let {
                    FileStorage.clearProgress(context, it)
                    FileStorage.clearResult(context, it)   // the green tick goes: it's a new attempt
                }
            },
            onDismiss = { showRetakeConfirm = false }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset this test?") },
            text = { Text("All your selected answers will be cleared and you'll start over from question 1. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    resetQuiz()
                    showResetConfirm = false
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showFinishConfirm) {
        val skippedCount = answers.count { it == null }
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finish this test?") },
            text = {
                Text(
                    if (skippedCount > 0)
                        "You still have $skippedCount question${if (skippedCount != 1) "s" else ""} unanswered. You won't be able to change any answers after finishing."
                    else
                        "You won't be able to change any answers after finishing."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    finishQuiz()
                }) {
                    Text("Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Keep going") }
            }
        )
    }
}

/** Shared by the normal quiz and Exam Mode. [showSolution] is turned off for exams, since a
 *  solution's explanation would give away which answer is correct mid-exam. With [liveAnswers]
 *  (exams) there is no Submit step: [onSelect] records the answer straight into [answers], and it
 *  stays editable until the exam is finished. */
@Composable
internal fun QuizBody(
    questions: List<McqQuestion>,
    answers: List<String?>,
    drafts: List<String?>,
    quizMode: QuizMode,
    current: Int,
    onJump: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showSolution: Boolean = true,
    liveAnswers: Boolean = false,
    /** Normal tests pass the per-question stopwatch; exams (which have their own countdown) don't. */
    clock: QuestionClock? = null,
    /** Exam Mode only: which questions are currently flagged "mark for review" (index-parallel to
     *  [questions]); [onToggleMarkForReview], when non-null, draws the mark-for-review box between
     *  the Prev/Next arrows and flips [current]'s flag when tapped. Both stay unused (and nothing
     *  extra is drawn) for a normal test. */
    markedForReview: List<Boolean> = emptyList(),
    onToggleMarkForReview: (() -> Unit)? = null,
    /** Test tab only (Quiz/Practice): which questions are bookmarked (index-parallel to
     *  [questions]); [onToggleBookmark], when non-null, draws the bookmark toggle in the same spot
     *  as the mark-for-review box. The two are mutually exclusive -- a caller passes one or the
     *  other, never both -- but both are optional so a plain call needs neither. */
    bookmarked: List<Boolean> = emptyList(),
    onToggleBookmark: (() -> Unit)? = null,
    /** Opens Grid View (see QuizScreen.kt / ExamQuizScreen.kt's own showGridView state). Drawn
     *  fixed at the right edge of the question-number row below, outside its horizontal scroll,
     *  so it stays in place while the numbers scroll past it. */
    onOpenGridView: () -> Unit
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxSize()) {

        // Question numbers: single horizontally-swipeable row, compact. Follows `current`
        // so resuming a partially-attempted test (where current starts mid-list, not at 0)
        // scrolls the slider to the right question instead of always showing it from 1. The
        // Grid View button sits outside the LazyRow itself (a sibling, not an item) so it stays
        // fixed at the right edge instead of scrolling away with the numbers.
        val chipListState = rememberLazyListState()
        LaunchedEffect(current) {
            chipListState.animateScrollToItem(current)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = chipListState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(questions.size) { index ->
                    QuestionNumberChip(
                        number = index + 1,
                        isCurrent = index == current,
                        isAnswered = answers[index] != null,
                        isCorrect = isCorrect(questions[index], answers[index]),
                        revealCorrectness = quizMode == QuizMode.QUIZ,
                        markedForReview = markedForReview.getOrNull(index) == true,
                        onClick = { onJump(index) }
                    )
                }
            }
            IconButton(onClick = onOpenGridView) {
                Icon(Icons.Default.Apps, contentDescription = "Grid view")
            }
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = (current + 1f) / questions.size,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Question ${current + 1} of ${questions.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Test tab only (never Exam Mode -- see the onToggleBookmark doc above). Icon
                // only, right next to the per-question timer, same spot as most quiz apps put it.
                if (onToggleBookmark != null) {
                    val isBookmarked = bookmarked.getOrNull(current) == true
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark this question",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (clock != null) QuestionTimeLabel(clock)
            }
        }

        Spacer(Modifier.height(12.dp))
        // Content card scrolls internally so long questions/options never get clipped.
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                val q = questions[current]
                if (q.isMultiAnswer) {
                    Text(
                        "MULTIPLE ANSWERS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                MathAwareText(q.question, style = MaterialTheme.typography.titleMedium)
                if (q.allImages.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    QuestionImages(q.allImages)
                }
                Spacer(Modifier.height(16.dp))
                val submittedAnswer = answers[current]
                val draftAnswer = drafts[current]
                // In live (exam) mode an answer is never "submitted" -- it stays editable.
                val isSubmitted = !liveAnswers && submittedAnswer != null
                val revealColors = isSubmitted && quizMode == QuizMode.QUIZ

                if (q.type == QuestionType.MCQ) {
                    // For a multi-answer question this is every letter currently picked (one or
                    // more); for a normal question it's a single-element set, same as before.
                    val pickedLetters = (if (isSubmitted || liveAnswers) submittedAnswer else draftAnswer)
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                    val correctLetters = q.answerLetters.toSet()
                    q.options.forEach { opt ->
                        val isPicked = opt.letter in pickedLetters
                        val bg = when {
                            revealColors && opt.letter in correctLetters -> AnswerColors.correctBg
                            revealColors && isPicked -> AnswerColors.incorrectBg
                            isPicked -> AnswerColors.selectedTint
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val borderColor = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(bg)
                                .border(1.dp, borderColor, RoundedCornerShape(50))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("${opt.letter}) ")
                                MathAwareText(opt.text, modifier = Modifier.weight(1f))
                            }
                            // Overlay drawn on top of the row above (including any math WebView in
                            // it) so this Compose click detector always wins the touch, regardless
                            // of a WebView underneath swallowing it first -- that swallowing was
                            // the actual cause of options being slow or impossible to (re)select.
                            if (!isSubmitted) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clickable(onClick = {
                                            if (q.isMultiAnswer) {
                                                val newPicked = if (isPicked) pickedLetters - opt.letter else pickedLetters + opt.letter
                                                // Empty is allowed -- unticking every option means
                                                // "no answer yet", same as never having picked one.
                                                // The Test tab's Submit button stays disabled on a
                                                // blank draft, and Exam mode's onSelect treats a
                                                // blank value as null (not answered), so this
                                                // never gets scored as a wrong pick.
                                                val ordered = q.options.map { it.letter }.filter { it in newPicked }
                                                onSelect(ordered.joinToString(","))
                                            } else {
                                                onSelect(opt.letter)
                                            }
                                        })
                                )
                            }
                        }
                    }
                } else if (liveAnswers) {
                    OutlinedTextField(
                        value = submittedAnswer ?: "",
                        onValueChange = onSelect,
                        label = { Text("Your answer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FillBlankInput(
                        question = q,
                        submittedAnswer = submittedAnswer,
                        draftAnswer = draftAnswer,
                        revealColors = revealColors,
                        onDraftChange = onSelect
                    )
                }

                if (isSubmitted && !revealColors) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Answer recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showSolution && isSubmitted && hasSolutionContent(q)) {
                    Spacer(Modifier.height(16.dp))
                    SolutionContent(q)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = current > 0) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous question")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val submittedAnswer = answers[current]
                if (liveAnswers) {
                    // No Submit in an exam -- just say whether this question has an answer yet.
                    Text(
                        if (submittedAnswer == null) "Not answered" else "Answered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (submittedAnswer == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                } else if (submittedAnswer == null) {
                    Button(
                        onClick = onSubmit,
                        enabled = !drafts[current].isNullOrBlank()
                    ) {
                        Text("Submit")
                    }
                } else {
                    val markText = when {
                        quizMode == QuizMode.PRACTICE -> "Answered"
                        isCorrect(questions[current], submittedAnswer) -> "Correct"
                        else -> "Incorrect"
                    }
                    Text(markText, style = MaterialTheme.typography.bodyMedium)
                }

                if (onToggleMarkForReview != null) {
                    val marked = markedForReview.getOrNull(current) == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onToggleMarkForReview)
                    ) {
                        // onCheckedChange left null (and the checkbox itself non-interactive) so
                        // tapping it doesn't ALSO fire the surrounding Row's click -- one tap
                        // anywhere in this box, one toggle, not a toggle that cancels itself out.
                        Checkbox(checked = marked, onCheckedChange = null)
                        Text("Mark for review", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            IconButton(onClick = onNext, enabled = current < questions.size - 1) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next question")
            }
        }
    }
}

@Composable
private fun FillBlankInput(
    question: McqQuestion,
    submittedAnswer: String?,
    draftAnswer: String?,
    revealColors: Boolean,
    onDraftChange: (String) -> Unit
) {
    if (submittedAnswer != null) {
        val correct = isCorrect(question, submittedAnswer)
        val bg = when {
            !revealColors -> AnswerColors.selectedTint
            correct -> AnswerColors.correctBg
            else -> AnswerColors.incorrectBg
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("Your answer: $submittedAnswer", style = MaterialTheme.typography.bodyMedium)
            if (revealColors && !correct) {
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Correct answer: ", style = MaterialTheme.typography.bodyMedium)
                    MathAwareText(question.answer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    } else {
        OutlinedTextField(
            value = draftAnswer ?: "",
            onValueChange = onDraftChange,
            label = { Text("Your answer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuestionNumberChip(
    number: Int,
    isCurrent: Boolean,
    isAnswered: Boolean,
    isCorrect: Boolean,
    revealCorrectness: Boolean,
    markedForReview: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        // Marked-for-review wins over every other coloring -- it's Exam Mode's own flag, not a
        // correctness signal, so it must stay visually distinct even on an answered question.
        markedForReview -> AnswerColors.reviewMark
        !isAnswered -> MaterialTheme.colorScheme.surfaceVariant
        !revealCorrectness -> AnswerColors.answeredNeutralChip
        isCorrect -> AnswerColors.correctChip
        else -> AnswerColors.incorrectChip
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$number", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GridStatChip(count: Int, label: String, chipColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(chipColor),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Every question number at once, in a scrollable grid, so a long test can be jumped around
 *  without swiping through the whole number strip. The tally at top shows correct/incorrect
 *  counts only when [revealCorrectness] is true (Quiz mode); Practice and Exam mode -- where
 *  correctness is never shown while answering -- get a plain answered/not-answered count
 *  instead, matching [QuestionNumberChip]'s own color rule for the same reason. */
@Composable
internal fun GridViewBody(
    questions: List<McqQuestion>,
    answers: List<String?>,
    current: Int,
    revealCorrectness: Boolean,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Exam Mode only: see [QuizBody]'s param of the same name. */
    markedForReview: List<Boolean> = emptyList()
) {
    val correctCount = questions.indices.count { statusOf(questions[it], answers[it]) == QuestionStatus.CORRECT }
    val wrongCount = questions.indices.count { statusOf(questions[it], answers[it]) == QuestionStatus.WRONG }
    val notAnsweredCount = answers.count { it == null }
    val answeredCount = answers.size - notAnsweredCount
    val markedCount = markedForReview.count { it }

    Column(modifier.padding(16.dp).fillMaxSize()) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (revealCorrectness) {
                        GridStatChip(correctCount, "Correct", AnswerColors.correctChip)
                        GridStatChip(wrongCount, "Incorrect", AnswerColors.incorrectChip)
                    } else {
                        GridStatChip(answeredCount, "Answered", AnswerColors.answeredNeutralChip)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GridStatChip(notAnsweredCount, "Not Answered", AnswerColors.skippedChip)
                    if (markedCount > 0) {
                        GridStatChip(markedCount, "Marked", AnswerColors.reviewMark)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions.size) { index ->
                QuestionNumberChip(
                    number = index + 1,
                    isCurrent = index == current,
                    isAnswered = answers[index] != null,
                    isCorrect = isCorrect(questions[index], answers[index]),
                    revealCorrectness = revealCorrectness,
                    markedForReview = markedForReview.getOrNull(index) == true,
                    onClick = { onJump(index) }
                )
            }
        }
    }
}

@Composable
private fun ResultView(
    questions: List<McqQuestion>,
    answers: List<String?>,
    times: List<Long?>,
    openIndex: Int?,
    onOpenIndex: (Int?) -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Kept ABOVE the early return below so the list's scroll position survives opening a question
    // and coming back (it used to restart from the top), and so the list can scroll to the
    // question that was just being read.
    val listState = rememberLazyListState()
    var lastOpened by remember { mutableStateOf<Int?>(null) }

    // A question was tapped in the review: show it in full, solution below.
    if (openIndex != null) {
        SideEffect { lastOpened = openIndex }
        ReviewQuestionDetail(
            questions = questions,
            answers = answers,
            index = openIndex,
            onIndexChange = { onOpenIndex(it) },
            modifier = modifier,
            timesMillis = times
        )
        return
    }

    LaunchedEffect(Unit) {
        lastOpened?.let { listState.scrollToItem(it) }
    }

    val correct = questions.indices.count { statusOf(questions[it], answers[it]) == QuestionStatus.CORRECT }
    val wrong = questions.indices.count { statusOf(questions[it], answers[it]) == QuestionStatus.WRONG }
    val skipped = questions.indices.count { statusOf(questions[it], answers[it]) == QuestionStatus.SKIPPED }

    Column(modifier.padding(16.dp).fillMaxSize()) {
        Text("Your score", style = MaterialTheme.typography.titleSmall)
        Text(
            "$correct / ${questions.size}",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScoreStat(label = "Correct", value = correct, color = AnswerColors.correctChip)
            ScoreStat(label = "Wrong", value = wrong, color = AnswerColors.incorrectChip)
            ScoreStat(label = "Skipped", value = skipped, color = AnswerColors.skippedChip)
        }

        Spacer(Modifier.height(20.dp))
        Text("Review", style = MaterialTheme.typography.labelLarge)
        Text(
            "Tap a question to see it in full, with its solution.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(questions.indices.toList(), key = { it }) { index ->
                ReviewQuestionRow(
                    index = index,
                    question = questions[index],
                    status = statusOf(questions[index], answers[index]),
                    onClick = { onOpenIndex(index) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Done")
            }
            Button(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Retake this set")
            }
        }
    }
}

@Composable
internal fun ScoreStat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("$value", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
