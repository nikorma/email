package com.niko.liberomail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.databinding.ActivityLoginBinding
import com.niko.liberomail.mail.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var store: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = CredentialStore(this)

        // Se già loggato, vai direttamente alla Posta in arrivo
        if (store.isLoggedIn) {
            goToInbox()
            return
        }

        binding.etImapHost.setText(CredentialStore.DEFAULT_IMAP_HOST)
        binding.etImapPort.setText(CredentialStore.DEFAULT_IMAP_PORT.toString())

        binding.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val host = binding.etImapHost.text?.toString()?.trim()
            .takeUnless { it.isNullOrBlank() } ?: CredentialStore.DEFAULT_IMAP_HOST
        val port = binding.etImapPort.text?.toString()?.trim()?.toIntOrNull()
            ?: CredentialStore.DEFAULT_IMAP_PORT

        if (email.isBlank() || password.isBlank()) {
            showError("Inserisci email e password")
            return
        }
        if (!email.contains("@")) {
            showError("Inserisci l'indirizzo email completo (es. nome@libero.it)")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(host, port, email, password).testConnection()
                }
            }
            setLoading(false)

            result.onSuccess {
                store.email = email
                store.password = password
                store.imapHost = host
                store.imapPort = port
                goToInbox()
            }.onFailure { e ->
                showError("Accesso non riuscito: ${e.message ?: "verifica i dati"}")
            }
        }
    }

    private fun goToInbox() {
        startActivity(Intent(this, InboxActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }
}
