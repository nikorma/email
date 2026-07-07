package com.niko.liberomail

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.niko.liberomail.data.MailRule
import com.niko.liberomail.data.RulesStore
import com.niko.liberomail.databinding.ActivityRulesBinding
import com.niko.liberomail.databinding.DialogAddRuleBinding
import com.niko.liberomail.ui.RulesAdapter

class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding
    private lateinit var store: RulesStore
    private lateinit var adapter: RulesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Regole"

        store = RulesStore(this)

        adapter = RulesAdapter { rule -> confirmDelete(rule) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter

        binding.btnAdd.setOnClickListener { showAddDialog() }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun refresh() {
        val rules = store.getRules()
        adapter.submit(rules)
        binding.tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddRuleBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Nuova regola")
            .setView(dialogBinding.root)
            .setPositiveButton("Aggiungi") { _, _ ->
                val value = dialogBinding.etValue.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) return@setPositiveButton
                val field =
                    if (dialogBinding.rbSubject.isChecked) MailRule.Field.SUBJECT
                    else MailRule.Field.SENDER
                store.addRule(field, value)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmDelete(rule: MailRule) {
        AlertDialog.Builder(this)
            .setMessage("Eliminare la regola: ${rule.describe()}?")
            .setPositiveButton("Elimina") { _, _ ->
                store.removeRule(rule.id)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
