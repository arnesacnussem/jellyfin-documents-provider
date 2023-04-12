package a.sac.jellyfindocumentsprovider.ui.fragments

import a.sac.jellyfindocumentsprovider.utils.PrefEnums.BitrateLimitType
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.BitrateLimits
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.PrefKeys
import a.sac.jellyfindocumentsprovider.utils.PrefEnums.WaveType
import a.sac.jellyfindocumentsprovider.utils.getString
import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPreference(ListPreference(ctx).apply {
            setDefaultValue(PrefKeys.WAVE_TYPE.defaultVal)
            key = PrefKeys.WAVE_TYPE.name
            title = "Poweramp Wave Type"
            summaryProvider = SummaryProvider { it: ListPreference ->
                "${WaveType.valueOf(it.value).description}\nYou have to re-scan your library in poweramp to make it work.\n\nSeems this option NOT working as I think..., but it truly avoid opening file twice."
            }
            entries = WaveType.values().map { it.name }.toTypedArray()
            entryValues = WaveType.values().map { it.name }.toTypedArray()
        })

        populateBitrateLimitPref(ctx, screen)

        screen.addPreference(ListPreference(ctx).apply {
            setDefaultValue(PrefKeys.LOG_LEVEL.defaultVal)
            key = PrefKeys.LOG_LEVEL.name
            title = "Log level"
            summaryProvider = SummaryProvider { it: ListPreference ->
                it.value
            }
            entries = LogPriority.values().map { it.name }.toTypedArray()
            entryValues = LogPriority.values().map { it.name }.toTypedArray()
            onPreferenceChangeListener = OnPreferenceChangeListener { _, nVal ->
                val ll = LogPriority.valueOf(nVal as String)
                LogcatLogger.uninstall()
                LogcatLogger.install(AndroidLogcatLogger(ll))
                println("Log level changed to $nVal")
                true
            }
        })

        preferenceScreen = screen
    }


    private fun populateBitrateLimitPref(ctx: Context, screen: PreferenceScreen) {
        val seek = ListPreference(ctx).apply {
            setDefaultValue(PrefKeys.BITRATE_LIMIT.defaultVal)
            key = PrefKeys.BITRATE_LIMIT.name
            title = "Streaming bitrate limit"
            summaryProvider = SummaryProvider { it: ListPreference ->
                BitrateLimits.valueOf(it.value).description + "\n\nWith transcode on jellyfin side, this not work as I want, Poweramp will try to read near file end, but this is impossible since it's a real-time generated file."
            }
            entries = BitrateLimits.values().map { it.readable }.toTypedArray()
            entryValues = BitrateLimits.values().map { it.name }.toTypedArray()
            isVisible = BitrateLimitType.valueOf(
                PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(PrefKeys.BITRATE_LIMIT_TYPE)
            ).seekBarVisible
        }

        val type = ListPreference(ctx).apply {
            setDefaultValue(PrefKeys.BITRATE_LIMIT_TYPE.defaultVal)
            key = PrefKeys.BITRATE_LIMIT_TYPE.name
            title = "Bitrate Limit"
            summaryProvider = SummaryProvider { it: ListPreference ->
                BitrateLimitType.valueOf(it.value).description
            }
            entries = BitrateLimitType.values().map { it.readable }.toTypedArray()
            entryValues = BitrateLimitType.values().map { it.name }.toTypedArray()
            onPreferenceChangeListener = OnPreferenceChangeListener { _, nVal ->
                seek.isVisible = BitrateLimitType.valueOf(nVal as String).seekBarVisible
                true
            }
        }
        screen.addPreference(type)
        screen.addPreference(seek)
    }
}