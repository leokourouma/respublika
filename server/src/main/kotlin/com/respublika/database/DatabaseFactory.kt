// server/src/main/kotlin/com/respublika/database/DatabaseFactory.kt
package com.respublika.database

import com.respublika.model.VoteTypeObjet
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object Organes : Table("organes") {
    val uid = varchar("uid", 50)
    val codeType = varchar("code_type", 50)
    val libelle = text("libelle")
    val libelleAbrege = varchar("libelle_abrege", 100).nullable()
    val couleurAssociee = varchar("couleur_associee", 10).nullable()
    override val primaryKey = PrimaryKey(uid)
}

object Deputes : Table("deputes") {
    val idAn = varchar("id_an", 20)
    val idHatvp = varchar("id_hatvp", 100).nullable()
    val civPrenomNom = text("civ_prenom_nom")
    val profession = text("profession").nullable()
    val groupePolitiqueUid = varchar("groupe_politique_uid", 50).nullable()
    val slugUrl = text("slug_url")
    override val primaryKey = PrimaryKey(idAn)
}

object Dossiers : Table("dossiers") {
    val uid = varchar("uid", 50)
    val titre = text("titre")
    override val primaryKey = PrimaryKey(uid)
}

object Scrutins : Table("scrutins") {
    val uid = varchar("uid", 50)
    val titre = text("titre")
    val sort = text("sort").nullable()
    val dateVote = varchar("date_vote", 50)
    val nombreVotants = integer("nombre_votants").nullable()
    val suffragesExprimes = integer("suffrages_exprimes").nullable()
    val nbreSuffragesRequis = integer("nbre_suffrages_requis").nullable()
    override val primaryKey = PrimaryKey(uid)
}

object VotesGroupes : IntIdTable("votes_groupes") {
    val scrutinUid = varchar("scrutin_uid", 50)
    val groupeUid = varchar("groupe_uid", 50)
    val nombreMembres = integer("nombre_membres").default(0)
    val positionMajoritaire = text("position_majoritaire").nullable()
    val pour = integer("pour").default(0)
    val contre = integer("contre").default(0)
    val abstentions = integer("abstentions").default(0)
    val nonVotants = integer("non_votants").default(0)
}

object VotesIndividuels : IntIdTable("votes_individuels") {
    val scrutinUid = varchar("scrutin_uid", 50)
    val deputeId = varchar("depute_id", 20)
    val position = text("position")
    val parDelegation = bool("par_delegation").default(false)
}

object Deports : Table("deports") {
    val uid = varchar("uid", 50)
    val deputeId = varchar("depute_id", 20)
    val libellePortee = text("libelle_portee")
    val explicationHtml = text("explication_html")
    override val primaryKey = PrimaryKey(uid)
}

object AgentRuns : IntIdTable("agent_runs") {
    val agentName = varchar("agent_name", 100)
    val triggeredBy = varchar("triggered_by", 100)
    val startedAt = varchar("started_at", 50)
    val finishedAt = varchar("finished_at", 50)
    val status = varchar("status", 20)
    val recordsProcessed = integer("records_processed")
    val recordsAffected = integer("records_affected")
    val findings = text("findings")
    val errorDetail = text("error_detail").nullable()
}

object VoteInsights : IntIdTable("vote_insights") {
    val scrutinUid = varchar("scrutin_uid", 50).uniqueIndex()
    val titreCourt = text("titre_court").nullable()
    val typeObjet = enumerationByName<VoteTypeObjet>("type_objet", 50).nullable()
    val estConsensuel = bool("est_consensuel").default(false)
    val demandeursGroupes = array<String>("demandeurs_groupes").nullable()
    val tauxParticipation = double("taux_participation").nullable()
    val insightsVersion = integer("insights_version").default(1)
    val computedAt = varchar("computed_at", 50)
}

object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = text("password_hash")
    val nom = varchar("nom", 200)
    val localite = varchar("localite", 300)
    val emailVerified = bool("email_verified").default(true)
    val createdAt = varchar("created_at", 50).default("")
    val updatedAt = varchar("updated_at", 50).default("")
}

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val jdbcUrl = "jdbc:postgresql://respublika-db:5432/respublika_db"
        val user = "respublika_admin"
        val password = "Nipanimay666"

        val database = Database.connect(jdbcUrl, driverClassName, user, password)

        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Organes, Deputes, Dossiers, Scrutins, VotesGroupes, VotesIndividuels, Deports, VoteInsights, Users)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction { block() }
}
