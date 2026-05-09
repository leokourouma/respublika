// server/src/main/kotlin/com/respublika/model/Dossier.kt
package com.respublika.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate

/**
 * Coarse classification of a dossier — drives the UI card layout.
 * Mapping rules are documented in V5__Add_Dossiers_Legislatifs.sql.
 */
enum class DossierType {
    LOI_ORDINAIRE,
    LOI_ORGANIQUE,
    PROPOSITION_RESOLUTION,
    MOTION_CENSURE,
    AUTRE,
}

/**
 * Lifecycle state of a dossier — derived from the most recent decision-type acte.
 * Mapping rules are documented in V5__Add_Dossiers_Legislatifs.sql.
 */
enum class DossierEtat {
    DORMANT,
    EN_COURS,
    ADOPTE,
    REJETE,
    RETIRE,
    PROMULGUE,
}

/**
 * How a scrutin → dossier link was established. `voteRef` is exact (confidence 1.0),
 * `seance` is heuristic (confidence 0.85 for a unique match, lower when ambiguous).
 */
enum class LinkMethod {
    voteRef,
    seance,
}

/**
 * In-memory representation of a `dossiers_legislatifs` row.
 * `rawJson` is the full `dossierParlementaire` object (V2-proofing for amendments,
 * themes extraction, document linkage in later phases).
 */
data class Dossier(
    val uid: String,
    val titre: String,
    val titreCourt: String?,
    val type: DossierType,
    val etat: DossierEtat,
    val legislature: Short,
    val dateDepot: LocalDate?,
    val dateDerniereDecision: LocalDate?,
    val datePromulgation: LocalDate?,
    val numeroLoi: String?,
    val themes: List<String>?,
    val rawJson: JsonElement,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Wire format for /api/dossiers list items. Field names are snake_case per
 * project convention (see CLAUDE.md §4.5).
 */
@Serializable
data class DossierResume(
    val uid: String,
    val titre: String,
    @SerialName("titre_court") val titreCourt: String?,
    val type: String,
    val etat: String,
    val legislature: Short,
    @SerialName("date_depot") val dateDepot: String?,
    @SerialName("date_derniere_decision") val dateDerniereDecision: String?,
    @SerialName("date_promulgation") val datePromulgation: String?,
    @SerialName("numero_loi") val numeroLoi: String?,
    @SerialName("nb_scrutins") val nbScrutins: Long,
)

/**
 * Wire format for /api/dossiers/{uid}. Includes a list of associated scrutins
 * (those resolved to this dossier via voteRef or unique séance).
 */
@Serializable
data class DossierDetail(
    val uid: String,
    val titre: String,
    @SerialName("titre_court") val titreCourt: String?,
    val type: String,
    val etat: String,
    val legislature: Short,
    @SerialName("date_depot") val dateDepot: String?,
    @SerialName("date_derniere_decision") val dateDerniereDecision: String?,
    @SerialName("date_promulgation") val datePromulgation: String?,
    @SerialName("numero_loi") val numeroLoi: String?,
    val scrutins: List<DossierScrutinSummary>,
)

@Serializable
data class DossierScrutinSummary(
    val uid: String,
    val titre: String,
    @SerialName("date_vote") val dateVote: String,
    val sort: String?,
    @SerialName("nombre_votants") val nombreVotants: Int?,
)

/**
 * Minimal dossier reference embedded in a scrutin response. The link
 * confidence and method are NOT exposed in the public API.
 */
@Serializable
data class ScrutinDossierRef(
    val uid: String,
    val titre: String,
    val etat: String,
)

fun DossierResume.toJsonElement(): JsonElement = buildJsonObject {
    put("uid", JsonPrimitive(uid))
    put("titre", JsonPrimitive(titre))
    put("titre_court", titreCourt?.let { JsonPrimitive(it) } ?: JsonNull)
    put("type", JsonPrimitive(type))
    put("etat", JsonPrimitive(etat))
    put("legislature", JsonPrimitive(legislature.toInt()))
    put("date_depot", dateDepot?.let { JsonPrimitive(it) } ?: JsonNull)
    put("date_derniere_decision", dateDerniereDecision?.let { JsonPrimitive(it) } ?: JsonNull)
    put("date_promulgation", datePromulgation?.let { JsonPrimitive(it) } ?: JsonNull)
    put("numero_loi", numeroLoi?.let { JsonPrimitive(it) } ?: JsonNull)
    put("nb_scrutins", JsonPrimitive(nbScrutins))
}

fun DossierScrutinSummary.toJsonElement(): JsonElement = buildJsonObject {
    put("uid", JsonPrimitive(uid))
    put("titre", JsonPrimitive(titre))
    put("date_vote", JsonPrimitive(dateVote))
    put("sort", sort?.let { JsonPrimitive(it) } ?: JsonNull)
    put("nombre_votants", nombreVotants?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun DossierDetail.toJsonElement(): JsonElement = buildJsonObject {
    put("uid", JsonPrimitive(uid))
    put("titre", JsonPrimitive(titre))
    put("titre_court", titreCourt?.let { JsonPrimitive(it) } ?: JsonNull)
    put("type", JsonPrimitive(type))
    put("etat", JsonPrimitive(etat))
    put("legislature", JsonPrimitive(legislature.toInt()))
    put("date_depot", dateDepot?.let { JsonPrimitive(it) } ?: JsonNull)
    put("date_derniere_decision", dateDerniereDecision?.let { JsonPrimitive(it) } ?: JsonNull)
    put("date_promulgation", datePromulgation?.let { JsonPrimitive(it) } ?: JsonNull)
    put("numero_loi", numeroLoi?.let { JsonPrimitive(it) } ?: JsonNull)
    put("scrutins", buildJsonArray { scrutins.forEach { add(it.toJsonElement()) } })
}

fun ScrutinDossierRef.toJsonElement(): JsonElement = buildJsonObject {
    put("uid", JsonPrimitive(uid))
    put("titre", JsonPrimitive(titre))
    put("etat", JsonPrimitive(etat))
}
