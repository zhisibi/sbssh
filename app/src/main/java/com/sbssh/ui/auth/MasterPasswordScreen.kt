package com.sbssh.ui.auth

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.util.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterPasswordScreen(
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val cryptoManager = remember { CryptoManager(context) }
    val viewModel: MasterPasswordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = MasterPasswordViewModel.Factory(cryptoManager)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SbSSH") },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (uiState.isFirstLaunch) "Set Master Password" else "Enter Master Password",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (uiState.isFirstLaunch)
                    "Create a strong master password to encrypt your data"
                else
                    "Enter your master password to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.isFirstLaunch) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                imageVector = if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (uiState.isFirstLaunch) {
                        viewModel.setPassword(password, confirmPassword)
                    } else {
                        viewModel.unlock(password)
                    }
                },
                enabled = !uiState.isLoading && password.isNotEmpty() &&
                        (!uiState.isFirstLaunch || confirmPassword.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (uiState.isFirstLaunch) "Create" else "Unlock")
                }
            }

            if (!uiState.isFirstLaunch && uiState.biometricAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val cipher = cryptoManager.getBiometricCipher()
                        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
                        BiometricHelper.showBiometricPromptWithCrypto(
                            activity = activity,
                            cryptoObject = cryptoObject,
                            onSuccess = { co ->
                                try {
                                    val decrypted = cryptoManager.decryptKeyWithBiometric(
                                        co?.cipher ?: return@showBiometricPromptWithCrypto
                                    )
                                    viewModel.unlockWithBiometric(decrypted)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Biometric error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = { _, errString ->
                                Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                            },
                            onFailed = {
                                Toast.makeText(context, "Biometric not recognized", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }
        }
    }
}
