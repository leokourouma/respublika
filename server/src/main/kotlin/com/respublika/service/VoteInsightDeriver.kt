package com.respublika.service

import com.respublika.model.VoteInsight
import com.respublika.model.VoteTypeObjet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class VoteInsightDeriver {

    fun derive(jsonString: String): VoteInsight =
        derive(Json.parseToJsonElement(jsonString))

    fun derive(scrutinJson: JsonElement): VoteInsight {
        val root = scrutinJson.asObjectOrNull()
            ?: throw IllegalArgumentException("Expected JSON object at root")
        val scrutin = root["scrutin"]?.asObjectOrNull()
            ?: throw IllegalArgumentException("Missing 'scrutin' object at root")
        val uid = scrutin["uid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required field 'scrutin.uid'")

        val warnings = mutableListOf<String>()

        val titre = scrutin["titre"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (titre == null) warnings += "scrutin.titre missing or blank"

        val titreCourt = titre?.let { deriveTitreCourt(it) }
        val typeObjet = titre?.let { classifyTypeObjet(it) }

        val synthese = scrutin["syntheseVote"]?.asObjectOrNull()
        if (synthese == null) warnings += "scrutin.syntheseVote missing"

        val estConsensuel = computeConsensuel(synthese, warnings)
        val tauxParticipation = computeTauxParticipation(synthese, warnings)

        val demandeurTexte = scrutin["demandeur"]?.asObjectOrNull()
            ?.get("texte")?.jsonPrimitive?.contentOrNull
        // TODO: map extracted group names to canonical group codes (organes.uid).
        val demandeursGroupes = parseDemandeurs(demandeurTexte)

        return VoteInsight(
            voteUid = uid,
            titreCourt = titreCourt,
            typeObjet = typeObjet,
            estConsensuel = estConsensuel,
            demandeursGroupes = demandeursGroupes,
            tauxParticipation = tauxParticipation,
            warnings = warnings.toList(),
        )
    }

    private fun deriveTitreCourt(titre: String): String {
        val original = titre.trim()

        AMENDEMENT_PREFIX.find(original)?.let { match ->
            val rest = original.substring(match.range.last + 1).trim()
            val candidate = "Amendement: $rest"
            return if (candidate.length < 5) original else candidate
        }

        var working = original
        for (pattern in STRIP_PREFIXES) {
            val match = pattern.find(working)
            if (match != null) {
                working = working.substring(match.range.last + 1).trim()
                break
            }
        }

        if (working.length < 5) return original
        return working.replaceFirstChar { it.uppercase() }
    }

    private fun classifyTypeObjet(titre: String): VoteTypeObjet {
        val lower = titre.lowercase().trim()
        return when {
            "commission d'enquête" in lower -> VoteTypeObjet.RESOLUTION_COMMISSION_ENQUETE
            "motion de censure" in lower -> VoteTypeObjet.MOTION_CENSURE
            "déclaration de politique générale" in lower -> VoteTypeObjet.DECLARATION_POLITIQUE_GENERALE
            "l'ensemble du projet de loi" in lower -> VoteTypeObjet.PROJET_LOI_ENSEMBLE
            "l'ensemble de la proposition de loi" in lower -> VoteTypeObjet.PROPOSITION_LOI_ENSEMBLE
            "amendement" in lower -> VoteTypeObjet.AMENDEMENT
            lower.startsWith("article") -> VoteTypeObjet.ARTICLE
            else -> VoteTypeObjet.AUTRE
        }
    }

    private fun computeConsensuel(synthese: JsonObject?, warnings: MutableList<String>): Boolean {
        val decompte = synthese?.get("decompte")?.asObjectOrNull() ?: return false
        val pour = decompte["pour"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val contre = decompte["contre"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (pour == null || contre == null) {
            warnings += "syntheseVote.decompte.pour/contre missing or unparseable"
            return false
        }
        val exprimes = pour + contre
        if (exprimes < 50) return false
        val pourRatio = pour.toDouble() / exprimes
        val contreRatio = contre.toDouble() / exprimes
        return pourRatio >= CONSENSUS_THRESHOLD || contreRatio >= CONSENSUS_THRESHOLD
    }

    private fun computeTauxParticipation(synthese: JsonObject?, warnings: MutableList<String>): Double? {
        if (synthese == null) return null
        val raw = synthese["nombreVotants"]?.jsonPrimitive?.contentOrNull
        if (raw.isNullOrBlank()) {
            warnings += "syntheseVote.nombreVotants missing"
            return null
        }
        val nombreVotants = raw.toIntOrNull()
        if (nombreVotants == null) {
            warnings += "syntheseVote.nombreVotants unparseable: '$raw'"
            return null
        }
        return nombreVotants.toDouble() / TOTAL_SEATS
    }

    private fun parseDemandeurs(texte: String?): List<String> {
        if (texte.isNullOrBlank()) return emptyList()
        return QUOTED_NAME.findAll(texte)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    companion object {
        // TODO: make legislature-dependent (currently hardcoded for Assemblée Nationale).
        const val TOTAL_SEATS = 577
        private const val CONSENSUS_THRESHOLD = 0.90

        private val AMENDEMENT_PREFIX = Regex(
            """^l'amendement n° ?\d+ """,
            RegexOption.IGNORE_CASE,
        )

        private val STRIP_PREFIXES = listOf(
            Regex(
                """^l'ensemble (du|de la) (projet|proposition) de loi """,
                RegexOption.IGNORE_CASE,
            ),
            // Spec said "(de loi )?"; widened to also match "de résolution" — real
            // fixtures use "proposition de résolution tendant à ...".
            // Trailing article ("la "/"le "/"l'"/"les ") stripped so that
            // "tendant à la création de X" yields "Création de X".
            Regex(
                """^l'article unique (du|de la) (projet|proposition|résolution)( de (loi|résolution))? (tendant à|visant à|relative à|relatif à) (la |le |l'|les )?""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """^la (proposition|projet) de (loi|résolution) (tendant à|visant à|relative à|relatif à) (la |le |l'|les )?""",
                RegexOption.IGNORE_CASE,
            ),
        )

        private val QUOTED_NAME = Regex(""""([^"]+)"""")
    }
}
