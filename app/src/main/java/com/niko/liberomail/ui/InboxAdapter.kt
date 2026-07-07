package com.niko.liberomail.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niko.liberomail.data.EmailMessage
import com.niko.liberomail.databinding.ItemEmailBinding

class InboxAdapter(
    private val onOpen: (EmailMessage) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<InboxAdapter.VH>() {

    private val items = mutableListOf<EmailMessage>()
    private val selected = linkedSetOf<Long>()
    var selectionMode = false
        private set

    fun submit(list: List<EmailMessage>) {
        items.clear()
        items.addAll(list)
        // mantieni selezionati solo gli uid ancora presenti
        selected.retainAll(list.map { it.uid }.toSet())
        if (selected.isEmpty()) selectionMode = false
        notifyDataSetChanged()
    }

    fun selectedUids(): List<Long> = selected.toList()

    fun exitSelection() {
        selected.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    private fun toggle(uid: Long) {
        if (!selected.add(uid)) selected.remove(uid)
        if (selected.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEmailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemEmailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmailMessage) {
            val contactLabel =
                if (item.outgoing) "A: ${item.contact}" else item.contact

            binding.tvContact.text = contactLabel
            binding.tvSubject.text = item.subject
            binding.tvDate.text = if (item.dateMillis > 0) {
                DateUtils.getRelativeTimeSpanString(
                    item.dateMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            } else ""

            val style = if (item.seen) Typeface.NORMAL else Typeface.BOLD
            binding.tvContact.setTypeface(null, style)
            binding.tvSubject.setTypeface(null, style)
            binding.unreadDot.visibility =
                if (item.seen) View.INVISIBLE else View.VISIBLE

            val isSelected = selected.contains(item.uid)
            binding.root.isActivated = isSelected

            if (isSelected) {
                binding.avatar.text = "✓"
                binding.avatar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#0B57D0"))
            } else {
                binding.avatar.text = initialOf(contactLabel)
                binding.avatar.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(colorFor(item.contact))
            }

            binding.root.setOnClickListener {
                if (selectionMode) toggle(item.uid) else onOpen(item)
            }
            binding.root.setOnLongClickListener {
                if (!selectionMode) selectionMode = true
                toggle(item.uid)
                true
            }
        }
    }

    private fun initialOf(name: String): String {
        val clean = name.trim().removePrefix("A: ")
        val c = clean.firstOrNull { it.isLetterOrDigit() }
        return c?.uppercaseChar()?.toString() ?: "?"
    }

    private fun colorFor(key: String): Int {
        val idx = (Math.abs(key.hashCode())) % AVATAR_COLORS.size
        return AVATAR_COLORS[idx]
    }

    companion object {
        private val AVATAR_COLORS = intArrayOf(
            Color.parseColor("#1E88E5"), Color.parseColor("#43A047"),
            Color.parseColor("#E53935"), Color.parseColor("#8E24AA"),
            Color.parseColor("#FB8C00"), Color.parseColor("#00897B"),
            Color.parseColor("#3949AB"), Color.parseColor("#D81B60"),
            Color.parseColor("#6D4C41"), Color.parseColor("#7CB342")
        )
    }
}
