package com.niko.liberomail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.data.EmailMessage
import com.niko.liberomail.data.MailFolder
import com.niko.liberomail.data.RulesStore
import com.niko.liberomail.data.SortOrder
import com.niko.liberomail.databinding.ActivityInboxBinding
import com.niko.liberomail.mail.MailClient
import com.niko.liberomail.ui.InboxAdapter
import com.niko.liberomail.util.NotificationHelper
import com.niko.liberomail.work.MailCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InboxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInboxBinding
    private lateinit var store: CredentialStore
    private lateinit var adapter: InboxAdapter

    private var currentFolder = MailFolder.INBOX
    private var currentSort = SortOrder.DATE_DESC
    private var onlyUnread = false
    private var currentLimit = 60
    private var cachedFolders: List<MailFolder> = emptyList()
    private val fullList = mutableListOf<EmailMessage>()
    private var selectionCount = 0

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun client() =
        MailClient(store.imapHost, store.imapPort, store.email, store.password,
            store.smtpHost, store.smtpPort)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        store = CredentialStore(this)
        if (!store.isLoggedIn) { goToLogin(); return }

        adapter = InboxAdapter(
            onOpen = { email -> openMessage(email) },
            onSelectionChanged = { count -> onSelectionChanged(count) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadMail(showSpinner = false) }
        binding.fabCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }

        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        MailCheckWorker.schedule(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.selectionMode) adapter.exitSelection() else finish()
            }
        })

        updateTitle()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && !adapter.selectionMode) {
            loadMail(showSpinner = fullList.isEmpty())
        }
    }

    private fun loadMail(showSpinner: Boolean) {
        if (showSpinner) binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val rules = RulesStore(this@InboxActivity).getRules()
                    client().fetchMessages(
                        currentFolder.fullName, currentFolder.isSent,
                        limit = currentLimit, rules = rules
                    )
                }
            }
            binding.progress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { list ->
                fullList.clear()
                fullList.addAll(list)
                applySortAndSubmit()
                updateTitle()

                if (currentFolder.isInbox && list.isNotEmpty()) {
                    val maxUid = list.maxOf { it.uid }
                    if (maxUid > store.lastNotifiedUid) store.lastNotifiedUid = maxUid
                }
            }.onFailure { e ->
                binding.tvEmpty.text = "Errore di caricamento: ${e.message ?: ""}"
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun applySortAndSubmit() {
        val base = if (onlyUnread && !currentFolder.isSent) fullList.filter { !it.seen } else fullList
        val sorted = when (currentSort) {
            SortOrder.DATE_DESC -> base.sortedByDescending { it.dateMillis }
            SortOrder.DATE_ASC -> base.sortedBy { it.dateMillis }
            SortOrder.SENDER_AZ -> base.sortedBy { it.contact.lowercase() }
            SortOrder.UNREAD_FIRST ->
                base.sortedWith(compareBy<EmailMessage> { it.seen }.thenByDescending { it.dateMillis })
        }
        adapter.submit(sorted)

        binding.tvEmpty.text =
            if (onlyUnread && !currentFolder.isSent) "Nessun messaggio non letto" else "Nessun messaggio"
        binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSortDialog() {
        val options = SortOrder.values()
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Ordina per")
            .setSingleChoiceItems(labels, options.indexOf(currentSort)) { dialog, which ->
                currentSort = options[which]
                applySortAndSubmit()
                dialog.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showFolderPicker() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val folders = runCatching {
                withContext(Dispatchers.IO) { client().listFolders() }
            }.getOrNull()
            binding.progress.visibility = View.GONE

            if (folders.isNullOrEmpty()) {
                AlertDialog.Builder(this@InboxActivity)
                    .setMessage("Impossibile leggere l'elenco delle cartelle.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            cachedFolders = folders
            val names = folders.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(this@InboxActivity)
                .setTitle("Cartelle")
                .setItems(names) { _, which ->
                    currentFolder = folders[which]
                    currentLimit = 60
                    onlyUnread = false
                    adapter.exitSelection()
                    updateTitle()
                    loadMail(showSpinner = true)
                }
                .show()
        }
    }

    private fun onSelectionChanged(count: Int) {
        selectionCount = count
        if (count > 0) {
            supportActionBar?.title = "$count selezionati"
            supportActionBar?.subtitle = null
            binding.toolbar.setNavigationIcon(R.drawable.ic_close)
            binding.toolbar.setNavigationOnClickListener { adapter.exitSelection() }
        } else {
            updateTitle()
            binding.toolbar.navigationIcon = null
        }
        invalidateOptionsMenu()
    }

    private fun updateTitle() {
        supportActionBar?.title = currentFolder.displayName
        val unread = fullList.count { !it.seen }
        supportActionBar?.subtitle = when {
            currentFolder.isSent -> "${fullList.size} messaggi"
            unread == 0 -> "Nessuna da leggere · ${fullList.size} messaggi"
            unread == 1 -> "1 da leggere · ${fullList.size} messaggi"
            else -> "$unread da leggere · ${fullList.size} messaggi"
        }
    }

    private fun confirmDeleteSelected() {
        val uids = adapter.selectedUids()
        if (uids.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Eliminare ${uids.size} messaggi?")
            .setMessage("Verranno spostati nel Cestino di Libero.")
            .setPositiveButton("Elimina") { _, _ -> deleteSelected(uids) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteSelected(uids: List<Long>) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    client().deleteMessages(currentFolder.fullName, uids)
                }
            }
            binding.progress.visibility = View.GONE
            adapter.exitSelection()
            result.onSuccess { loadMail(showSpinner = true) }
                .onFailure { e ->
                    AlertDialog.Builder(this@InboxActivity)
                        .setMessage("Errore durante l'eliminazione: ${e.message ?: ""}")
                        .setPositiveButton("OK", null).show()
                }
        }
    }

    private fun openMessage(email: EmailMessage) {
        startActivity(Intent(this, MessageActivity::class.java).apply {
            putExtra(MessageActivity.EXTRA_UID, email.uid)
            putExtra(MessageActivity.EXTRA_FOLDER, currentFolder.fullName)
            putExtra(MessageActivity.EXTRA_IS_SENT, currentFolder.isSent)
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (selectionCount > 0) {
            menuInflater.inflate(R.menu.menu_selection, menu)
        } else {
            menuInflater.inflate(R.menu.menu_inbox, menu)
            menu.findItem(R.id.action_filter_unread)?.isChecked = onlyUnread
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_folders -> { showFolderPicker(); true }
            R.id.action_sort -> { showSortDialog(); true }
            R.id.action_filter_unread -> {
                onlyUnread = !onlyUnread
                item.isChecked = onlyUnread
                applySortAndSubmit()
                true
            }
            R.id.action_refresh -> { loadMail(showSpinner = true); true }
            R.id.action_load_more -> {
                currentLimit += 50
                loadMail(showSpinner = true)
                true
            }
            R.id.action_domains -> {
                startActivity(Intent(this, DomainsActivity::class.java).apply {
                    putExtra(DomainsActivity.EXTRA_FOLDER, currentFolder.fullName)
                    putExtra(DomainsActivity.EXTRA_IS_SENT, currentFolder.isSent)
                    putExtra(DomainsActivity.EXTRA_LIMIT, maxOf(currentLimit, 200))
                })
                true
            }
            R.id.action_rules -> {
                startActivity(Intent(this, RulesActivity::class.java)); true
            }
            R.id.action_contacts -> {
                startActivity(Intent(this, RubricaActivity::class.java)); true
            }
            R.id.action_logout -> { logout(); true }
            R.id.action_select_all -> { adapter.selectAllVisible(); true }
            R.id.action_delete_selected -> { confirmDeleteSelected(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        MailCheckWorker.cancel(this)
        store.clear()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
