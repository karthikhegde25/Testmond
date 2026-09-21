package com.testmond.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.testmond.app.safeNavigate
import com.testmond.app.ActiveFolderHolder
import com.testmond.app.ActiveSetHolder
import com.testmond.app.ExamSessionHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.data.ImportOutcome
import com.testmond.app.data.ImportPreview
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.ExamRules
import com.testmond.app.model.McqSet
import com.testmond.app.model.QuizAttempt
import com.testmond.app.model.TestFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    pendingImportUri: Uri?,
    onImportHandled: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    var testFiles by remember { mutableStateOf(FileStorage.listLibrary(context)) }
    var folderFiles by remember { mutableStateOf(FileStorage.listFolders(context)) }
    var infoMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var pendingDeleteTest by remember { mutableStateOf<File?>(null) }
    var pendingRenameTest by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var historyFor by remember { mutableStateOf<File?>(null) }

    var examFiles by remember { mutableStateOf(FileStorage.resolveExamModeSets(context)) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var examTimeFor by remember { mutableStateOf<File?>(null) }
    var examMinutesText by remember { mutableStateOf("60") }
    var examStartFor by remember { mutableStateOf<File?>(null) }
    var examStartMinutes by remember { mutableStateOf(60) }
    var pendingRemoveExam by remember { mutableStateOf<File?>(null) }
    // Custom rules dialog: which test it is for and the three text boxes (blank = 0).
    var rulesFor by remember { mutableStateOf<File?>(null) }
    var rulesCorrectText by remember { mutableStateOf("") }
    var rulesWrongText by remember { mutableStateOf("") }
    var rulesSkippedText by remember { mutableStateOf("") }
    var rulesVersion by remember { mutableStateOf(0) }   // bumped on save so cards re-read their rules
    var foldersVersion by remember { mutableStateOf(0) } // bumped when a test is deleted/renamed so folder counts are re-read

    var pendingDeleteFolder by remember { mutableStateOf<File?>(null) }
    var pendingRenameFolder by remember { mutableStateOf<File?>(null) }
    var folderRenameText by remember { mutableStateOf("") }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var mainMenuExpanded by remember { mutableStateOf(false) }
    var fabMenuExpanded by remember { mutableStateOf(false) }
    var fabVisible by remember { mutableStateOf(true) }
    val fabScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -1f) fabVisible = false
                else if (available.y > 1f) fabVisible = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // A file that was picked or opened and read, waiting for the user to confirm the import.
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }

    fun refreshTests() { testFiles = FileStorage.listLibrary(context) }
    fun refreshFolders() { folderFiles = FileStorage.listFolders(context) }
    fun refreshExam() { examFiles = FileStorage.resolveExamModeSets(context) }

    // Card data (attempts, progress, solutions, folder contents, exam history and rules) is read on a
    // background thread and kept in memory; scrolling only looks values up. Starts from the last
    // result, so the lists show instantly and then update if anything changed.
    var testInfo by remember { mutableStateOf(HomeCardCache.tests) }
    var folderInfo by remember { mutableStateOf(HomeCardCache.folders) }
    var examInfo by remember { mutableStateOf(HomeCardCache.exams) }
    LaunchedEffect(testFiles) {
        val loaded = withContext(Dispatchers.IO) { loadTestCardInfo(context, testFiles, HomeCardCache.tests) }
        HomeCardCache.tests = loaded
        testInfo = loaded
    }
    LaunchedEffect(folderFiles, foldersVersion) {
        val loaded = withContext(Dispatchers.IO) { loadFolderCardInfo(context, folderFiles) }
        HomeCardCache.folders = loaded
        folderInfo = loaded
    }
    LaunchedEffect(examFiles, rulesVersion) {
        val loaded = withContext(Dispatchers.IO) { loadExamCardInfo(context, examFiles, HomeCardCache.exams) }
        HomeCardCache.exams = loaded
        examInfo = loaded
    }

    // In-progress tests float to the top; a stable sort keeps everything else in its existing
    // most-recent-first order. Once a test is finished (progress cleared) it falls back to its
    // normal position. Computed from the in-memory info, not by reading files.
    val sortedTestFiles = remember(testFiles, testInfo) {
        testFiles.sortedByDescending { testInfo[it.name]?.hasProgress == true }
    }

    // Opening a test means reading and parsing its whole file (a big one with images can be several
    // MB). That happens on a background thread, so the tap never freezes the screen: the screen
    // stays live, a thin progress bar appears only if it takes longer than a moment, taps meanwhile
    // are ignored (so it can't open twice), and the next screen opens as soon as the test is ready.
    var opening by remember { mutableStateOf(false) }
    var showOpening by remember { mutableStateOf(false) }
    fun loadThen(file: File, onLoaded: (McqSet) -> Unit) {
        if (opening) return
        opening = true
        coroutineScope.launch {
            val spinner = launch {
                delay(250)
                showOpening = true
            }
            val set = runCatching { withContext(Dispatchers.IO) { FileStorage.loadFromFile(file) } }.getOrNull()
            spinner.cancel()
            showOpening = false
            opening = false
            if (set == null) {
                errorMsg = "Couldn't open \"${file.nameWithoutExtension}\"."
            } else {
                onLoaded(set)
            }
        }
    }
    fun openInHolder(set: McqSet, file: File) {
        ActiveSetHolder.current.value = set
        ActiveSetHolder.currentFile.value = file
    }

    // Owned here (not inside each page) so a tab keeps its scroll position when you swipe away
    // and back, and after returning from another screen.
    val testsListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    val examListState = rememberLazyListState()

    fun handleImportOutcome(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.SetImported -> {
                refreshTests()
                infoMsg = "Imported \"${outcome.file.nameWithoutExtension}\"."
            }
            is ImportOutcome.FolderImported -> {
                refreshTests()
                refreshFolders()
                infoMsg = "Imported folder \"${outcome.folderFile.nameWithoutExtension}\" with ${outcome.testCount} test(s)."
            }
        }
    }

    // A file shared in from another app / file manager: read it and ask before importing.
    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            try {
                pendingImport = FileStorage.previewImportFromUri(context, uri)
            } catch (e: Exception) {
                errorMsg = "Could not import file: ${e.message}"
            }
            onImportHandled()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                pendingImport = FileStorage.previewImportFromUri(context, uri)
            } catch (e: Exception) {
                errorMsg = "Could not import file: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                val searchFocusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                }
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search tests and folders") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchActive = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Testmond",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                            fontSize = 24.sp,
                            letterSpacing = 0.3.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search tests and folders")
                        }
                        Box {
                            IconButton(onClick = { mainMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = mainMenuExpanded, onDismissRequest = { mainMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { mainMenuExpanded = false; navController.safeNavigate("settings") }
                                )
                                DropdownMenuItem(
                                    text = { Text("How to use") },
                                    onClick = { mainMenuExpanded = false; navController.safeNavigate("howto") }
                                )
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    onClick = { mainMenuExpanded = false; navController.safeNavigate("about") }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!searchActive) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = fabVisible,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                ) {
                    Box(Modifier.offset(x = (-10).dp, y = (-10).dp)) {
                        FloatingActionButton(onClick = {
                            // On the Exam tab the + adds existing tests to it; on the other
                            // tabs it opens the Import / Create menu as before.
                            if (pagerState.currentPage == 2) {
                                showAddExamDialog = true
                            } else {
                                fabMenuExpanded = true
                            }
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (pagerState.currentPage == 2) "Add tests to Exam Mode" else "Import or create"
                            )
                        }
                        DropdownMenu(expanded = fabMenuExpanded, onDismissRequest = { fabMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Import") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    fabMenuExpanded = false
                                    importLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (pagerState.currentPage == 0) "Create test" else "Create folder") },
                                leadingIcon = {
                                    Icon(
                                        if (pagerState.currentPage == 0) Icons.Default.Add else Icons.Default.CreateNewFolder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    fabMenuExpanded = false
                                    if (pagerState.currentPage == 0) navController.safeNavigate("create") else showCreateFolder = true
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (searchActive) {
            SearchResultsList(
                modifier = Modifier.padding(padding).fillMaxSize(),
                query = searchQuery,
                testFiles = testFiles,
                folderFiles = folderFiles,
                onOpenTest = { file ->
                    loadThen(file) { set ->
                        openInHolder(set, file)
                        navController.safeNavigate("quiz")
                    }
                },
                onOpenFolder = { file ->
                    ActiveFolderHolder.currentFile.value = file
                    navController.safeNavigate("folderDetail")
                }
            )
        } else {
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(fabScrollConnection)
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Tests") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Folders") }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Exam Mode") }
                )
            }

            // Appears only if opening a (large) test takes more than a moment.
            if (showOpening) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Column(Modifier.padding(16.dp).fillMaxSize()) {
                    infoMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(16.dp))

                    if (page == 0) {
                        if (testFiles.isEmpty()) {
                            EmptyState("No tests yet.\nTap + to create one, or import a file.")
                        } else {
                            LazyColumn(
                                state = testsListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sortedTestFiles, key = { it.name }) { file ->
                                    val info = testInfo[file.name]
                                    val hasSolution = info?.hasSolution == true
                                    TestCard(
                                        file = file,
                                        attempts = info?.attempts ?: emptyList(),
                                        hasProgress = info?.hasProgress == true,
                                        hasSolution = hasSolution,
                                        onOpen = {
                                            loadThen(file) { set ->
                                                openInHolder(set, file)
                                                navController.safeNavigate("quiz")
                                            }
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
                                            pendingRenameTest = file
                                        },
                                        onEdit = {
                                            loadThen(file) { set ->
                                                openInHolder(set, file)
                                                navController.safeNavigate("editTest")
                                            }
                                        },
                                        onSolution = {
                                            loadThen(file) { set ->
                                                openInHolder(set, file)
                                                // Decided from the file itself, not the (possibly still loading) card data.
                                                val hasSolutions = set.questions.any { it.solution != null }
                                                navController.safeNavigate(if (hasSolutions) "editSolution" else "addSolution")
                                            }
                                        },
                                        onHistory = { historyFor = file },
                                        onDeleteRequest = { pendingDeleteTest = file }
                                    )
                                }
                            }
                        }
                    } else if (page == 1) {
                        if (folderFiles.isEmpty()) {
                            EmptyState("No folders yet.\nTap + to create one to organize your tests.")
                        } else {
                            LazyColumn(
                                state = foldersListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(folderFiles, key = { it.name }) { file ->
                                    FolderCard(
                                        file = file,
                                        testCount = folderInfo[file.name],
                                        onOpen = {
                                            ActiveFolderHolder.currentFile.value = file
                                            navController.safeNavigate("folderDetail")
                                        },
                                        onShare = {
                                            val uri = FileStorage.shareFolderUriFor(context, FileStorage.loadFolder(file))
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share folder"))
                                        },
                                        onRename = {
                                            folderRenameText = file.nameWithoutExtension
                                            pendingRenameFolder = file
                                        },
                                        onDeleteRequest = { pendingDeleteFolder = file }
                                    )
                                }
                            }
                        }
                    } else {
                        if (examFiles.isEmpty()) {
                            EmptyState("No exam tests yet.\nTap + to add tests from your library.")
                        } else {
                            LazyColumn(
                                state = examListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(examFiles, key = { it.name }) { file ->
                                    val info = examInfo[file.name]
                                    val examAttempts = info?.attempts ?: emptyList()
                                    ExamCard(
                                        file = file,
                                        attempts = examAttempts,
                                        rules = info?.rules,
                                        onCustomRules = {
                                            // Pre-fill with the current rules; blank when there are none.
                                            val current = FileStorage.loadExamRules(context, file)
                                            rulesCorrectText = current?.let { formatMarks(it.correct) } ?: ""
                                            rulesWrongText = current?.let { formatMarks(it.wrong) } ?: ""
                                            rulesSkippedText = current?.let { formatMarks(it.skipped) } ?: ""
                                            rulesFor = file
                                        },
                                        onOpen = {
                                            // Pre-fill with the time limit used last time, if any.
                                            val lastMinutes = examAttempts.maxByOrNull { it.timestampMillis }
                                                ?.durationMillis
                                                ?.let { (it / 60_000L).toInt() }
                                                ?.takeIf { it > 0 }
                                            examMinutesText = (lastMinutes ?: 60).toString()
                                            examTimeFor = file
                                        },
                                        onReview = {
                                            loadThen(file) { set ->
                                                openInHolder(set, file)
                                                navController.safeNavigate("examReview")
                                            }
                                        },
                                        onRemoveRequest = { pendingRemoveExam = file }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // ---------- Test dialogs ----------

    pendingDeleteTest?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTest = null },
            title = { Text("Delete this test?") },
            text = { Text("\"${file.nameWithoutExtension}\" will be permanently deleted, along with its progress and score history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    FileStorage.deleteFromLibrary(context, file)
                    refreshTests()
                    refreshExam()
                    foldersVersion += 1   // the folders that held it now hold one test fewer
                    pendingDeleteTest = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTest = null }) { Text("Cancel") }
            }
        )
    }

    pendingRenameTest?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRenameTest = null },
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
                            refreshTests()
                            refreshExam()
                            foldersVersion += 1
                        }
                        pendingRenameTest = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenameTest = null }) { Text("Cancel") }
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

    // ---------- Folder dialogs ----------

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false; newFolderName = "" },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        FileStorage.saveFolder(context, TestFolder(name = newFolderName.trim()))
                        refreshFolders()
                        newFolderName = ""
                        showCreateFolder = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false; newFolderName = "" }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteFolder?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFolder = null },
            title = { Text("Delete this folder?") },
            text = { Text("\"${file.nameWithoutExtension}\" will be removed. The tests inside it are not deleted and stay in the Tests tab.") },
            confirmButton = {
                TextButton(onClick = {
                    FileStorage.deleteFolder(file)
                    refreshFolders()
                    pendingDeleteFolder = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFolder = null }) { Text("Cancel") }
            }
        )
    }

    pendingRenameFolder?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRenameFolder = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = folderRenameText,
                    onValueChange = { folderRenameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderRenameText.isNotBlank()) {
                            FileStorage.renameFolder(context, file, folderRenameText)
                            refreshFolders()
                        }
                        pendingRenameFolder = null
                    },
                    enabled = folderRenameText.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenameFolder = null }) { Text("Cancel") }
            }
        )
    }

    // ---------- Import confirmation ----------

    pendingImport?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onCancel = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                try {
                    handleImportOutcome(FileStorage.commitImport(context, preview))
                } catch (e: Exception) {
                    errorMsg = "Could not import file: ${e.message}"
                }
            }
        )
    }

    // ---------- Exam Mode dialogs ----------

    if (showAddExamDialog) {
        AddExamTestsDialog(
            context = context,
            alreadyAdded = examFiles.map { it.name }.toSet(),
            onDismiss = { showAddExamDialog = false },
            onConfirm = { names ->
                FileStorage.addTestsToExamMode(context, names)
                refreshExam()
                showAddExamDialog = false
            }
        )
    }

    // Step 1: set the time limit.
    examTimeFor?.let { file ->
        val minutes = examMinutesText.toIntOrNull()?.takeIf { it in 1..600 }
        AlertDialog(
            onDismissRequest = { examTimeFor = null },
            title = { Text("Set exam time") },
            text = {
                Column {
                    Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = examMinutesText,
                        onValueChange = { input ->
                            if (input.length <= 3 && input.all { it.isDigit() }) examMinutesText = input
                        },
                        label = { Text("Duration (minutes)") },
                        supportingText = { Text("Between 1 and 600 minutes") },
                        isError = examMinutesText.isNotEmpty() && minutes == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (minutes != null) {
                            examStartMinutes = minutes
                            examStartFor = file
                            examTimeFor = null
                        }
                    },
                    enabled = minutes != null
                ) {
                    Text("Set time")
                }
            },
            dismissButton = {
                TextButton(onClick = { examTimeFor = null }) { Text("Cancel") }
            }
        )
    }

    // Step 2: confirm and start -- the clock only begins once Start is tapped.
    examStartFor?.let { file ->
        AlertDialog(
            onDismissRequest = { examStartFor = null },
            title = { Text("Start exam?") },
            text = {
                Text(
                    "\"${file.nameWithoutExtension}\" \u00B7 $examStartMinutes minute${if (examStartMinutes != 1) "s" else ""}.\n\n" +
                        "The timer starts as soon as you tap Start and can't be paused. Correct answers " +
                        "stay hidden until you finish, and leaving early loses your progress."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    examStartFor = null
                    val minutes = examStartMinutes
                    // The timer only starts once the test has loaded, right before the exam opens.
                    loadThen(file) { set ->
                        if (set.questions.isEmpty()) {
                            errorMsg = "Couldn't start the exam: this test has no readable questions."
                        } else {
                            errorMsg = null
                            openInHolder(set, file)
                            ExamSessionHolder.start(set.questions.size, minutes * 60_000L)
                            navController.safeNavigate("examQuiz")
                        }
                    }
                }) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { examStartFor = null }) { Text("Cancel") }
            }
        )
    }

    rulesFor?.let { file ->
        val correctMarks = parseMarks(rulesCorrectText)
        val wrongMarks = parseMarks(rulesWrongText)
        val skippedMarks = parseMarks(rulesSkippedText)
        val valid = correctMarks != null && wrongMarks != null && skippedMarks != null
        AlertDialog(
            onDismissRequest = { rulesFor = null },
            title = { Text("Custom rules") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Marks used to work out your score in the review. Use a minus sign for " +
                            "negative marking. Leave everything blank (or 0) for normal scoring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rulesCorrectText,
                        onValueChange = { if (isValidMarksInput(it)) rulesCorrectText = it },
                        label = { Text("Correct answer") },
                        placeholder = { Text("e.g. 4") },
                        isError = correctMarks == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rulesWrongText,
                        onValueChange = { if (isValidMarksInput(it)) rulesWrongText = it },
                        label = { Text("Wrong answer") },
                        placeholder = { Text("e.g. -1") },
                        isError = wrongMarks == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rulesSkippedText,
                        onValueChange = { if (isValidMarksInput(it)) rulesSkippedText = it },
                        label = { Text("Skipped question") },
                        placeholder = { Text("e.g. 0") },
                        isError = skippedMarks == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (correctMarks != null && wrongMarks != null && skippedMarks != null) {
                            val rules = ExamRules(correctMarks, wrongMarks, skippedMarks)
                            FileStorage.saveExamRules(context, file, rules)
                            rulesVersion += 1
                            infoMsg = if (rules.isActive()) {
                                "Custom rules saved."
                            } else {
                                "Custom rules cleared -- normal scoring is used."
                            }
                            rulesFor = null
                        }
                    },
                    enabled = valid
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { rulesFor = null }) { Text("Cancel") }
            }
        )
    }

    pendingRemoveExam?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRemoveExam = null },
            title = { Text("Remove from Exam Mode?") },
            text = {
                Text(
                    "\"${file.nameWithoutExtension}\" will be removed from this tab. The test itself stays in " +
                        "the Tests tab, and its exam history is kept in case you add it back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    FileStorage.removeTestFromExamMode(context, file.name)
                    refreshExam()
                    pendingRemoveExam = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveExam = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SearchResultsList(
    modifier: Modifier,
    query: String,
    testFiles: List<File>,
    folderFiles: List<File>,
    onOpenTest: (File) -> Unit,
    onOpenFolder: (File) -> Unit
) {
    val trimmedQuery = query.trim()
    val matchingTests = remember(trimmedQuery, testFiles) {
        if (trimmedQuery.isEmpty()) emptyList()
        else testFiles.filter { it.nameWithoutExtension.contains(trimmedQuery, ignoreCase = true) }
    }
    val matchingFolders = remember(trimmedQuery, folderFiles) {
        if (trimmedQuery.isEmpty()) emptyList()
        else folderFiles.filter { it.nameWithoutExtension.contains(trimmedQuery, ignoreCase = true) }
    }

    Column(modifier.padding(16.dp)) {
        if (trimmedQuery.isEmpty()) {
            EmptyState("Start typing to search your tests and folders.")
        } else if (matchingTests.isEmpty() && matchingFolders.isEmpty()) {
            EmptyState("No tests or folders match \"$trimmedQuery\".")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matchingTests.isNotEmpty()) {
                    item {
                        Text(
                            "Tests",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(matchingTests) { file ->
                        SearchResultRow(
                            name = file.nameWithoutExtension,
                            icon = Icons.Default.Description,
                            onClick = { onOpenTest(file) }
                        )
                    }
                }
                if (matchingFolders.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Folders",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(matchingFolders) { file ->
                        SearchResultRow(
                            name = file.nameWithoutExtension,
                            icon = Icons.Default.Folder,
                            onClick = { onOpenFolder(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TestCard(
    file: File,
    attempts: List<QuizAttempt>,
    hasProgress: Boolean,
    hasSolution: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onEdit: () -> Unit,
    onSolution: () -> Unit,
    onHistory: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                val subtitle = buildString {
                    if (hasProgress) append("In progress")
                    if (attempts.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        val last = attempts.maxByOrNull { it.timestampMillis }!!
                        append("${attempts.size} attempt${if (attempts.size != 1) "s" else ""} · last ${last.correct}/${last.total}")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

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

@Composable
private fun FolderCard(
    file: File,
    testCount: Int?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                if (testCount != null) {
                    Text(
                        "$testCount test${if (testCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDeleteRequest() })
                }
            }
        }
    }
}

@Composable
private fun ExamCard(
    file: File,
    attempts: List<ExamAttempt>,
    rules: ExamRules?,
    onOpen: () -> Unit,
    onReview: () -> Unit,
    onCustomRules: () -> Unit,
    onRemoveRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                val subtitle = if (attempts.isEmpty()) {
                    "No exams taken yet"
                } else {
                    val last = attempts.maxByOrNull { it.timestampMillis }!!
                    "${attempts.size} exam${if (attempts.size != 1) "s" else ""} \u00B7 last ${scoreText(last, rules)}"
                }
                Text(
                    if (rules != null) "$subtitle \u00B7 custom marks" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Review") }, onClick = { menuExpanded = false; onReview() })
                    DropdownMenuItem(text = { Text("Custom rules") }, onClick = { menuExpanded = false; onCustomRules() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menuExpanded = false; onRemoveRequest() })
                }
            }
        }
    }
}

@Composable
private fun AddExamTestsDialog(
    context: android.content.Context,
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val available = remember { FileStorage.listLibrary(context).filter { it.name !in alreadyAdded } }
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tests to Exam Mode") },
        text = {
            if (available.isEmpty()) {
                Text("Every test is already in Exam Mode, or you don't have any tests yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(available) { file ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked[file.name] ?: false,
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

private fun questionCountLabel(count: Int): String =
    if (count == 1) "1 question" else "$count questions"

/**
 * Shown after a file is picked or opened, before anything is saved: what is in it -- the number
 * of questions for a test, or how many tests a folder holds and how many questions each has --
 * with an Import button to confirm.
 */
@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val canImport = when (preview) {
        is ImportPreview.SetPreview -> preview.set.questions.isNotEmpty()
        is ImportPreview.FolderPreview -> true
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                when (preview) {
                    is ImportPreview.SetPreview -> "Import this test?"
                    is ImportPreview.FolderPreview -> "Import this folder?"
                }
            )
        },
        text = {
            when (preview) {
                is ImportPreview.SetPreview -> {
                    Column {
                        Text(preview.set.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val count = preview.set.questions.size
                        Text(
                            if (count == 0) "This file has no questions, so it can't be imported."
                            else questionCountLabel(count)
                        )
                    }
                }
                is ImportPreview.FolderPreview -> {
                    val sets = preview.bundle.sets
                    val usable = sets.filter { it.questions.isNotEmpty() }
                    Column {
                        Text(preview.bundle.folderName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (usable.size == 1) "1 test" else "${usable.size} tests") +
                                " \u00B7 " + questionCountLabel(usable.sumOf { it.questions.size }) + " in total",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(sets) { testSet ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        testSet.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        if (testSet.questions.isEmpty()) "no questions (skipped)"
                                        else questionCountLabel(testSet.questions.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canImport) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

/** Accepts what can be typed toward a marks value: an optional leading minus, digits and at most
 *  one decimal point (e.g. "4", "-1", "-0.25", or the partial "-" while typing). */
private fun isValidMarksInput(text: String): Boolean {
    if (text.length > 8) return false
    var seenDot = false
    for ((i, c) in text.withIndex()) {
        when {
            c == '-' && i == 0 -> {}
            c == '.' && !seenDot -> seenDot = true
            c.isDigit() -> {}
            else -> return false
        }
    }
    return true
}

/** Blank counts as 0; null means not a complete number yet (e.g. just "-" or "."). */
private fun parseMarks(text: String): Double? =
    if (text.isBlank()) 0.0 else text.toDoubleOrNull()
