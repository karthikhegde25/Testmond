package com.testmond.app.parser

import com.testmond.app.model.McqOption
import com.testmond.app.model.McqQuestion
import com.testmond.app.model.ParseIssue
import com.testmond.app.model.ParseResult
import com.testmond.app.model.QuestionType

// A label at the start of a question's first line: "Q1.", "Q:", "Q 3)" or a bare "1." / "12)".
// It must be a real label, not just anything that starts with Q or a digit -- otherwise a stem
// like "Quantum numbers of ..." lost its leading "Q", and "100 mL of ..." / "Q: 2 moles of ..."
// lost the quantity at the front (it was mistaken for the question number). A bare number needs
// a delimiter that is not the start of a decimal ("1.5 g of ..." is not a label).
private val QUESTION_LABEL = Regex("^(?:[Qq]\\s*(\\d+)?\\s*(?:[:)]|\\.(?!\\d))\\s*|(\\d+)[.):\\-](?!\\d)\\s*)")
private val OPTION_LINE = Regex("^\\(?([A-Za-z])[).]\\s*(.+)$")
private val ANSWER_LINE = Regex("^ANSWER[:.]?\\s*(.+)$", RegexOption.IGNORE_CASE)
// A line that starts a new numbered question, e.g. "1.", "12)", "3 -" -- used as a
// hard question-boundary so paste blocks without a blank line between them still split.
private val QUESTION_NUMBER_START = Regex("^\\d+[.)\\-:](?!\\d)\\s*\\S")
// Matches "1. B", "1) B", "1 - B", "1: B" style answer-key entries. Value stays free-text
// (not letter-specific) since a mixed paste's key can hold both MCQ letters and fill-blank text.
private val ANSWER_KEY_ENTRY = Regex("^(\\d+)[.)\\-:]?\\s*(.+)$")

// Lines that are formatting rather than content and can show up when a whole Markdown file (for
// example one produced by an AI assistant) is pasted: <!-- comments -->, "---" / "***" rules and
// a "# Heading" standing alone as its own paragraph. Left in, a heading glued itself onto the
// first question's text (and hid a fill-in-the-blank "*" marker behind it).
private val HTML_COMMENT_LINE = Regex("^\\s*<!--.*-->\\s*$")
private val HORIZONTAL_RULE_LINE = Regex("^\\s*([-*])\\1{2,}\\s*$")
private val HEADING_LINE = Regex("^\\s{0,3}#{1,6}\\s+\\S.*$")

private fun stripPasteNoise(text: String): String {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val kept = ArrayList<String>(lines.size)
    for ((i, line) in lines.withIndex()) {
        if (HTML_COMMENT_LINE.matches(line) || HORIZONTAL_RULE_LINE.matches(line)) continue
        if (HEADING_LINE.matches(line)) {
            val prevBlank = i == 0 || lines[i - 1].isBlank()
            val nextBlank = i == lines.size - 1 || lines[i + 1].isBlank()
            if (prevBlank && nextBlank) continue
        }
        kept.add(line)
    }
    return kept.joinToString("\n")
}

/** Splits a leading question label off [line]: (number if the label had one, remaining text). */
private fun splitQuestionLabel(line: String): Pair<Int?, String> {
    val m = QUESTION_LABEL.find(line) ?: return Pair(null, line)
    val number = (m.groups[1]?.value ?: m.groups[2]?.value)?.toIntOrNull()
    return Pair(number, line.substring(m.range.last + 1).trim())
}

object McqParser {

