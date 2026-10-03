package com.testmond.app

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.testmond.app.data.AppSettings
import com.testmond.app.model.ExamAttempt
import com.testmond.app.model.McqSet
import com.testmond.app.model.ThemeMode
import com.testmond.app.ui.AboutScreen
import com.testmond.app.ui.AddSolutionScreen
import com.testmond.app.ui.BookmarksScreen
import com.testmond.app.ui.CreateScreen
import com.testmond.app.ui.EditSolutionScreen
import com.testmond.app.ui.EditTestScreen
import com.testmond.app.ui.ExamQuizScreen
import com.testmond.app.ui.ExamReviewScreen
import com.testmond.app.ui.FolderScreen
import com.testmond.app.ui.HomeScreen
import com.testmond.app.ui.HowToUseScreen
import com.testmond.app.ui.QuizScreen
import com.testmond.app.ui.SettingsScreen
import com.testmond.app.ui.theme.TestmondTheme
import java.io.File

/** Holds the currently active set (and its backing file) so it survives navigation
 *  between Quiz/Result, and so progress/attempt history can be keyed to the right file. */
object ActiveSetHolder {
    val current = mutableStateOf<McqSet?>(null)
    val currentFile = mutableStateOf<File?>(null)
    /** Set just before navigating to "quiz" from a bookmarked question (see BookmarksScreen) so
     *  the quiz opens straight to that question instead of wherever progress last left off.
     *  QuizScreen reads and clears this once on entry. */
    val jumpToIndex = mutableStateOf<Int?>(null)
    /** Set just before navigating to "quiz" from a completed test's "View" option so QuizScreen
     *  opens straight onto that finished attempt's results/review instead of starting the test.
     *  Read and cleared once on entry, like [jumpToIndex]. */
    val viewResult = mutableStateOf(false)
}

/** Holds the folder currently being viewed in FolderScreen. */
object ActiveFolderHolder {
    val currentFile = mutableStateOf<File?>(null)
}

/** Holds which test's bookmarks are being viewed in BookmarksScreen. */
object ActiveBookmarksFileHolder {
    val currentFile = mutableStateOf<File?>(null)
}

/** Live state of the exam currently being taken. It lives here instead of in the screen's own
 *  `remember` state so a rotation (activity recreation) doesn't wipe the exam mid-way, and the
 *  countdown is anchored to a fixed end time so it keeps running correctly either way.
 *  Deliberately never written to disk: exams have no resume -- leaving loses the progress. */
object ExamSessionHolder {
    var durationMillis: Long = 0L
    /** SystemClock.elapsedRealtime() value at which the time runs out. */
    var endAtElapsedMillis: Long = 0L
    val current = mutableStateOf(0)
    val answers = mutableStateListOf<String?>()
    val drafts = mutableStateListOf<String?>()
    /** Which questions are flagged "mark for review" -- index-parallel to [answers]. Purely a
     *  navigation aid for the person taking the exam; never scored, never saved to the attempt. */
    val markedForReview = mutableStateListOf<Boolean>()
    /** Null while the exam is running; set once it's finished or timed out. */
    val result = mutableStateOf<ExamAttempt?>(null)

    fun start(questionCount: Int, durationMillis: Long) {
        this.durationMillis = durationMillis
        endAtElapsedMillis = SystemClock.elapsedRealtime() + durationMillis
        current.value = 0
        answers.clear()
        answers.addAll(List(questionCount) { null })
        drafts.clear()
        drafts.addAll(List(questionCount) { null })
        markedForReview.clear()
        markedForReview.addAll(List(questionCount) { false })
        result.value = null
    }
}

class MainActivity : ComponentActivity() {

    // Compose state (not a plain field): a file opened while the app is already running has to be
    // noticed by the home screen even if it was already on screen.
    private var pendingImportUri by mutableStateOf<Uri?>(null)
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        // Only on a fresh start: after a rotation the activity is recreated with the same intent,
        // which used to bring the "Import this test?" dialog back for a file already handled.
        if (savedInstanceState == null) handleIncomingIntent()

        setContent {
            navController = rememberNavController()
            val darkTheme = when (AppSettings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TestmondTheme(darkTheme = darkTheme, themeColor = AppSettings.themeColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                navController = navController,
                                pendingImportUri = pendingImportUri,
                                onImportHandled = { pendingImportUri = null }
                            )
                        }
                        composable("create") {
                            CreateScreen(navController = navController)
                        }
                        composable("quiz") {
                            QuizScreen(navController = navController)
                        }
                        composable("examQuiz") {
                            ExamQuizScreen(navController = navController)
                        }
                        composable("examReview") {
                            ExamReviewScreen(navController = navController)
                        }
                        composable("folderDetail") {
                            FolderScreen(navController = navController)
                        }
                        composable("bookmarks") {
                            BookmarksScreen(navController = navController)
                        }
                        composable("editTest") {
                            EditTestScreen(navController = navController)
                        }
                        composable("addSolution") {
                            AddSolutionScreen(navController = navController)
                        }
                        composable("editSolution") {
                            EditSolutionScreen(navController = navController)
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController)
                        }
                        composable("howto") {
                            HowToUseScreen(navController = navController)
                        }
                        composable("about") {
                            AboutScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent()
        if (::navController.isInitialized && pendingImportUri != null) {
            // Go back to the existing home screen (instead of stacking another copy of it).
            navController.popBackStack("home", false)
        }
    }

    private fun handleIncomingIntent() {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            pendingImportUri = intent.data
        }
    }
}
