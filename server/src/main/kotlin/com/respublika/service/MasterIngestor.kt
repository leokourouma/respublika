// server/src/main/kotlin/com/respublika/service/MasterIngestor.kt
package com.respublika.service

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.models.VotePosition
import org.jetbrains.exposed.sql.*
import kotlinx.serialization.json.*
import java.io.File

data class FolderReport(
    val folder: String,
    var total: Int = 0,
    var success: Int = 0,
    var skipped: Int = 0,
    val errors: MutableList<Pair<String, String>> = mutableListOf()
)

class MasterIngestor(private val dataPath: String) {

    private val jsonHandler = Json { ignoreUnknownKeys = true }
    private val scrutinIngestor = ScrutinIngestor()

    suspend fun processAll(): String {
        println("🚀 Lancement de l'ingestion exhaustive ResPublika...")

        val reports = mutableListOf<FolderReport>()

        // 1. ORGANES (avec libellé abrégé et couleur pour les groupes politiques)
        reports += ingestFolder("organes") { json ->
            val root = jsonHandler.parseToJsonElement(json).jsonObject["organe"]?.jsonObject
            root?.let {
                dbQuery {
                    Organes.insertIgnore { row ->
                        row[uid] = it["uid"]?.jsonPrimitive?.content ?: ""
                        row[codeType] = it["codeType"]?.jsonPrimitive?.content ?: ""
                        row[libelle] = it["libelle"]?.jsonPrimitive?.content ?: ""
                        row[libelleAbrege] = it["libelleAbrege"]?.jsonPrimitive?.contentOrNull
                        row[couleurAssociee] = it["couleurAssociee"]?.jsonPrimitive?.contentOrNull
                    }
                }
            }
        }

        // 2. DOSSIERS
        reports += ingestFolder("dossiers") { json ->
            val root = jsonHandler.parseToJsonElement(json).jsonObject["dossierParlementaire"]?.jsonObject
            root?.let {
                dbQuery {
                    Dossiers.insertIgnore { row ->
                        row[uid] = it["uid"]?.jsonPrimitive?.content ?: ""
                        row[titre] = it["titreDossier"]?.jsonObject?.get("titre")?.jsonPrimitive?.content ?: ""
                    }
                }
            }
        }

        // 3. ACTEURS (Députés) avec groupe politique
        reports += ingestFolder("acteurs") { json ->
            val root = jsonHandler.parseToJsonElement(json).jsonObject["acteur"]?.jsonObject
            root?.let { actor ->
                val civil = actor["etatCivil"]?.jsonObject?.get("ident")?.jsonObject
                val nomComplet = "${civil?.get("prenom")?.jsonPrimitive?.content} ${civil?.get("nom")?.jsonPrimitive?.content}"
                val acteurUid = actor["uid"]?.jsonObject?.get("#text")?.jsonPrimitive?.content ?: ""
                val groupeUid = findGroupePolitique(actor)

                dbQuery {
                    Deputes.insertIgnore { row ->
                        row[idAn] = acteurUid
                        row[civPrenomNom] = nomComplet
                        row[slugUrl] = acteurUid.lowercase()
                        row[groupePolitiqueUid] = groupeUid
                    }
                }
            }
        }

        // 4. SCRUTINS + VOTES (batch insert par scrutin)
        reports += ingestFolder("scrutins") { json ->
            val root = jsonHandler.parseToJsonElement(json).jsonObject["scrutin"]?.jsonObject
            root?.let { s ->
                val scrutinUid = s["uid"]?.jsonPrimitive?.content ?: ""
                val synthese = s["syntheseVote"]?.jsonObject

                // Insérer le scrutin
                dbQuery {
                    Scrutins.insertIgnore { row ->
                        row[uid] = scrutinUid
                        row[titre] = s["titre"]?.jsonPrimitive?.content ?: ""
                        row[sort] = s["sort"]?.jsonObject?.get("libelle")?.jsonPrimitive?.content
                        row[dateVote] = s["dateScrutin"]?.jsonPrimitive?.content ?: ""
                        row[nombreVotants] = synthese?.get("nombreVotants")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        row[suffragesExprimes] = synthese?.get("suffragesExprimes")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        row[nbreSuffragesRequis] = synthese?.get("nbrSuffragesRequis")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    }
                }

                // Batch insert votes_groupes
                batchInsertVotesGroupes(scrutinUid, s)

                // Batch insert votes_individuels via ScrutinIngestor (avec Mises au Point)
                val votesMap = scrutinIngestor.ingestScrutin(json)
                if (votesMap.isNotEmpty()) {
                    dbQuery {
                        VotesIndividuels.batchInsert(votesMap.entries, ignore = true) { (deputeId, position) ->
                            this[VotesIndividuels.scrutinUid] = scrutinUid
                            this[VotesIndividuels.deputeId] = deputeId
                            this[VotesIndividuels.position] = position.name.lowercase()
                            this[VotesIndividuels.parDelegation] = false
                        }
                    }
                }
            }
        }

        // 5. DEPORTS
        reports += ingestFolder("deports") { json ->
            val root = jsonHandler.parseToJsonElement(json).jsonObject["deport"]?.jsonObject
            root?.let { d ->
                dbQuery {
                    Deports.insertIgnore { row ->
                        row[uid] = d["uid"]?.jsonPrimitive?.content ?: ""
                        row[deputeId] = d["refActeur"]?.jsonPrimitive?.content ?: ""
                        row[libellePortee] = d["portee"]?.jsonObject?.get("libelle")?.jsonPrimitive?.content ?: ""
                        row[explicationHtml] = d["explication"]?.jsonPrimitive?.content ?: ""
                    }
                }
            }
        }

        val summary = buildReport(reports)
        println(summary)
        return summary
    }

