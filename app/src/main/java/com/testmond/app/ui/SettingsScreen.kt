package com.testmond.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.data.AppSettings
import com.testmond.app.model.QuizMode
import com.testmond.app.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SettingsOption(
                title = "System default",
                subtitle = "Follows your device's light/dark setting",
                selected = AppSettings.themeMode == ThemeMode.SYSTEM,
                onClick = { AppSettings.setThemeMode(context, ThemeMode.SYSTEM) }
            )
            SettingsOption(
                title = "Light",
                subtitle = null,
                selected = AppSettings.themeMode == ThemeMode.LIGHT,
                onClick = { AppSettings.setThemeMode(context, ThemeMode.LIGHT) }
            )
            SettingsOption(
                title = "Dark",
                subtitle = null,
                selected = AppSettings.themeMode == ThemeMode.DARK,
                onClick = { AppSettings.setThemeMode(context, ThemeMode.DARK) }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("Test mode", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Applies to every test in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SettingsOption(
                title = "Quiz mode",
                subtitle = "See whether each answer is correct right after you submit it",
                selected = AppSettings.quizMode == QuizMode.QUIZ,
                onClick = { AppSettings.setQuizMode(context, QuizMode.QUIZ) }
            )
            SettingsOption(
                title = "Practice mode",
                subtitle = "No feedback during the test -- see everything on the review page after Finish",
                selected = AppSettings.quizMode == QuizMode.PRACTICE,
                onClick = { AppSettings.setQuizMode(context, QuizMode.PRACTICE) }
            )
        }
    }
}

@Composable
private fun SettingsOption(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
