package com.testmond.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.testmond.app.model.McqQuestion
import com.testmond.app.model.QuestionType

// ---------- plain-text previews for the review LIST ----------
//
// The review list used to render every row through MathAwareText, i.e. one WebView (loading KaTeX)
// per row that contains math. With dozens of questions that lagged the tab badly, and because
// each row started tiny and then grew as its WebView finished, rows kept changing height under
// the user's finger, so a scroll or fling jumped around. List rows now show a light plain-text
// preview with a fixed maximum height; the exact formula is rendered only in the opened question.

private val MATH_REGION = Regex("""\$\$[\s\S]+?\$\$|\$[^$]+\$|\\\([\s\S]+?\\\)|\\\[[\s\S]+?\\\]""")
private val FRACTION = Regex("""\\d?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
private val SQRT = Regex("""\\sqrt\s*\{([^{}]*)\}""")
private val WRAPPER_COMMAND =
    Regex("""\\(?:text|mathrm|mathbf|mathit|textbf|mathbb|mathcal|operatorname|boldsymbol|vec|hat|bar)\s*\{([^{}]*)\}""")
private val SUPERSCRIPT = Regex("""\^\{([^{}]*)\}|\^([^\s{\\])""")
private val SUBSCRIPT = Regex("""_\{([^{}]*)\}|_([^\s{\\])""")
private val BEGIN_END = Regex("""\\(?:begin|end)\s*\{[^{}]*\}""")
private val OTHER_COMMAND = Regex("""\\[a-zA-Z]+""")
private val SPACES = Regex("""\s+""")

// (command, symbol) -- matched as whole commands only, so \le never eats the start of \left.
private val SYMBOL_PAIRS = listOf(
    "\\cdot" to "\u00B7", "\\times" to "\u00D7", "\\div" to "\u00F7", "\\pm" to "\u00B1",
    "\\rightarrow" to "\u2192", "\\to" to "\u2192", "\\leftarrow" to "\u2190",
    "\\rightleftharpoons" to "\u21CC", "\\geq" to "\u2265", "\\ge" to "\u2265",
    "\\leq" to "\u2264", "\\le" to "\u2264", "\\neq" to "\u2260", "\\ne" to "\u2260",
    "\\approx" to "\u2248", "\\propto" to "\u221D", "\\infty" to "\u221E", "\\degree" to "\u00B0",
    "\\Delta" to "\u0394", "\\delta" to "\u03B4", "\\alpha" to "\u03B1", "\\beta" to "\u03B2",
    "\\gamma" to "\u03B3", "\\theta" to "\u03B8", "\\lambda" to "\u03BB", "\\mu" to "\u03BC",
    "\\pi" to "\u03C0", "\\rho" to "\u03C1", "\\sigma" to "\u03C3", "\\phi" to "\u03C6",
    "\\omega" to "\u03C9", "\\Omega" to "\u03A9", "\\epsilon" to "\u03B5", "\\varepsilon" to "\u03B5"
)
private val SYMBOLS: List<Pair<Regex, String>> =
    SYMBOL_PAIRS.map { (command, symbol) -> Regex(Regex.escape(command) + "(?![a-zA-Z])") to symbol }

private const val SUPER_CHARS = "0123456789+-=()n"
private const val SUPER_MAP = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079\u207A\u207B\u207C\u207D\u207E\u207F"
private const val SUB_CHARS = "0123456789+-=()"
private const val SUB_MAP = "\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089\u208A\u208B\u208C\u208D\u208E"

/** x^2 -> x², x_1 -> x₁; anything that has no compact form is kept readable as ^(...) / _(...). */
private fun scriptText(body: String, marker: Char, chars: String, map: String): String {
    val converted = StringBuilder()
    for (c in body) {
        val at = chars.indexOf(c)
        if (at < 0) return if (body.length == 1) "$marker$body" else "$marker($body)"
        converted.append(map[at])
    }
    return converted.toString()
}

/** One math region -> readable plain text: fractions as a/b, roots, common symbols and Greek
 *  letters, digits as super/subscripts; anything fancier just loses its markup. */
private fun mathToPlain(latex: String): String {
    var t = latex
    repeat(3) {   // nested groups, e.g. a fraction inside a fraction
        t = FRACTION.replace(t) { m ->
            val top = m.groupValues[1].trim()
            val bottom = m.groupValues[2].trim()
            val a = if (top.contains(' ')) "($top)" else top
            val b = if (bottom.contains(' ')) "($bottom)" else bottom
            "$a/$b"
        }
        t = SQRT.replace(t) { m -> "\u221A(" + m.groupValues[1].trim() + ")" }
        t = WRAPPER_COMMAND.replace(t) { m -> m.groupValues[1] }
    }
    for ((commandRegex, symbol) in SYMBOLS) t = commandRegex.replace(t, symbol)
    t = SUPERSCRIPT.replace(t) { m ->
        scriptText(m.groupValues[1].ifEmpty { m.groupValues[2] }, '^', SUPER_CHARS, SUPER_MAP)
    }
    t = SUBSCRIPT.replace(t) { m ->
        scriptText(m.groupValues[1].ifEmpty { m.groupValues[2] }, '_', SUB_CHARS, SUB_MAP)
    }
    t = BEGIN_END.replace(t, " ")
    // "\\\\" (a LaTeX line break) first, then the short spacing commands, then "&" (a table column).
    t = t.replace("\\\\", "; ").replace("\\,", " ").replace("\\;", " ").replace("\\!", "").replace("\\ ", " ").replace("&", " ")
    t = OTHER_COMMAND.replace(t, "")
    return t.replace("{", "").replace("}", "")
}

/** Plain, single-paragraph text for a list row: math regions converted by [mathToPlain], the rest
 *  left exactly as written, line breaks turned into spaces. */
internal fun previewText(text: String): String {
    // An escaped dollar sign (\$) is a literal $, not a math delimiter: park it while matching.
    val protectedText = text.replace("\\$", "\u0001")
    val withMathFlattened = MATH_REGION.replace(protectedText) { m ->
        val region = m.value
        val inner = when {
            region.startsWith("$$") -> region.removePrefix("$$").removeSuffix("$$")
            region.startsWith("$") -> region.removePrefix("$").removeSuffix("$")
            else -> region.substring(2, region.length - 2)   // \( ... \)  or  \[ ... \]
        }
        mathToPlain(inner)
    }
    return withMathFlattened.replace("\u0001", "$").replace(SPACES, " ").trim()
}

/** True when a question has anything to show in its solution section: text and/or an image. */
internal fun hasSolutionContent(q: McqQuestion): Boolean =
    !q.solution.isNullOrBlank() || q.solutionImageBase64 != null

/** The "Solution" block: divider, heading, the solution text (LaTeX-aware) and, if attached,
 *  its image below. Used while taking a test (after submitting) and on the review detail. */
@Composable
internal fun SolutionContent(q: McqQuestion) {
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text("Solution", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    val text = q.solution
    if (!text.isNullOrBlank()) {
        MathAwareText(text, style = MaterialTheme.typography.bodyMedium)
    }
    val image = q.solutionImageBase64
    if (image != null) {
        Spacer(Modifier.height(8.dp))
        QuestionImage(image)
    }
}

@Composable
private fun ReviewStatusBadge(status: QuestionStatus) {
    val (badgeText, badgeColor) = when (status) {
        QuestionStatus.CORRECT -> "Correct" to AnswerColors.correctChip
        QuestionStatus.WRONG -> "Wrong" to AnswerColors.incorrectChip
        QuestionStatus.SKIPPED -> "Skipped" to AnswerColors.skippedChip
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(badgeText, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * One row in the review list: the question number and a two-line plain-text preview plus its
 * Correct / Wrong / Skipped badge. Tapping it opens the full question (see
 * [ReviewQuestionDetail]), where the formulas are rendered properly.
 */
@Composable
internal fun ReviewQuestionRow(
    index: Int,
    question: McqQuestion,
    status: QuestionStatus,
    onClick: () -> Unit
) {
    // Converting the LaTeX to plain text is a bit of regex work: do it once per question, not on
    // every scroll-driven recomposition of the row.
    val preview = remember(question.question) { previewText(question.question) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Box {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Q${index + 1}: $preview",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ReviewStatusBadge(status)
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick)
            )
        }
    }
}

/**
 * A single question opened from the review: the question and image, every option with the
 * correct one and the user's own pick highlighted, and the solution (text and image) below it
 * when one is attached. Previous / next arrows step through the other questions.
 */
@Composable
internal fun ReviewQuestionDetail(
    questions: List<McqQuestion>,
    answers: List<String?>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** How long each question took (ms), when known -- shown next to "Question X of Y". */
    timesMillis: List<Long?> = emptyList()
) {
    val q = questions.getOrNull(index) ?: return
    val answer = answers.getOrNull(index)
    val status = statusOf(q, answer)
    val tookMillis = timesMillis.getOrNull(index)

    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Question ${index + 1} of ${questions.size}" +
                    if (tookMillis != null) "  \u00B7  Time taken ${formatClock(tookMillis / 1000)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ReviewStatusBadge(status)
        }

        Spacer(Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // key(index) gives every question a fresh scroll position and fresh math views.
            key(index) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    MathAwareText(q.question, style = MaterialTheme.typography.titleMedium)
                    if (q.imageBase64 != null) {
                        Spacer(Modifier.height(12.dp))
                        QuestionImage(q.imageBase64)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (q.type == QuestionType.MCQ) {
                        q.options.forEach { opt ->
                            val isCorrectOption = opt.letter == q.answer
                            val isUserPick = answer != null && opt.letter == answer
                            val bg = when {
                                isCorrectOption -> AnswerColors.correctBg
                                isUserPick -> AnswerColors.incorrectBg
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val borderColor =
                                if (isUserPick) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(bg)
                                    .border(1.dp, borderColor, RoundedCornerShape(50))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text("${opt.letter}) ")
                                    MathAwareText(opt.text, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (status) {
                                QuestionStatus.CORRECT -> "Your answer was correct."
                                QuestionStatus.WRONG -> "Your answer: $answer  ·  Correct: ${q.answer}"
                                QuestionStatus.SKIPPED -> "You skipped this question  ·  Correct: ${q.answer}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val boxBg = when (status) {
                            QuestionStatus.CORRECT -> AnswerColors.correctBg
                            QuestionStatus.WRONG -> AnswerColors.incorrectBg
                            QuestionStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(boxBg, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                if (answer != null) "Your answer: $answer" else "You skipped this question",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (status != QuestionStatus.CORRECT) {
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Text("Correct answer: ", style = MaterialTheme.typography.bodyMedium)
                                MathAwareText(q.answer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (hasSolutionContent(q)) {
                        Spacer(Modifier.height(16.dp))
                        SolutionContent(q)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onIndexChange(index - 1) }, enabled = index > 0) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous question")
            }
            IconButton(onClick = { onIndexChange(index + 1) }, enabled = index < questions.size - 1) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next question")
            }
        }
    }
}
