package a.sac.jellyfindocumentsprovider.ui.adapters

import a.sac.jellyfindocumentsprovider.MediaLibraryListItem
import a.sac.jellyfindocumentsprovider.databinding.MediaLibraryListItemBinding
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil

class LibrarySelectionListAdapter(
    context: Context, private var items: List<MediaLibraryListItem> = arrayListOf()
) : ArrayAdapter<MediaLibraryListItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: MediaLibraryListItemBinding = if (convertView != null) {
            DataBindingUtil.bind(convertView)!!
        } else {
            MediaLibraryListItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        }
        binding.item = items[position]

        return binding.root
    }

    override fun getCount(): Int {
        return items.size
    }
}