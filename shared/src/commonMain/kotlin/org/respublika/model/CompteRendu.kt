// shared/src/commonMain/kotlin/org/respublika/model/CompteRendu.kt
package org.respublika.model

import kotlinx.serialization.*

@Serializable
data class Intervention(
    val acteurId: String?,
    val texte: String,
    val stime: Double?, // Timestamp de séance pour le direct
    val ambiance: List<String> = emptyList() // Tags comme "Applaudissements"
)