package com.niko.liberomail

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.data.Mailbox
import com.niko.liberomail.databinding.ActivityMessageBinding
import com.niko.liberomail.mail.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageBinding
    private lateinit var store: CredentialStore
    private var uid: Long = -1L
    private var mailbox: Mailbox = Mailbox.INBOX

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        store = CredentialStore(this)
        uid = intent.getLongExtra(EXTRA_UID, -1L)
        mailbox = runCatching {
            Mailbox.valueOf(intent.getStringExtra(EXTRA_MAILBOX) ?: Mailbox.INBOX.name)
        }.getOrDefault(Mailbox.INBOX)
        if (uid < 0) { finish(); return }

        setupWebView()
        loadBody()
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = false
            loadsImagesAutomatically = true
            blockNetworkImage = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            @Suppress("DEPRECATION")
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun loadBody() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(store.imapHost, store.imapPort, store.email, store.password)
                        .fetchBody(mailbox, uid)
                }
            }
            binding.progress.visibility = View.GONE

            result.onSuccess { msg ->
                if (msg == null) {
                    binding.tvSubject.text = "Messaggio non trovato"
                    return@onSuccess
                }
                binding.tvSubject.text = msg.subject
                val label = if (msg.outgoing) "A: ${msg.contact}" else msg.contact
                binding.tvContact.text = label
                binding.tvDate.text = if (msg.dateMillis > 0) {
                    DateUtils.formatDateTime(
                        this@MessageActivity, msg.dateMillis,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
                    )
                } else ""

                binding.avatar.text = initialOf(msg.contact)
                binding.avatar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(colorFor(msg.contact))

                val html = wrapHtml(msg.body)
                binding.webView.loadDataWithBaseURL(
                    "https://mail.invalid/", html, "text/html", "UTF-8", null
                )
            }.onFailure { e ->
                binding.tvSubject.text = "Errore"
                binding.webView.loadData(
                    "Impossibile leggere il messaggio: ${e.message ?: ""}",
                    "text/plain", "UTF-8"
                )
            }
        }
    }

    private fun wrapHtml(body: String): String = """
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body { margin:0; padding:14px; font-family:sans-serif; font-size:15px;
                 line-height:1.5; color:#1A1C1E; word-wrap:break-word; overflow-wrap:anywhere; }
          img { max-width:100%; height:auto; }
          table { max-width:100%; }
          a { color:#0B57D0; }
          pre { white-space:pre-wrap; word-wrap:break-word; }
        </style>
        </head><body>$body</body></html>
    """.trimIndent()

    private fun initialOf(name: String): String {
        val c = name.trim().firstOrNull { it.isLetterOrDigit() }
        return c?.uppercaseChar()?.toString() ?: "?"
    }

    private fun colorFor(key: String): Int {
        val colors = intArrayOf(
            Color.parseColor("#1E88E5"), Color.parseColor("#43A047"),
            Color.parseColor("#E53935"), Color.parseColor("#8E24AA"),
            Color.parseColor("#FB8C00"), Color.parseColor("#00897B"),
            Color.parseColor("#3949AB"), Color.parseColor("#D81B60")
        )
        return colors[Math.abs(key.hashCode()) % colors.size]
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
            .setMessage("Verrà spostato nel Cestino di Libero.")
            .setPositiveButton("Elimina") { _, _ -> deleteMessage() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteMessage() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(store.imapHost, store.imapPort, store.email, store.password)
                        .deleteMessages(mailbox, listOf(uid))
                }
            }
            binding.progress.visibility = View.GONE
            result.onSuccess { ok ->
                if (ok) finish()
                else AlertDialog.Builder(this@MessageActivity)
                    .setMessage("Impossibile eliminare il messaggio.")
                    .setPositiveButton("OK", null).show()
            }.onFailure { e ->
                AlertDialog.Builder(this@MessageActivity)
                    .setMessage("Errore durante l'eliminazione: ${e.message ?: ""}")
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_UID = "extra_uid"
        const val EXTRA_MAILBOX = "extra_mailbox"
    }
}
