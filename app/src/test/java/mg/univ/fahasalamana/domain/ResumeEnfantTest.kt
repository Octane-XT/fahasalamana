package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de [ProchaineEcheance] et de [ResumeEnfant.aJour] — règle R6 (§B4).
 *
 * Ce que ces tests défendent tient en une phrase : **« rien à faire » et « rien à
 * calculer » ne doivent pas se ressembler**. Les deux compteurs de R6 valent zéro dans les
 * deux cas ; seule [ResumeEnfant.prochaineEcheance] les sépare, et c'est le domaine qui les
 * sépare, pas l'écran. Tant que cette distinction vivait dans `MesEnfantsUiState`, la fiche
 * enfant — qui ne l'avait pas recopiée — affichait le badge vert « À jour » à un enfant
 * dont aucune ligne n'était calculable.
 *
 * Enfant de référence : Faly, né le 01/01/2026, comme dans `CalculateurEcheancierTest`.
 */
class ResumeEnfantTest {

    private val calc = CalculateurEcheancier()
    private val naissance = LocalDate.of(2026, 1, 1)
    private val faly = enfantNeLe(naissance)

    private fun resumeAu(
        aujourdHui: LocalDate,
        administres: List<VaccinAdministre> = emptyList(),
        calendrier: List<VaccinReference> = CalendrierDeTest.COMPLET,
    ): ResumeEnfant = calc.resume(calc.echeancier(faly, calendrier, administres, aujourdHui))

    /** Les seize doses reçues le même jour : le carnet est complet pour de bon. */
    private fun carnetComplet(): List<VaccinAdministre> =
        CalendrierDeTest.COMPLET.map { administre(it.id, LocalDate.of(2026, 1, 5)) }

    // ----------------------------------------------------------------------
    // Carnet complet
    // ----------------------------------------------------------------------

    @Test
    fun carnetComplet_donneCarnetComplet() {
        val resume = resumeAu(LocalDate.of(2027, 6, 1), carnetComplet())

        assertEquals(ProchaineEcheance.CarnetComplet, resume.prochaineEcheance)
        assertEquals(0, resume.nbEnRetard)
        assertEquals(0, resume.nbAFaire)
    }

    /** C'est le seul cas où l'écran a le droit d'écrire que tout est fait. */
    @Test
    fun carnetComplet_estAJour() {
        assertTrue(resumeAu(LocalDate.of(2027, 6, 1), carnetComplet()).aJour)
    }

    // ----------------------------------------------------------------------
    // Calendrier de référence vide
    // ----------------------------------------------------------------------

    /**
     * Amorçage des assets pas terminé, ou en échec (`App.kt` le journalise et continue) :
     * il n'y a pas une ligne à calculer, donc rien à dire d'autre que « on ne sait pas ».
     */
    @Test
    fun calendrierVide_donneIndeterminable() {
        val resume = resumeAu(LocalDate.of(2026, 3, 20), calendrier = emptyList())

        assertEquals(ProchaineEcheance.Indeterminable, resume.prochaineEcheance)
    }

    /** Le défaut d'origine : deux compteurs à zéro, et pourtant surtout pas « À jour ». */
    @Test
    fun calendrierVide_nEstPasAJour() {
        val resume = resumeAu(LocalDate.of(2026, 3, 20), calendrier = emptyList())

        assertEquals(0, resume.nbEnRetard)
        assertEquals(0, resume.nbAFaire)
        assertFalse(resume.aJour)
    }

    /**
     * Le cœur de la correction : les deux situations donnent des résumés **différents**.
     *
     * Avant, `ResumeEnfant` valait (0, 0, null) dans les deux cas et rien ne permettait de
     * les départager sans relire l'échéancier brut. Un enfant qui n'a jamais reçu une seule
     * dose ne doit pas produire le même résumé qu'un enfant dont les seize doses sont faites.
     */
    @Test
    fun calendrierVide_etCarnetComplet_neSeConfondentPlus() {
        val jour = LocalDate.of(2027, 6, 1)

        val sansCalendrier = resumeAu(jour, calendrier = emptyList())
        val complet = resumeAu(jour, carnetComplet())

        // Les compteurs de R6, eux, restent identiques : c'était toute l'ambiguïté.
        assertEquals(complet.nbEnRetard, sansCalendrier.nbEnRetard)
        assertEquals(complet.nbAFaire, sansCalendrier.nbAFaire)

        assertNotEquals(complet.prochaineEcheance, sansCalendrier.prochaineEcheance)
        assertNotEquals(complet, sansCalendrier)
        assertTrue(complet.aJour)
        assertFalse(sansCalendrier.aJour)
    }

