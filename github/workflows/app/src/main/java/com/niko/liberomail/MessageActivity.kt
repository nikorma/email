package com.niko.liberomail

import android.os.Bundle
import android.text.Html
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.databinding.ActivityMessageBinding
import com.niko.liberomail.mail.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageBinding
    private lateinit var store: CredentialStore
    private var uid: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = CredentialStore(this)
        uid = intent.getLongExtra(EXTRA_UID, -1L)
        if (uid < 0) { finish(); return }

        loadBody()
    }

    private fun loadBody() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(
                        store.imapHost, store.imapPort, store.email, store.password
                    ).fetchBody(uid)
                }
            }
            binding.progress.visibility = View.GONE

            result.onSuccess { msg ->
                if (msg == null) {
                    binding.tvBody.text = "Messaggio non trovato."
                    return@onSuccess
                }
                binding.tvSubject.text = msg.subject
                binding.tvFrom.text = msg.from
                binding.tvDate.text = if (msg.dateMillis > 0) {
                    DateUtils.formatDateTime(
                        this@MessageActivity,
                        msg.dateMillis,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
                    )
                } else ""
                binding.tvBody.text = if (msg.isHtml) {
                    Html.fromHtml(msg.body, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    msg.body.ifBlank { "(messaggio senza testo)" }
                }
            }.onFailure { e ->
                binding.tvBody.text = "Errore: ${e.message ?: "impossibile leggere il messaggio"}"
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_message, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_delete -> { confirmDelete(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminare il messaggio?")
            .setMessage("Il messaggio verrà spostato nel Cestino di Libero.")
            .setPositiveButton("Elimina") { _, _ -> deleteMessage() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteMessage() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(
                        store.imapHost, store.imapPort, store.email, store.password
                    ).deleteMessage(uid)
                }
            }
            binding.progress.visibility = View.GONE

            result.onSuccess { ok ->
                if (ok) {
                    finish() // InboxActivity ricaricherà la lista in onResume
                } else {
                    AlertDialog.Builder(this@MessageActivity)
                        .setMessage("Impossibile eliminare il messaggio.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.onFailure { e ->
                AlertDialog.Builder(this@MessageActivity)
                    .setMessage("Errore durante l'eliminazione: ${e.message ?: ""}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    companion object {
        const val EXTRA_UID = "extra_uid"
    }
}
