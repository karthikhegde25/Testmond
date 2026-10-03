package com.testmond.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.testmond.app.safePopBackStack
import com.testmond.app.R

private const val GITHUB_URL = "https://github.com/karthikhegde25/Testmond"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.en.html"
private const val KATEX_URL = "https://katex.org"
private const val KATEX_LICENSE_ASSET = "katex/KATEX_LICENSE.txt"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavHostController) {
    val context = LocalContext.current
    var showKatexLicense by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                // The screen is taller than a phone once the logo is added, so it scrolls.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Testmond logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Testmond",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Developer: Karthik Hegde",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Version 1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Testmond is a free, open-source, offline-first app for building and taking your " +
                "own tests. Paste in multiple-choice or fill-in-the-blank questions -- with a " +
                "second paste format for when your questions and answer key come from separate " +
                "sources -- and Testmond turns them into a study set in seconds, no internet " +
                "required at any point, even for rendering mathematical notation.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Organize sets into folders, attach images to individual questions, and choose " +
                "between Quiz mode (instant right/wrong feedback) or Practice mode (everything " +
                "revealed only on the review page after you finish). Every test tracks your full " +
                "attempt history and score, and picks up exactly where you left off if you step " +
                "away mid-test. Sets and folders are just self-contained files, so sharing one " +
                "with a friend -- images, LaTeX, and all -- is as simple as sending it through any " +
                "app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "View source on GitHub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("License", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Testmond is free software, licensed under the GNU General Public License " +
                "v3.0 (GPL-3.0). You're free to use, study, modify, and redistribute it -- " +
                "including any modified version -- as long as that version is also released " +
                "under the GPL and its source code stays available. There is no warranty of " +
                "any kind.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Read the full GPL-3.0 license",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GPL_URL)))
                }
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Open source licenses", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Testmond uses the following open-source software:",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "KaTeX",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "The math typesetting library that displays LaTeX formulas in questions, " +
                "options, answers and solutions. It is bundled entirely offline -- " +
                "including its stylesheet and fonts, all covered by the same license.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Copyright (c) 2013-2020 Khan Academy and other contributors. " +
                "MIT License.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "View license text",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showKatexLicense = true }
                )
                Text(
                    "katex.org",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KATEX_URL)))
                    }
                )
            }
        }
    }

    if (showKatexLicense) {
        // The license text is bundled with the app, so this works with no network.
        val licenseText = remember {
            runCatching {
                context.assets.open(KATEX_LICENSE_ASSET).bufferedReader().use { it.readText() }
            }.getOrDefault("The KaTeX license text could not be loaded. See katex.org.")
        }
        AlertDialog(
            onDismissRequest = { showKatexLicense = false },
            title = { Text("KaTeX license") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showKatexLicense = false }) { Text("Close") }
            }
        )
    }
}
