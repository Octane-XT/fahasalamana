package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de [validerSaisie] — règle R5 (§B4), user stories US-B3 et US-B4 (§B3), tâche B09.
 *
 * Logique métier pure, donc testée en JVM sans émulateur (Definition of Done, §0.4).
 *
 * Enfant de référence : Faly, né le 01/01/2026 (l'enfant des scénarios du CDC). Dose de
 * référence : Pentavalent 1re dose, prévue à 42 jours — soit le 12/02/2026 — avec une
 * fenêtre de tolérance de 14 jours, qui court donc jusqu'au 26/02/2026 inclus.
 *
 * Ce que ces tests défendent, au-delà des dates : **hors fenêtre n'est pas une erreur.**
 * Un seul test suffirait à prouver qu'une saisie tardive est acceptée ; il y en a plusieurs,
 * parce que c'est la règle qu'une « amélioration » de bonne foi casserait en premier (R7).
 */
class ValidationSaisieTest {

    private val naissance = LocalDate.of(2026, 1, 1)

    /** Penta 1 : 42 jours après la naissance. */
    private val prevuLe = LocalDate.of(2026, 2, 12)

    /** Tolérance de Penta 1 dans le calendrier de démonstration (§B5.1). */
    private val tolerance = 14

    /** Dernier jour de la fenêtre, borne comprise. */
    private val finFenetre = LocalDate.of(2026, 2, 26)

    private fun valider(
        dateSaisie: LocalDate,
        aujourdHui: LocalDate,
        dateNaissance: LocalDate = naissance,
        datePrevue: LocalDate? = prevuLe,
        toleranceJours: Int = tolerance,
    ): ResultatSaisie = validerSaisie(
        dateSaisie = dateSaisie,
        dateNaissance = dateNaissance,
        aujourdHui = aujourdHui,
        prevuLe = datePrevue,
        toleranceJours = toleranceJours,
    )

    // ----------------------------------------------------------------------
    // R5 — les deux seuls refus
    // ----------------------------------------------------------------------

    /** Une dose ne peut pas avoir été reçue avant la naissance. */
    @Test
    fun dateAvantLaNaissance_estRefusee() {
        val resultat = valider(
            dateSaisie = LocalDate.of(2025, 12, 31),
            aujourdHui = LocalDate.of(2026, 2, 20),
        )

        assertEquals(ResultatSaisie.Refusee(MotifRefus.AvantLaNaissance(naissance)), resultat)
    }

    /** Une dose ne peut pas avoir été reçue demain. */
    @Test
    fun dateDansLeFutur_estRefusee() {
        val aujourdHui = LocalDate.of(2026, 2, 20)

        val resultat = valider(dateSaisie = aujourdHui.plusDays(1), aujourdHui = aujourdHui)

        assertEquals(ResultatSaisie.Refusee(MotifRefus.DansLeFutur(aujourdHui)), resultat)
    }

    /** Un an à l'avance : même refus, le motif ne dépend pas de l'ampleur de l'écart. */
    @Test
    fun dateTresLoinDansLeFutur_estRefusee() {
        val aujourdHui = LocalDate.of(2026, 2, 20)

        val resultat = valider(dateSaisie = LocalDate.of(2027, 2, 20), aujourdHui = aujourdHui)

        assertEquals(ResultatSaisie.Refusee(MotifRefus.DansLeFutur(aujourdHui)), resultat)
    }

    /**
     * Date à la fois antérieure à la naissance et postérieure à aujourd'hui : c'est la
     * naissance qui est signalée, motif le plus proche de la cause (ordre documenté de R5).
     */
    @Test
    fun dateAvantLaNaissanceEtDansLeFutur_signaleLaNaissance() {
        val naissanceTardive = LocalDate.of(2026, 6, 1)

        val resultat = valider(
            dateSaisie = LocalDate.of(2026, 3, 1),
            aujourdHui = LocalDate.of(2026, 1, 15),
            dateNaissance = naissanceTardive,
        )

        assertEquals(
            ResultatSaisie.Refusee(MotifRefus.AvantLaNaissance(naissanceTardive)),
            resultat,
        )
    }

    // ----------------------------------------------------------------------
    // R5 — les deux bornes sont inclusives
    // ----------------------------------------------------------------------

    /**
     * Le jour de la naissance est une date valide : BCG et Polio 0 sont donnés à la
     * maternité, et leur date prévue est la naissance elle-même.
     */
    @Test
    fun dateDuJourDeLaNaissance_estAcceptee() {
        val resultat = valider(
            dateSaisie = naissance,
            aujourdHui = LocalDate.of(2026, 1, 3),
            datePrevue = naissance,
            toleranceJours = 30,
        )

        assertEquals(ResultatSaisie.Acceptee(), resultat)
    }

    /** Le jour même est une date valide : c'est le cas le plus courant, la dose du matin. */
    @Test
    fun dateDuJour_estAcceptee() {
        val aujourdHui = LocalDate.of(2026, 2, 20)

        val resultat = valider(dateSaisie = aujourdHui, aujourdHui = aujourdHui)

        assertEquals(ResultatSaisie.Acceptee(), resultat)
    }

    /** La veille de la naissance reste refusée : la borne est bien sur le jour de naissance. */
    @Test
    fun veilleDeLaNaissance_estRefusee() {
        val resultat = valider(
            dateSaisie = naissance.minusDays(1),
            aujourdHui = LocalDate.of(2026, 2, 20),
            datePrevue = naissance,
            toleranceJours = 30,
        )

        assertEquals(ResultatSaisie.Refusee(MotifRefus.AvantLaNaissance(naissance)), resultat)
    }

    // ----------------------------------------------------------------------
    // R5 — dans la fenêtre : accepté, sans un mot
    // ----------------------------------------------------------------------

    /** Le jour prévu : aucun avertissement. */
    @Test
    fun dateDuJourPrevu_estAccepteeSansAvertissement() {
        val resultat = valider(dateSaisie = prevuLe, aujourdHui = LocalDate.of(2026, 2, 20))

        assertEquals(ResultatSaisie.Acceptee(avertissement = null), resultat)
    }

    /** Trois jours après le jour prévu, donc dans la fenêtre : aucun avertissement. */
    @Test
    fun dateDansLaFenetre_estAccepteeSansAvertissement() {
        val resultat = valider(
            dateSaisie = LocalDate.of(2026, 2, 15),
            aujourdHui = LocalDate.of(2026, 2, 20),
        )

        assertEquals(ResultatSaisie.Acceptee(avertissement = null), resultat)
    }

    /** Dernier jour de la fenêtre (12/02 + 14 jours) : encore dedans, borne comprise. */
    @Test
    fun dernierJourDeLaFenetre_estAccepteeSansAvertissement() {
        val resultat = valider(dateSaisie = finFenetre, aujourdHui = LocalDate.of(2026, 3, 1))

        assertEquals(ResultatSaisie.Acceptee(avertissement = null), resultat)
    }

    // ----------------------------------------------------------------------
    // R5 et R7 — hors fenêtre : accepté, avec un avertissement neutre
    // ----------------------------------------------------------------------

    /** Le lendemain de la fin de fenêtre : premier jour qui déclenche l'avertissement. */
    @Test
    fun lendemainDeLaFenetre_estAccepteeAvecAvertissement() {
        val dateSaisie = finFenetre.plusDays(1)

        val resultat = valider(dateSaisie = dateSaisie, aujourdHui = LocalDate.of(2026, 3, 1))

        assertEquals(
            ResultatSaisie.Acceptee(
                AvertissementSaisie.ApresLaFenetre(
                    prevuLe = prevuLe,
                    finFenetre = finFenetre,
                    // 15 jours depuis la date prévue, et non depuis la fin de la fenêtre.
                    joursApres = 15L,
                ),
            ),
            resultat,
        )
    }

    /**
     * **Le test qui porte la règle.** Six mois de retard : la saisie est acceptée.
     *
     * C'est le scénario du CDC — une mère qui fait vacciner son enfant avec six mois de
     * retard doit pouvoir l'enregistrer. L'application constate l'écart et enregistre.
     */
    @Test
    fun saisieTresEnRetard_estAccepteeAvecAvertissement() {
        val dateSaisie = prevuLe.plusDays(180)

        val resultat = valider(dateSaisie = dateSaisie, aujourdHui = dateSaisie.plusDays(5))

        assertEquals(
            ResultatSaisie.Acceptee(
                AvertissementSaisie.ApresLaFenetre(
                    prevuLe = prevuLe,
                    finFenetre = finFenetre,
                    joursApres = 180L,
                ),
            ),
            resultat,
        )
    }

    /** Formulation générale de la même règle : un retard, quel qu'il soit, ne bloque jamais. */
    @Test
    fun aucunRetard_nEstBloquant() {
        val retards = listOf(15L, 30L, 180L, 365L, 1_000L)

        retards.forEach { jours ->
            val dateSaisie = prevuLe.plusDays(jours)
            val resultat = valider(dateSaisie = dateSaisie, aujourdHui = dateSaisie)

            assertTrue(
                "Une saisie $jours jours après la date prévue doit rester acceptée (R5)",
                resultat is ResultatSaisie.Acceptee,
            )
        }
    }

    /** Dose reçue avant l'ouverture de la fenêtre : accepté aussi, avec l'avertissement inverse. */
    @Test
    fun saisieAvantLaDatePrevue_estAccepteeAvecAvertissement() {
        val resultat = valider(
            dateSaisie = LocalDate.of(2026, 2, 7),
            aujourdHui = LocalDate.of(2026, 2, 20),
        )

        assertEquals(
            ResultatSaisie.Acceptee(
                AvertissementSaisie.AvantLaDatePrevue(prevuLe = prevuLe, joursAvance = 5L),
            ),
            resultat,
        )
    }

    // ----------------------------------------------------------------------
    // Cas limites du calendrier de référence
    // ----------------------------------------------------------------------

    /**
     * Chaîne de dépendances cassée (`prevuLe == null`, voir [LigneEcheancier]) : aucun écart
     * n'est calculable, donc aucun avertissement — et surtout, la saisie reste possible.
     */
    @Test
    fun sansDatePrevue_estAccepteeSansAvertissement() {
        val resultat = valider(
            dateSaisie = LocalDate.of(2026, 2, 15),
            aujourdHui = LocalDate.of(2026, 2, 20),
            datePrevue = null,
        )

        assertEquals(ResultatSaisie.Acceptee(), resultat)
        assertNull((resultat as ResultatSaisie.Acceptee).avertissement)
    }

    /** Tolérance nulle : la fenêtre se réduit au jour prévu, le lendemain avertit déjà. */
    @Test
    fun toleranceNulle_lendemainDuJourPrevu_avertit() {
        val resultat = valider(
            dateSaisie = prevuLe.plusDays(1),
            aujourdHui = LocalDate.of(2026, 3, 1),
            toleranceJours = 0,
        )

        assertEquals(
            ResultatSaisie.Acceptee(
                AvertissementSaisie.ApresLaFenetre(
                    prevuLe = prevuLe,
                    finFenetre = prevuLe,
                    joursApres = 1L,
                ),
            ),
            resultat,
        )
    }

    /**
     * Tolérance négative dans un `calendrier.json` malformé : traitée comme nulle, pour ne
     * pas inventer un avertissement sur une date pourtant prévue ce jour-là.
     */
    @Test
    fun toleranceNegative_estTraiteeCommeNulle() {
        val resultat = valider(
            dateSaisie = prevuLe,
            aujourdHui = LocalDate.of(2026, 3, 1),
            toleranceJours = -10,
        )

        assertEquals(ResultatSaisie.Acceptee(), resultat)
    }

    /** Année bissextile : le 29/02/2028 existe et se valide comme n'importe quel jour. */
    @Test
    fun jourBissextil_estAccepte() {
        val naissanceBissextile = LocalDate.of(2028, 1, 1)
        val prevuBissextil = LocalDate.of(2028, 2, 12)

        val resultat = valider(
            dateSaisie = LocalDate.of(2028, 2, 29),
            aujourdHui = LocalDate.of(2028, 3, 5),
            dateNaissance = naissanceBissextile,
            datePrevue = prevuBissextil,
        )

        assertEquals(
            ResultatSaisie.Acceptee(
                AvertissementSaisie.ApresLaFenetre(
                    prevuLe = prevuBissextil,
                    // 12/02 + 14 jours = 26/02/2028.
                    finFenetre = LocalDate.of(2028, 2, 26),
                    joursApres = 17L,
                ),
            ),
            resultat,
        )
    }
}
