// shared/src/commonMain/kotlin/com/respublika/models/Actor.kt
package com.respublika.models // Doit correspondre à l'import du serveur

import kotlinx.serialization.*

@Serializable
data class Actor( // Renommé 'Actor' pour correspondre au serveur
    val uid: String,
    val prenom: String,
    val nom: String,
    val civ: String = "", // Ajouté pour éviter l'erreur de compilation dans Ingestor
    val dateNaissance: String = "",
    val villeNaissance: String = "",
    val profession: String? = null,
    val uriHatvp: String? = null
)

// Garde tes classes de mapping JSON ici si tu les utilises pour le parsing
@Serializable
data class ActeurWrapper(val acteur: ActeurData)

@Serializable
@SerialName("acteur")
data class ActeurData(
    val uid: UidData,
    val etatCivil: EtatCivil,
    val profession: Profession? = null,
    val uri_hatvp: String? = null
)

@Serializable
data class UidData(
    @SerialName("#text") val text: String
)

@Serializable
data class EtatCivil(val ident: Ident)

@Serializable
data class Ident(val prenom: String, val nom: String)

@Serializable
data class Profession(val libelleCourant: String? = null)

@Serializable
data class Acteur(
    val uid: String,
    val prenom: String,
    val nom: String,
    val profession: String? = null,
    val uriHatvp: String? = null
)