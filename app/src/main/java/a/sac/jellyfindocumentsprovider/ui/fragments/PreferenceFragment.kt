package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.utils.PrefEnums.BitrateLimitType
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.BitrateLimits
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.WaveType
import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPreference(ListPreference(ctx).apply {
            key = "wave_type"
            title = "Poweramp Wave Type"
            summaryProvider = SummaryProvider { it: ListPreference ->
                WaveType.valueOf(it.value).description
            }
            entries = WaveType.values().map { it.name }.toTypedArray()
            entryValues = WaveType.values().map { it.name }.toTypedArray()
            setDefaultValue(WaveType.REAL.name)
        })

        populateBitrateLimitPref(ctx, screen)

        preferenceScreen = screen
    }


    private fun populateBitrateLimitPref(ctx: Context, screen: PreferenceScreen) {
        val type = ListPreference(ctx).apply {
            key = "bitrate_limit_type"
            title = "Bitrate Limit"
            summaryProvider = SummaryProvider { it: ListPreference ->
                BitrateLimitType.valueOf(it.value).description
            }
            entries = BitrateLimitType.values().map { it.readable }.toTypedArray()
            entryValues = BitrateLimitType.values().map { it.name }.toTypedArray()
            setDefaultValue(BitrateLimitType.NONE.name)
        }

        val seek = ListPreference(ctx).apply {
            key = "bitrate_limit"
            title = "Streaming bit rate limit"
            summaryProvider = SummaryProvider { it: ListPreference ->
                BitrateLimits.valueOf(it.value).description
            }
            entries = BitrateLimits.values().map { it.readable }.toTypedArray()
            entryValues = BitrateLimits.values().map { it.name }.toTypedArray()
            setDefaultValue(BitrateLimits.K320.name)
        }
        type.setOnPreferenceChangeListener { _, newValue ->
            seek.isVisible = BitrateLimitType.valueOf(newValue as String).seekBarVisible
            true
        }
        screen.addPreference(type)
        screen.addPreference(seek)
    }
}