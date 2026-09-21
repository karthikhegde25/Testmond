package com.testmond.app.parser

/** Matches a solution's leading line, e.g. "1. text", "1) text", "Q1. text", "Q1: text". */
private val SOLUTION_LINE_START = Regex("^(?:[Qq])?(\\d+)[.):\\-]?\\s*(.*)$")

object SolutionParser {
    /**
     * Parses bulk-pasted solutions into a map from question NUMBER (1-based) to solution
     * text. A solution can span multiple lines -- everything up to the next numbered line
     * belongs to it, same convention as question parsing elsewhere in the app.
     */
    fun parseSolutions(text: String): Map<Int, String> {
        val lines = text.lines()
        val startIndices = lines.indices.filter { i ->
            val trimmed = lines[i].trim()
            trimmed.isNotEmpty() && SOLUTION_LINE_START.matches(trimmed)
        }
        if (startIndices.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, String>()
        for (i in startIndices.indices) {
            val start = startIndices[i]
            val end = if (i + 1 < startIndices.size) startIndices[i + 1] else lines.size
            val blockLines = lines.subList(start, end).map { it.trim() }.filter { it.isNotEmpty() }
            if (blockLines.isEmpty()) continue

            val match = SOLUTION_LINE_START.find(blockLines.first()) ?: continue
            val number = match.groupValues[1].toIntOrNull() ?: continue
            val firstContent = match.groupValues[2].trim()
            val fullText = (listOf(firstContent) + blockLines.drop(1))
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trim()

            if (fullText.isNotBlank()) {
                result[number] = fullText
            }
        }
        return result
    }
}
