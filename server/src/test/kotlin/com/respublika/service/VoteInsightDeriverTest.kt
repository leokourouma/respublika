package com.respublika.service

import com.respublika.model.VoteTypeObjet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoteInsightDeriverTest {

    private val deriver = VoteInsightDeriver()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "fixture not found: /fixtures/$name"
        }.bufferedReader().use { it.readText() }

    private fun wrap(scrutinBody: String): String = """{"scrutin": $scrutinBody}"""

    // ---------------------------------------------------------------------
    // Happy path against the real V3 fixture
    // ---------------------------------------------------------------------

    @Test
    fun `derives insight from real VTANR5L17V3 fixture`() {
        val json = loadFixture("scrutin_VTANR5L17V3.json")
        val insight = deriver.derive(json)

        assertEquals("VTANR5L17V3", insight.voteUid)
        assertEquals(VoteTypeObjet.RESOLUTION_COMMISSION_ENQUETE, insight.typeObjet)
        assertTrue(insight.estConsensuel, "204/204 pour should be consensual")

        // After stripping "l'article unique de la proposition de résolution tendant à la "
        // and capitalizing.
        val titreCourt = assertNotNull(insight.titreCourt)
        assertTrue(
            titreCourt.startsWith("Création d'une commission d'enquête"),
            "expected titreCourt to start with 'Création d'une commission d'enquête', got: $titreCourt",
        )

        // Real fixture has exactly one demandeur (LFI-NFP); spec example listed three but
        // that doesn't match this scrutin.
        assertEquals(
            listOf("La France insoumise - Nouveau Front Populaire"),
            insight.demandeursGroupes,
        )

        val taux = assertNotNull(insight.tauxParticipation)
        assertEquals(204.0 / 577.0, taux, absoluteTolerance = 1e-9)

        assertEquals(emptyList(), insight.warnings, "happy path should produce no warnings")
    }

    // ---------------------------------------------------------------------
    // Hard integrity errors
    // ---------------------------------------------------------------------

    @Test
    fun `throws when scrutin envelope is missing`() {
        assertFails { deriver.derive("""{"foo": {"uid": "X"}}""") }
    }

    @Test
    fun `throws when scrutin uid is missing`() {
        assertFails { deriver.derive(wrap("""{"titre": "anything"}""")) }
    }

    @Test
    fun `throws when scrutin uid is blank`() {
        assertFails { deriver.derive(wrap("""{"uid": ""}""")) }
    }

    // ---------------------------------------------------------------------
    // Defensive null handling
    // ---------------------------------------------------------------------

    @Test
    fun `null demandeur produces empty demandeursGroupes`() {
        val insight = deriver.derive(wrap("""{"uid": "X1", "titre": "Quelque chose", "demandeur": null}"""))
        assertEquals(emptyList(), insight.demandeursGroupes)
    }

    @Test
    fun `missing demandeur produces empty demandeursGroupes`() {
        val insight = deriver.derive(wrap("""{"uid": "X1", "titre": "Quelque chose"}"""))
        assertEquals(emptyList(), insight.demandeursGroupes)
    }

    @Test
    fun `demandeur with no quoted names yields empty list`() {
        val insight = deriver.derive(
            wrap("""{"uid": "X", "titre": "T", "demandeur": {"texte": "Some text without quotes"}}"""),
        )
        assertEquals(emptyList(), insight.demandeursGroupes)
    }

    @Test
    fun `multi-line demandeur extracts each quoted group`() {
        val texte = """Président du groupe "Ensemble pour la République"
                      |Présidente du groupe "La France insoumise - Nouveau Front Populaire"
                      |Président du groupe "Les Démocrates"""".trimMargin()
        // Encode for embedding inside a JSON string literal.
        val escaped = texte.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val insight = deriver.derive(
            wrap("""{"uid": "X", "titre": "T", "demandeur": {"texte": "$escaped"}}"""),
        )
        assertEquals(
            listOf(
                "Ensemble pour la République",
                "La France insoumise - Nouveau Front Populaire",
                "Les Démocrates",
            ),
            insight.demandeursGroupes,
        )
    }

    @Test
    fun `null syntheseVote yields null tauxParticipation and warning`() {
        val insight = deriver.derive(wrap("""{"uid": "X", "titre": "T", "syntheseVote": null}"""))
        assertNull(insight.tauxParticipation)
        assertFalse(insight.estConsensuel)
        assertTrue(
            insight.warnings.any { "syntheseVote" in it },
            "expected a warning mentioning syntheseVote, got ${insight.warnings}",
        )
    }

    @Test
    fun `unparseable nombreVotants yields null tauxParticipation and warning`() {
        val insight = deriver.derive(
            wrap("""{"uid": "X", "titre": "T", "syntheseVote": {"nombreVotants": "abc"}}"""),
        )
        assertNull(insight.tauxParticipation)
        assertTrue(insight.warnings.any { "unparseable" in it })
    }

    @Test
    fun `missing titre yields null titreCourt and typeObjet plus warning`() {
        val insight = deriver.derive(wrap("""{"uid": "X"}"""))
        assertNull(insight.titreCourt)
        assertNull(insight.typeObjet)
        assertTrue(insight.warnings.any { "titre" in it })
    }

    // ---------------------------------------------------------------------
    // titreCourt stripping
    // ---------------------------------------------------------------------

    @Test
    fun `strips l'ensemble du projet de loi prefix`() {
        val titre = "l'ensemble du projet de loi de finances pour 2025"
        val insight = derive(titre)
        assertEquals("De finances pour 2025", insight.titreCourt)
    }

    @Test
    fun `strips l'ensemble de la proposition de loi prefix`() {
        val titre = "l'ensemble de la proposition de loi visant à protéger les locataires"
        val insight = derive(titre)
        assertEquals("Visant à protéger les locataires", insight.titreCourt)
    }

    @Test
    fun `strips la proposition de résolution prefix`() {
        val titre = "la proposition de résolution tendant à la modification du règlement"
        val insight = derive(titre)
        assertEquals("Modification du règlement", insight.titreCourt)
    }

    @Test
    fun `replaces l'amendement prefix with Amendement label`() {
        val titre = "l'amendement n° 42 présenté par M. Dupont à l'article 3"
        val insight = derive(titre)
        assertEquals("Amendement: présenté par M. Dupont à l'article 3", insight.titreCourt)
    }

    @Test
    fun `replaces l'amendement prefix without space before number`() {
        val titre = "l'amendement n°1234 portant sur le budget"
        val insight = derive(titre)
        assertEquals("Amendement: portant sur le budget", insight.titreCourt)
    }

    @Test
    fun `falls back to original titre when stripping leaves less than 5 chars`() {
        val titre = "l'ensemble du projet de loi X"
        val insight = derive(titre)
        assertEquals(titre, insight.titreCourt)
    }

    @Test
    fun `non-matching titre is preserved with first letter capitalized`() {
        // No prefix matches → working == original (length >= 5) → capitalize first letter.
        val titre = "déclaration de politique générale"
        val insight = derive(titre)
        assertEquals("Déclaration de politique générale", insight.titreCourt)
    }

    // ---------------------------------------------------------------------
    // typeObjet classification
    // ---------------------------------------------------------------------

    @Test
    fun `classifies motion de censure`() {
        val insight = derive("motion de censure déposée en application de l'article 49")
        assertEquals(VoteTypeObjet.MOTION_CENSURE, insight.typeObjet)
    }

    @Test
    fun `classifies déclaration de politique générale`() {
        val insight = derive("déclaration de politique générale du Premier ministre")
        assertEquals(VoteTypeObjet.DECLARATION_POLITIQUE_GENERALE, insight.typeObjet)
    }

    @Test
    fun `classifies projet de loi ensemble`() {
        val insight = derive("l'ensemble du projet de loi de finances")
        assertEquals(VoteTypeObjet.PROJET_LOI_ENSEMBLE, insight.typeObjet)
    }

    @Test
    fun `classifies proposition de loi ensemble`() {
        val insight = derive("l'ensemble de la proposition de loi sur le logement")
        assertEquals(VoteTypeObjet.PROPOSITION_LOI_ENSEMBLE, insight.typeObjet)
    }

    @Test
    fun `classifies amendement`() {
        val insight = derive("l'amendement n° 5 à l'article 2")
        assertEquals(VoteTypeObjet.AMENDEMENT, insight.typeObjet)
    }

    @Test
    fun `classifies article when titre starts with 'article'`() {
        val insight = derive("article 5 du projet de loi de finances")
        assertEquals(VoteTypeObjet.ARTICLE, insight.typeObjet)
    }

    @Test
    fun `classifies AUTRE for unknown patterns`() {
        val insight = derive("résolution diverse n° 12")
        assertEquals(VoteTypeObjet.AUTRE, insight.typeObjet)
    }

    @Test
    fun `commission d'enquête priority beats AMENDEMENT keyword`() {
        // Synthetic edge: titre contains both "amendement" and "commission d'enquête";
        // higher-priority rule should win.
        val insight = derive("amendement à la commission d'enquête sur X")
        assertEquals(VoteTypeObjet.RESOLUTION_COMMISSION_ENQUETE, insight.typeObjet)
    }

    // ---------------------------------------------------------------------
    // estConsensuel edge cases
    // ---------------------------------------------------------------------

    @Test
    fun `estConsensuel true at exactly 90 percent pour`() {
        // pour=90, contre=10, exprimes=100, ratio=0.90 → true (>=)
        val insight = deriveWithDecompte(pour = 90, contre = 10)
        assertTrue(insight.estConsensuel)
    }

    @Test
    fun `estConsensuel false at 89 percent pour`() {
        // pour=89, contre=11, ratio=0.89 → false
        val insight = deriveWithDecompte(pour = 89, contre = 11)
        assertFalse(insight.estConsensuel)
    }

    @Test
    fun `estConsensuel true at 91 percent contre`() {
        // pour=9, contre=91, contre/exprimes=0.91 → true (consensus against)
        val insight = deriveWithDecompte(pour = 9, contre = 91)
        assertTrue(insight.estConsensuel)
    }

    @Test
    fun `estConsensuel false when total exprimes below 50 even at 100 percent`() {
        // pour=49, contre=0 → exprimes=49 < 50 → false (insufficient signal)
        val insight = deriveWithDecompte(pour = 49, contre = 0)
        assertFalse(insight.estConsensuel)
    }

    @Test
    fun `estConsensuel ignores abstentions in the ratio`() {
        // pour=90, contre=10, abstentions=500 → still consensual (abstentions ignored)
        val insight = deriveWithDecompte(pour = 90, contre = 10, abstentions = 500)
        assertTrue(insight.estConsensuel)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun derive(titre: String) = deriver.derive(
        wrap("""{"uid": "X", "titre": "${titre.replace("\"", "\\\"")}"}"""),
    )

    private fun deriveWithDecompte(pour: Int, contre: Int, abstentions: Int = 0) =
        deriver.derive(
            wrap(
                """
                {"uid": "X", "titre": "T", "syntheseVote": {
                  "nombreVotants": "${pour + contre + abstentions}",
                  "decompte": {"pour": "$pour", "contre": "$contre", "abstentions": "$abstentions"}
                }}
                """.trimIndent(),
            ),
        )
}
