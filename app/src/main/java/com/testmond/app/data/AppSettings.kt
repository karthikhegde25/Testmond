package com.testmond.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.testmond.app.model.QuizMode
import com.testmond.app.model.ThemeMode

/** App-wide settings: theme and quiz/practice mode, applying to every test set.
 *  Loaded once at app start and kept as Compose state so any screen reading it
 *  recomposes immediately when Settings changes it. */
object AppSettings {
    private const val PREFS_NAME = "testmond_settings"
    // Old name from before the app was renamed. Kept only so existing users' saved
    // theme / quiz-mode choice carries over once instead of silently resetting.
    private const val LEGACY_PREFS_NAME = "testanium_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_QUIZ_MODE = "quiz_mode"

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var quizMode by mutableStateOf(QuizMode.QUIZ)
        private set

    /** One-time copy of any values saved under the legacy prefs name into the current one
     *  (never overwriting a value already saved under the new name), then empties the old file. */
    private fun migrateLegacyPrefs(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val current = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = current.edit()
        for ((key, value) in legacy.all) {
            if (!current.contains(key) && value is String) editor.putString(key, value)
        }
        editor.apply()
        legacy.edit().clear().apply()
    }

    fun load(context: Context) {
        migrateLegacyPrefs(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        quizMode = runCatching {
            QuizMode.valueOf(prefs.getString(KEY_QUIZ_MODE, QuizMode.QUIZ.name)!!)
        }.getOrDefault(QuizMode.QUIZ)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setQuizMode(context: Context, mode: QuizMode) {
        quizMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUIZ_MODE, mode.name).apply()
    }
}
