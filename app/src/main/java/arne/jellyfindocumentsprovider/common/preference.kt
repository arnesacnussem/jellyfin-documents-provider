package arne.jellyfindocumentsprovider.common

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import logcat.LogPriority

enum class PrefKeys(private val defaultEnum: Enum<*>, val asEnum: (name: String) -> Any) {
    BITRATE_LIMIT(BitrateLimits.K320, { BitrateLimits.valueOf(it) }),
    BITRATE_LIMIT_TYPE(BitrateLimitType.NONE, { BitrateLimitType.valueOf(it) }),
    WAVE_TYPE(WaveType.REAL, { WaveType.valueOf(it) }),
    LOG_LEVEL(LogPriority.DEBUG, { LogPriority.valueOf(it) }),
    POWERAMP_SCAN_ON_SYNC(PowerampScanToggle.DISABLED, { PowerampScanToggle.valueOf(it) });

    val defaultVal
        get() = defaultEnum.name
}

enum class PowerampScanToggle { ENABLED, DISABLED }

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

enum class BitrateLimits(private val bitrate: Int) {
    K64(64), K128(128), K256(256), K320(320);

    val bps
        get() = bitrate * 1000
    val readable
        get() = "$bitrate Kbps"
    val description
        get() = "Limit to $bitrate Kbps."

}

enum class WaveType(val description: String) {
    REAL("Use real wave generated from file"), FAKE("Generate random waves"), NONE("No waves")
}


fun Context.encryptedPrefs(): SharedPreferences {
    val masterKey = MasterKey.Builder(this)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        this,
        "jellyfin_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun SharedPreferences.getString(key: PrefKeys): String =
    this.getString(key.name, null) ?: key.defaultVal

@Suppress("UNCHECKED_CAST")
fun <T> SharedPreferences.getEnum(key: PrefKeys) = key.asEnum(this.getString(key)) as T