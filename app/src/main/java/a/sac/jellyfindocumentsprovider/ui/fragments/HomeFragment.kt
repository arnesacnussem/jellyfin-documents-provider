package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.R
import a.sac.jellyfindocumentsprovider.database.AppDatabase
import a.sac.jellyfindocumentsprovider.databinding.FragmentHomeBinding
import a.sac.jellyfindocumentsprovider.jellyfin.AuthorizationException
import a.sac.jellyfindocumentsprovider.jellyfin.JellyfinProvider
import a.sac.jellyfindocumentsprovider.ui.WizardViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject
    lateinit var jellyfinProvider: JellyfinProvider

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WizardViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.libraryPreference.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_LibrarySelectionFragment)
        }

        checkIfNeedWizard()
    }

    private fun checkIfNeedWizard() {
        val credentialDao = AppDatabase.getDatabase(requireContext()).credentialDao()
        lifecycleScope.launch(Dispatchers.IO) {
            val all = credentialDao.loadAllUsers()
            if (all.isEmpty()) {
                withContext(Dispatchers.Main) {
                    findNavController().navigate(R.id.action_home_to_ServerInfoFragment)
                }
                return@launch
            }

            with(all[0]) {
                val valid = try {
                    jellyfinProvider.verifySavedCredential(this)
                    viewModel.credential = this
                    true
                } catch (e: AuthorizationException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
                    }
                    false
                }

                if (!valid) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Credential expired!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    withContext(Dispatchers.Main) {
                        findNavController().navigate(R.id.action_home_to_ServerInfoFragment)
                    }
                    return@launch
                }


                if (library.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        findNavController().navigate(R.id.action_home_to_LibrarySelectionFragment)
                    }
                }
            }
        }
    }
}