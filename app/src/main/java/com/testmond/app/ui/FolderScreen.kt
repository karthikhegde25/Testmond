package com.testmond.app.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safeNavigate
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveFolderHolder
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderScreen(navController: NavHostController) {
    val context = LocalContext.current
    val folderFile = ActiveFolderHolder.currentFile.value
    if (folderFile == null) {
        navController.popBackStack()
        return
    }

    var folder by remember { mutableStateOf(FileStorage.loadFolder(folderFile)) }
    var testFiles by remember { mutableStateOf(FileStorage.resolveFolderSets(context, folder)) }
    var showAddDialog by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedNames = remember { mutableStateListOf<String>() }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var pendingRename by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var historyFor by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        folder = FileStorage.loadFolder(folderFile)
        testFiles = FileStorage.resolveFolderSets(context, folder)
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedNames.clear()
    }

    fun toggleSelection(name: String) {
        if (selectedNames.contains(name)) selectedNames.remove(name) else selectedNames.add(name)
        if (selectedNames.isEmpty()) selectionMode = false
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedNames.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showRemoveConfirm = true },
                            enabled = selectedNames.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from folder")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(folder.name) },
                    navigationIcon = {
                        IconButton(onClick = { navController.safePopBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add tests to folder")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (testFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No tests in this folder yet.\nTap the add icon above to bring some in.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(testFiles) { file ->
                        val isSelected = selectedNames.contains(file.name)
                        val hasSolution = remember(file, testFiles) {
                            runCatching { FileStorage.loadFromFile(file).questions.any { it.solution != null } }.getOrDefault(false)
                        }
                        FolderTestRow(
                            file = file,
                            hasSolution = hasSolution,
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(file.name)
                                } else {
                                    val set = FileStorage.loadFromFile(file)
                                    ActiveSetHolder.current.value = set
                                    ActiveSetHolder.currentFile.value = file
                                    navController.safeNavigate("quiz")
                                }
                            },
                            onLongPress = {
                                if (!selectionMode) selectionMode = true
                                toggleSelection(file.name)
                            },
                            onShare = {
                                val uri = FileStorage.shareUriFor(context, file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share test"))
                            },
                            onRename = {
                                renameText = file.nameWithoutExtension
                                pendingRename = file
                            },
                            onEdit = {
                                val set = FileStorage.loadFromFile(file)
                                ActiveSetHolder.current.value = set
                                ActiveSetHolder.currentFile.value = file
                                navController.safeNavigate("editTest")
                            },
                            onSolution = {
                                val set = FileStorage.loadFromFile(file)
                                ActiveSetHolder.current.value = set
                                ActiveSetHolder.currentFile.value = file
                                navController.safeNavigate(if (hasSolution) "editSolution" else "addSolution")
                            },
                            onHistory = { historyFor = file },
                            onDeleteRequest = { pendingDelete = file }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTestsDialog(
            context = context,
            alreadyInFolder = folder.testFileNames.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { selected ->
                FileStorage.addTestsToFolder(context, folderFile, selected)
                refresh()
                showAddDialog = false
            }
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove from folder?") },
            text = {
                Text(
                    "${selectedNames.size} test${if (selectedNames.size != 1) "s" else ""} will be removed from \"${folder.name}\". " +
                        "The test${if (selectedNames.size != 1) "s are" else " is"} not deleted -- it stays in the Tests tab."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedNames.forEach { name -> FileStorage.removeTestFromFolder(folderFile, name) }
                    refresh()
                    exitSelectionMode()
                    showRemoveConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this test?") },
            text = { Text("\"${file.nameWithoutExtension}\" will be permanently deleted, along with its progress and score history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    FileStorage.deleteFromLibrary(context, file)
                    refresh()
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    pendingRename?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename test") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            FileStorage.renameSet(context, file, renameText)
                            refresh()
                        }
                        pendingRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("Cancel") }
            }
        )
    }

    historyFor?.let { file ->
        val attempts = remember(file) { FileStorage.loadAttempts(context, file) }
        AlertDialog(
            onDismissRequest = { historyFor = null },
            title = { Text("Attempt history") },
            text = {
                if (attempts.isEmpty()) {
                    Text("No completed attempts yet.")
                } else {
                    val fmt = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attempts.sortedByDescending { it.timestampMillis }.forEach { attempt ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(fmt.format(Date(attempt.timestampMillis)), style = MaterialTheme.typography.bodySmall)
                                Text("${attempt.correct}/${attempt.total}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyFor = null }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTestRow(
    file: File,
    hasSolution: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onSolution: () -> Unit,
    onHistory: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            } else {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                file.nameWithoutExtension,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            if (!selectionMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; onShare() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                        DropdownMenuItem(text = { Text("Edit question") }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(if (hasSolution) "Edit solution" else "Add solution") },
                            onClick = { menuExpanded = false; onSolution() }
                        )
                        DropdownMenuItem(text = { Text("Attempt history") }, onClick = { menuExpanded = false; onHistory() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDeleteRequest() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTestsDialog(
    context: android.content.Context,
    alreadyInFolder: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val allTests = remember { FileStorage.listLibrary(context) }
    val available = remember { allTests.filter { it.name !in alreadyInFolder } }
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tests to folder") },
        text = {
            if (available.isEmpty()) {
                Text("Every test is already in this folder.")
            } else {
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(available) { file ->
                        val isChecked = checked[file.name] ?: false
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked[file.name] = it }
                            )
                            Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(checked.filterValues { it }.keys.toList()) },
                enabled = checked.any { it.value }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
