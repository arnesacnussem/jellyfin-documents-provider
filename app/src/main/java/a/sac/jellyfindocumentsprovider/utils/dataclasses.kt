package a.sac.jellyfindocumentsprovider.utils

@Suppress("unused")
class PrefEnums {
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

