package com.niko.liberomail

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.niko.liberomail.data.Contact
import com.niko.liberomail.data.ContactStore
import com.niko.liberomail.databinding.ActivityRubricaBinding
import com.niko.liberomail.databinding.DialogAddContactBinding
import com.niko.liberomail.ui.RubricaAdapter

class RubricaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRubricaBinding
    private lateinit var store: ContactStore
    private lateinit var adapter: RubricaAdapter

    private val pickPhoneEmail =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val uri = res.data?.data ?: return@registerForActivityResult
                try {
                    contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Email.ADDRESS,
                            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
                        ),
                        null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val email = c.getString(0) ?: return@use
                            val name = c.getString(1) ?: ""
                            store.addContact(name, email)
                            refresh()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRubricaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Rubrica"

        store = ContactStore(this)

        adapter = RubricaAdapter { c -> confirmDelete(c) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter

        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnFromPhone.setOnClickListener { importFromPhone() }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refresh() {
        val contacts = store.getContacts()
        adapter.submit(contacts)
        binding.tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Nuovo contatto")
            .setView(dialogBinding.root)
            .setPositiveButton("Salva") { _, _ ->
                val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
                val email = dialogBinding.etEmail.text?.toString()?.trim().orEmpty()
                if (email.isEmpty()) return@setPositiveButton
                store.addContact(name, email)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun importFromPhone() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
        }
        try {
            pickPhoneEmail.launch(intent)
        } catch (_: Exception) {
            AlertDialog.Builder(this)
                .setMessage("Nessuna app contatti disponibile.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun confirmDelete(c: Contact) {
        AlertDialog.Builder(this)
            .setMessage("Eliminare ${if (c.name.isNotBlank()) c.name else c.email}?")
            .setPositiveButton("Elimina") { _, _ ->
                store.removeContact(c.id)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
