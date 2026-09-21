package com.testmond.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.ExamSessionHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.QuestionType
import com.testmond.app.model.QuizMode
import kotlinx.coroutines.delay

/** 75 -> "01:15", 3725 -> "1:02:05". */
internal fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/** Whole seconds left until [endAtElapsedMillis] (rounded up, so the clock shows 00:01 until
 *  the very last moment instead of hitting 00:00 early). */
private fun secondsLeftUntil(endAtElapsedMillis: Long): Int {
    val left = endAtElapsedMillis - SystemClock.elapsedRealtime()
    return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
}

/**
 * A timed exam. Looks and works like the normal test screen (select an option, press Submit,
 * jump between questions with the number strip) with these differences:
 *  - the countdown timer replaces the test name in the top bar;
 *  - correct answers (and solutions) are NEVER shown while the exam runs;
 *  - there is no Reset, and no resume -- leaving asks for confirmation and loses everything;
 *  - when time runs out the exam is submitted automatically.
 * Once finished, the result (with the full answer review) is shown and the attempt is saved
 * to this test's exam history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamQuizScreen(navController: NavHostController) {
    val context = LocalContext.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    val session = ExamSessionHolder

    // No live exam to show (e.g. the app was killed mid-exam): exams don't resume, so go back.
    if (set == null || setFile == null || set.questions.isEmpty() ||
        session.answers.size != set.questions.size
    ) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val questions = set.questions
    val result = session.result.value
    // Custom marking for this test (Exam tab -> Custom rules), applied to the score in the result.
    val rules = remember(setFile) { FileStorage.loadExamRules(context, setFile) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    // Which question is opened from the result's review list (null = the list itself).
    var reviewIndex by remember { mutableStateOf<Int?>(null) }
    var secondsLeft by remember { mutableStateOf(secondsLeftUntil(session.endAtElapsedMillis)) }

    fun finishExam() {
        if (session.result.value != null) return

        val answers = session.answers.toList()
        val statuses = questions.indices.map { statusOf(questions[it], answers[it]) }
        val leftMillis = (session.endAtElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val attempt = ExamAttempt(
            timestampMillis = System.currentTimeMillis(),
            correct = statuses.count { it == QuestionStatus.CORRECT },
            wrong = statuses.count { it == QuestionStatus.WRONG },
            skipped = statuses.count { it == QuestionStatus.SKIPPED },
            total = questions.size,
            answers = answers,
            // Text-only snapshot: images (question and solution) are borrowed from the live test.
            questions = questions.map { it.copy(imageBase64 = null, solutionImageBase64 = null) },
            durationMillis = session.durationMillis,
            timeTakenMillis = (session.durationMillis - leftMillis).coerceIn(0L, session.durationMillis)
        )
        FileStorage.appendExamAttempt(context, setFile, attempt)
        session.result.value = attempt
    }

    // Countdown. Anchored to a fixed end time, so it stays correct across rotation or the app
    // being in the background, and submits the exam the moment it reaches zero.
    LaunchedEffect(Unit) {
        while (session.result.value == null) {
            secondsLeft = secondsLeftUntil(session.endAtElapsedMillis)
            if (secondsLeft <= 0) {
                finishExam()
                break
            }
            delay(250)
        }
    }

    // Back (gesture or arrow): during the exam it asks first. On the result screen it first closes
    // an opened question, then leaves.
    fun handleBack() {
        if (session.result.value == null) {
            showLeaveConfirm = true
        } else if (reviewIndex != null) {
            reviewIndex = null
        } else {
            navController.safePopBackStack()
        }
    }
    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (result == null) {
                        Text(
                            formatClock(secondsLeft.toLong()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (secondsLeft <= 60) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    } else {
                        Text("Exam result", style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (result == null) {
                        TextButton(onClick = { showFinishConfirm = true }) { Text("Finish") }
                    }
                }
            )
        }
    ) { padding ->
        if (result != null) {
            ExamAttemptDetail(
                attempt = result,
                liveQuestions = questions,
                rules = rules,
                openIndex = reviewIndex,
                onOpenIndex = { reviewIndex = it },
                onDone = { navController.safePopBackStack() },
                modifier = Modifier.padding(padding)
            )
        } else {
            QuizBody(
                questions = questions,
                answers = session.answers,
                drafts = session.drafts,
                // PRACTICE = no correct/incorrect colours anywhere while answering.
                quizMode = QuizMode.PRACTICE,
                current = session.current.value,
                onJump = { session.current.value = it },
                // No Submit step: a pick is recorded the moment it is made and can be changed at
                // any time until the exam is finished. Tapping the picked option again clears it;
                // emptying a fill-in-the-blank box clears it too.
                onSelect = { value ->
                    val i = session.current.value
                    val isChoice = questions[i].type == QuestionType.MCQ
                    session.answers[i] = when {
                        isChoice && session.answers[i] == value -> null
                        value.isBlank() -> null
                        else -> value
                    }
                },
                onSubmit = {},
                onPrev = {
                    if (session.current.value > 0) {
                        session.current.value = session.current.value - 1
                    }
                },
                onNext = {
                    if (session.current.value < questions.size - 1) {
                        session.current.value = session.current.value + 1
                    }
                },
                modifier = Modifier.padding(padding),
                // A solution's explanation would give the answer away mid-exam.
                showSolution = false,
                liveAnswers = true
            )
        }
    }

    if (result == null && showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave this exam?") },
            text = { Text("Do you want to leave? Your progress will be lost, and this attempt won't be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    navController.safePopBackStack()
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Keep going") }
            }
        )
    }

    if (result == null && showFinishConfirm) {
        val unanswered = questions.indices.count { i -> session.answers[i] == null }
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finish this exam?") },
            text = {
                Text(
                    if (unanswered > 0)
                        "You still have $unanswered question${if (unanswered != 1) "s" else ""} unanswered. You won't be able to change any answers after finishing."
                    else
                        "You won't be able to change any answers after finishing."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    finishExam()
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
