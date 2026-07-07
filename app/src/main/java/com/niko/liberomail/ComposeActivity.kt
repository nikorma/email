package com.niko.liberomail

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niko.liberomail.data.ContactStore
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.databinding.ActivityComposeBinding
import com.niko.liberomail.mail.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding
    private lateinit var store: CredentialStore

    private val pickPhoneEmail =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val uri = res.data?.data ?: return@registerForActivityResult
                try {
                    contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                        null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) addRecipient(c.getString(0))
                    }
                } catch (_: Exception) {
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuovo messaggio"

        store = CredentialStore(this)

        binding.btnRubrica.setOnClickListener { pickFromRubrica() }
        binding.btnPhone.setOnClickListener { pickFromPhone() }
    }

    private fun addRecipient(email: String?) {
        if (email.isNullOrBlank()) return
        val cur = binding.etTo.text?.toString()?.trim().orEmpty()
        val newText = if (cur.isEmpty()) email else "$cur, $email"
        binding.etTo.setText(newText)
        binding.etTo.setSelection(binding.etTo.text?.length ?: 0)
    }

    private fun pickFromRubrica() {
        val contacts = ContactStore(this).getContacts()
        if (contacts.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("La rubrica è vuota. Aggiungi contatti dalla schermata Rubrica.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = contacts.map {
            if (it.name.isNotBlank()) "${it.name} <${it.email}>" else it.email
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Rubrica")
            .setItems(labels) { _, which -> addRecipient(contacts[which].email) }
            .show()
    }

    private fun pickFromPhone() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
        }
        try {
            pickPhoneEmail.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Nessuna app contatti disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_compose, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_send -> { send(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun send() {
        val to = binding.etTo.text?.toString()?.trim().orEmpty()
        val subject = binding.etSubject.text?.toString()?.trim().orEmpty()
        val body = binding.etBody.text?.toString().orEmpty()

        if (to.isEmpty()) {
            binding.etTo.error = "Inserisci almeno un destinatario"
            return
        }

        setSending(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(
                        store.imapHost, store.imapPort, store.email, store.password,
                        store.smtpHost, store.smtpPort
                    ).sendEmail(to, subject, body)
                }
            }
            setSending(false)
            result.onSuccess {
                Toast.makeText(this@ComposeActivity, "Email inviata", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { e ->
                AlertDialog.Builder(this@ComposeActivity)
                    .setTitle("Invio non riuscito")
                    .setMessage(e.message ?: "Errore sconosciuto")
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun setSending(sending: Boolean) {
        binding.progress.visibility = if (sending) View.VISIBLE else View.GONE
        binding.etTo.isEnabled = !sending
        binding.etSubject.isEnabled = !sending
        binding.etBody.isEnabled = !sending
    }
}
