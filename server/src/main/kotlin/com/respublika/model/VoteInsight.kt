package com.respublika.model

data class VoteInsight(
    val voteUid: String,
    val titreCourt: String?,
    val typeObjet: VoteTypeObjet?,
    val estConsensuel: Boolean,
    val demandeursGroupes: List<String>,
    val tauxParticipation: Double?,
    val warnings: List<String>,
)
