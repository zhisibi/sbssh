package com.sbssh.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import com.sbssh.util.AppLogger

class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("sbssh_prefs", Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(): Boolean {
        return !prefs.contains("salt")
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun saveSalt(salt: ByteArray) {
        prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
    }

    fun clearMasterPasswordState() {
        prefs.edit()
            .remove("salt")
            .remove("password_hash")
            .remove("bio_encrypted_key")
            .remove("bio_key_iv")
            .apply()
    }

    fun getSalt(): ByteArray {
        val saltStr = prefs.getString("salt", null)
            ?: throw IllegalStateException("Salt not found. Call generateSalt() first.")
        return Base64.decode(saltStr, Base64.NO_WRAP)
    }

    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Derive key bytes AND return a SQLCipher-compatible passphrase string.
     * The passphrase is a hex representation of the derived key, with unsigned byte handling.
     */
    fun deriveKeyForDb(password: String, salt: ByteArray): Pair<ByteArray, String> {
        val keyBytes = deriveKey(password, salt)
        val hexKey = keyBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return Pair(keyBytes, hexKey)
    }

    fun storeBiometricKey(keyBytes: ByteArray) {
        val keyAlias = "sbssh_biometric_key"
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
        } catch (_: Exception) { }

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        keyGen.init(builder.build())
        val biometricKey = keyGen.generateKey()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, biometricKey)
        val encrypted = cipher.doFinal(keyBytes)
        val iv = cipher.iv

        prefs.edit()
            .putString("bio_encrypted_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("bio_key_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun getBiometricCipher(): Cipher {
        val keyAlias = "sbssh_biometric_key"
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val secretKey = ks.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher
    }

    fun decryptKeyWithBiometric(cipher: Cipher): ByteArray {
        val encrypted = Base64.decode(
            prefs.getString("bio_encrypted_key", null)
                ?: throw IllegalStateException("No biometric key stored"),
            Base64.NO_WRAP
        )
        return cipher.doFinal(encrypted)
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.contains("bio_encrypted_key")
    }

    fun enableBiometric(keyBytes: ByteArray) {
        storeBiometricKey(keyBytes)
    }

    fun disableBiometric() {
        val keyAlias = "sbssh_biometric_key"
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
        } catch (_: Exception) { }
        prefs.edit()
            .remove("bio_encrypted_key")
            .remove("bio_key_iv")
            .apply()
    }

    fun isMasterPasswordSet(): Boolean {
        return prefs.contains("salt") && prefs.contains("password_hash")
    }

    fun setPasswordVerification(password: String, salt: ByteArray) {
        val verifySpec = PBEKeySpec((password + "verify").toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = Base64.encodeToString(factory.generateSecret(verifySpec).encoded, Base64.NO_WRAP)
        AppLogger.log("CRYPTO", "setPasswordVerification: pwdLen=${password.length}, saltLen=${salt.size}, hashLen=${hash.length}")
        prefs.edit().putString("password_hash", hash).apply()
    }

    fun verifyMasterPassword(password: String): Boolean {
        val salt = getSalt()
        val verifySpec = PBEKeySpec((password + "verify").toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = Base64.encodeToString(factory.generateSecret(verifySpec).encoded, Base64.NO_WRAP)
        val storedHash = prefs.getString("password_hash", null)
        AppLogger.log("CRYPTO", "verifyMasterPassword: pwdLen=${password.length}, saltLen=${salt.size}, hashLen=${hash.length}, storedHashLen=${storedHash?.length}, match=${hash == storedHash}")
        return storedHash != null && hash == storedHash
    }

    /**
     * Change master password.
     * Returns the new session key bytes for the caller to update SessionKeyHolder and re-encrypt data.
     */
    fun changeMasterPassword(oldPassword: String, newPassword: String): ByteArray {
        if (!verifyMasterPassword(oldPassword)) {
            throw IllegalArgumentException("Old password is incorrect")
        }
        if (newPassword.length < 6) {
            throw IllegalArgumentException("New password must be at least 6 characters")
        }

        // Generate new salt
        val newSalt = generateSalt()
        val newKeyBytes = deriveKey(newPassword, newSalt)

        // Store new salt and verification hash
        saveSalt(newSalt)
        setPasswordVerification(newPassword, newSalt)

        // Update biometric if enabled
        if (isBiometricEnabled()) {
            enableBiometric(newKeyBytes)
        }

        return newKeyBytes
    }
}
