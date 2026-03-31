package com.sbssh.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.appcompat.app.AppCompatActivity
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(context, activity)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.saveBackupToUri(it) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreServers(it) }
    }

    // Watch for backup data readiness, then launch file chooser
    LaunchedEffect(uiState.pendingBackupFileName) {
        val fileName = uiState.pendingBackupFileName
        if (fileName != null) {
            backupLauncher.launch(fileName)
        }
    }

    // Watch for language change -> restart activity
    LaunchedEffect(uiState.shouldRestart) {
        if (uiState.shouldRestart) {
            viewModel.onRestartConsumed()
            activity?.recreate()
        }
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
            // 1. Biometric
            SettingsCard(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Login",
                subtitle = if (uiState.biometricEnabled) "Enabled" else "Disabled",
                onClick = { viewModel.showBiometricPasswordDialog() }
            ) {
                Switch(
                    checked = uiState.biometricEnabled,
                    onCheckedChange = { viewModel.showBiometricPasswordDialog() }
                )
            }

            // 2. Language
            SettingsCard(
                icon = Icons.Default.Language,
                title = if (uiState.language == "zh") "语言" else "Language",
                subtitle = if (uiState.language == "zh") "中文" else "English",
                onClick = { viewModel.showLanguageDialog() }
            )

            // 3. Font size
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

            // 4. Backup
            SettingsCard(
                icon = Icons.Default.Backup,
                title = if (uiState.language == "zh") "服务器备份" else "Server Backup",
                subtitle = if (uiState.language == "zh") "导出加密备份文件" else "Export encrypted backup",
                onClick = { viewModel.prepareBackup() }
            )

            // 5. Restore
            SettingsCard(
                icon = Icons.Default.Restore,
                title = if (uiState.language == "zh") "服务器恢复" else "Server Restore",
                subtitle = if (uiState.language == "zh") "从备份文件恢复" else "Restore from backup",
                onClick = { restoreLauncher.launch(arrayOf("*/*")) }
            )

            // 6. Change Password
            SettingsCard(
                icon = Icons.Default.Lock,
                title = if (uiState.language == "zh") "修改密码" else "Change Password",
                subtitle = if (uiState.language == "zh") "修改主密码并重新加密数据" else "Change master password & re-encrypt",
                onClick = { viewModel.showChangePasswordDialog() }
            )

            // 7. Cloud Sync (placeholder)
            SettingsCard(
                icon = Icons.Default.CloudSync,
                title = if (uiState.language == "zh") "云同步" else "Cloud Sync",
                subtitle = if (uiState.cloudSyncEnabled) {
                    if (uiState.language == "zh") "已启用" else "Enabled"
                } else {
                    if (uiState.language == "zh") "未启用（即将支持）" else "Not enabled (coming soon)"
                },
                onClick = { viewModel.showCloudSyncDialog() }
            )

            // 8. About
            SettingsCard(
                icon = Icons.Default.Info,
                title = if (uiState.language == "zh") "关于" else "About",
                subtitle = "SbSSH v1.0",
                onClick = { viewModel.showAbout() }
            )
        }
    }

    // ========== Dialogs ==========

    // Biometric password dialog
    if (uiState.showBiometricPasswordDialog) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissBiometricPasswordDialog() },
            title = {
                Text(
                    if (uiState.biometricEnabled) "Disable Biometric"
                    else "Enable Biometric"
                )
            },
            text = {
                Column {
                    if (!uiState.biometricEnabled) {
                        Text(
                            "Enter your master password to enable fingerprint login",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("Master Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.toggleBiometric(pwd) },
                    enabled = pwd.isNotEmpty()
                ) {
                    Text(if (uiState.biometricEnabled) "Disable" else "Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBiometricPasswordDialog() }) { Text("Cancel") }
            }
        )
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
            dismissButton = { TextButton(onClick = { viewModel.dismissLanguageDialog() }) { Text("Cancel") } }
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
                    for ((key, _) in sizes) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    when (key) {
                                        "small" -> if (uiState.language == "zh") "小" else "Small"
                                        "medium" -> if (uiState.language == "zh") "中" else "Medium"
                                        "large" -> if (uiState.language == "zh") "大" else "Large"
                                        else -> key
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
            dismissButton = { TextButton(onClick = { viewModel.dismissFontSizeDialog() }) { Text("Cancel") } }
        )
    }

    // Cloud sync dialog
    if (uiState.showCloudSyncDialog) {
        var enabled by remember { mutableStateOf(uiState.cloudSyncEnabled) }
        var url by remember { mutableStateOf(uiState.cloudSyncUrl) }
        var username by remember { mutableStateOf(uiState.cloudSyncUsername) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudSyncDialog() },
            title = { Text(if (uiState.language == "zh") "云同步设置" else "Cloud Sync") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (uiState.language == "zh") "启用云同步" else "Enable Cloud Sync")
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    if (enabled) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Server URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (uiState.language == "zh") "⚠️ 云同步功能即将上线" else "⚠️ Cloud sync coming soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveCloudSync(enabled, url, username) }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissCloudSyncDialog() }) { Text("Cancel") } }
        )
    }

    // Change password dialog
    if (uiState.showChangePasswordDialog) {
        var oldPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var showOld by remember { mutableStateOf(false) }
        var showNew by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissChangePasswordDialog() },
            title = { Text(if (uiState.language == "zh") "修改密码" else "Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oldPwd,
                        onValueChange = { oldPwd = it },
                        label = { Text(if (uiState.language == "zh") "旧密码" else "Old Password") },
                        singleLine = true,
                        visualTransformation = if (showOld) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showOld = !showOld }) {
                                Icon(if (showOld) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text(if (uiState.language == "zh") "新密码" else "New Password") },
                        singleLine = true,
                        visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showNew = !showNew }) {
                                Icon(if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it },
                        label = { Text(if (uiState.language == "zh") "确认新密码" else "Confirm Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.changePassword(oldPwd, newPwd, confirmPwd) },
                    enabled = oldPwd.isNotEmpty() && newPwd.isNotEmpty() && confirmPwd.isNotEmpty()
                ) {
                    Text(if (uiState.language == "zh") "确认修改" else "Change")
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissChangePasswordDialog() }) { Text("Cancel") } }
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
            confirmButton = { TextButton(onClick = { viewModel.dismissAbout() }) { Text("OK") } }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
