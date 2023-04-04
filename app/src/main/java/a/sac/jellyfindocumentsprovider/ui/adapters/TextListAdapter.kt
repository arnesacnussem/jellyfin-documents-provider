package a.sac.jellyfindocumentsprovider.ui.adapters

import a.sac.jellyfindocumentsprovider.databinding.TextListItemBinding
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class TextListAdapter(private val items: List<String>) :
    RecyclerView.Adapter<TextListAdapter.TextListAdapterViewHolder>() {
    class TextListAdapterViewHolder(val binding: TextListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextListAdapterViewHolder {
        val binding =
            TextListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TextListAdapterViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: TextListAdapterViewHolder, position: Int) {
        holder.binding.text.text = items[position]
    }
}