    /**
     * COMBINED format: one paste box holding both question types together. A block is a
     * fill-in-the-blank question if its first non-blank line is a lone "*" marker (the
     * marker line is then discarded and the actual question starts on the next line);
     * otherwise it's parsed as MCQ, exactly as before. Split on each ANSWER: line as the
     * hard end-of-question boundary -- robust to missing/extra blank lines between pasted
     * questions, and works identically for either type since both end with ANSWER:.
     */
    fun parseCombined(text: String): ParseResult {
        val blocks = splitCombinedBlocks(stripPasteNoise(text))
        val questions = mutableListOf<McqQuestion>()
        val issues = mutableListOf<ParseIssue>()

        blocks.forEachIndexed { index, block ->
            val label = "block ${index + 1}"
            val rawLines = block.lines()
            val firstNonBlankIdx = rawLines.indexOfFirst { it.trim().isNotEmpty() }
            val isFillBlank = firstNonBlankIdx >= 0 && rawLines[firstNonBlankIdx].trim() == "*"
            val contentLines = if (isFillBlank) rawLines.drop(firstNonBlankIdx + 1) else rawLines

            if (isFillBlank) {
                val questionLines = mutableListOf<String>()
                var answerRaw: String? = null
                for (rawLine in contentLines) {
                    val line = rawLine.trim()
                    if (line.isEmpty()) {
                        if (answerRaw == null && questionLines.isNotEmpty()) questionLines.add("")
                        continue
                    }
                    val ansMatch = ANSWER_LINE.find(line)
                    if (ansMatch != null) {
                        answerRaw = ansMatch.groupValues[1].trim()
                    } else if (answerRaw == null) {
                        questionLines.add(if (questionLines.isEmpty()) extractQuestionText(line) else line)
                    }
                }
                val questionText = questionLines.joinToString("\n").trim()
                if (questionText.isBlank()) {
                    issues.add(ParseIssue(label, "No question text found"))
                } else if (answerRaw.isNullOrBlank()) {
                    issues.add(ParseIssue(label, "No ANSWER: line found"))
                } else {
                    questions.add(McqQuestion(QuestionType.FILL_BLANK, questionText, emptyList(), answerRaw))
                }
            } else {
                val questionLines = mutableListOf<String>()
                val options = mutableListOf<McqOption>()
                var answerRaw: String? = null
                var pastQuestion = false

                for (rawLine in contentLines) {
                    val line = rawLine.trim()
                    if (line.isEmpty()) {
                        if (!pastQuestion && questionLines.isNotEmpty()) questionLines.add("")
                        continue
                    }
                    val ansMatch = ANSWER_LINE.find(line)
                    if (ansMatch != null) {
                        answerRaw = ansMatch.groupValues[1].trim()
                        pastQuestion = true
                        continue
                    }
                    val optMatch = OPTION_LINE.find(line)
                    val letter = optMatch?.groupValues?.get(1)?.uppercase()
                    // Only accept a line as a real option if it's "A" (the first option ever
                    // seen) or continues right after an already-accepted option -- this is
                    // what stops a Roman-numeral sub-statement like "I. Current flows..."
                    // from being mistaken for a single-letter option "I)".
                    val isAcceptableOption = optMatch != null && (options.isNotEmpty() || letter == "A")
                    if (isAcceptableOption) {
                        options.add(McqOption(letter!!, optMatch!!.groupValues[2].trim()))
                        pastQuestion = true
                    } else if (!pastQuestion) {
                        questionLines.add(if (questionLines.isEmpty()) extractQuestionText(line) else line)
                    }
                }

                val questionText = questionLines.joinToString("\n").trim()
                val answerLetter = answerRaw?.uppercase()?.trim()
                if (questionText.isBlank()) {
                    issues.add(ParseIssue(label, "No question text found"))
                } else if (options.size < 2) {
                    issues.add(ParseIssue(label, "Fewer than 2 options found"))
                } else if (answerLetter.isNullOrBlank()) {
                    issues.add(ParseIssue(label, "No ANSWER: line found"))
                } else if (options.none { it.letter == answerLetter }) {
                    issues.add(ParseIssue(label, "ANSWER: $answerLetter does not match any option"))
                } else {
                    questions.add(McqQuestion(QuestionType.MCQ, questionText, options, answerLetter))
                }
            }
        }
        return ParseResult(questions, issues)
    }

    /**
     * SEPARATE_KEY format: numbered questions (mixing both types) in one box, a numbered
     * answer key in another, matched by number (falling back to matching by position if
     * numbers aren't detected in the key). A question is fill-in-the-blank if its text
     * (right after the leading number) starts with "*" -- e.g. "1. *The capital is ____.".
     * That block then expects no options, taking every remaining line as question text;
     * otherwise it's parsed as MCQ exactly as before. The shared answer key box can hold
     * a mix of MCQ letters and free-text fill-blank answers -- each is interpreted
     * according to its own question's detected type.
     */
    fun parseSeparate(questionsText: String, answerKeyText: String): ParseResult {
        val blocks = splitNumberedBlocks(stripPasteNoise(questionsText))
        val issues = mutableListOf<ParseIssue>()
        val parsedQuestions = mutableListOf<Triple<Int?, String, List<McqOption>>>()
        val typeByQuestion = mutableListOf<QuestionType>()

        blocks.forEachIndexed { index, block ->
            val questionLines = mutableListOf<String>()
            var number: Int? = null
            val options = mutableListOf<McqOption>()
            var firstLineSeen = false
            var isFillBlank = false

            for (rawLine in block.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    if (options.isEmpty() && questionLines.isNotEmpty()) questionLines.add("")
                    continue
                }
                if (!firstLineSeen) {
                    firstLineSeen = true
                    val (labelNumber, labelRest) = splitQuestionLabel(line)
                    number = labelNumber
                    var firstContent = labelRest
                    if (firstContent.startsWith("*")) {
                        isFillBlank = true
                        firstContent = firstContent.removePrefix("*").trim()
                    }
                    questionLines.add(firstContent)
                    continue
                }
                if (isFillBlank) {
                    // No options in a fill-blank block -- every remaining line is question text.
                    questionLines.add(line)
                    continue
                }
                val optMatch = OPTION_LINE.find(line)
                val letter = optMatch?.groupValues?.get(1)?.uppercase()
                val isAcceptableOption = optMatch != null && (options.isNotEmpty() || letter == "A")
                if (isAcceptableOption) {
                    options.add(McqOption(letter!!, optMatch!!.groupValues[2].trim()))
                } else if (options.isEmpty()) {
                    questionLines.add(line)
                }
            }

            val questionText = questionLines.joinToString("\n").trim()
            val label = "question block ${index + 1}"
            if (questionText.isBlank() || (!isFillBlank && options.size < 2)) {
                issues.add(ParseIssue(label, "Could not parse question" + if (!isFillBlank) " or options" else ""))
            } else {
                parsedQuestions.add(Triple(number, questionText, options))
                typeByQuestion.add(if (isFillBlank) QuestionType.FILL_BLANK else QuestionType.MCQ)
            }
        }

