package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de [dosesHorsCalendrier] et de [nbFaits] — CDC §B5.2 : une administration dont le
 * vaccin a disparu du calendrier de référence est « conservée **et** affichée ».
 *
 * Ce qui est vérifié ici n'est pas une fonctionnalité de confort : la clé étrangère de
 * `vaccins_administres` vers `vaccins_reference` a été retirée (B04) pour que la dose survive
 * à une mise à jour du calendrier (B19). Sans ces deux fonctions, elle survivait en base mais
 * n'apparaissait sur aucun écran — une dose correctement saisie par une mère disparaissait de
 * la fiche de son enfant du jour au lendemain.
 *
 * Enfant de référence : Faly, né le 01/01/2026, comme dans [CalculateurEcheancierTest].
 */
class DosesHorsCalendrierTest {

    private val calc = CalculateurEcheancier()
    private val faly = enfantNeLe(LocalDate.of(2026, 1, 1))

    // ----------------------------------------------------------------------
    // dosesHorsCalendrier
    // ----------------------------------------------------------------------

    /** Cas normal : tout ce qui a été saisi figure au calendrier, la section reste vide. */
    @Test
    fun calendrierComplet_neLaisseAucuneDoseHorsCalendrier() {
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
        )

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, CalendrierDeTest.COMPLET)

        assertTrue(horsCalendrier.isEmpty())
    }

    /** Le cas de B19 : une nouvelle version du calendrier retire une dose déjà saisie. */
    @Test
    fun doseRetireeDuCalendrier_estRetrouvee() {
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("rota1", LocalDate.of(2026, 2, 15)),
        )

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, CalendrierDeTest.sans("rota1"))

        assertEquals(listOf("rota1"), horsCalendrier.map(VaccinAdministre::vaccinId))
        assertEquals(LocalDate.of(2026, 2, 15), horsCalendrier.single().date)
    }

    /** Plusieurs doses retirées : la fiche les affiche de la plus ancienne à la plus récente. */
    @Test
    fun plusieursDosesRetirees_sontTrieesParDate() {
        val saisie = listOf(
            administre("rota2", LocalDate.of(2026, 3, 20)),
            administre("pcv1", LocalDate.of(2026, 2, 12)),
            administre("rota1", LocalDate.of(2026, 2, 15)),
        )

        val horsCalendrier =
            dosesHorsCalendrier(faly, saisie, CalendrierDeTest.sans("rota1", "rota2", "pcv1"))

        assertEquals(
            listOf("pcv1", "rota1", "rota2"),
            horsCalendrier.map(VaccinAdministre::vaccinId),
        )
    }

    /** Donnée de santé : la fiche de Faly ne montre jamais une dose saisie pour un autre enfant. */
    @Test
    fun doseDUnAutreEnfant_estIgnoree() {
        val saisie = listOf(
            administre("rota1", LocalDate.of(2026, 2, 15), enfantId = "enfant-soa"),
        )

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, CalendrierDeTest.sans("rota1"))

        assertTrue(horsCalendrier.isEmpty())
    }

    /** Carnet importé abîmé : deux saisies pour la même dose, la plus ancienne date fait foi. */
    @Test
    fun saisieDupliquee_nApparaitQuUneFois() {
        val saisie = listOf(
            administre("rota1", LocalDate.of(2026, 3, 5)),
            administre("rota1", LocalDate.of(2026, 2, 20)),
        )

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, CalendrierDeTest.sans("rota1"))

        assertEquals(1, horsCalendrier.size)
        assertEquals(LocalDate.of(2026, 2, 20), horsCalendrier.single().date)
    }

    /**
     * Calendrier absent du téléphone : la fiche n'affiche aucune échéance, mais elle continue
     * de montrer ce qui a été saisi plutôt qu'un carnet vide.
     */
    @Test
    fun calendrierVide_renvoieToutesLesDosesSaisies() {
        val saisie = listOf(
            administre("vpo0", LocalDate.of(2026, 1, 3)),
            administre("bcg", LocalDate.of(2026, 1, 2)),
        )

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, emptyList())

        assertEquals(listOf("bcg", "vpo0"), horsCalendrier.map(VaccinAdministre::vaccinId))
    }

    /** Aucune saisie : rien à afficher, et surtout pas d'exception sur un calendrier amputé. */
    @Test
    fun aucuneSaisie_neDonneAucuneDoseHorsCalendrier() {
        val horsCalendrier = dosesHorsCalendrier(faly, emptyList(), CalendrierDeTest.sans("rota1"))

        assertTrue(horsCalendrier.isEmpty())
    }

    // ----------------------------------------------------------------------
    // nbFaits — compteur « x faits » de l'en-tête de la fiche
    // ----------------------------------------------------------------------

    @Test
    fun nbFaits_sansSaisie_estZero() {
        val lignes = calc.echeancier(
            faly,
            CalendrierDeTest.COMPLET,
            emptyList(),
            LocalDate.of(2026, 2, 20),
        )

        assertEquals(0, nbFaits(lignes, emptyList()))
    }

    @Test
    fun nbFaits_compteLesDosesDuCalendrierEtCellesQuiEnSontSorties() {
        val calendrier = CalendrierDeTest.sans("rota1")
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
            administre("rota1", LocalDate.of(2026, 2, 15)),
        )

        val lignes = calc.echeancier(faly, calendrier, saisie, LocalDate.of(2026, 2, 20))
        val horsCalendrier = dosesHorsCalendrier(faly, saisie, calendrier)

        // Deux lignes `Fait` dans l'échéancier, plus la dose sortie du calendrier.
        assertEquals(2, lignes.count { it.statut is StatutVaccin.Fait })
        assertEquals(3, nbFaits(lignes, horsCalendrier))
    }

    /**
     * Le défaut corrigé : le compteur de doses reçues ne doit pas baisser parce que le
     * calendrier de référence a changé. L'enfant a reçu trois doses avant la mise à jour, il
     * en a toujours reçu trois après.
     */
    @Test
    fun nbFaits_neBaissePasQuandUneDoseSortDuCalendrier() {
        val aujourdHui = LocalDate.of(2026, 2, 20)
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
            administre("rota1", LocalDate.of(2026, 2, 15)),
        )

        val avant = CalendrierDeTest.COMPLET
        val apres = CalendrierDeTest.sans("rota1")

        val nbAvant = nbFaits(
            calc.echeancier(faly, avant, saisie, aujourdHui),
            dosesHorsCalendrier(faly, saisie, avant),
        )
        val nbApres = nbFaits(
            calc.echeancier(faly, apres, saisie, aujourdHui),
            dosesHorsCalendrier(faly, saisie, apres),
        )

        assertEquals(3, nbAvant)
        assertEquals(nbAvant, nbApres)
    }

    /** Calendrier absent : l'échéancier est vide, le carnet montre quand même ses trois doses. */
    @Test
    fun nbFaits_calendrierVide_compteQuandMemeLesDosesSaisies() {
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
            administre("rota1", LocalDate.of(2026, 2, 15)),
        )

        val lignes = calc.echeancier(faly, emptyList(), saisie, LocalDate.of(2026, 2, 20))
        val horsCalendrier = dosesHorsCalendrier(faly, saisie, emptyList())

        assertTrue(lignes.isEmpty())
        assertEquals(3, nbFaits(lignes, horsCalendrier))
    }

    /** Les doses d'un autre enfant n'entrent dans aucun des deux compteurs. */
    @Test
    fun nbFaits_ignoreLesDosesDUnAutreEnfant() {
        val calendrier = CalendrierDeTest.sans("rota1")
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("rota1", LocalDate.of(2026, 2, 15), enfantId = "enfant-soa"),
        )

        val lignes = calc.echeancier(faly, calendrier, saisie, LocalDate.of(2026, 2, 20))
        val horsCalendrier = dosesHorsCalendrier(faly, saisie, calendrier)

        assertEquals(1, nbFaits(lignes, horsCalendrier))
    }
}
