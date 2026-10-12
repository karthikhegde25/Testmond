package com.testmond.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.ExamAttemptHistory
import com.testmond.app.model.ExamModeList
import com.testmond.app.model.CreatedTimesStore
import com.testmond.app.model.Bookmarks
import com.testmond.app.model.ExamRules
import com.testmond.app.model.ExamRulesStore
import com.testmond.app.model.FolderBundle
import com.testmond.app.model.McqSet
import com.testmond.app.model.QuizAttempt
import com.testmond.app.model.QuizAttemptHistory
import com.testmond.app.model.QuizProgress
import com.testmond.app.model.QuizResult
import com.testmond.app.model.TestFolder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

/** Writes [text] to a temporary file beside [file] and then renames it over [file], so a crash or a
 *  full disk in the middle of a save can never leave a half-written (corrupt) test, progress or
 *  history file behind. */
private fun writeAtomically(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.writeText(text)
        tmp.delete()
    }
}

/** Strips only characters that are actually unsafe in a filename (path separators,
 *  Windows-reserved characters, control characters) -- everything else, including
 *  every language's script and most symbols/emoji, passes through untouched. The
 *  previous version only allowed ASCII letters/digits, which silently turned any
 *  non-English or symbol-containing title into "untitled" in the visible file list. */
private fun sanitizeFileName(raw: String): String =
    raw.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "").trim()

/** What a picked/opened file turned out to contain, so the caller (Home screen) knows
 *  whether to refresh the Tests list, the Folders list, or both. */
/** What a picked or opened file contains, read and validated but NOT yet saved anywhere --
 *  the app shows it to the user first and only imports after they confirm. */
sealed class ImportPreview {
    data class SetPreview(val set: McqSet) : ImportPreview()
    data class FolderPreview(val bundle: FolderBundle) : ImportPreview()
}

sealed class ImportOutcome {
    data class SetImported(val file: File) : ImportOutcome()
    data class FolderImported(val folderFile: File, val testCount: Int) : ImportOutcome()
}

object FileStorage {

    private fun setsDir(context: Context): File =
        File(context.filesDir, "mcq_sets").apply { mkdirs() }

    private fun foldersDir(context: Context): File =
        File(context.filesDir, "folders").apply { mkdirs() }

    private fun progressDir(context: Context): File =
        File(context.filesDir, "progress").apply { mkdirs() }

    private fun resultsDir(context: Context): File =
        File(context.filesDir, "results").apply { mkdirs() }

    private fun attemptsDir(context: Context): File =
        File(context.filesDir, "attempts").apply { mkdirs() }

    private fun bookmarksDir(context: Context): File =
        File(context.filesDir, "bookmarks").apply { mkdirs() }

    private fun examAttemptsDir(context: Context): File =
        File(context.filesDir, "exam_attempts").apply { mkdirs() }

    private fun examListFile(context: Context): File =
        File(context.filesDir, "exam_mode.json")

    private fun examRulesFile(context: Context): File =
        File(context.filesDir, "exam_rules.json")

    private fun createdTimesFile(context: Context): File =
        File(context.filesDir, "created_times.json")

    // ---------- creation times (for sorting) ----------

