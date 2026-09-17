package mg.univ.fahasalamana.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de `LiensCentre.kt` — tâche B14, US-B6 scénarios 2 et 3 (§B3).
 *
 * La seule partie de B14 qui soit de la logique pure : transformer un numéro tel que
 * l'annuaire le publie en une URI `tel:` que le composeur accepte. Testée en JVM, sans
 * émulateur, alors que l'`Intent` qui la porte ne le serait qu'à la main sur un téléphone.
 *
 * Ce que ces tests défendent, au-delà du format :
 *
 *  - **un espace ne doit pas casser l'Intent.** L'annuaire publie « +261 34 00 000 00 »
 *    parce que c'est lisible à l'écran, mais une URI n'accepte pas l'espace ; le composeur
 *    s'ouvrirait vide, sans qu'aucun message ne le dise ;
 *  - **un champ sans chiffre ne produit pas d'Intent.** `null` remonte jusqu'à l'écran, qui
 *    cache le bouton au lieu d'ouvrir un composeur vide.
 *
 * Le centre de référence est celui du scénario 2 : « CSB2 Ankirihiry », à Toamasina I.
 */
class LiensCentreTest {

    /** Le numéro est publié avec ses espaces ; une URI n'en accepte aucun. */
    @Test
    fun numeroPublieAvecEspaces_perdSesEspaces() {
        assertEquals("tel:+261340000000", uriTelephone("+261 34 00 000 00"))
    }

    /** Numéro local, sans indicatif international. */
    @Test
    fun numeroLocal_estConserveTelQuel() {
        assertEquals("tel:0321234567", uriTelephone("032 12 345 67"))
    }

    /** Tirets, points et parenthèses sont des séparateurs visuels, pas des chiffres. */
    @Test
    fun separateursVisuels_sontRetires() {
        assertEquals("tel:0202212345", uriTelephone("(020) 22-123.45"))
    }

    /**
     * Le `+` n'a de sens qu'en tête (RFC 3966). Ailleurs, c'est une coquille de saisie
     * dans l'annuaire, et le laisser passer ferait échouer l'ouverture du composeur.
     */
    @Test
    fun plusAuMilieuDUnNumero_estIgnore() {
        assertEquals("tel:0321234", uriTelephone("032 12+34"))
    }

    /** Espaces de début et de fin d'un champ mal saisi. */
    @Test
    fun espacesAutourDuNumero_sontIgnores() {
        assertEquals("tel:0321234567", uriTelephone("  032 12 345 67  "))
    }

    /** Champ absent de l'annuaire : il n'y a pas d'Intent à lancer, donc pas de bouton. */
    @Test
    fun numeroVide_neDonneAucuneUri() {
        assertNull(uriTelephone(""))
        assertNull(uriTelephone("   "))
    }

    /** Une mention sans le moindre chiffre ne doit pas ouvrir un composeur vide. */
    @Test
    fun mentionSansChiffre_neDonneAucuneUri() {
        assertNull(uriTelephone("—"))
        assertNull(uriTelephone("non communiqué"))
    }

    /** Un `+` seul n'est pas un numéro. */
    @Test
    fun plusSeul_neDonneAucuneUri() {
        assertNull(uriTelephone("+"))
    }

    /**
     * Les chiffres non latins ne sont pas gardés : aucun composeur ne les interprète dans
     * une URI `tel:`, et les laisser passer produirait une URI acceptée puis inerte.
     */
    @Test
    fun chiffresNonLatins_neSontPasGardes() {
        assertNull(uriTelephone("٠٣٢"))
    }
}
