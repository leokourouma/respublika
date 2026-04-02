// shared/src/commonMain/kotlin/org/respublika/model/Depute.kt
package org.respublika.model

import kotlinx.serialization.Serializable

@Serializable
data class Depute(
    val idAn: String,          // Format: PA368
    val nomComplet: String,
    val groupePolitique: String?,
    val circonscriptionId: String?,
    val estGouvernement: Boolean = false,
    val uriHatvp: String? = null,
    val reseauxSociaux: Map<String, String> = emptyMap()
)