    private fun loadCreatedStore(context: Context): Map<String, Long> {
        val f = createdTimesFile(context)
        if (!f.exists()) return emptyMap()
        return try {
            json.decodeFromString(CreatedTimesStore.serializer(), f.readText()).times
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveCreatedStore(context: Context, times: Map<String, Long>) {
        writeAtomically(createdTimesFile(context), json.encodeToString(CreatedTimesStore(times)))
    }

    /** Records now as [key]'s creation time, unless it already has one (so re-saving an existing
     *  file, e.g. on rename, never resets it -- see [moveCreatedEntry] for that case instead). */
    private fun recordCreatedNow(context: Context, key: String) {
        val store = loadCreatedStore(context)
        if (store.containsKey(key)) return
        saveCreatedStore(context, store + (key to System.currentTimeMillis()))
    }

    /** Carries a creation time from [oldKey] over to [newKey] (used when a rename changes a
     *  file's name), dropping the old key. A file saved before this feature existed has no
     *  entry, and stays that way -- callers fall back to [File.lastModified] for it. */
    private fun moveCreatedEntry(context: Context, oldKey: String, newKey: String) {
        val store = loadCreatedStore(context)
        val time = store[oldKey] ?: return
        saveCreatedStore(context, (store - oldKey) + (newKey to time))
    }

    private fun clearCreatedEntry(context: Context, key: String) {
        val store = loadCreatedStore(context)
        if (!store.containsKey(key)) return
        saveCreatedStore(context, store - key)
    }

    /** A test's creation time if known, else its last-modified time (the best available guess for
     *  a test that existed before this feature was added). */
    fun testCreatedMillis(context: Context, file: File): Long =
        loadCreatedStore(context)["t:${file.name}"] ?: file.lastModified()

    /** A folder's creation time if known, else its last-modified time. */
    fun folderCreatedMillis(context: Context, file: File): Long =
        loadCreatedStore(context)["f:${file.name}"] ?: file.lastModified()

    // ---------- test sets ----------

    /** Saves a set into the app's own storage (shows up in the Tests tab). */
    fun saveToLibrary(context: Context, set: McqSet): File {
        val safeName = sanitizeFileName(set.title).ifBlank { "untitled" }
        var file = File(setsDir(context), "$safeName.mcqz")
        var counter = 1
        while (file.exists()) {
            file = File(setsDir(context), "$safeName ($counter).mcqz")
            counter++
        }
        writeAtomically(file, json.encodeToString(set))
        recordCreatedNow(context, "t:${file.name}")
        return file
    }

    fun listLibrary(context: Context): List<File> =
        setsDir(context).listFiles { f -> f.extension == "mcqz" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun loadFromFile(file: File): McqSet =
        json.decodeFromString(McqSet.serializer(), file.readText())

    /** Overwrites a set's content in place (same filename) -- used by the Edit screen,
     *  where only question content changes, not the title/filename. */
    fun updateSet(file: File, set: McqSet) {
        writeAtomically(file, json.encodeToString(set))
    }

    /** Deletes a set along with any saved progress, attempt history and exam history tied to
     *  it, drops it from the Exam tab, and removes it from every folder that contained it (so a
     *  folder's test count is right straight away). Folders also still tolerate a missing
     *  referenced file by skipping it, for anything deleted some other way. */
    fun deleteFromLibrary(context: Context, file: File): Boolean {
        clearCreatedEntry(context, "t:${file.name}")
        clearProgress(context, file)
        clearResult(context, file)
        clearAttempts(context, file)
        clearExamAttempts(context, file)
        clearBookmarks(context, file)
        removeTestFromExamMode(context, file.name)
        for (folderFile in listFolders(context)) {
            val folder = runCatching { loadFolder(folderFile) }.getOrNull() ?: continue
            if (folder.testFileNames.contains(file.name)) {
                saveFolderInPlace(folderFile, folder.copy(testFileNames = folder.testFileNames - file.name))
            }
        }
        saveExamRules(context, file, ExamRules())   // all zero = drop this test's custom rules
        return file.delete()
    }

    /** Renames a set: updates the internal title and moves it (and its progress/attempts) to a
     *  matching new filename. Keeps the list display name and the quiz-screen title in sync.
     *  Also updates the filename reference inside any folder that contains this set. */
    fun renameSet(context: Context, file: File, newTitle: String): File {
        val current = loadFromFile(file)
        // Nothing to do (and without this the test would be re-saved as "Name (1)").
        if (current.title == newTitle.trim()) return file
        val updated = current.copy(title = newTitle.trim())
        val progress = loadProgress(context, file)
        val result = loadResult(context, file)
        val attempts = loadAttempts(context, file)
        val examAttempts = loadExamAttempts(context, file)
        val bookmarks = loadBookmarks(context, file)

        val newFile = saveToLibrary(context, updated)
        if (newFile.absolutePath != file.absolutePath) {
            moveCreatedEntry(context, "t:${file.name}", "t:${newFile.name}")
            clearProgress(context, file)
            clearResult(context, file)
            clearAttempts(context, file)
            clearExamAttempts(context, file)
            clearBookmarks(context, file)
            file.delete()
            progress?.let { saveProgress(context, newFile, it) }
            result?.let { saveResult(context, newFile, it) }
            if (attempts.isNotEmpty()) {
                writeAtomically(attemptsFile(context, newFile), json.encodeToString(QuizAttemptHistory(attempts)))
            }
            if (examAttempts.isNotEmpty()) {
                writeAtomically(examAttemptsFile(context, newFile), json.encodeToString(ExamAttemptHistory(examAttempts)))
            }
            if (bookmarks.isNotEmpty()) {
                writeAtomically(bookmarksFile(context, newFile), json.encodeToString(Bookmarks(bookmarks.sorted())))
            }
            // Custom marking rules follow the renamed test.
            loadExamRules(context, file)?.let { rules ->
                saveExamRules(context, newFile, rules)
                saveExamRules(context, file, ExamRules())
            }
            // Keep the Exam tab's reference pointing at the right file too.
            val examList = loadExamList(context)
            if (examList.testFileNames.contains(file.name)) {
                saveExamList(
                    context,
                    ExamModeList(examList.testFileNames.map { if (it == file.name) newFile.name else it })
                )
            }
            // Keep folder references pointing at the right file.
            for (folderFile in listFolders(context)) {
                val folder = runCatching { loadFolder(folderFile) }.getOrNull() ?: continue
                if (folder.testFileNames.contains(file.name)) {
                    val updatedNames = folder.testFileNames.map { if (it == file.name) newFile.name else it }
                    saveFolderInPlace(folderFile, folder.copy(testFileNames = updatedNames))
                }
            }
        }
        return newFile
    }

    // ---------- folders ----------

    /** Saves a new folder (or overwrites-by-recreate on rename) into the app's own storage. */
    fun saveFolder(context: Context, folder: TestFolder): File {
        val safeName = sanitizeFileName(folder.name).ifBlank { "untitled folder" }
        var file = File(foldersDir(context), "$safeName.tfolder")
        var counter = 1
        while (file.exists()) {
            file = File(foldersDir(context), "$safeName ($counter).tfolder")
            counter++
        }
        writeAtomically(file, json.encodeToString(folder))
        recordCreatedNow(context, "f:${file.name}")
        return file
    }

    /** Overwrites an existing folder file in place (used for internal updates like
     *  adding tests or fixing a rename reference, where the filename doesn't change). */
    private fun saveFolderInPlace(file: File, folder: TestFolder) {
        writeAtomically(file, json.encodeToString(folder))
    }

    fun listFolders(context: Context): List<File> =
        foldersDir(context).listFiles { f -> f.extension == "tfolder" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun loadFolder(file: File): TestFolder =
        json.decodeFromString(TestFolder.serializer(), file.readText())

    /** Deletes the folder grouping. When [deleteTests] is true, also deletes every test still in
     *  it (each one fully, same as [deleteFromLibrary]) -- otherwise the tests are untouched and
     *  remain in the Tests tab. */
    fun deleteFolder(context: Context, file: File, deleteTests: Boolean = false): Boolean {
        if (deleteTests) {
            val folder = runCatching { loadFolder(file) }.getOrNull()
            folder?.let { resolveFolderSets(context, it) }?.forEach { testFile -> deleteFromLibrary(context, testFile) }
        }
        clearCreatedEntry(context, "f:${file.name}")
        return file.delete()
    }

    /** Renames a folder by recreating it under a new filename (mirrors renameSet). */
    fun renameFolder(context: Context, file: File, newName: String): File {
        val current = loadFolder(file)
        if (current.name == newName.trim()) return file   // unchanged -- don't re-save it as "Name (1)"
        val updated = current.copy(name = newName.trim())
        val newFile = saveFolder(context, updated)
        if (newFile.absolutePath != file.absolutePath) {
            moveCreatedEntry(context, "f:${file.name}", "f:${newFile.name}")
            file.delete()
        }
        return newFile
    }

    /** Adds test files (by their library filename) to a folder, de-duplicated. */
    fun addTestsToFolder(context: Context, folderFile: File, testFileNamesToAdd: List<String>) {
        val folder = loadFolder(folderFile)
        val merged = (folder.testFileNames + testFileNamesToAdd).distinct()
        saveFolderInPlace(folderFile, folder.copy(testFileNames = merged))
    }

    /** Removes a single test reference from a folder (the test itself is untouched). */
    fun removeTestFromFolder(folderFile: File, testFileName: String) {
        val folder = loadFolder(folderFile)
        saveFolderInPlace(folderFile, folder.copy(testFileNames = folder.testFileNames - testFileName))
    }

    /** Resolves a folder's referenced filenames into actual set files that still exist,
     *  silently skipping any that were deleted independently. */
    fun resolveFolderSets(context: Context, folder: TestFolder): List<File> {
        val dir = setsDir(context)
        return folder.testFileNames.mapNotNull { name ->
            File(dir, name).takeIf { it.exists() }
        }
    }

    /** Builds the full, portable export payload for a folder: its name plus the complete
     *  content of every test currently in it (not just filenames). */
    fun buildFolderBundle(context: Context, folder: TestFolder): FolderBundle {
        val sets = resolveFolderSets(context, folder).map { loadFromFile(it) }
        return FolderBundle(folderName = folder.name, sets = sets)
    }

    // ---------- in-progress quiz state ----------

    private fun progressFile(context: Context, setFile: File): File =
        File(progressDir(context), "${setFile.nameWithoutExtension}.progress.json")

    fun saveProgress(context: Context, setFile: File, progress: QuizProgress) {
        writeAtomically(progressFile(context, setFile), json.encodeToString(progress))
        // The test is in progress again, so it no longer counts as completed (the green tick goes).
        clearResult(context, setFile)
    }

    fun loadProgress(context: Context, setFile: File): QuizProgress? {
        val f = progressFile(context, setFile)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(QuizProgress.serializer(), f.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun clearProgress(context: Context, setFile: File) {
        progressFile(context, setFile).delete()
    }

    // ---------- finished-attempt result (the "completed" state of a test) ----------

    private fun resultFile(context: Context, setFile: File): File =
        File(resultsDir(context), "${setFile.nameWithoutExtension}.result.json")

    fun saveResult(context: Context, setFile: File, result: QuizResult) {
        writeAtomically(resultFile(context, setFile), json.encodeToString(result))
    }

    fun loadResult(context: Context, setFile: File): QuizResult? {
        val f = resultFile(context, setFile)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(QuizResult.serializer(), f.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun hasResult(context: Context, setFile: File): Boolean = resultFile(context, setFile).exists()

    fun clearResult(context: Context, setFile: File) {
        resultFile(context, setFile).delete()
    }

    // ---------- bookmarks (Test tab only, never shown/used in Exam Mode) ----------

    private fun bookmarksFile(context: Context, setFile: File): File =
        File(bookmarksDir(context), "${setFile.nameWithoutExtension}.bookmarks.json")

    /** The bookmarked question indexes for this test, if any -- empty if none, or if the file
     *  is missing/corrupt (never crashes the caller over a bookmark). */
    fun loadBookmarks(context: Context, setFile: File): Set<Int> {
        val f = bookmarksFile(context, setFile)
        if (!f.exists()) return emptySet()
        return try {
            json.decodeFromString(Bookmarks.serializer(), f.readText()).questionIndexes.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setBookmarked(context: Context, setFile: File, questionIndex: Int, bookmarked: Boolean): Set<Int> {
        val updated = loadBookmarks(context, setFile).toMutableSet()
        if (bookmarked) updated.add(questionIndex) else updated.remove(questionIndex)
        writeAtomically(bookmarksFile(context, setFile), json.encodeToString(Bookmarks(updated.sorted())))
        return updated
    }

    /** Overwrites this test's bookmarks (used when questions are edited/deleted/reordered and the
     *  bookmarked indexes have to follow their questions). An empty set removes the file. */
    fun replaceBookmarks(context: Context, setFile: File, questionIndexes: Set<Int>) {
        if (questionIndexes.isEmpty()) {
            clearBookmarks(context, setFile)
        } else {
            writeAtomically(bookmarksFile(context, setFile), json.encodeToString(Bookmarks(questionIndexes.sorted())))
        }
    }

    private fun clearBookmarks(context: Context, setFile: File) {
        bookmarksFile(context, setFile).delete()
    }

    // ---------- attempt history ----------

    private fun attemptsFile(context: Context, setFile: File): File =
        File(attemptsDir(context), "${setFile.nameWithoutExtension}.attempts.json")

    fun loadAttempts(context: Context, setFile: File): List<QuizAttempt> {
        val f = attemptsFile(context, setFile)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString(QuizAttemptHistory.serializer(), f.readText()).attempts
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appendAttempt(context: Context, setFile: File, attempt: QuizAttempt) {
        val existing = loadAttempts(context, setFile)
        val updated = QuizAttemptHistory(existing + attempt)
        writeAtomically(attemptsFile(context, setFile), json.encodeToString(updated))
    }

    private fun clearAttempts(context: Context, setFile: File) {
        attemptsFile(context, setFile).delete()
    }

    // ---------- exam mode ----------

    fun loadExamList(context: Context): ExamModeList {
        val f = examListFile(context)
        if (!f.exists()) return ExamModeList()
        return try {
            json.decodeFromString(ExamModeList.serializer(), f.readText())
        } catch (e: Exception) {
            ExamModeList()
        }
    }

    private fun saveExamList(context: Context, list: ExamModeList) {
        writeAtomically(examListFile(context), json.encodeToString(list))
    }

    /** Adds tests (by library filename) to the Exam tab, de-duplicated. */
    fun addTestsToExamMode(context: Context, testFileNamesToAdd: List<String>) {
        val merged = (loadExamList(context).testFileNames + testFileNamesToAdd).distinct()
        saveExamList(context, ExamModeList(merged))
    }

    /** Removes a test from the Exam tab only. The test itself and its exam history are kept,
     *  so adding it back later brings the old attempts back with it. */
    fun removeTestFromExamMode(context: Context, testFileName: String) {
        val list = loadExamList(context)
        if (!list.testFileNames.contains(testFileName)) return
        saveExamList(context, ExamModeList(list.testFileNames - testFileName))
    }

    /** The Exam tab's tests as real files, most recently added first, silently skipping any
     *  that were deleted independently. */
    fun resolveExamModeSets(context: Context): List<File> {
        val dir = setsDir(context)
        return loadExamList(context).testFileNames.asReversed().mapNotNull { name ->
            File(dir, name).takeIf { it.exists() }
        }
    }

    private fun fileStamp(f: File): String = if (f.exists()) "${f.lastModified()}:${f.length()}" else "-"

    /** A cheap fingerprint (just file timestamps and sizes, no reading) of everything a home-screen
     *  card shows for this test: the test itself, its attempt history, saved progress, exam history
     *  and the exam rules. If it hasn't changed, the card data computed last time is still right. */
    fun cardStamp(context: Context, setFile: File): String =
        listOf(
            setFile,
            attemptsFile(context, setFile),
            progressFile(context, setFile),
            resultFile(context, setFile),
            examAttemptsFile(context, setFile),
            examRulesFile(context)
        ).joinToString("|") { fileStamp(it) }

    private fun examAttemptsFile(context: Context, setFile: File): File =
        File(examAttemptsDir(context), "${setFile.nameWithoutExtension}.exam.json")

    fun loadExamAttempts(context: Context, setFile: File): List<ExamAttempt> {
        val f = examAttemptsFile(context, setFile)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString(ExamAttemptHistory.serializer(), f.readText()).attempts
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appendExamAttempt(context: Context, setFile: File, attempt: ExamAttempt) {
        val updated = ExamAttemptHistory(loadExamAttempts(context, setFile) + attempt)
        writeAtomically(examAttemptsFile(context, setFile), json.encodeToString(updated))
    }

    private fun clearExamAttempts(context: Context, setFile: File) {
        examAttemptsFile(context, setFile).delete()
    }

    private fun loadExamRulesStore(context: Context): ExamRulesStore {
        val f = examRulesFile(context)
        if (!f.exists()) return ExamRulesStore()
        return try {
            json.decodeFromString(ExamRulesStore.serializer(), f.readText())
        } catch (e: Exception) {
            ExamRulesStore()
        }
    }

    /** The custom marking rules for a test, or null when it has none (normal scoring). */
    fun loadExamRules(context: Context, setFile: File): ExamRules? =
        loadExamRulesStore(context).rules[setFile.name]?.takeIf { it.isActive() }

    /** Saves a test's custom rules. Rules that are all zero mean "no custom rule", so they
     *  remove the entry and the test goes back to normal scoring. */
    fun saveExamRules(context: Context, setFile: File, rules: ExamRules) {
        val current = loadExamRulesStore(context).rules
        val updated = if (rules.isActive()) current + (setFile.name to rules) else current - setFile.name
        if (updated == current) return
        writeAtomically(examRulesFile(context), json.encodeToString(ExamRulesStore(updated)))
    }

    // ---------- import / export / share ----------

    /** Writes a set to the SAF-picked destination Uri (user chooses where, e.g. Downloads/Drive). */
    fun exportToUri(context: Context, set: McqSet, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(set).toByteArray())
        }
    }

    /** Writes a folder bundle (folder name + full contents of every test in it) to the
     *  SAF-picked destination Uri -- a single portable file, not a directory. */
    fun exportFolderToUri(context: Context, folder: TestFolder, uri: Uri) {
        val bundle = buildFolderBundle(context, folder)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(bundle).toByteArray())
        }
    }

    /** Reads a set from any content:// or file:// Uri. Kept for direct single-set use. */
    fun importFromUri(context: Context, uri: Uri): McqSet {
        val text = readText(context, uri)
        return json.decodeFromString(McqSet.serializer(), text)
    }

    /** Reads any file the app can open (a single test, a folder bundle, or a file handed
     *  in via the VIEW intent filter) and works out what it is -- WITHOUT saving anything.
     *  The caller shows the contents (question counts, tests in a folder) and only calls
     *  [commitImport] once the user confirms. */
    fun previewImportFromUri(context: Context, uri: Uri): ImportPreview {
        val text = readText(context, uri)

        val setResult = runCatching { json.decodeFromString(McqSet.serializer(), text) }
        if (setResult.isSuccess) {
            return ImportPreview.SetPreview(setResult.getOrThrow())
        }

        val bundleResult = runCatching { json.decodeFromString(FolderBundle.serializer(), text) }
        if (bundleResult.isSuccess) {
            return ImportPreview.FolderPreview(bundleResult.getOrThrow())
        }

        throw IllegalStateException("Unrecognized file -- not a Testmond test or folder file")
    }

    /** Actually saves a previewed import: a single test lands in the library as usual; a folder
     *  bundle recreates the folder AND saves every test it contains into the library too, so
     *  they also show up in the Tests tab. [selectedIndices], when given, restricts a folder
     *  import to just those positions in [ImportPreview.FolderPreview.bundle]'s test list (the
     *  user's checkbox picks in the preview dialog) -- null means "all of them", used when there
     *  was no confirmation step to narrow it. */
    fun commitImport(context: Context, preview: ImportPreview, selectedIndices: Set<Int>? = null): ImportOutcome =
        when (preview) {
            is ImportPreview.SetPreview ->
                ImportOutcome.SetImported(saveToLibrary(context, preview.set))
            is ImportPreview.FolderPreview -> {
                // A test with no questions can't be opened, so it is skipped (the preview says so),
                // same as one the user left unticked.
                val savedFileNames = preview.bundle.sets.withIndex()
                    .filter { (i, set) -> set.questions.isNotEmpty() && (selectedIndices == null || i in selectedIndices) }
                    .map { (_, set) -> saveToLibrary(context, set).name }
                val folder = TestFolder(name = preview.bundle.folderName, testFileNames = savedFileNames)
                val folderFile = saveFolder(context, folder)
                ImportOutcome.FolderImported(folderFile, savedFileNames.size)
            }
        }

    /** Preview and import in one step (no confirmation). The app itself goes through
     *  [previewImportFromUri] + [commitImport] so the user confirms first. */
    fun importAnyFromUri(context: Context, uri: Uri): ImportOutcome =
        commitImport(context, previewImportFromUri(context, uri))

    private fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Could not read file")

    /** Copies a library file into cache and returns a content:// Uri suitable for ACTION_SEND,
     *  so the user can share it to a friend via any app. */
    fun shareUriFor(context: Context, file: File): Uri {
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val shareFile = File(shareDir, file.name)
        file.copyTo(shareFile, overwrite = true)
        return FileProvider.getUriForFile(context, "com.testmond.app.fileprovider", shareFile)
    }

    /** Writes a folder's full bundle into a temp file and returns a shareable content:// Uri --
     *  a folder is never shared as a directory, always as one self-contained .mcqzf file. */
    fun shareFolderUriFor(context: Context, folder: TestFolder): Uri {
        val bundle = buildFolderBundle(context, folder)
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = sanitizeFileName(folder.name).ifBlank { "folder" }
        val shareFile = File(shareDir, "$safeName.mcqzf")
        shareFile.writeText(json.encodeToString(bundle))
        return FileProvider.getUriForFile(context, "com.testmond.app.fileprovider", shareFile)
    }
}
