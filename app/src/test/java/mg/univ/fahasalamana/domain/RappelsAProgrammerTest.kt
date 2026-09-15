package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests de `CalculateurEcheancier.rappelsAProgrammer` — scénarios Gherkin de
 * US-B5 (§B3) et règles R3 / R4 (§B4).
 *
 * Enfant de référence : Faly, né le 01/01/2026, Penta 1 prévu le 12/02/2026,
 * donc rappel attendu le 09/02/2026 à 09:00.
 */
class RappelsAProgrammerTest {

    private val calc = CalculateurEcheancier()
    private val naissance = LocalDate.of(2026, 1, 1)
    private val faly = enfantNeLe(naissance)

    private fun rappels(
        aujourdHui: LocalDate,
        maintenant: LocalDateTime,
        administres: List<VaccinAdministre> = emptyList(),
        avecRappelFenetre: Boolean = false,
    ): List<Rappel> = calc.rappelsAProgrammer(
        echeancier = calc.echeancier(faly, CalendrierDeTest.COMPLET, administres, aujourdHui),
        maintenant = maintenant,
        avecRappelFenetre = avecRappelFenetre,
    )

    /** Scénario « rappel programmé » : trois jours avant l'échéance, à 09:00. */
    @Test
    fun rappel_estProgrammeTroisJoursAvantLEcheanceA9h() {
        val liste = rappels(LocalDate.of(2026, 1, 20), LocalDateTime.of(2026, 1, 20, 8, 0))

        assertEquals(
            Rappel(
                vaccinId = "penta1",
                prevuLe = LocalDate.of(2026, 2, 12),
                dateHeure = LocalDateTime.of(2026, 2, 9, 9, 0),
                type = TypeRappel.AVANT_ECHEANCE,
            ),
            liste.first(),
        )
    }

    /** Seules les doses à venir sont enfilées : 7 sur 16 le 20/01/2026. */
    @Test
    fun rappels_uniquementPourLesDosesAVenir() {
        val liste = rappels(LocalDate.of(2026, 1, 20), LocalDateTime.of(2026, 1, 20, 8, 0))

        assertEquals(
            listOf("penta1", "vpo1", "pcv1", "rota1", "vpi", "rr1", "rr2"),
            liste.map { it.vaccinId },
        )
        assertTrue(liste.all { it.type == TypeRappel.AVANT_ECHEANCE })
    }

    /** Une dose À faire ou En retard ne déclenche aucun rappel : R3 ne vise que « À venir ». */
    @Test
    fun rappels_aucunPourUneDoseAFaireOuEnRetard() {
        val liste = rappels(LocalDate.of(2026, 2, 20), LocalDateTime.of(2026, 2, 20, 8, 0))

        // BCG et VPO 0 sont en retard, les quatre doses de 6 semaines sont à faire.
        assertFalse(liste.any { it.vaccinId in listOf("bcg", "vpo0", "penta1", "vpo1", "pcv1", "rota1") })
    }

    /** Une dose en attente d'une dose précédente n'a pas encore de rappel (R3). */
    @Test
    fun rappels_aucunPourUneDoseEnAttente() {
        val liste = rappels(LocalDate.of(2026, 1, 20), LocalDateTime.of(2026, 1, 20, 8, 0))

        assertFalse(liste.any { it.vaccinId == "penta2" })
    }

    /** Scénario « vaccin saisi avant le rappel » : la dose faite sort de la liste. */
    @Test
    fun rappels_aucunPourUneDoseDejaFaite_etLaSuivanteEstReprogrammee() {
        val saisie = listOf(administre("penta1", LocalDate.of(2026, 2, 5)))

        val liste = rappels(
            aujourdHui = LocalDate.of(2026, 2, 6),
            maintenant = LocalDateTime.of(2026, 2, 6, 8, 0),
            administres = saisie,
        )

        assertFalse(liste.any { it.vaccinId == "penta1" })
        assertEquals(
            Rappel(
                vaccinId = "penta2",
                prevuLe = LocalDate.of(2026, 3, 5),
                dateHeure = LocalDateTime.of(2026, 3, 2, 9, 0),
            ),
            liste.first { it.vaccinId == "penta2" },
        )
    }

    /** Un rappel dont l'heure d'émission est déjà passée n'est jamais enfilé (R3). */
    @Test
    fun rappels_aucunSiLHeureDEmissionEstDepassee() {
        val aujourdHui = LocalDate.of(2026, 2, 9)

        val uneMinuteAvant = rappels(aujourdHui, LocalDateTime.of(2026, 2, 9, 8, 59))
        val aLHeurePile = rappels(aujourdHui, LocalDateTime.of(2026, 2, 9, 9, 0))

        assertTrue(uneMinuteAvant.any { it.vaccinId == "penta1" })
        assertFalse(aLHeurePile.any { it.vaccinId == "penta1" })
    }

    @Test
    fun rappels_sontTriesParHeureDEmission() {
        val liste = rappels(LocalDate.of(2026, 1, 20), LocalDateTime.of(2026, 1, 20, 8, 0))

        assertEquals(liste.sortedBy { it.dateHeure }, liste)
        assertEquals(LocalDateTime.of(2027, 3, 24, 9, 0), liste.last().dateHeure)
    }

    /** R4 — rejouer le calcul donne exactement la même liste : la replanification est idempotente. */
    @Test
    fun rappels_sontIdempotents() {
        val aujourdHui = LocalDate.of(2026, 1, 20)
        val maintenant = LocalDateTime.of(2026, 1, 20, 8, 0)

        assertEquals(rappels(aujourdHui, maintenant), rappels(aujourdHui, maintenant))
    }

    /** R3 (Should) — second rappel deux jours avant la fermeture de la fenêtre, sur demande. */
    @Test
    fun rappelFenetre_estOptionnel_etTombeDeuxJoursAvantLaFermeture() {
        val aujourdHui = LocalDate.of(2026, 2, 20)
        val maintenant = LocalDateTime.of(2026, 2, 20, 8, 0)

        val sans = rappels(aujourdHui, maintenant)
        val avec = rappels(aujourdHui, maintenant, avecRappelFenetre = true)

        assertFalse(sans.any { it.type == TypeRappel.FENETRE_BIENTOT_FERMEE })
        assertEquals(
            Rappel(
                vaccinId = "penta1",
                prevuLe = LocalDate.of(2026, 2, 12),
                dateHeure = LocalDateTime.of(2026, 2, 24, 9, 0),
                type = TypeRappel.FENETRE_BIENTOT_FERMEE,
            ),
            avec.first { it.vaccinId == "penta1" },
        )
    }

    /** Un échéancier sans date calculable ne produit aucun rappel. */
    @Test
    fun rappels_ignorentLesLignesSansDate() {
        val echeancier = calc.echeancier(
            enfant = faly,
            calendrier = CalendrierDeTest.CIRCULAIRE,
            administres = emptyList(),
            aujourdHui = LocalDate.of(2026, 1, 20),
        )

        assertTrue(calc.rappelsAProgrammer(echeancier, LocalDateTime.of(2026, 1, 20, 8, 0)).isEmpty())
    }
}
