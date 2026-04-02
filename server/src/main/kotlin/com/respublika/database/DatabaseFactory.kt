// server/src/main/kotlin/com/respublika/database/DatabaseFactory.kt
package com.respublika.database

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

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val jdbcUrl = "jdbc:postgresql://respublika-db:5432/respublika_db"
        val user = "respublika_admin"
        val password = "Nipanimay666"

        val database = Database.connect(jdbcUrl, driverClassName, user, password)

        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Organes, Deputes, Dossiers, Scrutins, VotesGroupes, VotesIndividuels, Deports)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction { block() }
}
