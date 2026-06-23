package com.niko.liberomail.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niko.liberomail.data.EmailMessage
import com.niko.liberomail.databinding.ItemEmailBinding

class InboxAdapter(
    private val onClick: (EmailMessage) -> Unit
) : RecyclerView.Adapter<InboxAdapter.VH>() {

    private val items = mutableListOf<EmailMessage>()

    fun submit(list: List<EmailMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun removeByUid(uid: Long) {
        val index = items.indexOfFirst { it.uid == uid }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEmailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemEmailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmailMessage) {
            binding.tvFrom.text = item.from
            binding.tvSubject.text = item.subject
            binding.tvDate.text = if (item.dateMillis > 0) {
                DateUtils.getRelativeTimeSpanString(
                    item.dateMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else ""

            // Grassetto se non letta
            val style = if (item.seen) android.graphics.Typeface.NORMAL
            else android.graphics.Typeface.BOLD
            binding.tvFrom.setTypeface(null, style)
            binding.tvSubject.setTypeface(null, style)
            binding.unreadDot.visibility =
                if (item.seen) android.view.View.INVISIBLE else android.view.View.VISIBLE

            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
