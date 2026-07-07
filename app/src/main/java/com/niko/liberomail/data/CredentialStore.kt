package com.niko.liberomail.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Salva in modo cifrato (EncryptedSharedPreferences) le credenziali IMAP/SMTP
 * sul dispositivo. La password non lascia mai il telefono se non per
 * autenticarsi direttamente verso i server di Libero.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "libero_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var imapHost: String
        get() = prefs.getString(KEY_IMAP_HOST, DEFAULT_IMAP_HOST) ?: DEFAULT_IMAP_HOST
        set(value) = prefs.edit().putString(KEY_IMAP_HOST, value).apply()

    var imapPort: Int
        get() = prefs.getInt(KEY_IMAP_PORT, DEFAULT_IMAP_PORT)
        set(value) = prefs.edit().putInt(KEY_IMAP_PORT, value).apply()

    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, DEFAULT_SMTP_HOST) ?: DEFAULT_SMTP_HOST
        set(value) = prefs.edit().putString(KEY_SMTP_HOST, value).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, DEFAULT_SMTP_PORT)
        set(value) = prefs.edit().putInt(KEY_SMTP_PORT, value).apply()

    /** UID più alto già notificato, per non rimostrare le stesse email. */
    var lastNotifiedUid: Long
        get() = prefs.getLong(KEY_LAST_UID, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UID, value).apply()

    val isLoggedIn: Boolean
        get() = email.isNotBlank() && password.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_IMAP_HOST = "imapmail.libero.it"
        const val DEFAULT_IMAP_PORT = 993
        const val DEFAULT_SMTP_HOST = "smtp.libero.it"
        const val DEFAULT_SMTP_PORT = 465

        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IMAP_HOST = "imap_host"
        private const val KEY_IMAP_PORT = "imap_port"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_LAST_UID = "last_uid"
    }
}
