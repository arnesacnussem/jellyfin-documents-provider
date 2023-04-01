package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.database.AppDatabase
import a.sac.jellyfindocumentsprovider.database.dao.CredentialDao
import a.sac.jellyfindocumentsprovider.databinding.FragmentLibrarySelectionBinding
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.ui.WizardViewModel
import a.sac.jellyfindocumentsprovider.ui.adapters.LibrarySelectionListAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@AndroidEntryPoint
class WizardLibrarySelectionFragment : Fragment() {

    private var _binding: FragmentLibrarySelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel by activityViewModels<WizardViewModel>()
    private lateinit var credentialDao: CredentialDao

    @Inject
    lateinit var jellyfinProvider: JellyfinProvider


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibrarySelectionBinding.inflate(inflater, container, false)
        credentialDao = AppDatabase.getDatabase(requireContext()).credentialDao()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.confirm.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (viewModel.libraryInfo.count { it.checked } <= 0) {
                    Snackbar.make(view, "Select at least one item.", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.fab).show()
                    return@launch
                }
                credentialDao.updateLibrary(
                    viewModel.credential!!.uid,
                    viewModel.libraryInfo.filter { it.checked }.associate { it.id to it.name }
                )
                withContext(Dispatchers.Main) {
                    findNavController().navigate(R.id.action_LibrarySelectionFragment_to_wizardUpdateDb)
                }
            }
        }
        loadMediaLibraryList()
    }

    private fun loadMediaLibraryList() {
        if (viewModel.credential == null) {
            Toast.makeText(requireContext(), "Credential not set!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val map = jellyfinProvider.getUserViews()
            if (map.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Current user has no media library available!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                viewModel.libraryInfo.clear()
                viewModel.libraryInfo.addAll(map)
                binding.listview.adapter =
                    LibrarySelectionListAdapter(requireContext(), viewModel.libraryInfo)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}