package com.niko.liberomail.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.niko.liberomail.data.DomainStat
import com.niko.liberomail.databinding.ItemDomainBinding

class DomainsAdapter(
    private val onClick: (DomainStat) -> Unit
) : RecyclerView.Adapter<DomainsAdapter.VH>() {

    private val items = mutableListOf<DomainStat>()

    fun submit(list: List<DomainStat>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDomainBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemDomainBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(stat: DomainStat) {
            binding.tvDomain.text = stat.domain
            binding.tvCount.text = stat.total.toString()
            if (stat.unread > 0) {
                binding.tvUnread.visibility = View.VISIBLE
                binding.tvUnread.text = "${stat.unread} da leggere"
            } else {
                binding.tvUnread.visibility = View.GONE
            }
            binding.root.setOnClickListener { onClick(stat) }
        }
    }
}
