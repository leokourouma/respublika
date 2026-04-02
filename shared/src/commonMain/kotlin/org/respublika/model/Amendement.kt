package org.respublika.model

import kotlinx.serialization.*

@Serializable
data class AmendementWrapper(val amendement: AmendementData)

@Serializable
data class AmendementData(
    val uid: String,
    val identification: Identification,
    val signataires: Signataires,
    val corps: Corps,
    val cycleDeVie: CycleDeVie
)

@Serializable
data class Identification(val numeroLong: String)

@Serializable
data class Signataires(val auteur: AuteurRef)

@Serializable
data class AuteurRef(val acteurRef: String, val groupePolitiqueRef: String)

@Serializable
data class Corps(val contenuAuteur: ContenuAuteur)

@Serializable
data class ContenuAuteur(val dispositif: String, val exposeSommaire: String? = null)

@Serializable
data class CycleDeVie(val dateDepot: String, val sort: String? = null)