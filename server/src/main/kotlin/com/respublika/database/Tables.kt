// server/src/main/kotlin/com/respublika/database/Tables.kt

package com.respublika.database

import org.jetbrains.exposed.sql.Table

object DeputesTable : Table("deputes") {
    val idAn = varchar("id_an", 20)
    val civPrenomNom = text("civ_prenom_nom")
    val profession = text("profession").nullable()
    val uriHatvp = text("uri_hatvp").nullable()
    val twitter = text("twitter").nullable()
    val slugUrl = text("slug_url")

    override val primaryKey = PrimaryKey(idAn)
}