package com.sbssh.ui.vpslist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sbssh.data.db.VpsEntity
import com.sbssh.ui.SimpleLoadingText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpsListScreen(
    onAddVps: () -> Unit,
    onEditVps: (Long) -> Unit,
    onConnectTerminal: (Long) -> Unit,
    onConnectSftp: (Long) -> Unit
) {
    val viewModel: VpsListViewModel = viewModel(factory = VpsListViewModel.Factory())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Servers") },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Language") },
                                onClick = { showSettingsMenu = false },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Font Size") },
                                onClick = { showSettingsMenu = false },
                                leadingIcon = { Icon(Icons.Default.FormatSize, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Server Backup") },
                                onClick = { showSettingsMenu = false },
                                leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Server Restore") },
                                onClick = { showSettingsMenu = false },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = { showSettingsMenu = false },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVps) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                SimpleLoadingText("Loading servers...")
            }
        } else if (uiState.vpsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖥️", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No servers yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap + to add your first server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.vpsList, key = { it.id }) { vps ->
                    VpsCard(
                        vps = vps,
                        onEdit = { onEditVps(vps.id) },
                        onDelete = { viewModel.confirmDelete(vps.id) },
                        onConnectTerminal = { onConnectTerminal(vps.id) },
                        onConnectSftp = { onConnectSftp(vps.id) }
                    )
                }
            }
        }
    }

    uiState.showDeleteDialog?.let { vpsId ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete this server?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteVps(vpsId) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VpsCard(
    vps: VpsEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnectTerminal: () -> Unit,
    onConnectSftp: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🖥️  ${vps.alias}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = vps.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (vps.authType == "KEY") "SSH Key Authentication" else "Password Authentication",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnectTerminal,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SSH")
                }
                OutlinedButton(
                    onClick = onConnectSftp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SFTP")
                }
            }
        }
    }
}
