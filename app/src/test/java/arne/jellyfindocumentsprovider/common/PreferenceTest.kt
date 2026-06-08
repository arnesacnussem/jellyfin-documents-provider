package arne.jellyfindocumentsprovider.common

import logcat.LogPriority
import org.junit.Assert.*
import org.junit.Test

class PreferenceTest {

    // ─── BitrateLimits ───────────────────────────────────────

    @Test
    fun bitrateLimits_bps() {
        assertEquals(64_000, BitrateLimits.K64.bps)
        assertEquals(128_000, BitrateLimits.K128.bps)
        assertEquals(256_000, BitrateLimits.K256.bps)
        assertEquals(320_000, BitrateLimits.K320.bps)
    }

    @Test
    fun bitrateLimits_readable() {
        assertEquals("64 Kbps", BitrateLimits.K64.readable)
        assertEquals("128 Kbps", BitrateLimits.K128.readable)
        assertEquals("256 Kbps", BitrateLimits.K256.readable)
        assertEquals("320 Kbps", BitrateLimits.K320.readable)
    }

    @Test
    fun bitrateLimits_description() {
        assertTrue(BitrateLimits.K64.description.contains("64"))
        assertTrue(BitrateLimits.K320.description.contains("320"))
    }

    // ─── BitrateLimitType ────────────────────────────────────

    @Test
    fun bitrateLimitType_none() {
        assertEquals("None", BitrateLimitType.NONE.readable)
        assertFalse(BitrateLimitType.NONE.seekBarVisible)
    }

    @Test
    fun bitrateLimitType_cell() {
        assertEquals("Cellular", BitrateLimitType.CELL.readable)
        assertTrue(BitrateLimitType.CELL.seekBarVisible)
    }

    @Test
    fun bitrateLimitType_all() {
        assertEquals("Always", BitrateLimitType.ALL.readable)
        assertTrue(BitrateLimitType.ALL.seekBarVisible)
    }

    // ─── WaveType ────────────────────────────────────────────

    @Test
    fun waveType_descriptions() {
        assertTrue(WaveType.REAL.description.contains("real"))
        assertTrue(WaveType.FAKE.description.contains("random"))
        assertTrue(WaveType.NONE.description.contains("No waves"))
    }

    // ─── PrefKeys ────────────────────────────────────────────

    @Test
    fun prefKeys_defaultVal() {
        assertEquals(BitrateLimits.K320.name, PrefKeys.BITRATE_LIMIT.defaultVal)
        assertEquals(BitrateLimitType.NONE.name, PrefKeys.BITRATE_LIMIT_TYPE.defaultVal)
        assertEquals(WaveType.REAL.name, PrefKeys.WAVE_TYPE.defaultVal)
        assertEquals(LogPriority.INFO.name, PrefKeys.LOG_LEVEL.defaultVal)
    }

    @Test
    fun prefKeys_asEnum_parsesCorrectNames() {
        assertEquals(BitrateLimits.K64, PrefKeys.BITRATE_LIMIT.asEnum("K64"))
        assertEquals(BitrateLimits.K128, PrefKeys.BITRATE_LIMIT.asEnum("K128"))
        assertEquals(BitrateLimits.K256, PrefKeys.BITRATE_LIMIT.asEnum("K256"))
        assertEquals(BitrateLimits.K320, PrefKeys.BITRATE_LIMIT.asEnum("K320"))

        assertEquals(BitrateLimitType.NONE, PrefKeys.BITRATE_LIMIT_TYPE.asEnum("NONE"))
        assertEquals(BitrateLimitType.CELL, PrefKeys.BITRATE_LIMIT_TYPE.asEnum("CELL"))
        assertEquals(BitrateLimitType.ALL, PrefKeys.BITRATE_LIMIT_TYPE.asEnum("ALL"))

        assertEquals(WaveType.REAL, PrefKeys.WAVE_TYPE.asEnum("REAL"))
        assertEquals(WaveType.FAKE, PrefKeys.WAVE_TYPE.asEnum("FAKE"))
        assertEquals(WaveType.NONE, PrefKeys.WAVE_TYPE.asEnum("NONE"))
    }
}
