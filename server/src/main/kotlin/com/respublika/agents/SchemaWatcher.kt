// server/src/main/kotlin/com/respublika/agents/SchemaWatcher.kt
package com.respublika.agents

import com.respublika.database.AgentRuns
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.service.NotificationService
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.insert
import java.io.File
import java.time.Instant

enum class SchemaSeverity { OK, INFO, BREAKING }

data class FolderDiff(
    val folder: String,
    val newFields: List<String>,
    val removedFields: List<String>,
    val severity: SchemaSeverity
)

data class SchemaWatcherResult(
    val overallSeverity: SchemaSeverity,
    val folders: List<FolderDiff>
)

class SchemaWatcher(private val dataPath: String) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(): SchemaWatcherResult {
        val startedAt = Instant.now()

        val snapshot = loadSnapshot()
        val diffs = mutableListOf<FolderDiff>()
        var foldersChecked = 0

        for ((folder, knownPaths) in snapshot) {
            val sampleFile = findSampleFile(folder)
            if (sampleFile == null) {
                println("  schema-watcher: no sample file found for '$folder', skipping")
                continue
            }

            foldersChecked++
            val livePaths = try {
                val root = json.parseToJsonElement(sampleFile.readText())
                extractPaths(root, "", 0)
            } catch (e: Exception) {
                println("  schema-watcher: failed to parse sample for '$folder': ${e.message}")
                continue
            }

            val knownSet = knownPaths.toSet()
            val newFields = (livePaths - knownSet).sorted()
            val removedFields = (knownSet - livePaths).sorted()

            val severity = when {
                removedFields.isNotEmpty() -> SchemaSeverity.BREAKING
                newFields.isNotEmpty() -> SchemaSeverity.INFO
                else -> SchemaSeverity.OK
            }

            diffs += FolderDiff(folder, newFields, removedFields, severity)
        }

        val overallSeverity = when {
            diffs.any { it.severity == SchemaSeverity.BREAKING } -> SchemaSeverity.BREAKING
            diffs.any { it.severity == SchemaSeverity.INFO } -> SchemaSeverity.INFO
            else -> SchemaSeverity.OK
        }

        val result = SchemaWatcherResult(overallSeverity, diffs)

        // Notify on breaking changes
        if (overallSeverity == SchemaSeverity.BREAKING) {
            val breakingFolders = diffs.filter { it.severity == SchemaSeverity.BREAKING }
            val body = breakingFolders.joinToString("\n") { d ->
                "${d.folder}: removed ${d.removedFields.size} fields: ${d.removedFields.joinToString(", ")}"
            }
            NotificationService.notifyBreaking("Schema BREAKING change detected", body)
        }

        // Persist to agent_runs
        val finishedAt = Instant.now()
        try {
            saveRun(result, foldersChecked, startedAt, finishedAt)
        } catch (e: Exception) {
            println("  schema-watcher: failed to save agent run: ${e.message}")
        }

        println("  Agent schema-watcher: $overallSeverity | folders=$foldersChecked")
        return result
    }

    // ── Field path extractor ──────────────────────────────────────

    private fun extractPaths(element: JsonElement, prefix: String, depth: Int): Set<String> {
        if (depth > 5) return emptySet()

        val paths = mutableSetOf<String>()

        when (element) {
            is JsonObject -> {
                for ((key, value) in element) {
                    val path = if (prefix.isEmpty()) key else "$prefix.$key"
                    paths += path
                    paths += extractPaths(value, path, depth + 1)
                }
            }
            is JsonArray -> {
                if (element.isNotEmpty()) {
                    paths += extractPaths(element[0], "$prefix[]", depth + 1)
                }
            }
            else -> { /* primitive — leaf node, already added by parent */ }
        }

        return paths
    }

    // ── Snapshot loading ──────────────────────────────────────────

    private fun loadSnapshot(): Map<String, List<String>> {
        val stream = this::class.java.classLoader.getResourceAsStream("schema_snapshot.json")
            ?: throw IllegalStateException("schema_snapshot.json not found in resources")

        val raw = stream.bufferedReader().readText()
        val root = json.parseToJsonElement(raw).jsonObject

        return root.mapValues { (_, value) ->
            value.jsonArray.map { it.jsonPrimitive.content }
        }
    }

    // ── Sample file finder ────────────────────────────────────────

    private fun findSampleFile(folder: String): File? {
        val dir = File("$dataPath/$folder")
        if (!dir.exists()) return null

        return dir.listFiles { _, name ->
            name.endsWith(".json") && !name.contains(":")
        }?.firstOrNull()
    }

    // ── Persist agent run ─────────────────────────────────────────

    private suspend fun saveRun(
        result: SchemaWatcherResult,
        foldersChecked: Int,
        startedAt: Instant,
        finishedAt: Instant
    ) {
        val status = when (result.overallSeverity) {
            SchemaSeverity.OK -> "success"
            SchemaSeverity.INFO -> "warning"
            SchemaSeverity.BREAKING -> "failure"
        }

        val findingsJson = buildJsonObject {
            put("overall_severity", result.overallSeverity.name)
            putJsonObject("folders") {
                for (d in result.folders) {
                    putJsonObject(d.folder) {
                        putJsonArray("new_fields") {
                            d.newFields.forEach { add(it) }
                        }
                        putJsonArray("removed_fields") {
                            d.removedFields.forEach { add(it) }
                        }
                        put("severity", d.severity.name)
                    }
                }
            }
        }

        val affectedCount = result.folders.sumOf { it.newFields.size + it.removedFields.size }

        dbQuery {
            AgentRuns.insert { row ->
                row[agentName] = "schema-watcher"
                row[triggeredBy] = "pre-ingest"
                row[AgentRuns.startedAt] = startedAt.toString()
                row[AgentRuns.finishedAt] = finishedAt.toString()
                row[AgentRuns.status] = status
                row[recordsProcessed] = foldersChecked
                row[recordsAffected] = affectedCount
                row[findings] = findingsJson.toString()
                row[errorDetail] = if (result.overallSeverity == SchemaSeverity.BREAKING) {
                    result.folders
                        .filter { it.severity == SchemaSeverity.BREAKING }
                        .joinToString("\n") { "${it.folder}: removed ${it.removedFields}" }
                } else null
            }
        }
    }
}
