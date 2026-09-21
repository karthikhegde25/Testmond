package com.testmond.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseScreen(navController: NavHostController) {
    val context = LocalContext.current
    var downloadMsg by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.assets.open("format_guide.md").bufferedReader().readText()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                downloadMsg = "Saved."
            } catch (e: Exception) {
                downloadMsg = "Couldn't save the file: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to use") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            OutlinedButton(
                onClick = { saveLauncher.launch("Testmond format guide.md") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download question format guide (.md)")
            }
            BodyText(
                "A reference file with every paste format and example below, written so " +
                "you can also hand it to an AI assistant with a request like \"generate 20 " +
                "questions about photosynthesis using this format\" -- it can follow the " +
                "rules directly and produce text ready to paste into this app."
            )
            downloadMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }

            SectionTitle("Creating a test")
            BodyText(
                "On the home screen's Tests tab, tap the + button and choose \"Create test\". " +
                "Pick a paste format, give the set a title, paste your questions in, and tap " +
                "\"Preview\" then \"Create test\". Multiple choice and fill-in-the-blank " +
                "questions can be freely mixed in the same paste -- there's no separate mode " +
                "to choose between them."
            )

            SectionTitle("Marking a fill-in-the-blank question")
            BodyText(
                "Anything without the \"*\" marker is treated as multiple choice. To mark a " +
                "question as fill-in-the-blank instead, put a lone \"*\" on its own line " +
                "right before it (combined format), or right after the number with no options " +
                "following (separate-key format)."
            )

            SectionTitle("1. Combined format")
            BodyText(
                "Each question, its options (for multiple choice), and ANSWER: all together. " +
                "Separate each question with a blank line. Number the questions (Q1., Q2., ...) " +
                "-- a bare \"Q:\" also works, but numbering is safer. Options can be written " +
                "A) or A. -- either is accepted -- one option per line, and ANSWER: is just the " +
                "letter."
            )
            CodeBlock(
                """
                Q1. What is the powerhouse of the cell?
                A) Nucleus
                B) Mitochondria
                C) Ribosome
                D) Golgi apparatus
                ANSWER: B

                *
                Q2. The powerhouse of the cell is ____.
                ANSWER: Mitochondria
                """.trimIndent()
            )

            SectionTitle("2. Separate answer key")
            BodyText(
                "Use this when your source has questions and answers in different places, like " +
                "a textbook's end-of-chapter key. Paste the numbered questions in one box and " +
                "the answer key in a second box -- they're matched automatically by number. A " +
                "\"*\" right after the number marks that one as fill-in-the-blank."
            )
            BodyText("Questions box:")
            CodeBlock(
                """
                1. What is the powerhouse of the cell?
                A) Nucleus
                B) Mitochondria
                C) Ribosome
                D) Golgi apparatus

                2. *The powerhouse of the cell is ____.
                """.trimIndent()
            )
            BodyText(
                "Answer key box -- one line per question, holding a letter for multiple choice " +
                "or free text for fill-in-the-blank, in any mix (also accepts \"1) B\", \"1-B\", " +
                "\"1: B\", or a plain list like \"B, C\"):"
            )
            CodeBlock(
                """
                1. B
                2. Mitochondria
                """.trimIndent()
            )
            BodyText(
                "Fill-in-the-blank grading ignores capitalization, extra spaces and \$ signs, so " +
                "an answer key of \$100\$ accepts a typed 100. Keep answers to what can be typed " +
                "on a keyboard; with several blanks in one question, the answers are typed in " +
                "order, separated by commas."
            )

            SectionTitle("Multi-line questions and a paste pitfall to avoid")
            BodyText(
                "A question can span multiple lines (even with a blank line inside it, for a " +
                "paragraph break) before its first option or ANSWER: line -- everything up to " +
                "that point is joined together as the question text, exactly as you typed it."
            )
            BodyText(
                "For multiple choice, the first option must always be lettered A (A) or A.) -- " +
                "and avoid using Roman numerals (I., II., III.) as option letters, since \"I.\" " +
                "looks identical to a single-letter option to the parser. Roman numerals are " +
                "fine inside a question's own text (e.g. a numbered list of statements the " +
                "question asks about), as long as they appear before the real lettered options " +
                "start."
            )
            BodyText(
                "Keep every option on ONE line: once the options have started, any line that " +
                "isn't the next option or the ANSWER: line is ignored, so an option wrapped " +
                "onto a second line loses the rest. ANSWER: must be just the letter (ANSWER: C, " +
                "not ANSWER: (C) or C.); a missing or unknown answer such as ANSWER: ? makes " +
                "the app skip that one question and list it in the Preview. A stray heading " +
                "line, \"---\" divider or <!-- comment --> line pasted along with the questions " +
                "is ignored."
            )

            SectionTitle("Match-the-following questions")
            BodyText(
                "These naturally have their own \"A) / B) / C) / D)\" column labels, which look " +
                "identical to real options to the parser and will misparse the question. Fix: " +
                "rewrite the internal column labels as Roman numerals instead. The same goes " +
                "for ANY labelled list inside a question -- (a), (b), (c) statements or an " +
                "\"(A) Assertion / (R) Reason\" pair: use Roman numerals (I, II, III, IV or " +
                "i, ii, iii, iv), or write \"Assertion (A): ...\" and \"Reason (R): ...\" with " +
                "the word first. Only the real answer options are lettered A, B, C, D."
            )
            BodyText("This fails:")
            CodeBlock(
                """
                Q3. Match Column I with Column II and mark the appropriate choice.
                Column I
                Column II
                A) Tertiary alcohol
                i) Butan-2-ol
                B) Allylic alcohol
                ii) 2-Methylpropan-2-ol
                C) Secondary alcohol
                iii) Propan-1-ol
                D) Primary alcohol
                iv) Prop-2-en-1-ol
                A. A-ii, B-iv, C-i, D-iii
                B. A-ii, B-i, C-iv, D-iii
                C. A-i, B-ii, C-iii, D-iv
                D. A-i, B-iv, C-iii, D-ii
                ANSWER: C
                """.trimIndent()
            )
            BodyText("This works:")
            CodeBlock(
                """
                Q3. Match Column I with Column II and mark the appropriate choice.
                Column I
                Column II
                I) Tertiary alcohol
                i) Butan-2-ol
                II) Allylic alcohol
                ii) 2-Methylpropan-2-ol
                III) Secondary alcohol
                iii) Propan-1-ol
                IV) Primary alcohol
                iv) Prop-2-en-1-ol
                A) I-ii, II-iv, III-i, IV-iii
                B) I-ii, II-i, III-iv, IV-iii
                C) I-i, II-ii, III-iii, IV-iv
                D) I-i, II-iv, III-iii, IV-ii
                ANSWER: C
                """.trimIndent()
            )

            SectionTitle("Using LaTeX")
            BodyText(
                "Question text, options, and answers can include LaTeX using \$...\$, \$\$...\$\$, " +
                "\\(...\\), or \\[...\\] delimiters -- paste it exactly as it appears in your source, " +
                "no extra formatting needed. Rendering is fully offline."
            )
            BodyText(
                "A formula can span several lines (a matrix or aligned block): wrap the whole " +
                "thing in \$\$ ... \$\$ and keep blank lines out of it. Write a literal dollar " +
                "sign as \\\$."
            )
            BodyText(
                "Tables: write a Markdown pipe table -- a header line, a |---|---| line, then " +
                "one line per row, with no blank line inside -- and it is drawn to fit the " +
                "screen: columns share the width, long text wraps in its cell, and a table " +
                "that is still too wide is shrunk (very wide ones use smaller text). About " +
                "4-5 columns with short cells work best. Cells can contain \$...\$ math."
            )
            CodeBlock(
                """
                | Trial | Rate |
                |---|---:|
                | 1 | 2.0 |
                | 2 | 4.0 |
                """.trimIndent()
            )

            SectionTitle("Editing a test")
            BodyText(
                "Open a test's three-dot menu and choose Edit. Question numbers run down the " +
                "left side -- tap one to load it into the editor on the right, where the " +
                "question, its options (with a checkmark to mark the correct one), and the " +
                "answer are all separately editable."
            )
            BodyText(
                "An \"Add image\" button attaches a photo directly to a question -- it's " +
                "embedded in the test file and shows up automatically while taking the test " +
                "and on the review page. The + button at the bottom of the number column now " +
                "offers three options: add one multiple-choice question, add one " +
                "fill-in-the-blank question, or \"Many questions\" -- which opens the same " +
                "paste box used when creating a test. Tap Preview to see how many questions " +
                "were detected (and which were skipped, and why), then \"Add\" appends them " +
                "after the existing questions (their final numbers just continue on from wherever " +
                "the test left off, regardless of any numbers in the pasted text itself). " +
                "Long-press and drag a question number to reorder it; its label deliberately " +
                "doesn't renumber until you save and reopen Edit. A trash icon next to " +
                "\"Question X of Y\" deletes the current question (confirmation first) -- if " +
                "the test has saved in-progress answers, Save carries them over correctly to " +
                "the edited list."
            )

            SectionTitle("Solutions")
            BodyText(
                "A question can carry a solution that is shown once you submit it and on the " +
                "review page. In a test's three-dot menu, \"Add solution\" takes one paste box " +
                "with a solution per question, identified by number (1. or Q1.). Once a test " +
                "has solutions the item becomes \"Edit solution\": pick a question on the " +
                "left, edit its text, and use \"Attach image\" to add a picture to that " +
                "solution (the \u00d7 removes it). Save writes everything back. Solutions " +
                "support LaTeX like everything else."
            )

            SectionTitle("Tests and folders")
            BodyText(
                "The home screen has three tabs -- swipe or tap to switch. Tests lists every " +
                "test you have; Folders lets you group tests together (a folder just " +
                "references existing tests, so deleting a folder never deletes what's inside " +
                "it); Exam Mode is for timed exams (see below). Tests and folders have a " +
                "three-dot menu for Share, Rename, and Delete (tests also get Edit and " +
                "Attempt history)."
            )
            BodyText(
                "Inside a folder, long-press a test to enter selection mode and multi-select " +
                "tests to remove from that folder (with confirmation) -- an \"add tests\" " +
                "icon in the folder's top bar brings more in."
            )

            SectionTitle("Sharing and importing")
            BodyText(
                "Share sends a test as a single self-contained file (including any attached " +
                "images and LaTeX). Sharing a folder bundles the folder's name together with " +
                "the full content of every test inside it into one portable file -- it's " +
                "never sent as a directory. Use \"Import\" (from the + button) to bring a " +
                "shared file in, or just tap it from another app; the type is detected " +
                "automatically, and importing a folder also adds its tests to the Tests tab."
            )
            BodyText(
                "Nothing is saved until you confirm: after you pick or open a file, a summary " +
                "shows how many questions the test has -- or, for a folder, how many tests it " +
                "contains and how many questions each one has -- and an \"Import\" button " +
                "finishes the job (Cancel discards it)."
            )

            SectionTitle("Search")
            BodyText(
                "The search icon in the top bar looks across both tests and folders at once, " +
                "showing results grouped and icon-tagged so you can tell them apart."
            )

            SectionTitle("Taking a test")
            BodyText(
                "Question numbers scroll in a row at the top -- tap any to jump there. " +
                "Selecting an option or typing a fill-in-the-blank answer only stages a draft " +
                "pick, which you can freely change; a Submit button between the prev/next " +
                "arrows locks it in. A reset icon in the top bar clears every answer and " +
                "restarts from question 1 (confirmation first); Finish also asks for " +
                "confirmation, warning if anything is still unanswered."
            )
            BodyText(
                "Leaving mid-test (back button or gesture) asks for confirmation and saves " +
                "your progress -- reopening the same test picks up exactly where you left off, " +
                "including an unsubmitted draft pick."
            )

            BodyText(
                "In a normal test, a small stopwatch on the right of the \"Question X of Y\" " +
                "line shows how long you have spent on the question. It starts when the " +
                "question opens, stops when you press Submit, and that time is kept -- the " +
                "review shows it as \"Time taken\" when you open the question. If you leave an " +
                "unsubmitted question and come back, it starts again from zero."
            )
            BodyText(
                "Images -- on a question or in a solution -- have a small magnifier badge on " +
                "their bottom-right corner. Tap it (or the image) to open the picture full " +
                "screen like a browser's image viewer: pinch to zoom, drag to move it around " +
                "when zoomed, double-tap to zoom in or out, and tap the X in the top-right " +
                "corner to close."
            )

            SectionTitle("Quiz mode vs. Practice mode")
            BodyText(
                "In Settings, choose whether answers are revealed instantly as you submit " +
                "them (Quiz mode) or only shown on the review page after Finish (Practice " +
                "mode). This applies to every test in the app. Either way, the review page " +
                "always shows full correct/wrong/skipped detail for every question, plus your " +
                "score. Tap any question in the review to open it in full -- your answer and " +
                "the correct one highlighted, with its solution (and solution image) below " +
                "when one is attached; the arrows at the bottom step through the others."
            )

            SectionTitle("Exam Mode")
            BodyText(
                "The third home tab is for timed exams. Tap the + button to add tests you " +
                "already have (they stay in the Tests tab too). Tap a test in Exam Mode, set " +
                "the time in minutes, then confirm Start -- the test appears as usual, but the " +
                "countdown timer replaces the test's name in the top bar, and when it reaches " +
                "zero the exam is submitted automatically. There is no Submit button in an " +
                "exam: tap an option (or type an answer) and it is saved immediately, and you " +
                "can change it any time before you finish -- tap the picked option again to " +
                "clear it."
            )
            BodyText(
                "During an exam nothing ever reveals which answer is correct (no colours, no " +
                "solutions), and there is no Reset. Finish asks for confirmation, and leaving " +
                "with back asks whether you're sure -- the progress is lost, since an exam " +
                "can't be resumed. Once it ends, you see your score and a full review with " +
                "the correct answers."
            )
            BodyText(
                "Every finished exam is saved. A test's three-dot menu in Exam Mode has " +
                "\"Review\", listing all of its past exams -- tap one to reopen which " +
                "questions were right and wrong, any time, and tap a question to open it in " +
                "full with its solution. \"Remove\" (with confirmation) " +
                "takes the test out of the Exam Mode tab without deleting the test or its " +
                "exam history."
            )
            BodyText(
                "\"Custom rules\" in the same menu sets the marks for each correct, wrong and " +
                "skipped question -- for example 4, -1 and 0. The review then shows your score " +
                "in marks (like 47 / 80) using those rules, for every attempt of that test. " +
                "Leave the boxes blank (or 0) and save to go back to the normal " +
                "correct-out-of-total score."
            )

            SectionTitle("Attempt history and appearance")
            BodyText(
                "A test's three-dot menu has \"Attempt history\", showing the date and score " +
                "of every completed attempt. In Settings, choose System default, Light, or " +
                "Dark for the app's appearance."
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
    Spacer(Modifier.height(8.dp))
}
