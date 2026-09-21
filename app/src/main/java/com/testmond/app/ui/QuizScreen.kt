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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
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

private val WHITESPACE_RUN = Regex("\\s+")

/** What a fill-in-the-blank answer is compared as: ignoring capitalisation, leading/trailing
 *  spaces, runs of spaces, and LaTeX `$` delimiters -- so an answer key written as `$100$` (the
 *  way an AI-formatted bank often has it) is matched by typing plain `100`. */
private fun normalizeBlankAnswer(text: String): String =
    text.replace("\$", "").trim().replace(WHITESPACE_RUN, " ").lowercase()

/** Forgiving match for fill-blank (see [normalizeBlankAnswer]); exact match for MCQ letters. */
internal fun isCorrect(question: McqQuestion, userAnswer: String?): Boolean {
    if (userAnswer == null) return false
    return if (question.type == QuestionType.FILL_BLANK) {
        normalizeBlankAnswer(userAnswer) == normalizeBlankAnswer(question.answer)
    } else {
        userAnswer == question.answer
    }
}

/** Correct/incorrect tints that adapt to light/dark theme instead of fixed hex colors. */
internal object AnswerColors {
    // Vivid, highly-saturated colors on purpose -- meant to stand out clearly ("like a bulb
    // glowing") rather than blend into the surrounding surface, so kept the same bright
    // values in both light and dark theme instead of the previous muted/darkened pairing.
    val correctBg: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFF00E676)
    val incorrectBg: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFFFF1744)
    val correctChip: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFF00C853)
    val incorrectChip: androidx.compose.ui.graphics.Color
        @Composable get() = androidx.compose.ui.graphics.Color(0xFFD50000)
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
    var current by remember { mutableStateOf(savedProgress?.currentIndex?.coerceIn(0, questions.size - 1) ?: 0) }
    val answers = remember {
        mutableStateListOf<String?>().apply {
            val restored = savedProgress?.answers
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
            if (restored != null && restored.size == questions.size) addAll(restored)
            else addAll(List(questions.size) { null })
        }
    }
    val clock = remember { QuestionClock() }
    var showResults by remember { mutableStateOf(false) }
    var restartCount by remember { mutableStateOf(0) }   // bumped on Reset / Retake so the clock restarts
    // Which question is opened from the review list (null = the list itself).
    var reviewIndex by remember { mutableStateOf<Int?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }

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
    BackHandler(enabled = !showResults) {
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
    ) { padding ->
        if (showResults) {
            ResultView(
                questions = questions,
                answers = answers,
                times = times,
                openIndex = reviewIndex,
                onOpenIndex = { reviewIndex = it },
                onRetake = {
                    for (i in answers.indices) answers[i] = null
                    for (i in drafts.indices) drafts[i] = null
                    for (i in times.indices) times[i] = null
                    restartCount += 1
                    current = 0
                    reviewIndex = null
                    showResults = false
                    setFile?.let { FileStorage.clearProgress(context, it) }
                },
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
                clock = clock
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
    clock: QuestionClock? = null
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxSize()) {

        // Question numbers: single horizontally-swipeable row, compact. Follows `current`
        // so resuming a partially-attempted test (where current starts mid-list, not at 0)
        // scrolls the slider to the right question instead of always showing it from 1.
        val chipListState = rememberLazyListState()
        LaunchedEffect(current) {
            chipListState.animateScrollToItem(current)
        }
        LazyRow(
            state = chipListState,
            modifier = Modifier.fillMaxWidth(),
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
                    onClick = { onJump(index) }
                )
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
            if (clock != null) QuestionTimeLabel(clock)
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
                MathAwareText(q.question, style = MaterialTheme.typography.titleMedium)
                if (q.imageBase64 != null) {
                    Spacer(Modifier.height(12.dp))
                    QuestionImage(q.imageBase64)
                }
                Spacer(Modifier.height(16.dp))
                val submittedAnswer = answers[current]
                val draftAnswer = drafts[current]
                // In live (exam) mode an answer is never "submitted" -- it stays editable.
                val isSubmitted = !liveAnswers && submittedAnswer != null
                val revealColors = isSubmitted && quizMode == QuizMode.QUIZ

                if (q.type == QuestionType.MCQ) {
                    q.options.forEach { opt ->
                        val isPicked = if (isSubmitted || liveAnswers) {
                            opt.letter == submittedAnswer
                        } else {
                            opt.letter == draftAnswer
                        }
                        val bg = when {
                            revealColors && opt.letter == q.answer -> AnswerColors.correctBg
                            revealColors && opt.letter == submittedAnswer -> AnswerColors.incorrectBg
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
                                        .clickable(onClick = { onSelect(opt.letter) })
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
    onClick: () -> Unit
) {
    val bg = when {
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
