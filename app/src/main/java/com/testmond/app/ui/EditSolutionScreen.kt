package com.testmond.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.data.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSolutionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    if (set == null || setFile == null) {
        navController.popBackStack()
        return
    }

    // Keyed by 0-based question index -> solution text.
    val solutions = remember {
        mutableStateMapOf<Int, String>().apply {
            set.questions.forEachIndexed { i, q -> q.solution?.let { put(i, it) } }
        }
    }
    // Optional image attached to each solution, same 0-based index -> base64 JPEG.
    val solutionImages = remember {
        mutableStateMapOf<Int, String>().apply {
            set.questions.forEachIndexed { i, q -> q.solutionImageBase64?.let { put(i, it) } }
        }
    }
    var currentQuestionIndex by remember { mutableStateOf(solutions.keys.minOrNull()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val target = currentQuestionIndex
        if (uri != null && target != null && solutions.containsKey(target)) {
            val encoded = ImageUtils.encodeForQuestion(context, uri)
            if (encoded != null) solutionImages[target] = encoded
        }
    }

    fun saveSolutions() {
        val updatedQuestions = set.questions.mapIndexed { i, q ->
            q.copy(
                solution = solutions[i],
                // An image only lives on a question that still has a solution entry.
                solutionImageBase64 = if (solutions.containsKey(i)) solutionImages[i] else null
            )
        }
        val updatedSet = set.copy(questions = updatedQuestions)
        FileStorage.updateSet(setFile, updatedSet)
        ActiveSetHolder.current.value = updatedSet
        savedMsg = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit solutions") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRemoveAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Remove solution for entire test")
                    }
                    TextButton(onClick = { saveSolutions() }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {

            // Solution navigator, left side -- shows only questions that currently have one.
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                solutions.keys.sorted().forEach { qIndex ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (qIndex == currentQuestionIndex) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (qIndex == currentQuestionIndex) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { currentQuestionIndex = qIndex }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${qIndex + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = { showAddDialog = true })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add new solution",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Editor for the selected solution.
            Column(
                Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val qIndex = currentQuestionIndex
                if (qIndex == null || !solutions.containsKey(qIndex)) {
                    Text(
                        "No solutions yet. Tap the + on the left to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Solution for question ${qIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete this solution",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = solutions[qIndex] ?: "",
                        onValueChange = { solutions[qIndex] = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    val solutionImage = solutionImages[qIndex]
                    if (solutionImage != null) {
                        Box {
                            QuestionImage(solutionImage, modifier = Modifier.padding(bottom = 4.dp))
                            IconButton(
                                onClick = { solutionImages.remove(qIndex) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove solution image",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Attach image")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddDialog) {
        val available = set.questions.indices.filter { it !in solutions.keys }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add solution for") },
            text = {
                if (available.isEmpty()) {
                    Text("Every question already has a solution.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 350.dp)) {
                        items(available) { idx ->
                            Text(
                                "Question ${idx + 1}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        solutions[idx] = ""
                                        currentQuestionIndex = idx
                                        showAddDialog = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteConfirm) {
        val qIndex = currentQuestionIndex
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this solution?") },
            text = { Text("The solution for question ${(qIndex ?: 0) + 1} will be removed. This can't be undone once you save.") },
            confirmButton = {
                TextButton(onClick = {
                    if (qIndex != null) {
                        solutions.remove(qIndex)
                        solutionImages.remove(qIndex)
                    }
                    currentQuestionIndex = solutions.keys.minOrNull()
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

    if (showRemoveAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAllConfirm = false },
            title = { Text("Remove solution for this whole test?") },
            text = { Text("Every solution in this test will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val updatedQuestions = set.questions.map { it.copy(solution = null, solutionImageBase64 = null) }
                    val updatedSet = set.copy(questions = updatedQuestions)
                    FileStorage.updateSet(setFile, updatedSet)
                    ActiveSetHolder.current.value = updatedSet
                    showRemoveAllConfirm = false
                    navController.safePopBackStack()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAllConfirm = false }) { Text("Cancel") }
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
