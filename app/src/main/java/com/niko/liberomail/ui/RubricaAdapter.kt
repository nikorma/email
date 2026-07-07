package com.niko.liberomail.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niko.liberomail.data.Contact
import com.niko.liberomail.databinding.ItemContactBinding

class RubricaAdapter(
    private val onDelete: (Contact) -> Unit
) : RecyclerView.Adapter<RubricaAdapter.VH>() {

    private val items = mutableListOf<Contact>()

    fun submit(list: List<Contact>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(c: Contact) {
            binding.tvName.text = if (c.name.isNotBlank()) c.name else c.email
            binding.tvEmail.text = c.email
            binding.btnDelete.setOnClickListener { onDelete(c) }
        }
    }
}
