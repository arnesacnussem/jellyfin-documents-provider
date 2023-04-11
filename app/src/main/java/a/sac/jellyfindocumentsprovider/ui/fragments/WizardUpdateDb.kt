package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.databinding.FragmentWizardUpdateDbBinding
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.ui.WizardViewModel
import a.sac.jellyfindocumentsprovider.ui.adapters.TextListAdapter
import a.sac.jellyfindocumentsprovider.utils.logcat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WizardUpdateDb : Fragment() {
    private var _binding: FragmentWizardUpdateDbBinding? = null
    private val binding get() = _binding!!
    private val viewModel by activityViewModels<WizardViewModel>()

    @Inject
    lateinit var jellyfinProvider: JellyfinProvider
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardUpdateDbBinding.inflate(inflater, container, false)
        binding.vm = viewModel
        binding.rvMsgWindow.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMsgWindow.adapter = TextListAdapter(viewModel.message)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOK.setOnClickListener {
            viewModel.message.clear()
            findNavController().navigate(R.id.action_wizardUpdateDb_to_home)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            jellyfinProvider.queryApiForLibraries(
                libraries = viewModel.libraryInfo.filter { it.checked },
                batchSize = 300,
                credential = viewModel.currentUser!!,
                onProgress = {
                    viewModel.progress.set(it)
                },
                onMessage = {
                    this@WizardUpdateDb.logcat {
                        "queryApiForLibraries(): $it"
                    }
                    viewModel.message.add(it)
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.rvMsgWindow.adapter?.notifyDataSetChanged()

                    }
                }
            )
        }

        Snackbar.make(view, "Library preference saved.", Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.progress).show()
    }
}