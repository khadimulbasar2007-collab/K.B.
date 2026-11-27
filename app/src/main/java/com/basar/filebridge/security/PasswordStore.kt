package com.basar.filebridge.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PasswordStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "filebridge_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun get(): String? = prefs.getString(KEY_PASSWORD, null)

    fun set(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val KEY_PASSWORD = "shared_password"
    }
}
