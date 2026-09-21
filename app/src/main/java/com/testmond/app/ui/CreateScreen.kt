package com.testmond.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safeNavigate
import com.testmond.app.safePopBackStack
import com.testmond.app.data.FileStorage
import com.testmond.app.model.McqSet
import com.testmond.app.model.ParseResult
import com.testmond.app.model.PasteFormat
import com.testmond.app.parser.McqParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(navController: NavHostController) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(PasteFormat.COMBINED) }
    var combinedText by remember { mutableStateOf("") }
    var questionsText by remember { mutableStateOf("") }
    var answerKeyText by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf<String?>(null) }
    var previewResult by remember { mutableStateOf<ParseResult?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var savedSet by remember { mutableStateOf<McqSet?>(null) }
    var savedFile by remember { mutableStateOf<java.io.File?>(null) }

    // Any change to the inputs invalidates a previous preview/save -- re-parsing is
    // required before creating, so a stale preview can never be saved after an edit.
    fun invalidate() {
        previewResult = null
        previewError = null
        savedSet = null
        savedFile = null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            savedSet?.let { FileStorage.exportToUri(context, it, uri) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New question set") },
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = null },
                label = { Text("Set title") },
                isError = titleError != null,
                supportingText = { titleError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Paste format", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegment(
                options = listOf("Combined (question + answer together)", "Separate answer key"),
                selectedIndex = if (format == PasteFormat.COMBINED) 0 else 1,
                onSelected = { format = if (it == 0) PasteFormat.COMBINED else PasteFormat.SEPARATE_KEY; invalidate() }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Multiple choice and fill-in-the-blank questions can be mixed freely in the " +
                "same paste. Mark a fill-in-the-blank question with a lone \"*\" on its own " +
                "line right before it; anything else is treated as multiple choice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            PasteFields(
                format = format,
                combinedText = combinedText,
                onCombinedChange = { combinedText = it; invalidate() },
                questionsText = questionsText,
                onQuestionsChange = { questionsText = it; invalidate() },
                answerKeyText = answerKeyText,
                onAnswerKeyChange = { answerKeyText = it; invalidate() }
            )

            previewError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))

            val preview = previewResult
            if (preview == null) {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = "Enter a set title"
                            return@Button
                        }
                        val result = if (format == PasteFormat.COMBINED) {
                            McqParser.parseCombined(combinedText)
                        } else {
                            McqParser.parseSeparate(questionsText, answerKeyText)
                        }
                        if (result.questions.isEmpty()) {
                            previewError = "Couldn't parse any questions. Check the format and try again."
                            return@Button
                        }
                        previewError = null
                        previewResult = result
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Preview")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${preview.questions.size} question${if (preview.questions.size != 1) "s" else ""} detected",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (preview.issues.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${preview.issues.size} skipped -- check formatting:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            preview.issues.forEach { issue ->
                                Text(
                                    "\u2022 ${issue.reference}: ${issue.message}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val set = McqSet(title = title.trim(), questions = preview.questions)
                        val file = FileStorage.saveToLibrary(context, set)
                        savedSet = set
                        savedFile = file
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create test")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { previewResult = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit before creating")
                }
            }

            if (savedSet != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Saved to your library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { exportLauncher.launch("${title.ifBlank { "questions" }}.mcqz") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Also export as file (to share manually)")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        com.testmond.app.ActiveSetHolder.current.value = savedSet
                        com.testmond.app.ActiveSetHolder.currentFile.value = savedFile
                        navController.safeNavigate("quiz") { popUpTo("home") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start now")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun PasteFields(
    format: PasteFormat,
    combinedText: String,
    onCombinedChange: (String) -> Unit,
    questionsText: String,
    onQuestionsChange: (String) -> Unit,
    answerKeyText: String,
    onAnswerKeyChange: (String) -> Unit
) {
    if (format == PasteFormat.COMBINED) {
        Text(
            "Each question, its options (if any), and ANSWER: together. Separate questions with a blank line.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = combinedText,
            onValueChange = onCombinedChange,
            placeholder = {
                Text(
                    "Q1. What is the powerhouse of the cell?\nA) Nucleus\nB) Mitochondria\nC) Ribosome\nD) Golgi apparatus\nANSWER: B\n\n" +
                        "*\nQ2. The powerhouse of the cell is ____.\nANSWER: Mitochondria"
                )
            },
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
    } else {
        Text(
            "Paste the numbered questions in the first box, and the answer key from your source in the second -- they'll be matched automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Questions", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = questionsText,
            onValueChange = onQuestionsChange,
            placeholder = {
                Text(
                    "1. What is the powerhouse of the cell?\nA) Nucleus\nB) Mitochondria\nC) Ribosome\nD) Golgi apparatus\n\n" +
                        "2. *The powerhouse of the cell is ____."
                )
            },
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Answer key", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = answerKeyText,
            onValueChange = onAnswerKeyChange,
            placeholder = { Text("1. B\n2. Mitochondria\n...  (also accepts 1) B  or  1-B  or a plain list B, C, A)") },
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
    }
}

@Composable
private fun SingleChoiceSegment(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Column {
        options.forEachIndexed { index, label ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(selected = index == selectedIndex, onClick = { onSelected(index) })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
