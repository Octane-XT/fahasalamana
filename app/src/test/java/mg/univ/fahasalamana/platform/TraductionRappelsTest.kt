package mg.univ.fahasalamana.platform

import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.CalendrierDeTest
import mg.univ.fahasalamana.domain.ID_FALY
import mg.univ.fahasalamana.domain.Rappel
import mg.univ.fahasalamana.domain.TypeRappel
import mg.univ.fahasalamana.domain.enfantNeLe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests de la traduction `Rappel` → paramètres de travail WorkManager (B11).
 *
 * Ce qui est testé ici est exactement ce que B11 décide : le **nom unique** (règle R4) et
 * le **délai initial** (§B8), plus le plafond de démonstration de la DoD. Le reste de la
 * tâche — enfiler, annuler, relire la base — demande un vrai WorkManager et appartient aux
 * tests instrumentés de B12 (`WorkManagerTestInitHelper`).
 *
 * Jeu de données : Faly, né le 01/01/2026, calendrier de démonstration complet. Penta 1 y
 * est prévu le 12/02/2026, donc son rappel part le 09/02/2026 à 09:00 (R3), ce que
 * `RappelsAProgrammerTest` fige déjà côté domaine.
 */
class TraductionRappelsTest {

    private val calc = CalculateurEcheancier()
    private val faly = enfantNeLe(LocalDate.of(2026, 1, 1))
    private val aujourdHui = LocalDate.of(2026, 1, 20)
    private val maintenant = LocalDateTime.of(2026, 1, 20, 8, 0)

    private fun rappels(avecRappelFenetre: Boolean = false): List<Rappel> = calc.rappelsAProgrammer(
        echeancier = calc.echeancier(faly, CalendrierDeTest.COMPLET, emptyList(), aujourdHui),
        maintenant = maintenant,
        avecRappelFenetre = avecRappelFenetre,
    )

    // --- Règle R4 : nom unique du travail -------------------------------------

    /** Le rappel principal porte la chaîne de R4, mot pour mot. */
    @Test
    fun nomTravail_duRappelPrincipal_estCeluiDeLaRegleR4() {
        assertEquals(
            "rappel-enfant-faly-penta1",
            nomTravailRappel(ID_FALY, "penta1", TypeRappel.AVANT_ECHEANCE),
        )
    }

    /**
     * Le second rappel porte un nom **distinct** du principal.
     *
     * C'est le piège documenté par `domain/Rappel.kt` : avec le même nom, la politique
     * `REPLACE` de R4 ferait disparaître le rappel principal au profit du second.
     */
    @Test
    fun nomTravail_duRappelFenetre_neRecouvrePasLePrincipal() {
        val principal = nomTravailRappel(ID_FALY, "penta1", TypeRappel.AVANT_ECHEANCE)
        val fenetre = nomTravailRappel(ID_FALY, "penta1", TypeRappel.FENETRE_BIENTOT_FERMEE)

        assertEquals("rappel-enfant-faly-penta1-fenetre", fenetre)
        assertNotEquals(principal, fenetre)
    }

    /** Deux doses du même enfant, deux noms ; deux enfants pour la même dose, deux noms. */
    @Test
    fun nomTravail_distingueLesDosesEtLesEnfants() {
        val noms = listOf(
            nomTravailRappel(ID_FALY, "penta1", TypeRappel.AVANT_ECHEANCE),
            nomTravailRappel(ID_FALY, "penta2", TypeRappel.AVANT_ECHEANCE),
            nomTravailRappel("enfant-soa", "penta1", TypeRappel.AVANT_ECHEANCE),
        )

        assertEquals(noms.size, noms.toSet().size)
    }

    /** L'étiquette de groupe ne peut pas être confondue avec un nom unique. */
    @Test
    fun etiquetteDeGroupe_neSeConfondPasAvecUnNomUnique() {
        val etiquette = etiquetteTravauxEnfant(ID_FALY)

        assertEquals("rappels-enfant-enfant-faly", etiquette)
        assertNotEquals(etiquetteRappel(ID_FALY, "penta1"), etiquette)
    }

    // --- Délai initial ---------------------------------------------------------

    /** `initialDelay = dateHeure - maintenant` (§B8) : 20 jours et 1 heure pour Penta 1. */
    @Test
    fun delai_estLEcartEntreMaintenantEtLEmission() {
        val delai = delaiInitial(
            maintenant = maintenant,
            emission = LocalDateTime.of(2026, 2, 9, 9, 0),
            plafond = null,
        )

        assertEquals(Duration.ofDays(20).plusHours(1), delai)
    }

    /**
     * Une émission déjà passée donne zéro, jamais un délai négatif.
     *
     * `rappelsAProgrammer` ne produit que des émissions futures, mais l'instant de
     * référence peut glisser entre les deux appels — et `setInitialDelay` refuse une valeur
     * négative.
     */
    @Test
    fun delai_neDevientJamaisNegatif() {
        val delai = delaiInitial(
            maintenant = maintenant,
            emission = maintenant.minusDays(4),
            plafond = null,
        )

        assertEquals(Duration.ZERO, delai)
    }

