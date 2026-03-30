package com.sbssh.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.SbsshApp
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val biometricAvailable: Boolean = false
)

class MasterPasswordViewModel(
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val isFirst = cryptoManager.isFirstLaunch()
        _uiState.value = _uiState.value.copy(
            isFirstLaunch = isFirst,
            biometricAvailable = cryptoManager.isBiometricEnabled()
        )
    }

    fun setPassword(password: String, confirmPassword: String) {
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val salt = cryptoManager.generateSalt()
                val keyBytes = cryptoManager.deriveKey(password, salt)
                cryptoManager.setPasswordVerification(password, salt)
                initDatabase(keyBytes)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to set password"
                )
            }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (cryptoManager.verifyMasterPassword(password)) {
                    val salt = cryptoManager.getSalt()
                    val keyBytes = cryptoManager.deriveKey(password, salt)
                    initDatabase(keyBytes)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Incorrect password"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to unlock"
                )
            }
        }
    }

    fun unlockWithBiometric(decryptedKey: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                initDatabase(decryptedKey)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Biometric unlock failed"
                )
            }
        }
    }

    private fun initDatabase(keyBytes: ByteArray) {
        AppDatabase.getInstance(SbsshApp.instance, keyBytes)
    }

    fun enableBiometric(password: String) {
        viewModelScope.launch {
            try {
                val salt = cryptoManager.getSalt()
                val keyBytes = cryptoManager.deriveKey(password, salt)
                cryptoManager.enableBiometric(keyBytes)
                _uiState.value = _uiState.value.copy(biometricAvailable = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(private val cryptoManager: CryptoManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MasterPasswordViewModel(cryptoManager) as T
        }
    }
}
