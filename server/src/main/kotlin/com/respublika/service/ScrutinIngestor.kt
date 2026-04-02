// server/src/main/kotlin/com/respublika/service/ScrutinIngestor.kt
package com.respublika.service

import com.respublika.models.VotePosition
import kotlinx.serialization.json.*

class ScrutinIngestor {

    fun ingestScrutin(jsonString: String): Map<String, VotePosition> {
        val root = Json.parseToJsonElement(jsonString).jsonObject
        val scrutin = root["scrutin"]?.jsonObjectOrNull ?: return emptyMap()
        val votesMap = mutableMapOf<String, VotePosition>()

        // Navigation corrigée : 'organe' au lieu de 'extraction'
        val groupes = scrutin["ventilationVotes"]?.jsonObjectOrNull
            ?.get("organe")?.jsonObjectOrNull
            ?.get("groupes")?.jsonObjectOrNull
            ?.get("groupe")

        when (groupes) {
            is JsonArray -> groupes.forEach { parseGroupe(it.jsonObjectOrNull, votesMap) }
            is JsonObject -> parseGroupe(groupes, votesMap)
            else -> {}
        }

        // Application de la Règle d'Or : Les Mises au Point écrasent le vote initial
        applyMisesAuPoint(scrutin["miseAuPoint"], votesMap)
        
        return votesMap
    }

    private fun parseGroupe(groupe: JsonObject?, map: MutableMap<String, VotePosition>) {
        val voteGroupe = groupe?.get("vote")?.jsonObjectOrNull ?: return
        
        listOf("pours" to VotePosition.POUR, "contres" to VotePosition.CONTRE, "abstentions" to VotePosition.ABSTENTION)
            .forEach { (key, pos) ->
                mapCategory(voteGroupe[key], pos, map)
            }
    }

    private fun mapCategory(cat: JsonElement?, pos: VotePosition, map: MutableMap<String, VotePosition>) {
        val votants = cat?.jsonObjectOrNull?.get("votant")
        when (votants) {
            is JsonArray -> votants.forEach { extractId(it, pos, map) }
            is JsonObject -> extractId(votants, pos, map)
            else -> {}
        }
    }

    private fun extractId(element: JsonElement, pos: VotePosition, map: MutableMap<String, VotePosition>) {
        val id = element.jsonObjectOrNull?.get("acteurRef")?.jsonPrimitive?.contentOrNull
        if (!id.isNullOrEmpty()) {
            map[id] = pos
        }
    }

    private fun applyMisesAuPoint(mapObj: JsonElement?, map: MutableMap<String, VotePosition>) {
        val obj = mapObj?.jsonObjectOrNull ?: return
        
        listOf("pours" to VotePosition.POUR, "contres" to VotePosition.CONTRE, "abstentions" to VotePosition.ABSTENTION)
            .forEach { (key, pos) ->
                val category = obj[key]?.jsonObjectOrNull
                val votants = category?.get("votant")
                
                val processVotant = { element: JsonElement ->
                    val id = element.jsonObjectOrNull?.get("acteurRef")?.jsonPrimitive?.contentOrNull
                    if (!id.isNullOrEmpty()) {
                        // Log de rectification conformément au Cahier des Charges
                        if (map.containsKey(id) && map[id] != pos) {
                            println("🔄 Rectification (MAP) : $id | ${map[id]} -> $pos")
                        }
                        map[id] = pos
                    }
                }

                when (votants) {
                    is JsonArray -> votants.forEach { processVotant(it) }
                    is JsonObject -> processVotant(votants)
                    else -> {}
                }
            }
    }

    // Extension de sécurité pour éviter le crash JsonNull
    private val JsonElement.jsonObjectOrNull: JsonObject?
        get() = if (this is JsonObject) this else null
}