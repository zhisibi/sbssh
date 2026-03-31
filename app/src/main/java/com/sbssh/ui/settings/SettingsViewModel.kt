package com.sbssh.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import com.sbssh.util.BiometricHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val language: String = "zh",
    val fontSize: String = "medium",
    val showAbout: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showFontSizeDialog: Boolean = false,
    val showBiometricPasswordDialog: Boolean = false,
    val showChangePasswordDialog: Boolean = false,
    val showCloudSyncDialog: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val pendingBackupJson: String? = null,
    val pendingBackupFileName: String? = null,
    val shouldRestart: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val cloudSyncUrl: String = "",
    val cloudSyncUsername: String = ""
)

class SettingsViewModel(
    private val context: Context,
    private val activity: AppCompatActivity? = null
) : ViewModel() {

    private val cryptoManager = CryptoManager(context)
    private val fieldCrypto = FieldCryptoManager()
    private val settingsManager = SettingsManager.getInstance(context)
    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val biometricAvailable = activity?.let { BiometricHelper.isBiometricAvailable(it) } ?: false
        val settings = settingsManager.settings.value

        _uiState.value = _uiState.value.copy(
            biometricEnabled = cryptoManager.isBiometricEnabled(),
            biometricAvailable = biometricAvailable,
            language = settings.language,
            fontSize = settings.fontSize,
            cloudSyncEnabled = settings.cloudSyncEnabled,
            cloudSyncUrl = settings.cloudSyncUrl,
            cloudSyncUsername = settings.cloudSyncUsername
        )
    }

    // ========== Biometric ==========
    fun showBiometricPasswordDialog() {
        _uiState.value = _uiState.value.copy(showBiometricPasswordDialog = true)
    }

    fun dismissBiometricPasswordDialog() {
        _uiState.value = _uiState.value.copy(showBiometricPasswordDialog = false)
    }

    fun toggleBiometric(password: String) {
        val current = _uiState.value.biometricEnabled
        if (current) {
            // Disable
            cryptoManager.disableBiometric()
            _uiState.value = _uiState.value.copy(
                biometricEnabled = false,
                showBiometricPasswordDialog = false,
                success = "Biometric login disabled"
            )
            return
        }

        // Enable: verify password first
        if (!cryptoManager.verifyMasterPassword(password)) {
            _uiState.value = _uiState.value.copy(
                error = "Password incorrect",
                showBiometricPasswordDialog = false
            )
            return
        }

        if (activity == null || !BiometricHelper.isBiometricAvailable(activity)) {
            _uiState.value = _uiState.value.copy(
                error = "Biometric not available on this device",
                showBiometricPasswordDialog = false
            )
            return
        }

        try {
            val salt = cryptoManager.getSalt()
            val keyBytes = cryptoManager.deriveKey(password, salt)
            cryptoManager.enableBiometric(keyBytes)
            _uiState.value = _uiState.value.copy(
                biometricEnabled = true,
                showBiometricPasswordDialog = false,
                success = "Biometric login enabled"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Enable failed: ${e.message}",
                showBiometricPasswordDialog = false
            )
        }
    }

    // ========== Language ==========
    fun showLanguageDialog() {
        _uiState.value = _uiState.value.copy(showLanguageDialog = true)
    }

    fun dismissLanguageDialog() {
        _uiState.value = _uiState.value.copy(showLanguageDialog = false)
    }

    fun setLanguage(lang: String) {
        settingsManager.setLanguage(lang)
        _uiState.value = _uiState.value.copy(
            language = lang,
            showLanguageDialog = false,
            shouldRestart = true
        )
    }

    fun onRestartConsumed() {
        _uiState.value = _uiState.value.copy(shouldRestart = false)
    }

    // ========== Font Size ==========
    fun showFontSizeDialog() {
        _uiState.value = _uiState.value.copy(showFontSizeDialog = true)
    }

    fun dismissFontSizeDialog() {
        _uiState.value = _uiState.value.copy(showFontSizeDialog = false)
    }

    fun setFontSize(size: String) {
        settingsManager.setFontSize(size)
        _uiState.value = _uiState.value.copy(
            fontSize = size,
            showFontSizeDialog = false,
            success = "Font size updated"
        )
    }

    // ========== Backup ==========
    fun prepareBackup() {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(error = "Database not initialized")
            return
        }
        viewModelScope.launch {
            try {
                val vpsList = dao!!.getAllVpsAsList()
                if (vpsList.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "No servers to backup")
                    return@launch
                }
                val key = SessionKeyHolder.get()
                val crypto = FieldCryptoManager()
                val backupList = vpsList.map { v ->
                    mapOf(
                        "alias" to v.alias,
                        "host" to v.host,
                        "port" to v.port,
                        "username" to v.username,
                        "authType" to v.authType,
                        "encryptedPassword" to v.encryptedPassword,
                        "encryptedKeyContent" to v.encryptedKeyContent,
                        "encryptedKeyPassphrase" to v.encryptedKeyPassphrase,
                        "createdAt" to v.createdAt,
                        "updatedAt" to v.updatedAt
                    )
                }
                val json = gson.toJson(backupList)
                val encrypted = crypto.encrypt(json, key) ?: json
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "sbssh_backup_${sdf.format(Date())}.enc"

                _uiState.value = _uiState.value.copy(
                    pendingBackupJson = encrypted,
                    pendingBackupFileName = fileName
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Backup prepare failed: ${e.message}")
            }
        }
    }

    fun saveBackupToUri(uri: Uri) {
        val data = _uiState.value.pendingBackupJson ?: return
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(data.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            _uiState.value = _uiState.value.copy(
                pendingBackupJson = null,
                pendingBackupFileName = null,
                success = "Backup saved successfully"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Save failed: ${e.message}")
        }
    }

    fun restoreServers(uri: Uri) {
        viewModelScope.launch {
            try {
                if (dao == null) {
                    _uiState.value = _uiState.value.copy(error = "Database not initialized")
                    return@launch
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception("Failed to read backup file")

                val key = SessionKeyHolder.get()
                val crypto = FieldCryptoManager()
                val json = try {
                    crypto.decrypt(content, key) ?: content
                } catch (_: Exception) {
                    content
                }

                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val backupList: List<Map<String, Any>> = gson.fromJson(json, type)

                val now = System.currentTimeMillis()
                var count = 0
                for (item in backupList) {
                    val vps = VpsEntity(
                        alias = item["alias"] as? String ?: "Unknown",
                        host = item["host"] as? String ?: "0.0.0.0",
                        port = (item["port"] as? Double)?.toInt() ?: 22,
                        username = item["username"] as? String ?: "root",
                        authType = item["authType"] as? String ?: "PASSWORD",
                        encryptedPassword = item["encryptedPassword"] as? String,
                        encryptedKeyContent = item["encryptedKeyContent"] as? String,
                        encryptedKeyPassphrase = item["encryptedKeyPassphrase"] as? String,
                        createdAt = (item["createdAt"] as? Double)?.toLong() ?: now,
                        updatedAt = (item["updatedAt"] as? Double)?.toLong() ?: now
                    )
                    dao!!.insertVps(vps)
                    count++
                }
                _uiState.value = _uiState.value.copy(success = "Restored $count server(s)")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Restore failed: ${e.message}")
            }
        }
    }

    // ========== Cloud Sync ==========
    fun showCloudSyncDialog() {
        _uiState.value = _uiState.value.copy(showCloudSyncDialog = true)
    }

    fun dismissCloudSyncDialog() {
        _uiState.value = _uiState.value.copy(showCloudSyncDialog = false)
    }

    fun saveCloudSync(enabled: Boolean, url: String, username: String) {
        settingsManager.setCloudSync(enabled, url, username)
        _uiState.value = _uiState.value.copy(
            cloudSyncEnabled = enabled,
            cloudSyncUrl = url,
            cloudSyncUsername = username,
            showCloudSyncDialog = false,
            success = if (enabled) "Cloud sync configured (coming soon)" else "Cloud sync disabled"
        )
    }

    // ========== Change Password ==========
    fun showChangePasswordDialog() {
        _uiState.value = _uiState.value.copy(showChangePasswordDialog = true)
    }

    fun dismissChangePasswordDialog() {
        _uiState.value = _uiState.value.copy(showChangePasswordDialog = false)
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Passwords do not match")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "New password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            try {
                val newKeyBytes = cryptoManager.changeMasterPassword(oldPassword, newPassword)
                // Re-encrypt all VPS data with new key
                if (dao != null) {
                    val vpsList = dao!!.getAllVpsAsList()
                    val oldKey = SessionKeyHolder.get()
                    for (vps in vpsList) {
                        val plainPassword = fieldCrypto.decrypt(vps.encryptedPassword, oldKey)
                        val plainKeyContent = fieldCrypto.decrypt(vps.encryptedKeyContent, oldKey)
                        val plainKeyPassphrase = fieldCrypto.decrypt(vps.encryptedKeyPassphrase, oldKey)
                        dao!!.updateVps(
                            vps.copy(
                                encryptedPassword = fieldCrypto.encrypt(plainPassword, newKeyBytes),
                                encryptedKeyContent = fieldCrypto.encrypt(plainKeyContent, newKeyBytes),
                                encryptedKeyPassphrase = fieldCrypto.encrypt(plainKeyPassphrase, newKeyBytes),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                SessionKeyHolder.set(newKeyBytes)
                _uiState.value = _uiState.value.copy(
                    showChangePasswordDialog = false,
                    success = "Password changed successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Password change failed")
            }
        }
    }

    // ========== About ==========
    fun showAbout() {
        _uiState.value = _uiState.value.copy(showAbout = true)
    }

    fun dismissAbout() {
        _uiState.value = _uiState.value.copy(showAbout = false)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }

    class Factory(
        private val context: Context,
        private val activity: AppCompatActivity? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context, activity) as T
        }
    }
}
