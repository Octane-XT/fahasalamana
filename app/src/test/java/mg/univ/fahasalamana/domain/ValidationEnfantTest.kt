package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de `domain/ValidationEnfant.kt` — scénarios Gherkin de US-B1 (§B3) et Definition of
 * Done du §0.4 : la logique métier pure se teste en JVM, sans émulateur.
 *
 * Jour de référence : le 15/09/2026. Toutes les dates sont fixées, aucune ne vient de
 * `LocalDate.now()` : un test qui dépend du jour où on le lance finit par échouer tout seul.
 *
 * Enfant de référence : Faly, né le 01/01/2026 — celui des scénarios du CDC.
 */
class ValidationEnfantTest {

    private val aujourdHui = LocalDate.of(2026, 9, 15)
    private val naissanceFaly = LocalDate.of(2026, 1, 1)

    private fun saisie(
        prenom: String = "Faly",
        dateNaissance: LocalDate? = naissanceFaly,
        sexe: Sexe = Sexe.GARCON,
    ) = SaisieEnfant(prenom = prenom, dateNaissance = dateNaissance, sexe = sexe)

    // ----------------------------------------------------------------------
    // US-B1 — scénario « création valide »
    // ----------------------------------------------------------------------

    /** « Quand je saisis "Faly", né le 01/01/2026, garçon, et je valide ». */
    @Test
    fun falyNeLe1erJanvier2026_estUneSaisieValide() {
        val validation = saisie().valider(aujourdHui)

        assertTrue(validation.valide)
        assertNull(validation.prenom)
        assertNull(validation.dateNaissance)
    }

    @Test
    fun saisieValide_produitLEnfantAEnregistrer() {
        val enfant = saisie().enfantValide(id = "enfant-1", aujourdHui = aujourdHui)

        assertEquals(
            Enfant(
                id = "enfant-1",
                prenom = "Faly",
                dateNaissance = naissanceFaly,
                sexe = Sexe.GARCON,
            ),
            enfant,
        )
    }

    /** Le sexe est facultatif : `NON_PRECISE` est une valeur valide, pas une saisie manquante. */
    @Test
    fun sexeNonPrecise_estAccepte() {
        val validation = saisie(sexe = Sexe.NON_PRECISE).valider(aujourdHui)

        assertTrue(validation.valide)
    }

    /** Né aujourd'hui : un nouveau-né s'inscrit le jour même, c'est le cas d'usage principal. */
    @Test
    fun neAujourdHui_estAccepte() {
        val validation = saisie(dateNaissance = aujourdHui).valider(aujourdHui)

        assertNull(validation.dateNaissance)
    }

    // ----------------------------------------------------------------------
    // US-B1 — scénario « date de naissance dans le futur »
    // ----------------------------------------------------------------------

    /** « Quand je saisis une date postérieure à aujourd'hui, alors le champ est en erreur ». */
    @Test
    fun dateDeDemain_estRefuseeCommeDansLeFutur() {
        val validation = saisie(dateNaissance = aujourdHui.plusDays(1)).valider(aujourdHui)

        assertEquals(ErreurDateNaissance.DansLeFutur, validation.dateNaissance)
        assertFalse(validation.valide)
    }

    @Test
    fun dateDansLeFutur_empecheDeConstruireLEnfant() {
        val enfant = saisie(dateNaissance = aujourdHui.plusYears(1))
            .enfantValide(id = "enfant-1", aujourdHui = aujourdHui)

        assertNull(enfant)
    }

    // ----------------------------------------------------------------------
    // Prénom
    // ----------------------------------------------------------------------

    @Test
    fun prenomVide_estRefuse() {
        val validation = saisie(prenom = "").valider(aujourdHui)

        assertEquals(ErreurPrenom.Vide, validation.prenom)
        assertFalse(validation.valide)
    }

    /** Un champ rempli d'espaces est un champ vide : c'est le nettoyage qui en décide. */
    @Test
    fun prenomFaitUniquementDEspaces_estRefuse() {
        val validation = saisie(prenom = "   \t ").valider(aujourdHui)

        assertEquals(ErreurPrenom.Vide, validation.prenom)
    }

    @Test
    fun prenomEntoureDEspaces_estAccepteEtEnregistreNettoye() {
        val avecEspaces = saisie(prenom = "  Faly  ")

        assertTrue(avecEspaces.valider(aujourdHui).valide)
        assertEquals(
            "Faly",
            avecEspaces.enfantValide(id = "enfant-1", aujourdHui = aujourdHui)?.prenom,
        )
    }

    /** Les espaces internes en trop sont ramenés à un seul : « Jean  Luc » reste « Jean Luc ». */
    @Test
    fun prenomComposeAEspacesMultiples_estNormalise() {
        assertEquals("Jean Luc", prenomNettoye("  Jean   Luc \n"))
    }

