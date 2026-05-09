// server/src/main/kotlin/com/respublika/repository/DossierRepository.kt
package com.respublika.repository

import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.database.DossiersLegislatifs
import com.respublika.database.Scrutins
import com.respublika.model.DossierDetail
import com.respublika.model.DossierEtat
import com.respublika.model.DossierResume
import com.respublika.model.DossierScrutinSummary
import com.respublika.model.ScrutinDossierRef
import org.jetbrains.exposed.sql.*

open class DossierRepository(private val database: Database? = null) {

    /**
     * Paginated, filtered list. Default sort: most recent decision first
     * (NULLS LAST), then date_depot DESC as a tie-breaker for dormant dossiers.
     */
    open suspend fun list(
        etats: Set<DossierEtat>?,
        since: java.time.LocalDate?,
        query: String?,
        limit: Int,
        offset: Long,
    ): Pair<Long, List<DossierResume>> = dbQuery(database) {
        val q = DossiersLegislatifs.selectAll()
        if (!etats.isNullOrEmpty()) q.andWhere { DossiersLegislatifs.etat inList etats.toList() }
        if (since != null) {
            q.andWhere { DossiersLegislatifs.dateDerniereDecision greaterEq since }
        }
        if (!query.isNullOrBlank()) {
            q.andWhere { DossiersLegislatifs.titre.lowerCase() like "%${query.lowercase()}%" }
        }

        val total = q.count()

        val rows = q
            .orderBy(
                DossiersLegislatifs.dateDerniereDecision to SortOrder.DESC_NULLS_LAST,
                DossiersLegislatifs.dateDepot to SortOrder.DESC_NULLS_LAST,
            )
            .limit(limit).offset(offset)
            .toList()

        if (rows.isEmpty()) return@dbQuery 0L to emptyList()

        val uids = rows.map { it[DossiersLegislatifs.uid] }
        val scrutinCounts = Scrutins
            .select(Scrutins.dossierUid, Scrutins.uid.count())
            .where { Scrutins.dossierUid inList uids }
            .groupBy(Scrutins.dossierUid)
            .associate { it[Scrutins.dossierUid]!! to it[Scrutins.uid.count()] }

        val resumes = rows.map { row ->
            DossierResume(
                uid = row[DossiersLegislatifs.uid],
                titre = row[DossiersLegislatifs.titre],
                titreCourt = row[DossiersLegislatifs.titreCourt],
                type = row[DossiersLegislatifs.type].name,
                etat = row[DossiersLegislatifs.etat].name,
                legislature = row[DossiersLegislatifs.legislature],
                dateDepot = row[DossiersLegislatifs.dateDepot]?.toString(),
                dateDerniereDecision = row[DossiersLegislatifs.dateDerniereDecision]?.toString(),
                datePromulgation = row[DossiersLegislatifs.datePromulgation]?.toString(),
                numeroLoi = row[DossiersLegislatifs.numeroLoi],
                nbScrutins = scrutinCounts[row[DossiersLegislatifs.uid]] ?: 0L,
            )
        }
        total to resumes
    }

    /** Returns null when the dossier doesn't exist. */
    open suspend fun detail(uid: String, scrutinsLimit: Int = 100): DossierDetail? = dbQuery(database) {
        val row = DossiersLegislatifs
            .selectAll()
            .where { DossiersLegislatifs.uid eq uid }
            .singleOrNull()
            ?: return@dbQuery null

        val scrutins = Scrutins
            .selectAll()
            .where { Scrutins.dossierUid eq uid }
            .orderBy(Scrutins.dateVote, SortOrder.DESC)
            .limit(scrutinsLimit)
            .map {
                DossierScrutinSummary(
                    uid = it[Scrutins.uid],
                    titre = it[Scrutins.titre],
                    dateVote = it[Scrutins.dateVote],
                    sort = it[Scrutins.sort],
                    nombreVotants = it[Scrutins.nombreVotants],
                )
            }

        DossierDetail(
            uid = row[DossiersLegislatifs.uid],
            titre = row[DossiersLegislatifs.titre],
            titreCourt = row[DossiersLegislatifs.titreCourt],
            type = row[DossiersLegislatifs.type].name,
            etat = row[DossiersLegislatifs.etat].name,
            legislature = row[DossiersLegislatifs.legislature],
            dateDepot = row[DossiersLegislatifs.dateDepot]?.toString(),
            dateDerniereDecision = row[DossiersLegislatifs.dateDerniereDecision]?.toString(),
            datePromulgation = row[DossiersLegislatifs.datePromulgation]?.toString(),
            numeroLoi = row[DossiersLegislatifs.numeroLoi],
            scrutins = scrutins,
        )
    }

    /** Paginated scrutins under a dossier. Returns (total, rows). */
    open suspend fun scrutins(
        uid: String,
        limit: Int,
        offset: Long,
    ): Pair<Long, List<DossierScrutinSummary>> = dbQuery(database) {
        val q = Scrutins.selectAll().where { Scrutins.dossierUid eq uid }
        val total = q.count()
        val rows = q
            .orderBy(Scrutins.dateVote, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map {
                DossierScrutinSummary(
                    uid = it[Scrutins.uid],
                    titre = it[Scrutins.titre],
                    dateVote = it[Scrutins.dateVote],
                    sort = it[Scrutins.sort],
                    nombreVotants = it[Scrutins.nombreVotants],
                )
            }
        total to rows
    }

    /** Used by the scrutin detail route to embed the parent dossier reference. */
    open suspend fun refForScrutin(scrutinUid: String): ScrutinDossierRef? = dbQuery(database) {
        Scrutins
            .join(DossiersLegislatifs, JoinType.INNER, Scrutins.dossierUid, DossiersLegislatifs.uid)
            .select(DossiersLegislatifs.uid, DossiersLegislatifs.titre, DossiersLegislatifs.etat)
            .where { Scrutins.uid eq scrutinUid }
            .singleOrNull()
            ?.let {
                ScrutinDossierRef(
                    uid = it[DossiersLegislatifs.uid],
                    titre = it[DossiersLegislatifs.titre],
                    etat = it[DossiersLegislatifs.etat].name,
                )
            }
    }
}
