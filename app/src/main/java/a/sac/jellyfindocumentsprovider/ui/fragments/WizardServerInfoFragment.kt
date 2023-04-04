package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.databinding.FragmentWizardServerInfoBinding
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.ui.WizardViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WizardServerInfoFragment : Fragment() {
    private var _binding: FragmentWizardServerInfoBinding? = null
    private val binding get() = _binding!!
    private val vm: WizardViewModel by activityViewModels()

    @Inject
    lateinit var jellyfinProvider: JellyfinProvider

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardServerInfoBinding.inflate(inflater, container, false)
        binding.vm = vm
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.test.setOnClickListener {
            if (vm.serverInfoValid.get() == true) {
                goNext()
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    vm.testConnection()
                }
            }
        }
    }

    private fun goNext() {
        findNavController().navigate(R.id.action_ServerInfoFragment_to_LibrarySelectionFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
