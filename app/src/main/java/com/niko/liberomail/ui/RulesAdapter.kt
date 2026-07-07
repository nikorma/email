package com.niko.liberomail.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niko.liberomail.data.MailRule
import com.niko.liberomail.databinding.ItemRuleBinding

class RulesAdapter(
    private val onDelete: (MailRule) -> Unit
) : RecyclerView.Adapter<RulesAdapter.VH>() {

    private val items = mutableListOf<MailRule>()

    fun submit(list: List<MailRule>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: MailRule) {
            binding.tvField.text = rule.field.label
            binding.tvValue.text = "\"${rule.value}\""
            binding.btnDelete.setOnClickListener { onDelete(rule) }
        }
    }
}