    @Test
    fun prenomDeLaLongueurMaximale_estAccepte() {
        val prenom = "a".repeat(LONGUEUR_MAX_PRENOM)

        assertNull(saisie(prenom = prenom).valider(aujourdHui).prenom)
    }

    @Test
    fun prenomPlusLongQueLeMaximum_estRefuse() {
        val prenom = "a".repeat(LONGUEUR_MAX_PRENOM + 1)

        assertEquals(
            ErreurPrenom.TropLong(LONGUEUR_MAX_PRENOM),
            saisie(prenom = prenom).valider(aujourdHui).prenom,
        )
    }

    /** La longueur se mesure après nettoyage : des espaces en trop ne font pas dépasser la limite. */
    @Test
    fun prenomDeLaLongueurMaximaleAvecDesEspaces_estAccepte() {
        val prenom = "  " + "a".repeat(LONGUEUR_MAX_PRENOM) + "  "

        assertNull(saisie(prenom = prenom).valider(aujourdHui).prenom)
    }

    // ----------------------------------------------------------------------
    // Date de naissance : absence et ancienneté
    // ----------------------------------------------------------------------

    @Test
    fun dateAbsente_estRefusee() {
        val validation = saisie(dateNaissance = null).valider(aujourdHui)

        assertEquals(ErreurDateNaissance.Absente, validation.dateNaissance)
        assertFalse(validation.valide)
    }

    @Test
    fun dateAbsente_empecheDeConstruireLEnfant() {
        assertNull(saisie(dateNaissance = null).enfantValide(id = "enfant-1", aujourdHui = aujourdHui))
    }

    @Test
    fun limiteDAnciennete_estAgeMaximumAnneesAvantAujourdHui() {
        assertEquals(LocalDate.of(2008, 9, 15), dateNaissanceMinimum(aujourdHui))
    }

    /** La borne est inclusive : le jour des dix-huit ans passe encore. */
    @Test
    fun dateExactementALaLimite_estAcceptee() {
        val validation = saisie(dateNaissance = LocalDate.of(2008, 9, 15)).valider(aujourdHui)

        assertNull(validation.dateNaissance)
    }

    @Test
    fun dateAnterieureALaLimite_estRefusee() {
        val validation = saisie(dateNaissance = LocalDate.of(2008, 9, 14)).valider(aujourdHui)

        assertEquals(
            ErreurDateNaissance.TropAncienne(limite = LocalDate.of(2008, 9, 15)),
            validation.dateNaissance,
        )
    }

    /** Le cas que la borne existe pour rattraper : l'année mal tapée. */
    @Test
    fun anneeMalTapee_estRefuseeCommeTropAncienne() {
        val validation = saisie(dateNaissance = LocalDate.of(1026, 1, 1)).valider(aujourdHui)

        assertTrue(validation.dateNaissance is ErreurDateNaissance.TropAncienne)
    }

    /**
     * Un 29 février, `minusYears` ramène au 28 : la borne reste une date réelle, et rien
     * n'explose le jour où l'application tourne une année bissextile.
     */
    @Test
    fun limiteCalculeeDepuisUn29Fevrier_retombeSurUneDateReelle() {
        assertEquals(LocalDate.of(2010, 2, 28), dateNaissanceMinimum(LocalDate.of(2028, 2, 29)))
    }

    // ----------------------------------------------------------------------
    // Les deux champs ensemble
    // ----------------------------------------------------------------------

    /** Les deux erreurs sont signalées d'un coup : on ne corrige pas l'une pour découvrir l'autre. */
    @Test
    fun prenomVideEtDateDansLeFutur_signalentLesDeuxErreurs() {
        val validation = saisie(prenom = " ", dateNaissance = aujourdHui.plusDays(30))
            .valider(aujourdHui)

        assertEquals(ErreurPrenom.Vide, validation.prenom)
        assertEquals(ErreurDateNaissance.DansLeFutur, validation.dateNaissance)
        assertFalse(validation.valide)
    }

    /** Saisie d'ouverture d'un formulaire de création : invalide, mais ce n'est pas une faute. */
    @Test
    fun saisieVierge_estInvalideSurLesDeuxChamps() {
        val validation = SaisieEnfant().valider(aujourdHui)

        assertEquals(ErreurPrenom.Vide, validation.prenom)
        assertEquals(ErreurDateNaissance.Absente, validation.dateNaissance)
        assertFalse(validation.valide)
    }

    @Test
    fun identifiantFourni_estRepriseTelQuelDansLEnfant() {
        val enfant = saisie().enfantValide(id = "uuid-existant", aujourdHui = aujourdHui)

        assertEquals("uuid-existant", enfant?.id)
    }
}