    // ----------------------------------------------------------------------
    // Chaîne `dependDe` cassée
    // ----------------------------------------------------------------------

    /**
     * Calendrier circulaire (donnée de référence invalide) : pas une seule date n'est
     * calculable, et aucune dose n'a été reçue. Même conclusion qu'un calendrier vide.
     */
    @Test
    fun chaineCassee_deBoutEnBout_donneIndeterminable() {
        val resume = resumeAu(LocalDate.of(2026, 3, 20), calendrier = CalendrierDeTest.CIRCULAIRE)

        assertEquals(ProchaineEcheance.Indeterminable, resume.prochaineEcheance)
        assertFalse(resume.aJour)
    }

    /**
     * Une chaîne cassée ne masque pas les doses qui, elles, ont une date.
     *
     * `penta1` a disparu du calendrier et n'a jamais été administré : `penta2` n'a plus
     * d'origine et reste sans date. Les doses de naissance en ont une, et c'est celle-là que
     * le résumé annonce.
     */
    @Test
    fun chaineCassee_partielle_donneLaProchaineDoseCalculable() {
        val lignes = calc.echeancier(
            enfant = faly,
            calendrier = CalendrierDeTest.sans("penta1"),
            administres = emptyList(),
            aujourdHui = LocalDate.of(2026, 1, 20),
        )

        assertNull(lignes.ligne("penta2").prevuLe)
        assertEquals(ProchaineEcheance.Prevue(naissance), calc.resume(lignes).prochaineEcheance)
    }

    /**
     * Chaîne cassée **et** toutes les doses reçues : le carnet est complet, pas indéterminé.
     *
     * C'est ce cas qui interdit de conclure « aucune date calculable » à partir des seules
     * dates : ce qui compte est qu'il ne reste aucune dose à faire.
     */
    @Test
    fun chaineCassee_maisToutesLesDosesRecues_donneCarnetComplet() {
        val saisie = CalendrierDeTest.CIRCULAIRE.map { administre(it.id, LocalDate.of(2026, 1, 10)) }

        val resume = resumeAu(LocalDate.of(2026, 3, 20), saisie, CalendrierDeTest.CIRCULAIRE)

        assertEquals(ProchaineEcheance.CarnetComplet, resume.prochaineEcheance)
        assertTrue(resume.aJour)
    }

    // ----------------------------------------------------------------------
    // `aJour` — un échéancier qui existe et n'appelle rien aujourd'hui
    // ----------------------------------------------------------------------

    /**
     * Un enfant à jour n'a pas un carnet complet : il a simplement une prochaine dose
     * devant lui. Au 20/01, BCG et Polio 0 sont faits et les doses de 6 semaines ne sont
     * dues que le 12/02.
     */
    @Test
    fun aJour_estVrai_quandLaProchaineDoseEstEncoreAVenir() {
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
        )

        val resume = resumeAu(LocalDate.of(2026, 1, 20), saisie)

        assertEquals(
            ProchaineEcheance.Prevue(LocalDate.of(2026, 2, 12)),
            resume.prochaineEcheance,
        )
        assertTrue(resume.aJour)
    }

    /** Une dose dans sa fenêtre, une autre en retard : il y a quelque chose à faire. */
    @Test
    fun aJour_estFaux_desQuUneDoseEstDue() {
        val resume = resumeAu(LocalDate.of(2026, 1, 20))

        assertEquals(1, resume.nbAFaire)
        assertEquals(1, resume.nbEnRetard)
        assertFalse(resume.aJour)
    }
}
