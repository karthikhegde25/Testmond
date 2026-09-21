package com.testmond.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.ExamRules
import com.testmond.app.model.McqQuestion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 4.0 -> "4", -1.0 -> "-1", 0.25 -> "0.25" (at most two decimals). */
internal fun formatMarks(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

private fun signedMarks(value: Double): String =
    if (value > 0.0) "+${formatMarks(value)}" else formatMarks(value)

/** "+4 correct · -1 wrong · 0 skipped" */
internal fun markingSummary(rules: ExamRules): String =
    "${signedMarks(rules.correct)} correct \u00B7 ${signedMarks(rules.wrong)} wrong \u00B7 ${signedMarks(rules.skipped)} skipped"

/**
 * The score to show for an attempt. Without custom rules (null, or all zero) it is the normal
 * "correct / total". With rules it is the marks earned, e.g. 13 correct at +4, 5 wrong at -1 and
 * 2 skipped at 0 = "47 / 80" (out of what an all-correct exam would have earned, when correct
 * answers score positive marks).
 */
internal fun scoreText(attempt: ExamAttempt, rules: ExamRules?): String {
    if (rules == null || !rules.isActive()) return "${attempt.correct} / ${attempt.total}"
    val marks = attempt.correct * rules.correct + attempt.wrong * rules.wrong + attempt.skipped * rules.skipped
    val maxMarks = if (rules.correct > 0.0) attempt.total * rules.correct else null
    return if (maxMarks != null) "${formatMarks(marks)} / ${formatMarks(maxMarks)}" else formatMarks(marks)
}

/**
 * The saved review for one test in Exam Mode: every exam ever taken on it (newest first). Tap an
 * attempt to reopen its full review -- which questions were right, wrong or skipped, with the
 * correct answers -- at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamReviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    if (set == null || setFile == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val attempts = remember(setFile) {
        FileStorage.loadExamAttempts(context, setFile).sortedByDescending { it.timestampMillis }
    }
    // Custom marking for this test, if any; the score below is calculated with it on the fly.
    val rules = remember(setFile) { FileStorage.loadExamRules(context, setFile) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    // Which question is opened inside the selected attempt's review (null = the review list).
    var openQuestion by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedAttempt = selected?.let { attempts.getOrNull(it) }

    // Back steps out one level at a time: opened question -> attempt review -> history list.
    fun stepBack() {
        if (openQuestion != null) {
            openQuestion = null
        } else {
            selected = null
        }
    }
    BackHandler(enabled = selectedAttempt != null) { stepBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        set.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedAttempt != null) {
                            stepBack()
                        } else {
                            navController.safePopBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedAttempt != null) {
            ExamAttemptDetail(
                attempt = selectedAttempt,
                liveQuestions = set.questions,
                rules = rules,
                openIndex = openQuestion,
                onOpenIndex = { openQuestion = it },
                onDone = null,
                modifier = Modifier.padding(padding)
            )
        } else {
            ExamHistoryList(
                attempts = attempts,
                rules = rules,
                onOpen = { index ->
                    openQuestion = null
                    selected = index
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamHistoryList(
    attempts: List<ExamAttempt>,
    rules: ExamRules?,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attempts.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No exam attempts yet.\nFinish an exam and its review will be saved here.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    Column(modifier.padding(16.dp).fillMaxSize()) {
        Text(
            "Exam history",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rules != null) {
            Text(
                "Marking: ${markingSummary(rules)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(attempts.indices.toList()) { index ->
                val attempt = attempts[index]
                ElevatedCard(onClick = { onOpen(index) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            // Numbered oldest = 1, so the newest attempt (top of the list) has the highest number.
                            Text("Attempt ${attempts.size - index}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                dateFormat.format(Date(attempt.timestampMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${attempt.correct} correct · ${attempt.wrong} wrong · ${attempt.skipped} skipped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(scoreText(attempt, rules), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** "Time used 12:30 of 60:00", or empty for an attempt that didn't record its time. */
private fun timeSummary(attempt: ExamAttempt): String =
    if (attempt.durationMillis > 0L) {
        "Time used ${formatClock(attempt.timeTakenMillis / 1000)} of ${formatClock(attempt.durationMillis / 1000)}"
    } else {
        ""
    }

/**
 * The attempt's questions to display: the text snapshot taken when the exam was finished (so the
 * review stays correct even if the test was edited since). The snapshot has no images (question
 * or solution), so each is borrowed back from the live test when the question at that position
 * is unchanged.
 */
private fun resolveReviewQuestions(attempt: ExamAttempt, live: List<McqQuestion>): List<McqQuestion> {
    val base = if (attempt.questions.isNotEmpty()) attempt.questions else live
    return base.mapIndexed { index, q ->
        val liveMatch = live.getOrNull(index)
        if (liveMatch != null && liveMatch.question == q.question) {
            q.copy(
                imageBase64 = q.imageBase64 ?: liveMatch.imageBase64,
                solutionImageBase64 = q.solutionImageBase64 ?: liveMatch.solutionImageBase64
            )
        } else {
            q
        }
    }
}

/**
 * Score, stats and the per-question review list for one finished exam. Tapping a question opens
 * it in full (correct answer highlighted, solution and its image below). Used right after an
 * exam ends (with a Done button) and when reopening an old attempt from the Exam tab's Review
 * (no button -- the top bar's back arrow leaves). The opened question is hoisted to the caller
 * so its back arrow / back gesture can close the question first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamAttemptDetail(
    attempt: ExamAttempt,
    liveQuestions: List<McqQuestion>,
    rules: ExamRules?,
    openIndex: Int?,
    onOpenIndex: (Int?) -> Unit,
    onDone: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val questions = remember(attempt, liveQuestions) { resolveReviewQuestions(attempt, liveQuestions) }

    // Kept ABOVE the early return below so the list's scroll position survives opening a question
    // and coming back, and so the list can scroll to the question that was just being read.
    val listState = rememberLazyListState()
    var lastOpened by remember { mutableStateOf<Int?>(null) }

    if (openIndex != null) {
        SideEffect { lastOpened = openIndex }
        ReviewQuestionDetail(
            questions = questions,
            answers = attempt.answers,
            index = openIndex,
            onIndexChange = { onOpenIndex(it) },
            modifier = modifier
        )
        return
    }

    LaunchedEffect(Unit) {
        lastOpened?.let { listState.scrollToItem(it) }
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    val timeLine = timeSummary(attempt)

    Column(modifier.padding(16.dp).fillMaxSize()) {
        Text("Your score", style = MaterialTheme.typography.titleSmall)
        Text(scoreText(attempt, rules), style = MaterialTheme.typography.headlineMedium)
        if (rules != null) {
            Text(
                "Marking: ${markingSummary(rules)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            dateFormat.format(Date(attempt.timestampMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (timeLine.isNotEmpty()) {
            Text(
                timeLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreStat(label = "Correct", value = attempt.correct, color = AnswerColors.correctChip)
            ScoreStat(label = "Wrong", value = attempt.wrong, color = AnswerColors.incorrectChip)
            ScoreStat(label = "Skipped", value = attempt.skipped, color = AnswerColors.skippedChip)
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
                    status = statusOf(questions[index], attempt.answers.getOrNull(index)),
                    onClick = { onOpenIndex(index) }
                )
            }
        }

        if (onDone != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
