package a.sac.jellyfindocumentsprovider.utils

import a.sac.jellyfindocumentsprovider.utils.PrefEnums.*
import logcat.LogPriority

@Suppress("unused")
class PrefEnums {
    enum class PrefKeys(val defaultEnum: Enum<*>, val asEnum: (name: String) -> Any) {
        BITRATE_LIMIT(BitrateLimits.K320, { BitrateLimits.valueOf(it) }),
        BITRATE_LIMIT_TYPE(BitrateLimitType.NONE, { BitrateLimitType.valueOf(it) }),
        WAVE_TYPE(WaveType.REAL, { WaveType.valueOf(it) }),
        LOG_LEVEL(LogPriority.DEBUG, { LogPriority.valueOf(it) });

        val defaultVal
            get() = defaultEnum.name
    }

    enum class BitrateLimitType(
        val readable: String,
        val description: String,
        val seekBarVisible: Boolean
    ) {
        NONE("None", "No bitrate limit, steaming the original file.", false),
        CELL(
            "Cellular",
            "Apply bitrate limit only when using cellular data.",
            true
        ),
        ALL("Always", "Always apply bitrate limit.", true)
    }

    enum class BitrateLimits(val bitrate: Int) {
        K64(64), K128(128), K256(256), K320(320);

        val readable
            get() = "$bitrate Kbps"
        val description
            get() = "Limit to $bitrate Kbps."

    }

    enum class WaveType(val description: String) {
        REAL("Use real wave generated from file"), FAKE("Generate random waves"), NONE("No waves")
    }

}

