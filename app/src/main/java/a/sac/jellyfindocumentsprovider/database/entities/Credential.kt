package a.sac.jellyfindocumentsprovider.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity()
data class Credential(
    /**
     * server and user id
     */
    @PrimaryKey val uid: String,
    val server: String,
    val token: String,
    val username: String,
    val serverName: String,
    /**
     * Pair<ID, NAME>
     */
    val library: Map<String, String>
)