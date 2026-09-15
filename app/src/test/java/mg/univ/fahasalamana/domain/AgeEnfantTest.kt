package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de `domain/AgeEnfant.kt` : l'âge écrit d'un enfant, unité comprise.
 *
 * Ces tests existent à cause d'un défaut trouvé en revue statique (15/09, point n° 9) :
 * l'âge du même enfant s'écrivait « 1 an » dans la liste et « 18 mois » dans sa fiche, à un
 * toucher d'intervalle. Les deux écrans appellent maintenant [ageDepuis] ; ce fichier fixe
 * ce que cette fonction répond, et donc ce que les deux écrans affichent.
 *
 * Jour de référence : le 15/09/2026, comme dans les autres tests du domaine. Aucune date ne
 * vient de `LocalDate.now()` : un test qui dépend du jour où on le lance finit par échouer
 * tout seul.
 */
class AgeEnfantTest {

    private val aujourdHui = LocalDate.of(2026, 9, 15)

    /** Âge au [aujourdHui] d'un enfant né il y a [jours] jours. */
    private fun neIlYA(jours: Long) = ageDepuis(aujourdHui.minusDays(jours), aujourdHui)

    // ----------------------------------------------------------------------
    // Le jour de la naissance
    // ----------------------------------------------------------------------

    /**
     * L'enfant est ajouté au carnet le jour de sa naissance, à la maternité (US-B1) : c'est
     * un âge qui s'affiche vraiment, pas un cas de bord théorique.
     */
    @Test
    fun enfantNeLeJourMeme_nAPasDAgeACompter() {
        assertEquals(AgeEnfant.JourDeNaissance, ageDepuis(aujourdHui, aujourdHui))
    }

    /**
     * Le formulaire interdit une date de naissance future (US-B1), mais un carnet importé
     * (B17) peut contenir n'importe quoi : aucun écran ne doit afficher « -3 mois ».
     */
    @Test
    fun dateDeNaissanceDansLeFutur_estRameneeAuJourDeNaissance() {
        val age = ageDepuis(LocalDate.of(2026, 12, 25), aujourdHui)

        assertEquals(AgeEnfant.JourDeNaissance, age)
    }

    // ----------------------------------------------------------------------
    // Jours, puis semaines : les premières lignes du calendrier
    // ----------------------------------------------------------------------

    /** Nourrisson de quelques jours : la maternité et le CSB comptent en jours. */
    @Test
    fun nourrissonDeTroisJours_seLitEnJours() {
        assertEquals(AgeEnfant.Jours(3), neIlYA(3))
    }

    @Test
    fun treizeJours_estLeDernierAgeQuiSeLitEnJours() {
        assertEquals(AgeEnfant.Jours(13), neIlYA(13))
    }

    @Test
    fun quatorzeJours_basculeEnSemaines() {
        assertEquals(AgeEnfant.Semaines(2), neIlYA(14))
    }

    /** Quelques semaines : on compte les semaines **révolues**, jamais l'arrondi supérieur. */
    @Test
    fun vingtQuatreJours_seLitTroisSemaines() {
        assertEquals(AgeEnfant.Semaines(3), neIlYA(24))
    }

    /**
     * Les semaines s'arrêtent au premier mois révolu, même quand il fait 31 jours : c'est le
     * mois du calendrier qui décide, pas une moyenne de 30,44 jours.
     */
    @Test
    fun unMoisRevolu_seLitEnMois() {
        val age = ageDepuis(LocalDate.of(2026, 8, 15), aujourdHui)

        assertEquals(AgeEnfant.Mois(1), age)
    }

    // ----------------------------------------------------------------------
    // Mois : toute la durée du calendrier vaccinal (naissance → 15 mois)
    // ----------------------------------------------------------------------

    /** Faly, né le 15/01/2026 : l'enfant de référence du CDC, au milieu de son calendrier. */
    @Test
    fun enfantDeHuitMois_seLitEnMois() {
        val age = ageDepuis(LocalDate.of(2026, 1, 15), aujourdHui)

        assertEquals(AgeEnfant.Mois(8), age)
    }