        // Answer key: free-text values, interpreted per-question below according to its type.
        val keyLines = answerKeyText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val numberedAnswers = mutableMapOf<Int, String>()
        for (line in keyLines) {
            val m = ANSWER_KEY_ENTRY.find(line)
            if (m != null) numberedAnswers[m.groupValues[1].toInt()] = m.groupValues[2].trim()
        }
        val useNumberedKey = numberedAnswers.isNotEmpty()
        val positionalAnswers: List<String> = if (!useNumberedKey) {
            answerKeyText.split(Regex("[,\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val finalQuestions = mutableListOf<McqQuestion>()
        parsedQuestions.forEachIndexed { idx, (number, qText, options) ->
            val type = typeByQuestion[idx]
            val label = number?.let { "Q$it" } ?: "question ${idx + 1}"
            val rawAnswer: String? = if (useNumberedKey) {
                number?.let { numberedAnswers[it] } ?: run {
                    issues.add(ParseIssue(label, "No question number detected to match against the answer key"))
                    null
                }
            } else {
                positionalAnswers.getOrNull(idx)
            }

            if (rawAnswer == null) {
                if (!useNumberedKey && idx >= positionalAnswers.size) {
                    issues.add(ParseIssue(label, "No matching answer found in answer key"))
                }
                return@forEachIndexed
            }

            if (type == QuestionType.MCQ) {
                val answer = rawAnswer.uppercase()
                if (options.none { it.letter == answer }) {
                    issues.add(ParseIssue(label, "Answer key value '$answer' does not match any option"))
                } else {
                    finalQuestions.add(McqQuestion(QuestionType.MCQ, qText, options, answer))
                }
            } else {
                finalQuestions.add(McqQuestion(QuestionType.FILL_BLANK, qText, emptyList(), rawAnswer))
            }
        }

        return ParseResult(finalQuestions, issues)
    }

    // ---------- shared helpers ----------

    /**
     * Splits COMBINED-format text into one block per question, using each
     * ANSWER: line as the hard end-of-question boundary. Robust to missing/extra
     * blank lines between pasted questions. Falls back to blank-line splitting
     * only if no ANSWER: line is found anywhere.
     */
    private fun splitCombinedBlocks(text: String): List<String> {
        val lines = text.lines()
        val answerIndices = lines.indices.filter { ANSWER_LINE.matches(lines[it].trim()) }
        if (answerIndices.isEmpty()) {
            return splitByBlankLines(text)
        }
        val blocks = mutableListOf<String>()
        var start = 0
        for (endIdx in answerIndices) {
            val block = lines.subList(start, endIdx + 1).joinToString("\n").trim()
            if (block.isNotBlank()) blocks.add(block)
            start = endIdx + 1
        }
        return blocks
    }

    /**
     * Splits SEPARATE_KEY-format question text into one block per question, using each
     * numbered line ("1.", "2)", etc.) as the hard start-of-question boundary. Falls back
     * to blank-line splitting if no numbered lines are found (unnumbered paste).
     */
    private fun splitNumberedBlocks(text: String): List<String> {
        val lines = text.lines()
        val startIndices = lines.indices.filter { QUESTION_NUMBER_START.containsMatchIn(lines[it].trim()) }
        if (startIndices.size < 2) {
            return splitByBlankLines(text)
        }
        val blocks = mutableListOf<String>()
        for (i in startIndices.indices) {
            val start = startIndices[i]
            val end = if (i + 1 < startIndices.size) startIndices[i + 1] else lines.size
            val block = lines.subList(start, end).joinToString("\n").trim()
            if (block.isNotBlank()) blocks.add(block)
        }
        return blocks
    }

    private fun splitByBlankLines(text: String): List<String> =
        text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractQuestionText(line: String): String = splitQuestionLabel(line).second
}
