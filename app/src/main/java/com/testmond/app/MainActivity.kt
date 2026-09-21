package com.testmond.app

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
}

/** Holds the folder currently being viewed in FolderScreen. */
object ActiveFolderHolder {
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
        result.value = null
    }
}

class MainActivity : ComponentActivity() {

    private var pendingImportUri: Uri? = null
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        handleIncomingIntent()

        setContent {
            navController = rememberNavController()
            val darkTheme = when (AppSettings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TestmondTheme(darkTheme = darkTheme) {
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
            navController.navigate("home")
        }
    }

    private fun handleIncomingIntent() {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            pendingImportUri = intent.data
        }
    }
}