    /**
     * **Le défaut du point n° 9.** 18 mois se lit « 18 mois », et une seule fois : les deux
     * écrans appellent cette fonction, aucun des deux ne peut plus répondre « 1 an ».
     */
    @Test
    fun enfantDeDixHuitMois_seLitEnMois_etNonEnAnnees() {
        val age = ageDepuis(LocalDate.of(2025, 3, 15), aujourdHui)

        assertEquals(AgeEnfant.Mois(18), age)
    }

    /**
     * Conséquence assumée du seuil unique : à un an tout juste, le carnet écrit « 12 mois ».
     * Le calendrier s'arrête à 15 mois — tant qu'il reste des doses, l'enfant se situe en
     * mois sur sa frise.
     */
    @Test
    fun enfantDeDouzeMois_seLitEncoreEnMois() {
        val age = ageDepuis(LocalDate.of(2025, 9, 15), aujourdHui)

        assertEquals(AgeEnfant.Mois(12), age)
    }

    @Test
    fun vingtTroisMois_estLeDernierAgeQuiSeLitEnMois() {
        val age = ageDepuis(LocalDate.of(2024, 10, 15), aujourdHui)

        assertEquals(AgeEnfant.Mois(23), age)
    }

    // ----------------------------------------------------------------------
    // Années : à partir de 24 mois révolus, le calendrier est derrière l'enfant
    // ----------------------------------------------------------------------

    /** Deux ans tout juste : le jour anniversaire bascule, pas la veille. */
    @Test
    fun deuxAnsToutJuste_basculeEnAnnees() {
        val age = ageDepuis(LocalDate.of(2024, 9, 15), aujourdHui)

        assertEquals(AgeEnfant.Annees(2), age)
    }

    @Test
    fun laVeilleDesDeuxAns_seLitEncoreEnMois() {
        val age = ageDepuis(LocalDate.of(2024, 9, 16), aujourdHui)

        assertEquals(AgeEnfant.Mois(23), age)
    }

    @Test
    fun enfantDeTroisAns_seLitEnAnnees() {
        val age = ageDepuis(LocalDate.of(2023, 9, 15), aujourdHui)

        assertEquals(AgeEnfant.Annees(3), age)
    }

    // ----------------------------------------------------------------------
    // 29 février : l'anniversaire qui n'existe pas trois années sur quatre
    // ----------------------------------------------------------------------

    private val ne29Fevrier2024: LocalDate = LocalDate.of(2024, 2, 29)

    @Test
    fun neLe29Fevrier_lendemain_seLitEnJours() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2024, 3, 1))

        assertEquals(AgeEnfant.Jours(1), age)
    }

    @Test
    fun neLe29Fevrier_unMoisPlusTard_seLitUnMois() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2024, 3, 29))

        assertEquals(AgeEnfant.Mois(1), age)
    }

    /**
     * Une année non bissextile n'a pas de 29 février : `java.time` ne compte le mois révolu
     * qu'au 1er mars. L'âge est donc annoncé un jour trop tard plutôt qu'un jour trop tôt —
     * c'est le sens que doit prendre l'incertitude sur un carnet de vaccination.
     */
    @Test
    fun neLe29Fevrier_le28FevrierSuivant_nAPasEncoreDouzeMois() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2025, 2, 28))

        assertEquals(AgeEnfant.Mois(11), age)
    }

    @Test
    fun neLe29Fevrier_le1erMarsSuivant_aDouzeMois() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2025, 3, 1))

        assertEquals(AgeEnfant.Mois(12), age)
    }

    @Test
    fun neLe29Fevrier_le28Fevrier2026_seLitEncoreEnMois() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2026, 2, 28))

        assertEquals(AgeEnfant.Mois(23), age)
    }

    @Test
    fun neLe29Fevrier_le1erMars2026_basculeEnAnnees() {
        val age = ageDepuis(ne29Fevrier2024, LocalDate.of(2026, 3, 1))

        assertEquals(AgeEnfant.Annees(2), age)
    }

    /** Le 29 février peut aussi être le jour **de calcul**, pour un enfant né un 28. */
    @Test
    fun le29FevrierCommeJourDeCalcul_compteUnMoisRevolu() {
        val age = ageDepuis(LocalDate.of(2024, 1, 28), LocalDate.of(2024, 2, 29))

        assertEquals(AgeEnfant.Mois(1), age)
    }
}
