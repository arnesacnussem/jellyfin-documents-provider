package a.sac.jellyfindocumentsprovider.database.entities

import a.sac.jellyfindocumentsprovider.utils.PrefEnums.WaveType
import android.database.MatrixCursor
import com.maxmpz.poweramp.player.TrackProviderConsts
import com.maxmpz.poweramp.player.TrackProviderHelper
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class PowerampExtraInfo(
    @Id var id: Long = 0,
    val lyrics: String?
) {
    private val hasLyrics get() = !lyrics.isNullOrBlank()
    fun appendTo(row: MatrixCursor.RowBuilder, waveType: WaveType = WaveType.REAL) {
        val flags = if (hasLyrics) TrackProviderConsts.FLAG_HAS_LYRICS else 0
        row.add(TrackProviderConsts.COLUMN_FLAGS, flags)
        row.add(TrackProviderConsts.COLUMN_TRACK_LYRICS_SYNCED, lyrics)

        when (waveType) {
            WaveType.NONE ->
                row.add(
                    TrackProviderConsts.COLUMN_TRACK_WAVE,
                    TrackProviderHelper.floatsToBytes(FloatArray(0))
                )

            WaveType.FAKE ->
                row.add(
                    TrackProviderConsts.COLUMN_TRACK_WAVE,
                    TrackProviderHelper.floatsToBytes(getFakeWave())
                )

            WaveType.REAL -> {}
        }
    }

    private fun getFakeWave() = FloatArray(100) { -1f + 2f * Math.random().toFloat() }

}