package mg.univ.fahasalamana.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le seul morceau de B10 testable sans Android — et le plus fragile, parce qu'il relie
 * quatre fichiers qui ne se compilent pas ensemble et ne se voient pas les uns les autres :
 * `AndroidManifest.xml`, `AppNavHost`, `NotificationHelper` et `PlanificateurRappels` (B11).
 *
 * Une divergence entre eux ne produit ni erreur de compilation, ni message à l'exécution :
 * la notification s'affiche, on la touche, et il ne se passe rien. Ce test fige donc les
 * chaînes attendues au lieu de les recalculer — le recalculer à partir des constantes ne
 * prouverait rien, puisque c'est justement la valeur des constantes qui est le contrat.
 */
class LienNotificationTest {

    @Test
    fun `le lien profond est celui declare dans le manifeste`() {
        // Doit correspondre, caractère pour caractère, au couple scheme/host de
        // l'intent-filter de MainActivity dans AndroidManifest.xml.
        assertEquals("fahasalamana", SCHEME_DEEP_LINK)
        assertEquals("enfant", HOTE_ENFANT)
    }

    @Test
    fun `la base du lien est celle attendue par navDeepLink dans AppNavHost`() {
        // `navDeepLink<FicheEnfant>(basePath = BASE_LIEN_ENFANT)` ajoute lui-même le
        // segment de l'argument obligatoire `enfantId`.
        assertEquals("fahasalamana://enfant", BASE_LIEN_ENFANT)
    }

    @Test
    fun `le lien d'une fiche ajoute l'identifiant en dernier segment`() {
        assertEquals(
            "fahasalamana://enfant/2f1c0b3a-0000-4000-8000-000000000001",
            lienFicheEnfant("2f1c0b3a-0000-4000-8000-000000000001"),
        )
    }

    @Test
    fun `un UUID passe tel quel dans le lien, sans caractere a echapper`() {
        val identifiant = "3d4b1e27-8f6a-4c12-9a55-6b7c8d9e0f11"
        val lien = lienFicheEnfant(identifiant)

        assertTrue("l'identifiant doit terminer le lien", lien.endsWith("/$identifiant"))
        // Un UUID ne contient que des chiffres, des lettres et des tirets : rien qui
        // découpe un URI. C'est ce qui autorise l'absence d'encodage dans lienFicheEnfant.
        assertTrue(
            "un UUID ne doit contenir aucun caractère réservé d'URI",
            identifiant.all { it.isLetterOrDigit() || it == '-' },
        )
    }

    @Test
    fun `l'etiquette d'un rappel suit le nom unique de travail de la regle R4`() {
        // Même chaîne des deux côtés : `uniqueWorkName` du OneTimeWorkRequest (B11) et
        // étiquette de la notification (B10). Annuler l'un revient à retirer l'autre.
        assertEquals("rappel-enfant-1-penta1", etiquetteRappel("enfant-1", "penta1"))
    }

    @Test
    fun `deux doses du meme enfant ont des etiquettes distinctes`() {
        val penta1 = etiquetteRappel("enfant-1", "penta1")
        val penta2 = etiquetteRappel("enfant-1", "penta2")

        assertTrue("deux doses ne doivent pas s'écraser dans le volet", penta1 != penta2)
    }

    @Test
    fun `deux enfants ayant la meme dose ont des etiquettes distinctes`() {
        val faly = etiquetteRappel("enfant-1", "penta1")
        val soa = etiquetteRappel("enfant-2", "penta1")

        assertTrue("le rappel d'un enfant ne doit pas remplacer celui d'un autre", faly != soa)
    }
}
