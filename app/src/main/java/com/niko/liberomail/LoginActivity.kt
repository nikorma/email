package com.niko.liberomail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.data.Provider
import com.niko.liberomail.databinding.ActivityLoginBinding
import com.niko.liberomail.mail.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var store: CredentialStore

    private var selectedProvider: Provider = Provider.LIBERO
    private var providerChosenByUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = CredentialStore(this)
        if (store.isLoggedIn) { goToInbox(); return }

        applyProvider(Provider.LIBERO)

        binding.btnProvider.setOnClickListener { showProviderPicker() }
        binding.btnAdvanced.setOnClickListener { toggleAdvanced() }
        binding.btnLogin.setOnClickListener { attemptLogin() }

        // Riconoscimento automatico dal dominio digitato
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !providerChosenByUser) autoDetectProvider()
        }
    }

    private fun autoDetectProvider() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val p = Provider.forEmail(email) ?: return
        if (p.name != selectedProvider.name) applyProvider(p)
    }

    private fun applyProvider(p: Provider) {
        selectedProvider = p
        binding.btnProvider.text = p.name
        binding.etImapHost.setText(p.imapHost)
        binding.etImapPort.setText(p.imapPort.toString())
        binding.etSmtpHost.setText(p.smtpHost)
        binding.etSmtpPort.setText(p.smtpPort.toString())

        if (p.note != null) {
            binding.tvNote.text = p.note
            binding.tvNote.visibility = View.VISIBLE
        } else {
            binding.tvNote.visibility = View.GONE
        }

        // Per "Altro" apriamo subito i parametri, vanno compilati a mano
        if (p.imapHost.isBlank()) showAdvanced(true)
    }

    private fun showProviderPicker() {
        val names = Provider.ALL.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Scegli il servizio email")
            .setItems(names) { _, which ->
                providerChosenByUser = true
                applyProvider(Provider.ALL[which])
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun toggleAdvanced() {
        showAdvanced(binding.advancedBox.visibility != View.VISIBLE)
    }

    private fun showAdvanced(show: Boolean) {
        binding.advancedBox.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAdvanced.text =
            if (show) "Nascondi parametri server" else "Parametri server (avanzate)"
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val imapHost = binding.etImapHost.text?.toString()?.trim().orEmpty()
        val imapPort = binding.etImapPort.text?.toString()?.trim()?.toIntOrNull() ?: 993
        val smtpHost = binding.etSmtpHost.text?.toString()?.trim().orEmpty()
        val smtpPort = binding.etSmtpPort.text?.toString()?.trim()?.toIntOrNull() ?: 465

        if (email.isBlank() || password.isBlank()) {
            showError("Inserisci email e password"); return
        }
        if (!email.contains("@")) {
            showError("Inserisci l'indirizzo email completo"); return
        }
        if (imapHost.isBlank()) {
            showAdvanced(true)
            showError("Inserisci il server IMAP del tuo provider"); return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(imapHost, imapPort, email, password, smtpHost, smtpPort)
                        .testConnection()
                }
            }
            setLoading(false)

            result.onSuccess {
                store.email = email
                store.password = password
                store.imapHost = imapHost
                store.imapPort = imapPort
                store.smtpHost = smtpHost.ifBlank { CredentialStore.DEFAULT_SMTP_HOST }
                store.smtpPort = smtpPort
                goToInbox()
            }.onFailure { e ->
                val extra = selectedProvider.note?.let { "\n\n$it" } ?: ""
                showError("Accesso non riuscito: ${e.message ?: "verifica i dati"}$extra")
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
