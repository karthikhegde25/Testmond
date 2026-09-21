package com.testmond.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.data.ImageUtils
import com.testmond.app.model.McqOption
import com.testmond.app.model.McqQuestion
import com.testmond.app.model.ParseResult
import com.testmond.app.model.PasteFormat
import com.testmond.app.model.QuestionType
import com.testmond.app.parser.McqParser
import kotlin.math.roundToInt

/** Wraps a question with a stable per-session id (for keying both the drag gesture and
 *  the composable itself, so an in-progress drag isn't cancelled when items reorder --
 *  see the key() wrapping below) and a display number that is captured once when the
 *  screen loads and then carried along with the question as it moves -- intentionally
 *  NOT recalculated to ascending order during this session. It only becomes correct
 *  again the next time this set is freshly opened for editing, after the reordered
 *  list has been saved. */
private data class EditableQuestion(val id: Int, val displayNumber: Int, val question: McqQuestion)

private const val CHIP_SLOT_HEIGHT_DP = 54 // chip height + vertical spacing, used for drag math

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    if (set == null || setFile == null) {
        navController.popBackStack()
        return
    }

    val idCounter = remember { intArrayOf(0) }
    val items = remember {
        mutableStateListOf<EditableQuestion>().apply {
            set.questions.forEachIndexed { i, q ->
                add(EditableQuestion(id = idCounter[0]++, displayNumber = i + 1, question = q))
            }
        }
    }
    // Snapshot of each question's ORIGINAL index at load time, keyed by its stable id.
    // Used only at Save time to remap saved quiz progress onto the edited list (see the
    // Save handler below) -- a question that survives editing keeps its progress
    // wherever it ends up, even through a reorder; a deleted question's progress entry
    // is simply dropped; a newly added question naturally has none.
    val originalIndexById = remember { items.associate { it.id to items.indexOf(it) } }

    var currentIndex by remember { mutableStateOf(0) }
    var savedMsg by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showManyQuestionsDialog by remember { mutableStateOf(false) }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        currentIndex = when {
            currentIndex == from -> to
            from < to && currentIndex in (from + 1)..to -> currentIndex - 1
            from > to && currentIndex in to until from -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun addNewQuestion(type: QuestionType) {
        val newQuestion = if (type == QuestionType.MCQ) {
            McqQuestion(
                type = QuestionType.MCQ,
                question = "",
                options = listOf(
                    McqOption("A", ""), McqOption("B", ""),
                    McqOption("C", ""), McqOption("D", "")
                ),
                answer = "A"
            )
        } else {
            McqQuestion(type = QuestionType.FILL_BLANK, question = "", options = emptyList(), answer = "")
        }
        items.add(EditableQuestion(id = idCounter[0]++, displayNumber = items.size + 1, question = newQuestion))
        currentIndex = items.size - 1
    }

    /** Appends bulk-parsed questions after whatever's already in the test -- their final
     *  position (and thus number) is simply "next available slot", regardless of whatever
     *  numbers appeared in the pasted separate-key text (those are only used internally to
     *  match questions against the answer key during parsing). */
    fun addManyQuestions(parsed: List<McqQuestion>) {
        parsed.forEach { q ->
            items.add(EditableQuestion(id = idCounter[0]++, displayNumber = items.size + 1, question = q))
        }
        if (parsed.isNotEmpty()) currentIndex = items.size - parsed.size
    }

    fun deleteCurrentQuestion() {
        if (items.size <= 1) return
        items.removeAt(currentIndex)
        if (currentIndex > items.lastIndex) currentIndex = items.lastIndex
    }

    fun saveChanges() {
        // Remap any saved in-progress quiz answers onto the edited list before writing
        // the new question order -- see the doc comment on originalIndexById above.
        val oldProgress = FileStorage.loadProgress(context, setFile)
        if (oldProgress != null) {
            val newAnswers = items.map { eq -> originalIndexById[eq.id]?.let { oldProgress.answers.getOrNull(it) } }
            val newDrafts = items.map { eq -> originalIndexById[eq.id]?.let { oldProgress.drafts.getOrNull(it) } }
            val newTimes = items.map { eq -> originalIndexById[eq.id]?.let { oldProgress.questionTimesMillis.getOrNull(it) } }
            if (newAnswers.any { it != null } || newDrafts.any { it != null }) {
                val oldCurrentQuestionId = items.firstOrNull { originalIndexById[it.id] == oldProgress.currentIndex }?.id
                val newCurrentIndex = items.indexOfFirst { it.id == oldCurrentQuestionId }.let { if (it >= 0) it else 0 }
                FileStorage.saveProgress(
                    context, setFile,
                    oldProgress.copy(
                        currentIndex = newCurrentIndex.coerceIn(0, items.lastIndex),
                        answers = newAnswers,
                        drafts = newDrafts,
                        questionTimesMillis = newTimes
                    )
                )
            } else {
                FileStorage.clearProgress(context, setFile)
            }
        }

        val updatedQuestions = items.map { it.question }
        FileStorage.updateSet(setFile, set.copy(questions = updatedQuestions))
        ActiveSetHolder.current.value = set.copy(questions = updatedQuestions)
        savedMsg = true
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageUtils.encodeForQuestion(context, uri)
            if (encoded != null) {
                val item = items[currentIndex]
                items[currentIndex] = item.copy(question = item.question.copy(imageBase64 = encoded))
            }
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
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { saveChanges() }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {

            // Question number navigator, left side -- long-press and drag a number to reorder.
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEachIndexed { index, item ->
                    // key() is what makes this composable (and the gesture-detector coroutine
                    // inside its pointerInput below) follow this SPECIFIC question across a
                    // reorder, rather than being torn down and recreated for whichever question
                    // now sits at this same list position. Without it, an in-progress drag was
                    // getting cancelled after moving exactly one slot, since from Compose's
                    // perspective the composable at this position had "changed identity".
                    key(item.id) {
                        val isDragging = draggingId == item.id
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = item.id
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = { draggingId = null; dragOffsetY = 0f },
                                        onDragCancel = { draggingId = null; dragOffsetY = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val slotPx = with(density) { CHIP_SLOT_HEIGHT_DP.dp.toPx() }
                                            val steps = (dragOffsetY / slotPx).roundToInt()
                                            if (steps != 0) {
                                                val fromIndex = items.indexOfFirst { it.id == item.id }
                                                val toIndex = (fromIndex + steps).coerceIn(0, items.lastIndex)
                                                if (toIndex != fromIndex) {
                                                    moveItem(fromIndex, toIndex)
                                                    dragOffsetY -= steps * slotPx
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            QuestionNavChip(
                                number = item.displayNumber,
                                isCurrent = index == currentIndex,
                                isDragging = isDragging,
                                onClick = { if (draggingId == null) currentIndex = index }
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = { showAddMenu = true })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add question",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Multiple choice") },
                            onClick = { showAddMenu = false; addNewQuestion(QuestionType.MCQ) }
                        )
                        DropdownMenuItem(
                            text = { Text("Fill in the blank") },
                            onClick = { showAddMenu = false; addNewQuestion(QuestionType.FILL_BLANK) }
                        )
                        DropdownMenuItem(
                            text = { Text("Many questions") },
                            onClick = { showAddMenu = false; showManyQuestionsDialog = true }
                        )
                    }
                }
            }

            // Edit form for the selected question.
            Column(
                Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val currentItem = items[currentIndex]
                val current = currentItem.question
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Question ${currentIndex + 1} of ${items.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = items.size > 1
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete this question",
                            tint = if (items.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = current.question,
                    onValueChange = { items[currentIndex] = currentItem.copy(question = current.copy(question = it)) },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                )

                Spacer(Modifier.height(12.dp))

                if (current.imageBase64 != null) {
                    Box {
                        QuestionImage(current.imageBase64, modifier = Modifier.padding(bottom = 4.dp))
                        IconButton(
                            onClick = { items[currentIndex] = currentItem.copy(question = current.copy(imageBase64 = null)) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add image")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (current.type == QuestionType.MCQ) {
                    Text("Options", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap the checkmark to mark the correct option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    current.options.forEachIndexed { i, opt ->
                        val isAnswer = opt.letter == current.answer
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                items[currentIndex] = currentItem.copy(question = current.copy(answer = opt.letter))
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Mark ${opt.letter} as correct",
                                    tint = if (isAnswer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            OutlinedTextField(
                                value = opt.text,
                                onValueChange = { newText ->
                                    val updatedOptions = current.options.toMutableList()
                                    updatedOptions[i] = opt.copy(text = newText)
                                    items[currentIndex] = currentItem.copy(question = current.copy(options = updatedOptions))
                                },
                                label = { Text("${opt.letter})") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = current.answer,
                        onValueChange = { items[currentIndex] = currentItem.copy(question = current.copy(answer = it)) },
                        label = { Text("Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this question?") },
            text = { Text("Question ${currentIndex + 1} will be removed. This can't be undone once you save.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCurrentQuestion()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showManyQuestionsDialog) {
        ManyQuestionsDialog(
            onDismiss = { showManyQuestionsDialog = false },
            onAdd = { parsedQuestions ->
                addManyQuestions(parsedQuestions)
                showManyQuestionsDialog = false
            }
        )
    }

    if (savedMsg) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1200)
            savedMsg = false
        }
        Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Saved",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ManyQuestionsDialog(
    onDismiss: () -> Unit,
    onAdd: (List<McqQuestion>) -> Unit
) {
    var format by remember { mutableStateOf(PasteFormat.COMBINED) }
    var combinedText by remember { mutableStateOf("") }
    var questionsText by remember { mutableStateOf("") }
    var answerKeyText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    // Same two-step flow as Create test: Preview first (how many questions were detected and which
    // were skipped), then add them. Any change to the pasted text throws the preview away, so a
    // stale preview can never be added after an edit.
    var previewResult by remember { mutableStateOf<ParseResult?>(null) }

    fun invalidate() {
        previewResult = null
        errorMsg = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(16.dp).fillMaxSize()) {
                Text("Add many questions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "These are appended after the existing questions -- numbers in a separate " +
                        "answer key are only used to match answers here, not the final position.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = format == PasteFormat.COMBINED,
                        onClick = { format = PasteFormat.COMBINED; invalidate() }
                    )
                    Text("Combined", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    RadioButton(
                        selected = format == PasteFormat.SEPARATE_KEY,
                        onClick = { format = PasteFormat.SEPARATE_KEY; invalidate() }
                    )
                    Text("Separate key", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    PasteFields(
                        format = format,
                        combinedText = combinedText,
                        onCombinedChange = { combinedText = it; invalidate() },
                        questionsText = questionsText,
                        onQuestionsChange = { questionsText = it; invalidate() },
                        answerKeyText = answerKeyText,
                        onAnswerKeyChange = { answerKeyText = it; invalidate() }
                    )
                }

                errorMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                val preview = previewResult
                if (preview != null) {
                    Spacer(Modifier.height(8.dp))
                    // Its own small scroll area, so a long list of skipped questions never pushes
                    // the buttons off the screen.
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)
                    ) {
                        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
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
                }

                Spacer(Modifier.height(12.dp))
                if (preview == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val result = if (format == PasteFormat.COMBINED) {
                                    McqParser.parseCombined(combinedText)
                                } else {
                                    McqParser.parseSeparate(questionsText, answerKeyText)
                                }
                                if (result.questions.isEmpty()) {
                                    errorMsg = "Couldn't parse any questions. Check the format and try again."
                                } else {
                                    errorMsg = null
                                    previewResult = result
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Preview")
                        }
                    }
                } else {
                    Button(
                        onClick = { onAdd(preview.questions) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add ${preview.questions.size} question${if (preview.questions.size != 1) "s" else ""}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        OutlinedButton(onClick = { previewResult = null }, modifier = Modifier.weight(1f)) {
                            Text("Edit before adding")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionNavChip(number: Int, isCurrent: Boolean, isDragging: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isDragging -> MaterialTheme.colorScheme.secondaryContainer
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                width = if (isCurrent || isDragging) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$number", style = MaterialTheme.typography.labelMedium)
    }
}
