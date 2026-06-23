package com.niko.liberomail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.niko.liberomail.data.CredentialStore
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

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* esito ignorato */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        store = CredentialStore(this)
        if (!store.isLoggedIn) {
            goToLogin()
            return
        }

        adapter = InboxAdapter { email -> openMessage(email.uid) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadInbox(showSpinner = false) }

        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        MailCheckWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // Carica all'avvio e ricarica quando si torna dal dettaglio (es. dopo cancellazione).
        // Lo spinner appare solo se la lista è ancora vuota.
        if (::adapter.isInitialized) {
            loadInbox(showSpinner = adapter.itemCount == 0)
        }
    }

    private fun loadInbox(showSpinner: Boolean) {
        if (showSpinner) binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MailClient(
                        store.imapHost, store.imapPort, store.email, store.password
                    ).fetchInbox(limit = 50)
                }
            }
            binding.progress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { list ->
                adapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                // aggiorna baseline notifiche
                if (list.isNotEmpty()) {
                    val maxUid = list.maxOf { it.uid }
                    if (maxUid > store.lastNotifiedUid) store.lastNotifiedUid = maxUid
                }
            }.onFailure { e ->
                binding.tvEmpty.text = "Errore di caricamento: ${e.message ?: ""}"
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun openMessage(uid: Long) {
        startActivity(Intent(this, MessageActivity::class.java).apply {
            putExtra(MessageActivity.EXTRA_UID, uid)
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_inbox, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadInbox(showSpinner = true); true
            }
            R.id.action_logout -> {
                logout(); true
            }
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
