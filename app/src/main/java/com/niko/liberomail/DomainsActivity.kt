package com.niko.liberomail

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.data.DomainStat
import com.niko.liberomail.data.EmailMessage
import com.niko.liberomail.data.MailFolder
import com.niko.liberomail.databinding.ActivityDomainsBinding
import com.niko.liberomail.mail.MailClient
import com.niko.liberomail.ui.DomainsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Riepilogo dei messaggi raggruppati per dominio del mittente,
 * con azioni di massa (elimina o sposta tutte le email di un dominio).
 */
class DomainsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDomainsBinding
    private lateinit var store: CredentialStore
    private lateinit var adapter: DomainsAdapter

    private var folderName: String = "INBOX"
    private var isSent: Boolean = false
    private var limit: Int = 200
    private var messages: List<EmailMessage> = emptyList()
    private var folders: List<MailFolder> = emptyList()

    private fun client() = MailClient(
        store.imapHost, store.imapPort, store.email, store.password,
        store.smtpHost, store.smtpPort
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDomainsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Domini"

        store = CredentialStore(this)
        folderName = intent.getStringExtra(EXTRA_FOLDER) ?: "INBOX"
        isSent = intent.getBooleanExtra(EXTRA_IS_SENT, false)
        limit = intent.getIntExtra(EXTRA_LIMIT, 200)

        adapter = DomainsAdapter { stat -> showActions(stat) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter

        load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val msgs = client().fetchMessages(folderName, isSent, limit = limit)
                    val fs = runCatching { client().listFolders() }.getOrDefault(emptyList())
                    msgs to fs
                }
            }
            binding.progress.visibility = View.GONE

            result.onSuccess { (msgs, fs) ->
                messages = msgs
                folders = fs
                val stats = msgs.groupBy { it.domain }
                    .map { (d, list) ->
                        DomainStat(d, list.size, list.count { !it.seen })
                    }
                    .sortedWith(compareByDescending<DomainStat> { it.total }.thenBy { it.domain })
                adapter.submit(stats)

                binding.tvSummary.text =
                    "${msgs.size} messaggi analizzati · ${stats.size} domini"
                binding.tvEmpty.visibility = if (stats.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                binding.tvEmpty.text = "Errore: ${e.message ?: ""}"
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun uidsFor(domain: String): List<Long> =
        messages.filter { it.domain == domain }.map { it.uid }

    private fun showActions(stat: DomainStat) {
        val options = arrayOf(
            "Elimina tutte (${stat.total})",
            "Sposta tutte in un'altra cartella"
        )
        AlertDialog.Builder(this)
            .setTitle(stat.domain)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmDelete(stat)
                    1 -> chooseTargetFolder(stat)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmDelete(stat: DomainStat) {
        AlertDialog.Builder(this)
            .setTitle("Eliminare ${stat.total} messaggi?")
            .setMessage("Tutte le email da ${stat.domain} verranno spostate nel Cestino.")
            .setPositiveButton("Elimina") { _, _ ->
                runAction("Eliminazione") {
                    client().deleteMessages(folderName, uidsFor(stat.domain))
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun chooseTargetFolder(stat: DomainStat) {
        val targets = folders.filter { !it.fullName.equals(folderName, true) }
        if (targets.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Nessuna cartella di destinazione disponibile.")
                .setPositiveButton("OK", null).show()
            return
        }
        val names = targets.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sposta in…")
            .setItems(names) { _, which ->
                val target = targets[which]
                runAction("Spostamento") {
                    client().moveMessages(folderName, target.fullName, uidsFor(stat.domain))
                }
            }
            .show()
    }

    private fun runAction(label: String, block: suspend () -> Boolean) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            binding.progress.visibility = View.GONE
            result.onSuccess { load() }
                .onFailure { e ->
                    AlertDialog.Builder(this@DomainsActivity)
                        .setMessage("$label non riuscito: ${e.message ?: ""}")
                        .setPositiveButton("OK", null).show()
                }
        }
    }

    companion object {
        const val EXTRA_FOLDER = "extra_folder"
        const val EXTRA_IS_SENT = "extra_is_sent"
        const val EXTRA_LIMIT = "extra_limit"
    }
}
