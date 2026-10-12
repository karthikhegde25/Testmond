package com.testmond.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.ActiveSetHolder
import com.testmond.app.data.FileStorage
import com.testmond.app.parser.SolutionParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSolutionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val set = ActiveSetHolder.current.value
    val setFile = ActiveSetHolder.currentFile.value
    if (set == null || setFile == null) {
        navController.popBackStack()
        return
    }

    var text by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add solutions") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "Paste one solution per question, using the question number for identification.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "1. or Q1. Explanation for question 1...\n2. Explanation for question 2...",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; errorMsg = null },
                placeholder = { Text("1. The powerhouse of the cell is the mitochondria because...\n2. ...") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val parsed = SolutionParser.parseSolutions(text)
                    if (parsed.isEmpty()) {
                        errorMsg = "Couldn't detect any solutions. Check the format and try again."
                        return@Button
                    }
                    val updatedQuestions = set.questions.mapIndexed { index, q ->
                        parsed[index + 1]?.let { q.copy(solution = it) } ?: q
                    }
                    val updatedSet = set.copy(questions = updatedQuestions)
                    FileStorage.updateSet(setFile, updatedSet)
                    ActiveSetHolder.current.value = updatedSet
                    navController.safePopBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save solutions")
            }
        }
    }
}
