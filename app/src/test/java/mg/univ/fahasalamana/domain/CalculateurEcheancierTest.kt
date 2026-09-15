package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de [CalculateurEcheancier] — scénarios Gherkin de US-B1 et US-B2 (§B3),
 * règles R1, R2 et R6 (§B4) et cas limites exigés par la stratégie de tests (§B9).
 *
 * Enfant de référence : Faly, né le 01/01/2026 (l'enfant des scénarios du CDC).
 */
class CalculateurEcheancierTest {

    private val calc = CalculateurEcheancier()
    private val naissance = LocalDate.of(2026, 1, 1)
    private val faly = enfantNeLe(naissance)

    private fun echeancierAu(
        aujourdHui: LocalDate,
        administres: List<VaccinAdministre> = emptyList(),
        calendrier: List<VaccinReference> = CalendrierDeTest.COMPLET,
        enfant: Enfant = faly,
    ): List<LigneEcheancier> = calc.echeancier(enfant, calendrier, administres, aujourdHui)

    // ----------------------------------------------------------------------
    // US-B2 — calcul de l'échéancier et statuts
    // ----------------------------------------------------------------------

    @Test
    fun echeancier_produitUneLigneParDose_trieeParOrdre() {
        val lignes = echeancierAu(LocalDate.of(2026, 1, 20))

        assertEquals(16, lignes.size)
        assertEquals(CalendrierDeTest.COMPLET.map { it.id }, lignes.map { it.vaccin.id })
    }

    /** Scénario « calcul de l'échéancier » : né le 01/01/2026, Penta 1 à 42 jours. */
    @Test
    fun penta1_estPrevuLe12Fevrier2026() {
        val lignes = echeancierAu(LocalDate.of(2026, 1, 20))

        assertEquals(LocalDate.of(2026, 2, 12), lignes.ligne("penta1").prevuLe)
    }

    /** Scénario « statut À venir » : le 20/01/2026, Penta 1 est à venir dans 23 jours. */
    @Test
    fun penta1_le20Janvier_estAVenirDans23Jours() {
        val lignes = echeancierAu(LocalDate.of(2026, 1, 20))

        assertEquals(
            StatutVaccin.AVenir(prevuLe = LocalDate.of(2026, 2, 12), dansJours = 23L),
            lignes.ligne("penta1").statut,
        )
    }

    /** Scénario « statut À faire » : le 15/02/2026, la fenêtre court jusqu'au 26/02/2026. */
    @Test
    fun penta1_le15Fevrier_estAFaireJusquAu26Fevrier() {
        val lignes = echeancierAu(LocalDate.of(2026, 2, 15))

        assertEquals(
            StatutVaccin.AFaire(prevuLe = LocalDate.of(2026, 2, 12), jusquAu = LocalDate.of(2026, 2, 26)),
            lignes.ligne("penta1").statut,
        )
    }

    /** Scénario « statut En retard » : le 15/03/2026, 31 jours après la date prévue. */
    @Test
    fun penta1_le15Mars_estEnRetardDepuisLe12Fevrier() {
        val lignes = echeancierAu(LocalDate.of(2026, 3, 15))

        assertEquals(
            StatutVaccin.EnRetard(prevuLe = LocalDate.of(2026, 2, 12), retardJours = 31L),
            lignes.ligne("penta1").statut,
        )
    }

    /** Scénario « statut Fait » : une administration saisie le 13/02/2026. */
    @Test
    fun penta1_saisiLe13Fevrier_estFait_etLeResteEnsuite() {
        val saisie = listOf(administre("penta1", LocalDate.of(2026, 2, 13)))

        val auLendemain = echeancierAu(LocalDate.of(2026, 2, 14), saisie)
        val unAnApres = echeancierAu(LocalDate.of(2027, 2, 14), saisie)

        assertEquals(StatutVaccin.Fait(LocalDate.of(2026, 2, 13)), auLendemain.ligne("penta1").statut)
        assertEquals(StatutVaccin.Fait(LocalDate.of(2026, 2, 13)), unAnApres.ligne("penta1").statut)
    }

    /**
     * Scénario « dose dépendante » : Penta 1 fait avec 30 jours de retard le
     * 15/03/2026, donc Penta 2 est repoussé au 12/04/2026 — la date réelle de la
     * dose précédente fait foi, pas sa date théorique (R1).
     */
    @Test
    fun penta2_partDeLaDateReelleDePenta1_quandElleEstFaite() {
        val saisie = listOf(administre("penta1", LocalDate.of(2026, 3, 15)))

        val lignes = echeancierAu(LocalDate.of(2026, 3, 20), saisie)

        assertEquals(LocalDate.of(2026, 4, 12), lignes.ligne("penta2").prevuLe)
        assertEquals(
            StatutVaccin.AVenir(prevuLe = LocalDate.of(2026, 4, 12), dansJours = 23L),
            lignes.ligne("penta2").statut,
        )
        // La 3e dose suit la chaîne : 28 jours après la date théorique de la 2e.
        assertEquals(LocalDate.of(2026, 5, 10), lignes.ligne("penta3").prevuLe)
    }

    /** R1 — tant que la dose précédente n'est pas faite, la chaîne reste théorique. */
    @Test
    fun penta2_etPenta3_suiventLaChaineTheorique_quandRienNestFait() {
        val lignes = echeancierAu(LocalDate.of(2026, 1, 20))

        assertEquals(LocalDate.of(2026, 2, 12), lignes.ligne("penta1").prevuLe)
        assertEquals(LocalDate.of(2026, 3, 12), lignes.ligne("penta2").prevuLe)
        assertEquals(LocalDate.of(2026, 4, 9), lignes.ligne("penta3").prevuLe)
    }

    /** R1 — en attente tant que la date théorique de la dose n'est pas atteinte. */
    @Test
    fun penta2_resteEnAttente_avantSaDateTheorique() {
        val lignes = echeancierAu(LocalDate.of(2026, 3, 11))

        assertEquals(StatutVaccin.EnAttente(dependDe = "penta1"), lignes.ligne("penta2").statut)
        assertEquals(LocalDate.of(2026, 3, 12), lignes.ligne("penta2").prevuLe)
    }

    /**
     * R1 — l'attente cesse le jour de la date théorique : on repasse en À faire
     * puis En retard pour que le retard cumulé de la série reste visible.
     */
    @Test
    fun penta2_cesseDEtreEnAttente_desQueSaDateTheoriqueEstAtteinte() {
        val leJourJ = echeancierAu(LocalDate.of(2026, 3, 12))
        val apresLaFenetre = echeancierAu(LocalDate.of(2026, 3, 27))

        assertEquals(
            StatutVaccin.AFaire(prevuLe = LocalDate.of(2026, 3, 12), jusquAu = LocalDate.of(2026, 3, 26)),
            leJourJ.ligne("penta2").statut,
        )
        assertEquals(
            StatutVaccin.EnRetard(prevuLe = LocalDate.of(2026, 3, 12), retardJours = 15L),
            apresLaFenetre.ligne("penta2").statut,
        )
    }

    /** §B9 — dose dépendante d'une dose en retard : toute la série affiche son retard. */
    @Test
    fun serieEntiere_enRetard_afficheLeRetardCumule() {
        val lignes = echeancierAu(LocalDate.of(2026, 6, 1))

        assertEquals(
            StatutVaccin.EnRetard(LocalDate.of(2026, 2, 12), 109L),
            lignes.ligne("penta1").statut,
        )
        assertEquals(
            StatutVaccin.EnRetard(LocalDate.of(2026, 3, 12), 81L),
            lignes.ligne("penta2").statut,
        )
        assertEquals(
            StatutVaccin.EnRetard(LocalDate.of(2026, 4, 9), 53L),
            lignes.ligne("penta3").statut,
        )
    }

    /**
     * Scénario « recomposition instantanée » (version pure) : saisir Penta 1 fait
     * basculer sa ligne et ne décale que sa propre série.
     */
    @Test
    fun saisieDePenta1_basculeSaLigne_etNeDecaleQueSaSerie() {
        val aujourdHui = LocalDate.of(2026, 2, 20)
        val avant = echeancierAu(aujourdHui)
        val apres = echeancierAu(aujourdHui, listOf(administre("penta1", aujourdHui)))

        assertEquals(
            StatutVaccin.AFaire(LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 26)),
            avant.ligne("penta1").statut,
        )
        assertEquals(StatutVaccin.Fait(aujourdHui), apres.ligne("penta1").statut)
        // La 2e dose de la même série repart de la date réelle…
        assertEquals(LocalDate.of(2026, 3, 12), avant.ligne("penta2").prevuLe)
        assertEquals(LocalDate.of(2026, 3, 20), apres.ligne("penta2").prevuLe)
        // …les autres séries ne bougent pas.
        assertEquals(avant.ligne("vpo1"), apres.ligne("vpo1"))
        assertEquals(avant.ligne("vpo2"), apres.ligne("vpo2"))
    }

    // ----------------------------------------------------------------------
    // US-B1 — un enfant qui vient de naître
    // ----------------------------------------------------------------------

    /**
     * Scénario « création valide » : un nouveau-né affiche « 2 vaccins à faire,
     * 0 en retard » — BCG et Polio oral dose 0, prévus à la naissance.
     */
    @Test
    fun enfantNeAujourdHui_aDeuxDosesAFaireEtAucunRetard() {
        val aujourdHui = LocalDate.of(2026, 5, 20)
        val lignes = calc.echeancier(enfantNeLe(aujourdHui), CalendrierDeTest.COMPLET, emptyList(), aujourdHui)

        assertEquals(
            StatutVaccin.AFaire(prevuLe = aujourdHui, jusquAu = LocalDate.of(2026, 6, 19)),
            lignes.ligne("bcg").statut,
        )
        assertEquals(
            StatutVaccin.AFaire(prevuLe = aujourdHui, jusquAu = LocalDate.of(2026, 6, 3)),
            lignes.ligne("vpo0").statut,
        )
        // R6 : la prochaine dose à faire, c'est BCG, prévue aujourd'hui même — et non la
        // première dose encore à venir (Pentavalent 1, le 01/07).
        assertEquals(
            ResumeEnfant(nbEnRetard = 0, nbAFaire = 2, prochaineEcheance = aujourdHui),
            calc.resume(lignes),
        )
    }

    // ----------------------------------------------------------------------
    // §B9 — cas limites
    // ----------------------------------------------------------------------

    /** Le 29 février compte comme un jour : 42 jours après le 31/01 tombent un jour plus tôt. */
    @Test
    fun anneeBissextile_le29FevrierEstCompteCommeUnJour() {
        val bissextile = LocalDate.of(2024, 1, 31)
        val ordinaire = LocalDate.of(2026, 1, 31)

        val en2024 = calc.echeancier(enfantNeLe(bissextile), CalendrierDeTest.COMPLET, emptyList(), bissextile)
        val en2026 = calc.echeancier(enfantNeLe(ordinaire), CalendrierDeTest.COMPLET, emptyList(), ordinaire)

        assertEquals(LocalDate.of(2024, 3, 13), en2024.ligne("penta1").prevuLe)
        assertEquals(LocalDate.of(2026, 3, 14), en2026.ligne("penta1").prevuLe)
    }

    /** Enfant né un 29 février : aucune date calculée ne tombe sur un jour inexistant. */
    @Test
    fun anneeBissextile_enfantNeLe29Fevrier() {
        val naissance29 = LocalDate.of(2024, 2, 29)
        val enfant = enfantNeLe(naissance29)

        val lignes = calc.echeancier(enfant, CalendrierDeTest.COMPLET, emptyList(), naissance29)

        assertEquals(naissance29, lignes.ligne("bcg").prevuLe)
        assertEquals(LocalDate.of(2024, 4, 11), lignes.ligne("penta1").prevuLe)
        assertEquals(LocalDate.of(2024, 11, 25), lignes.ligne("rr1").prevuLe)
        assertEquals(LocalDate.of(2025, 5, 24), lignes.ligne("rr2").prevuLe)
    }

    /** Une dose retirée du calendrier ne produit plus de ligne, même si elle a été saisie. */
    @Test
    fun vaccinRetireDuCalendrier_neProduitPlusDeLigne_etNeCassePasLeCalcul() {
        val calendrier = CalendrierDeTest.sans("rota1", "rota2")
        val saisie = listOf(administre("rota1", LocalDate.of(2026, 2, 15)))

        val lignes = echeancierAu(LocalDate.of(2026, 2, 20), saisie, calendrier)

        assertEquals(14, lignes.size)
        assertFalse(lignes.any { it.vaccin.id == "rota1" || it.vaccin.id == "rota2" })
        assertEquals(
            StatutVaccin.AFaire(LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 26)),
            lignes.ligne("penta1").statut,
        )
    }

    /**
     * Contrepartie du test précédent : la dose saisie ne disparaît pas pour autant du carnet.
     *
     * L'échéancier ne peut pas la porter — il suit le calendrier — mais `dosesHorsCalendrier`
     * la retrouve, et c'est elle que la fiche affiche en dernière section (CDC §B5.2 :
     * conservée **et** affichée). Les deux tests se lisent ensemble : sans celui-ci, une dose
     * correctement saisie serait silencieusement invisible.
     */
    @Test
    fun vaccinRetireDuCalendrier_resteRetrouveParDosesHorsCalendrier() {
        val calendrier = CalendrierDeTest.sans("rota1", "rota2")
        val saisie = listOf(administre("rota1", LocalDate.of(2026, 2, 15)))

        val horsCalendrier = dosesHorsCalendrier(faly, saisie, calendrier)

        assertEquals(listOf("rota1"), horsCalendrier.map(VaccinAdministre::vaccinId))
        assertEquals(LocalDate.of(2026, 2, 15), horsCalendrier.single().date)
        // Et elle continue de compter parmi les doses reçues de l'en-tête de la fiche.
        val lignes = echeancierAu(LocalDate.of(2026, 2, 20), saisie, calendrier)
        assertEquals(1, nbFaits(lignes, horsCalendrier))
    }

    /** Dose retirée du calendrier mais déjà administrée : elle ancre toujours la dose suivante. */
    @Test
    fun vaccinRetireDuCalendrier_maisDejaAdministre_ancreLaDoseSuivante() {
        val calendrier = CalendrierDeTest.sans("penta1")
        val saisie = listOf(administre("penta1", LocalDate.of(2026, 3, 15)))

        val lignes = echeancierAu(LocalDate.of(2026, 3, 20), saisie, calendrier)

        assertEquals(LocalDate.of(2026, 4, 12), lignes.ligne("penta2").prevuLe)
        assertEquals(
            StatutVaccin.AVenir(LocalDate.of(2026, 4, 12), 23L),
            lignes.ligne("penta2").statut,
        )
    }

    /** Chaîne cassée : sans date calculable, la dose reste explicitement en attente. */
    @Test
    fun vaccinRetireDuCalendrier_etJamaisAdministre_laisseLaSuiteSansDate() {
        val calendrier = CalendrierDeTest.sans("penta1")

        val lignes = echeancierAu(LocalDate.of(2026, 3, 20), calendrier = calendrier)

        assertNull(lignes.ligne("penta2").prevuLe)
        assertEquals(StatutVaccin.EnAttente("penta1"), lignes.ligne("penta2").statut)
        assertNull(lignes.ligne("penta3").prevuLe)
        assertEquals(StatutVaccin.EnAttente("penta2"), lignes.ligne("penta3").statut)
        // Les autres séries restent intactes.
        assertEquals(LocalDate.of(2026, 3, 12), lignes.ligne("vpo2").prevuLe)
    }

    /** Calendrier de référence invalide : le calcul ne boucle pas et ne lève rien. */
    @Test
    fun dependanceCirculaire_neBouclePas() {
        val lignes = echeancierAu(LocalDate.of(2026, 3, 20), calendrier = CalendrierDeTest.CIRCULAIRE)

        assertEquals(2, lignes.size)
        assertNull(lignes.ligne("a").prevuLe)
        assertNull(lignes.ligne("b").prevuLe)
        assertEquals(StatutVaccin.EnAttente("b"), lignes.ligne("a").statut)
        assertEquals(StatutVaccin.EnAttente("a"), lignes.ligne("b").statut)
    }

    /** Les doses d'un autre enfant n'entrent jamais dans cet échéancier. */
    @Test
    fun administrationsDUnAutreEnfant_sontIgnorees() {
        val saisie = listOf(administre("penta1", LocalDate.of(2026, 2, 13), enfantId = "enfant-soa"))

        val lignes = echeancierAu(LocalDate.of(2026, 2, 20), saisie)

        assertEquals(
            StatutVaccin.AFaire(LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 26)),
            lignes.ligne("penta1").statut,
        )
    }

    /** Carnet importé abîmé : deux saisies pour la même dose, la plus ancienne l'emporte. */
    @Test
    fun administrationDupliquee_laPlusAncienneDateFaitFoi() {
        val saisie = listOf(
            administre("penta1", LocalDate.of(2026, 3, 5)),
            administre("penta1", LocalDate.of(2026, 2, 20)),
        )

        val lignes = echeancierAu(LocalDate.of(2026, 3, 10), saisie)

        assertEquals(StatutVaccin.Fait(LocalDate.of(2026, 2, 20)), lignes.ligne("penta1").statut)
        assertEquals(LocalDate.of(2026, 3, 20), lignes.ligne("penta2").prevuLe)
    }

    /** Aucune dose dans le calendrier : échéancier vide, résumé neutre. */
    @Test
    fun calendrierVide_donneUnEcheancierVide() {
        val lignes = echeancierAu(LocalDate.of(2026, 3, 20), calendrier = emptyList())

        assertTrue(lignes.isEmpty())
        assertEquals(ResumeEnfant(0, 0, null), calc.resume(lignes))
    }

    // ----------------------------------------------------------------------
    // R6 — résumé (US-B8, badges)
    // ----------------------------------------------------------------------

    @Test
    fun resume_compteLesRetardsEtLesDosesAFaire() {
        val lignes = echeancierAu(LocalDate.of(2026, 2, 20))

        val resume = calc.resume(lignes)

        assertEquals(2, resume.nbEnRetard)   // BCG et Polio oral dose 0, fenêtres closes
        assertEquals(4, resume.nbAFaire)     // les quatre doses de 6 semaines
    }

    /**
     * `prochaineEcheance` est la date de la prochaine dose **à faire**, tous statuts non
     * faits confondus : ici BCG, prévu le 01/01 et déjà en retard, et non la première date
     * encore à venir (12/03, Pentavalent 2).
     */
    @Test
    fun resume_prochaineEcheance_estLaPlusProcheDoseNonFaite() {
        val lignes = echeancierAu(LocalDate.of(2026, 2, 20))

        assertEquals(LocalDate.of(2026, 1, 1), calc.resume(lignes).prochaineEcheance)
    }

    /** Les doses reçues sortent du calcul : l'échéance avance au fur et à mesure des saisies. */
    @Test
    fun resume_prochaineEcheance_ignoreLesDosesDejaFaites() {
        val saisie = listOf(
            administre("bcg", LocalDate.of(2026, 1, 2)),
            administre("vpo0", LocalDate.of(2026, 1, 2)),
        )

        val lignes = echeancierAu(LocalDate.of(2026, 2, 20), saisie)

        // Les quatre doses de 6 semaines, prévues le 12/02 et encore dans leur fenêtre.
        assertEquals(LocalDate.of(2026, 2, 12), calc.resume(lignes).prochaineEcheance)
    }

    /**
     * Le défaut corrigé, dans sa forme la plus visible : un enfant dont **toutes** les doses
     * restantes sont en retard.
     *
     * Restreint aux dates encore à venir, le résumé ne trouvait plus rien et la carte de
     * §B7.2 affichait deux lignes contradictoires côte à côte — « 16 en retard » et
     * « Prochain : aucune échéance à venir » — alors qu'il restait précisément seize doses à
     * faire. La prochaine est BCG, prévue le 01/01 : dépassée, mais c'est bien elle.
     */
    @Test
    fun resume_toutEnRetard_renvoieLaProchaineDoseAFaire() {
        val lignes = echeancierAu(LocalDate.of(2028, 1, 1))

        val resume = calc.resume(lignes)

        assertEquals(16, resume.nbEnRetard)
        assertEquals(0, resume.nbAFaire)
        assertEquals(LocalDate.of(2026, 1, 1), resume.prochaineEcheance)
    }

    /**
     * Même règle quand il reste des doses à venir : le résumé n'enjambe plus les retards.
     *
     * Au 01/06, quatorze doses sont en retard et Rougeole-Rubéole 1 est encore devant nous,
     * le 28/09. C'est l'ancienne réponse ; la prochaine dose à faire est la plus ancienne des
     * quatorze, pas la première encore à venir.
     */
    @Test
    fun resume_prochaineEcheance_nEnjambePasLesDosesEnRetard() {
        val lignes = echeancierAu(LocalDate.of(2026, 6, 1))

        val resume = calc.resume(lignes)

        assertEquals(14, resume.nbEnRetard)
        assertEquals(LocalDate.of(2026, 1, 1), resume.prochaineEcheance)
    }

    @Test
    fun resume_carnetComplet_neRenvoieNiRetardNiEcheance() {
        val saisie = CalendrierDeTest.COMPLET.map { administre(it.id, LocalDate.of(2026, 1, 5)) }

        val lignes = echeancierAu(LocalDate.of(2027, 6, 1), saisie)

        assertEquals(ResumeEnfant(nbEnRetard = 0, nbAFaire = 0, prochaineEcheance = null), calc.resume(lignes))
    }
}
