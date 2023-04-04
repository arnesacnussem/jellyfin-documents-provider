package a.sac.jellyfindocumentsprovider.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index


@Entity
data class Credential(

    @Id var id: Long = 0,
    @Index val uid: String,
    val server: String,
    val token: String,
    val username: String,
    val serverName: String,
    /**
     * Pair<ID, NAME>
     */
    val library: Map<String, String>
)