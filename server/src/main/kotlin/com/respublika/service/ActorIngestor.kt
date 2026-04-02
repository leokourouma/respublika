// server/src/main/kotlin/com/respublika/service/ActorIngestor.kt

package com.respublika.service

import com.respublika.models.Actor  // UNE SEULE FOIS
import kotlinx.serialization.json.*
import java.io.File

class ActorIngestor {

    /**
     * Ingests all JSON actor files from a directory.
     * Filters out system files like Windows Zone.Identifiers.
     */
    fun ingestAll(directoryPath: String): List<Actor> {
        val folder = File(directoryPath)
        
        println("🔍 Scanning directory: ${folder.absolutePath}")

        // Strict filter: Only .json files, excluding NTFS streams
        val files = folder.listFiles { _, name -> 
            name.endsWith(".json") && !name.contains(":") 
        }

        if (files.isNullOrEmpty()) {
            println("❌ No valid .json files found in $directoryPath")
            return emptyList()
        }

        return files.mapNotNull { file ->
            try {
                parseActor(file.readText())
            } catch (e: Exception) {
                println("⚠️ Parsing error on ${file.name}: ${e.message}")
                null
            }
        }
    }

    private fun parseActor(jsonString: String): Actor {
        val root = Json.parseToJsonElement(jsonString).jsonObject
        val acteur = root["acteur"]?.jsonObject ?: throw Exception("Missing 'acteur' key")
        
        // Basic identification
        val uid = acteur["uid"]?.jsonObject?.get("#text")?.jsonPrimitive?.content ?: ""
        val ident = acteur["etatCivil"]?.jsonObject?.get("ident")?.jsonObject
        val naissance = acteur["etatCivil"]?.jsonObject?.get("infoNaissance")?.jsonObject
        
        // Profession and HATVP Pivot
        val profession = acteur["profession"]?.jsonObject?.get("libelleCourant")?.jsonPrimitive?.content
        val uriHatvp = acteur["uri_hatvp"]?.jsonPrimitive?.content

        return Actor(
            uid = uid,
            civ = ident?.get("civ")?.jsonPrimitive?.content ?: "",
            prenom = ident?.get("prenom")?.jsonPrimitive?.content ?: "",
            nom = ident?.get("nom")?.jsonPrimitive?.content ?: "",
            dateNaissance = naissance?.get("dateNais")?.jsonPrimitive?.content ?: "",
            villeNaissance = naissance?.get("villeNais")?.jsonPrimitive?.content ?: "",
            profession = profession,
            uriHatvp = uriHatvp
        )
    }
}