    /**
     * Batch insert des votes agrégés par groupe politique pour un scrutin
     */
    private suspend fun batchInsertVotesGroupes(scrutinUid: String, scrutin: JsonObject) {
        val groupes = scrutin["ventilationVotes"]
            ?.jsonObject?.get("organe")
            ?.jsonObject?.get("groupes")
            ?.jsonObject?.get("groupe") ?: return

        val groupeList = when (groupes) {
            is JsonArray -> groupes.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(groupes)
            else -> return
        }

        data class GroupeVote(
            val groupeRef: String,
            val nombreMembres: Int,
            val positionMajoritaire: String?,
            val pour: Int,
            val contre: Int,
            val abstentions: Int,
            val nonVotants: Int
        )

        val batch = groupeList.mapNotNull { g ->
            val groupeRef = g["organeRef"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val vote = g["vote"]?.jsonObject ?: return@mapNotNull null
            val decompte = vote["decompteVoix"]?.jsonObject

            GroupeVote(
                groupeRef = groupeRef,
                nombreMembres = g["nombreMembresGroupe"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                positionMajoritaire = vote["positionMajoritaire"]?.jsonPrimitive?.contentOrNull,
                pour = decompte?.get("pour")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                contre = decompte?.get("contre")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                abstentions = decompte?.get("abstentions")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                nonVotants = decompte?.get("nonVotants")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            )
        }

        if (batch.isNotEmpty()) {
            dbQuery {
                VotesGroupes.batchInsert(batch, ignore = true) { gv ->
                    this[VotesGroupes.scrutinUid] = scrutinUid
                    this[VotesGroupes.groupeUid] = gv.groupeRef
                    this[VotesGroupes.nombreMembres] = gv.nombreMembres
                    this[VotesGroupes.positionMajoritaire] = gv.positionMajoritaire
                    this[VotesGroupes.pour] = gv.pour
                    this[VotesGroupes.contre] = gv.contre
                    this[VotesGroupes.abstentions] = gv.abstentions
                    this[VotesGroupes.nonVotants] = gv.nonVotants
                }
            }
        }
    }

    /**
     * Trouve le groupe politique (GP) le plus récent d'un acteur
     */
    private fun findGroupePolitique(actor: JsonObject): String? {
        val mandats = actor["mandats"]?.jsonObject?.get("mandat") ?: return null

        val mandatList = when (mandats) {
            is JsonArray -> mandats
            is JsonObject -> JsonArray(listOf(mandats))
            else -> return null
        }

        // Chercher le mandat GP actif (sans dateFin) le plus récent
        return mandatList
            .filterIsInstance<JsonObject>()
            .filter { it["typeOrgane"]?.jsonPrimitive?.contentOrNull == "GP" }
            .sortedByDescending { it["dateDebut"]?.jsonPrimitive?.contentOrNull ?: "" }
            .firstOrNull { mandat ->
                val dateFin = mandat["dateFin"]?.jsonPrimitive?.contentOrNull
                dateFin == null || dateFin.isBlank()
            }
            ?.let { it["organes"]?.jsonObject?.get("organeRef")?.jsonPrimitive?.contentOrNull }
            ?: mandatList
                .filterIsInstance<JsonObject>()
                .filter { it["typeOrgane"]?.jsonPrimitive?.contentOrNull == "GP" }
                .sortedByDescending { it["dateDebut"]?.jsonPrimitive?.contentOrNull ?: "" }
                .firstOrNull()
                ?.let { it["organes"]?.jsonObject?.get("organeRef")?.jsonPrimitive?.contentOrNull }
    }

    private suspend fun ingestFolder(subFolder: String, action: suspend (String) -> Unit): FolderReport {
        val report = FolderReport(folder = subFolder)
        val folder = File("$dataPath/$subFolder")

        if (!folder.exists()) {
            println("⚠️ Dossier manquant : ${folder.absolutePath}")
            return report
        }

        val files = folder.listFiles { _, name ->
            name.endsWith(".json") && !name.contains(":")
        } ?: return report

        report.total = files.size

        files.forEachIndexed { index, file ->
            try {
                val rawJson = file.readText()
                if (rawJson.isBlank()) {
                    report.skipped++
                    return@forEachIndexed
                }

                action(rawJson)
                report.success++

                if (index % 50 == 0) {
                    print("\r🚀 $subFolder : [${index + 1}/${files.size}]")
                }
            } catch (e: Exception) {
                report.errors.add(file.name to (e.localizedMessage ?: "Erreur inconnue"))
            }
        }

        println("\r🚀 $subFolder : [${files.size}/${files.size}] ✅")
        return report
    }

    private fun buildReport(reports: List<FolderReport>): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("╔══════════════════════════════════════════════════════════════╗")
        sb.appendLine("║              📊 RAPPORT D'INGESTION RESPUBLIKA             ║")
        sb.appendLine("╠══════════════════════════════════════════════════════════════╣")

        var totalSuccess = 0
        var totalErrors = 0
        var totalSkipped = 0
        var totalFiles = 0

        for (r in reports) {
            totalSuccess += r.success
            totalErrors += r.errors.size
            totalSkipped += r.skipped
            totalFiles += r.total

            val status = if (r.errors.isEmpty()) "✅" else "⚠️"
            sb.appendLine("║  $status %-12s │ Total: %-6d │ OK: %-6d │ Err: %-4d ║".format(
                r.folder, r.total, r.success, r.errors.size
            ))
        }

        sb.appendLine("╠══════════════════════════════════════════════════════════════╣")
        sb.appendLine("║  TOTAL          │ Total: %-6d │ OK: %-6d │ Err: %-4d ║".format(
            totalFiles, totalSuccess, totalErrors
        ))
        if (totalSkipped > 0) {
            sb.appendLine("║  Fichiers vides ignorés : %-34d ║".format(totalSkipped))
        }
        sb.appendLine("╚══════════════════════════════════════════════════════════════╝")

        val foldersWithErrors = reports.filter { it.errors.isNotEmpty() }
        if (foldersWithErrors.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📋 DÉTAIL DES ERREURS :")
            for (r in foldersWithErrors) {
                sb.appendLine("  ── ${r.folder} (${r.errors.size} erreurs) ──")
                val displayed = r.errors.take(10)
                for ((fileName, cause) in displayed) {
                    sb.appendLine("    ❌ $fileName → $cause")
                }
                if (r.errors.size > 10) {
                    sb.appendLine("    ... et ${r.errors.size - 10} autres erreurs")
                }
            }
        }

        sb.appendLine()
        sb.appendLine("🏆 Ingestion exhaustive ResPublika terminée.")
        return sb.toString()
    }
}