    /** DoD de B11 : en build debug, le plafond ramène un rappel lointain à une minute. */
    @Test
    fun delai_estPlafonneParLeDelaiDeDemonstration() {
        val delai = delaiInitial(
            maintenant = maintenant,
            emission = LocalDateTime.of(2027, 3, 24, 9, 0),
            plafond = Duration.ofMinutes(1),
        )

        assertEquals(Duration.ofMinutes(1), delai)
    }

    /** Le plafond raccourcit, il n'allonge pas : un rappel déjà proche garde son délai. */
    @Test
    fun delai_lePlafondNAllongeJamaisUnRappelProche() {
        val delai = delaiInitial(
            maintenant = maintenant,
            emission = maintenant.plusSeconds(10),
            plafond = Duration.ofMinutes(1),
        )

        assertEquals(Duration.ofSeconds(10), delai)
    }

    // --- Traduction d'une liste complète ---------------------------------------

    /** Scénario « rappel programmé » de US-B5, vu depuis le planificateur. */
    @Test
    fun travaux_traduisentLeRappelDePenta1() {
        val penta1 = travauxRappels(ID_FALY, rappels(), maintenant)
            .first { it.vaccinId == "penta1" }

        assertEquals("rappel-enfant-faly-penta1", penta1.nomUnique)
        assertEquals("rappels-enfant-enfant-faly", penta1.etiquetteEnfant)
        assertEquals(TypeRappel.AVANT_ECHEANCE, penta1.type)
        assertEquals(Duration.ofDays(20).plusHours(1), penta1.delai)
    }

    /** Les sept doses à venir donnent sept travaux, un par dose, sans doublon de nom. */
    @Test
    fun travaux_unParRappelEtAucunNomEnDouble() {
        val travaux = travauxRappels(ID_FALY, rappels(), maintenant)

        assertEquals(
            listOf("penta1", "vpo1", "pcv1", "rota1", "vpi", "rr1", "rr2"),
            travaux.map { it.vaccinId },
        )
        assertEquals(travaux.size, travaux.map { it.nomUnique }.toSet().size)
    }

    /**
     * Règle R4 — « tout replanifier » est idempotent.
     *
     * Deux traductions successives produisent exactement les mêmes noms uniques : avec
     * `ExistingWorkPolicy.REPLACE`, la seconde planification remplace la première au lieu
     * de s'y ajouter. C'est ce qui autorise à appeler `replanifier` à chaque écriture.
     */
    @Test
    fun travaux_sontIdempotentsDunAppelALautre() {
        val premier = travauxRappels(ID_FALY, rappels(), maintenant)
        val second = travauxRappels(ID_FALY, rappels(), maintenant)

        assertEquals(premier, second)
    }

    /** Les deux rappels d'une même dose coexistent : noms distincts, R4 tenue. */
    @Test
    fun travaux_lesDeuxRappelsDuneMemeDoseNeSeRecouvrentPas() {
        val travaux = travauxRappels(ID_FALY, rappels(avecRappelFenetre = true), maintenant)
        val penta1 = travaux.filter { it.vaccinId == "penta1" }

        assertEquals(2, penta1.size)
        assertEquals(2, penta1.map { it.nomUnique }.toSet().size)
        assertEquals(
            setOf(TypeRappel.AVANT_ECHEANCE, TypeRappel.FENETRE_BIENTOT_FERMEE),
            penta1.map { it.type }.toSet(),
        )
    }

    /** Tous les travaux d'un enfant partagent l'étiquette qui permet de les annuler en bloc. */
    @Test
    fun travaux_partagentLEtiquetteDeLenfant() {
        val travaux = travauxRappels(ID_FALY, rappels(), maintenant)

        assertTrue(travaux.all { it.etiquetteEnfant == etiquetteTravauxEnfant(ID_FALY) })
    }

    /** DoD de B11, vue de bout en bout : en debug, tous les rappels tombent en une minute. */
    @Test
    fun travaux_avecLePlafondDeDemonstration_tousLesDelaisValentUneMinute() {
        val travaux = travauxRappels(ID_FALY, rappels(), maintenant, plafond = Duration.ofMinutes(1))

        assertTrue(travaux.isNotEmpty())
        assertTrue(travaux.all { it.delai == Duration.ofMinutes(1) })
    }

    /** Sans plafond — build release —, les délais restent ceux de R3, donc des mois. */
    @Test
    fun travaux_sansPlafond_gardentLesDelaisReels() {
        val travaux = travauxRappels(ID_FALY, rappels(), maintenant, plafond = null)

        assertTrue(travaux.all { it.delai > Duration.ofDays(1) })
    }

    // --- Journalisation --------------------------------------------------------

    /** Le journal ne porte qu'un fragment d'identifiant (§B0 : aucune donnée de santé). */
    @Test
    fun abregerIdentifiant_neGardeQueHuitCaracteres() {
        assertEquals("3f2b1c4d", abregerIdentifiant("3f2b1c4d-9a7e-4f11-8c3a-0b6d5e2f7a91"))
        assertEquals("court", abregerIdentifiant("court"))
    }
}
