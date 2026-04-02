// shared/src/commonMain/kotlin/com/respublika/models/Vote.kt
package com.respublika.models

import kotlinx.serialization.Serializable

@Serializable
enum class VotePosition { POUR, CONTRE, ABSTENTION, NON_VOTANT }

@Serializable
data class Scrutin(
    val uid: String,
    val titre: String,
    val date: String,
    val sort: String, // Adopté ou Rejeté
    val votes: Map<String, VotePosition> = emptyMap() // Map<idAn, Position>
)