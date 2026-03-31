package com.sbssh.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(context)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreServers(it) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.success) {
        uiState.success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Biometric toggle
            SettingsCard(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Login",
                subtitle = if (uiState.biometricEnabled) "Enabled" else "Disabled",
                onClick = {
                    if (uiState.biometricEnabled) {
                        viewModel.toggleBiometric()
                    } else {
                        // Show dialog to enter password
                        viewModel.toggleBiometric()
                    }
                }
            ) {
                Switch(
                    checked = uiState.biometricEnabled,
                    onCheckedChange = {
                        if (it) {
                            viewModel.toggleBiometric()
                        } else {
                            viewModel.toggleBiometric()
                        }
                    }
                )
            }

            // Language switch
            SettingsCard(
                icon = Icons.Default.Language,
                title = if (uiState.language == "zh") "语言" else "Language",
                subtitle = if (uiState.language == "zh") "中文" else "English",
                onClick = { viewModel.showLanguageDialog() }
            )

            // Font size
            SettingsCard(
                icon = Icons.Default.FormatSize,
                title = if (uiState.language == "zh") "字体大小" else "Font Size",
                subtitle = when (uiState.fontSize) {
                    "small" -> if (uiState.language == "zh") "小" else "Small"
                    "medium" -> if (uiState.language == "zh") "中" else "Medium"
                    "large" -> if (uiState.language == "zh") "大" else "Large"
                    else -> "Medium"
                },
                onClick = { viewModel.showFontSizeDialog() }
            )

            // Server backup
            SettingsCard(
                icon = Icons.Default.Backup,
                title = if (uiState.language == "zh") "服务器备份" else "Server Backup",
                subtitle = if (uiState.language == "zh") "导出到 JSON 文件" else "Export to JSON file",
                onClick = { viewModel.backupServers() }
            )

            // Server restore
            SettingsCard(
                icon = Icons.Default.Restore,
                title = if (uiState.language == "zh") "服务器恢复" else "Server Restore",
                subtitle = if (uiState.language == "zh") "从 JSON 文件导入" else "Import from JSON file",
                onClick = { restoreLauncher.launch(arrayOf("application/json")) }
            )

            // About
            SettingsCard(
                icon = Icons.Default.Info,
                title = if (uiState.language == "zh") "关于" else "About",
                subtitle = "SbSSH v1.0",
                onClick = { viewModel.showAbout() }
            )
        }
    }

    // Language dialog
    if (uiState.showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLanguageDialog() },
            title = { Text("Language / 语言") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("中文") },
                        leadingContent = { if (uiState.language == "zh") Icon(Icons.Default.Check, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.setLanguage("zh") }
                    )
                    ListItem(
                        headlineContent = { Text("English") },
                        leadingContent = { if (uiState.language == "en") Icon(Icons.Default.Check, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.setLanguage("en") }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLanguageDialog() }) { Text("Cancel") }
            }
        )
    }

    // Font size dialog
    if (uiState.showFontSizeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFontSizeDialog() },
            title = { Text(if (uiState.language == "zh") "字体大小" else "Font Size") },
            text = {
                Column {
                    val sizes = listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
                    for ((key, label) in sizes) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    when (key) {
                                        "small" -> if (uiState.language == "zh") "小" else "Small"
                                        "medium" -> if (uiState.language == "zh") "中" else "Medium"
                                        "large" -> if (uiState.language == "zh") "大" else "Large"
                                        else -> label
                                    }
                                )
                            },
                            leadingContent = { if (uiState.fontSize == key) Icon(Icons.Default.Check, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.setFontSize(key) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFontSizeDialog() }) { Text("Cancel") }
            }
        )
    }

    // About dialog
    if (uiState.showAbout) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAbout() },
            title = { Text("About SbSSH") },
            text = {
                Column {
                    Text("SbSSH v1.0", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A secure Android SSH/SFTP client with local encryption.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Features:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("• SSH Terminal (password/key auth)", style = MaterialTheme.typography.bodySmall)
                    Text("• SFTP File Manager", style = MaterialTheme.typography.bodySmall)
                    Text("• Local AES-GCM encryption", style = MaterialTheme.typography.bodySmall)
                    Text("• Biometric unlock", style = MaterialTheme.typography.bodySmall)
                    Text("• Server backup/restore", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("© 2026 sbssh", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAbout() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
