package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Tests du verrouillage par code — §B8 point 4, US-B10, tâche B18.
 *
 * Logique pure, donc testée en JVM sans émulateur (Definition of Done, §0.4) : `javax.crypto`
 * et `java.security` appartiennent à la plateforme Java, pas à Android.
 *
 * **Vitesse des tests.** La valeur de production ([ITERATIONS_PBKDF2]) est lente par
 * conception ; l'appliquer à chaque cas allongerait `testDebugUnitTest` de plusieurs secondes
 * pour ne rien prouver de plus. Les cas passent donc par [ITERATIONS_TEST], et deux tests
 * seulement exercent la valeur réelle — c'est là que se vérifie qu'elle est bien celle qui
 * sert par défaut, et que le nombre d'itérations d'une empreinte est bien relu depuis
 * l'empreinte elle-même.
 *
 * Ce que ces tests défendent, au-delà des assertions : **le code n'apparaît nulle part**.
 * `empreinteNeContientPasLeCode` échouerait le jour où quelqu'un remplacerait la dérivation
 * par un encodage réversible en croyant simplifier.
 */
class VerrouillagePinTest {

    /** Assez d'itérations pour exercer le vrai algorithme, assez peu pour ne pas ralentir la CI. */
    private val iterationsTest = ITERATIONS_TEST

    private val code = "1234"
    private val sel = nouveauSel()

    // ----------------------------------------------------------------------
    // Format d'un code (erreurDePin)
    // ----------------------------------------------------------------------

    @Test
    fun codeVide_estRefuse() {
        assertEquals(ErreurPin.Vide, erreurDePin(""))
    }

    @Test
    fun codeTropCourt_estRefuseEtPorteLaLongueurMinimale() {
        assertEquals(ErreurPin.TropCourt(LONGUEUR_PIN_MIN), erreurDePin("123"))
    }

    @Test
    fun codeTropLong_estRefuseEtPorteLaLongueurMaximale() {
        assertEquals(ErreurPin.TropLong(LONGUEUR_PIN_MAX), erreurDePin("1234567"))
    }

    /** Le clavier est numérique, mais un collage ne l'est pas. */
    @Test
    fun codeAvecUneLettre_estRefuse() {
        assertEquals(ErreurPin.CaracteresInterdits, erreurDePin("12a4"))
    }

    /** Un espace se glisse facilement dans un collage, et ne se voit pas derrière les points. */
    @Test
    fun codeAvecUnEspace_estRefuse() {
        assertEquals(ErreurPin.CaracteresInterdits, erreurDePin("12 4"))
    }

    /**
     * Le format est vérifié avant la longueur : "12a" est refusé pour ses caractères, pas
     * pour sa taille. Le message affiché décrit alors la vraie cause.
     */
    @Test
    fun codeCourtEtNonNumerique_estRefusePourSesCaracteres() {
        assertEquals(ErreurPin.CaracteresInterdits, erreurDePin("12a"))
    }

    @Test
    fun codeDeQuatreChiffres_estAccepte() {
        assertNull(erreurDePin("1234"))
        assertTrue(pinValide("1234"))
    }

    @Test
    fun codeDeSixChiffres_estAccepte() {
        assertNull(erreurDePin("123456"))
        assertTrue(pinValide("123456"))
    }

    // ----------------------------------------------------------------------
    // Hachage : ce qui part dans DataStore
    // ----------------------------------------------------------------------

    /** Le test qui compte : rien de ce qui est stocké ne ressemble au code. */
    @Test
    fun empreinteNeContientPasLeCode() {
        val empreinte = creerEmpreintePin(code, iterations = iterationsTest)

        assertFalse(empreinte.hash.contains(code))
        assertFalse(empreinte.sel.contains(code))
    }

    /** Les paramètres voyagent avec l'empreinte : c'est ce qui permettra d'en changer. */
    @Test
    fun empreinte_porteSonAlgorithmeEtSesIterations() {
        val hash = hacherPin(code, sel, iterationsTest)
        val champs = hash.split("\$")

        assertEquals(3, champs.size)
        assertEquals("pbkdf2-sha256", champs[0])
        assertEquals(iterationsTest.toString(), champs[1])
        assertTrue(champs[2].isNotEmpty())
    }

    @Test
    fun memeCodeEtMemeSel_donnentLaMemeEmpreinte() {
        assertEquals(hacherPin(code, sel, iterationsTest), hacherPin(code, sel, iterationsTest))
    }

    @Test
    fun memeCodeEtSelsDifferents_donnentDesEmpreintesDifferentes() {
        val premiere = hacherPin(code, nouveauSel(), iterationsTest)
        val seconde = hacherPin(code, nouveauSel(), iterationsTest)

        assertNotEquals(premiere, seconde)
    }

    /**
     * Le sel est tiré par installation : deux téléphones sur lesquels le même code est choisi
     * ne stockent pas la même chose. C'est ce qui rend une table précalculée inutilisable
     * d'un appareil à l'autre.
     */
    @Test
    fun deuxEmpreintesDuMemeCode_ontDesSelsDifferents() {
        val premiere = creerEmpreintePin(code, iterations = iterationsTest)
        val seconde = creerEmpreintePin(code, iterations = iterationsTest)

        assertNotEquals(premiere.sel, seconde.sel)
        assertNotEquals(premiere.hash, seconde.hash)
    }

    @Test
    fun codesDifferents_donnentDesEmpreintesDifferentes() {
        assertNotEquals(hacherPin("1234", sel, iterationsTest), hacherPin("1235", sel, iterationsTest))
    }

    @Test
    fun hacherUnCodeVide_estRefuse() {
        assertThrows(IllegalArgumentException::class.java) { hacherPin("", sel, iterationsTest) }
    }

    @Test
    fun hacherAvecZeroIteration_estRefuse() {
        assertThrows(IllegalArgumentException::class.java) { hacherPin(code, sel, iterations = 0) }
    }

    /** Un sel de 16 octets, donc 24 caractères en Base64 : la longueur est fixe. */
    @Test
    fun selTireDeuxFois_estDifferentEtDeLongueurConstante() {
        val premier = nouveauSel()
        val second = nouveauSel()

        assertNotEquals(premier, second)
        assertEquals(premier.length, second.length)
    }

    // ----------------------------------------------------------------------
    // Vérification (pinCorrespond)
    // ----------------------------------------------------------------------

    @Test
    fun bonCode_estReconnu() {
        val empreinte = creerEmpreintePin(code, iterations = iterationsTest)

        assertTrue(pinCorrespond(code, empreinte))
    }

    @Test
    fun mauvaisCode_estRefuse() {
        val empreinte = creerEmpreintePin(code, iterations = iterationsTest)

        assertFalse(pinCorrespond("4321", empreinte))
    }

    @Test
    fun codeVide_neCorrespondJamais() {
        val empreinte = creerEmpreintePin(code, iterations = iterationsTest)

        assertFalse(pinCorrespond("", empreinte))
    }

    /** Le bon code avec le sel d'une autre installation ne doit pas ouvrir. */
    @Test
    fun bonCodeAvecUnAutreSel_estRefuse() {
        val empreinte = creerEmpreintePin(code, iterations = iterationsTest)
        val selEtranger = EmpreintePin(hash = empreinte.hash, sel = nouveauSel())

        assertFalse(pinCorrespond(code, selEtranger))
    }

    /**
     * Une empreinte créée avec un autre nombre d'itérations se relit **sans qu'on le lui
     * dise** : la recette est dans la chaîne stockée. C'est ce qui permettra d'augmenter
     * [ITERATIONS_PBKDF2] sans enfermer dehors ceux qui ont déjà un code.
     */
    @Test
    fun empreinteAncienne_seRelitAvecSesPropresIterations() {
        val ancienne = creerEmpreintePin(code, iterations = 2_000)

        assertTrue(pinCorrespond(code, ancienne))
        assertFalse(pinCorrespond("9999", ancienne))
    }

    // ----------------------------------------------------------------------
    // DataStore abîmé : refuser, jamais planter, jamais ouvrir
    // ----------------------------------------------------------------------

    @Test
    fun empreinteTronquee_estRefuseeSansException() {
        assertFalse(pinCorrespond(code, EmpreintePin(hash = "pbkdf2-sha256\$1000", sel = sel)))
    }

    @Test
    fun empreinteSansAlgorithmeConnu_estRefusee() {
        val vraie = creerEmpreintePin(code, iterations = iterationsTest)
        val douteuse = vraie.copy(hash = vraie.hash.replace("pbkdf2-sha256", "rot13"))

        assertFalse(pinCorrespond(code, douteuse))
    }

    @Test
    fun empreinteAvecIterationsIllisibles_estRefusee() {
        assertFalse(
            pinCorrespond(code, EmpreintePin(hash = "pbkdf2-sha256\$beaucoup\$AAAA", sel = sel)),
        )
    }

    @Test
    fun empreinteAvecIterationsNegatives_estRefusee() {
        assertFalse(
            pinCorrespond(code, EmpreintePin(hash = "pbkdf2-sha256\$-1\$AAAA", sel = sel)),
        )
    }

    @Test
    fun selIllisible_estRefuseSansException() {
        val vraie = creerEmpreintePin(code, iterations = iterationsTest)

        assertFalse(pinCorrespond(code, vraie.copy(sel = "ceci n'est pas du base64 !!")))
    }

    @Test
    fun empreinteVide_estRefusee() {
        assertFalse(pinCorrespond(code, EmpreintePin(hash = "", sel = "")))
    }

    /**
     * Empreinte lisible mais sel effacé : le cas qui ferait remonter une exception de
     * `PBEKeySpec` si le décodage acceptait une valeur vide. On attend `false`, pas un
     * plantage sur l'écran de verrouillage.
     */
    @Test
    fun selVideAvecUneEmpreinteValide_estRefuseSansException() {
        val vraie = creerEmpreintePin(code, iterations = iterationsTest)

        assertFalse(pinCorrespond(code, vraie.copy(sel = "")))
    }

    @Test
    fun hacherAvecUnSelVide_estRefuse() {
        assertThrows(IllegalArgumentException::class.java) { hacherPin(code, "", iterationsTest) }
    }

    // ----------------------------------------------------------------------
    // Valeur de production : le chemin par défaut fonctionne
    // ----------------------------------------------------------------------

    /**
     * Le seul test qui paie le prix réel de la dérivation, deux fois. S'il devient
     * douloureusement lent sur la machine du dev, c'est le signal que
     * [ITERATIONS_PBKDF2] est trop élevé pour le téléphone visé.
     */
    @Test
    fun cheminParDefaut_creeEtRelitUneEmpreinteVerifiable() {
        val empreinte = creerEmpreintePin("482913")

        assertTrue(empreinte.hash.startsWith("pbkdf2-sha256\$$ITERATIONS_PBKDF2\$"))
        assertTrue(pinCorrespond("482913", empreinte))
        assertFalse(pinCorrespond("482914", empreinte))
    }

    // ----------------------------------------------------------------------
    // Reverrouillage après deux minutes (fautIlReverrouiller)
    // ----------------------------------------------------------------------

    private val depart: Instant = Instant.parse("2026-09-16T08:00:00Z")

    /**
     * Démarrage à froid : le processus n'a jamais vu l'application partir en arrière-plan.
     * Le §B7.1 verrouille ce cas (`[*] --> Verrouillage : si PIN activé`).
     */
    @Test
    fun sansDernierAcces_ilFautVerrouiller() {
        assertTrue(fautIlReverrouiller(dernierAcces = null, maintenant = depart))
    }

    /** Scénario « reprise immédiate » de US-B10 : le carnet s'affiche sans redemander le code. */
    @Test
    fun retourAvantDeuxMinutes_neVerrouillePas() {
        assertFalse(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.plusSeconds(119),
            ),
        )
    }

    /** Lire un SMS pendant une saisie ne doit rien coûter : quelques secondes passent. */
    @Test
    fun allerEtRetourDeQuelquesSecondes_neVerrouillePas() {
        assertFalse(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.plusSeconds(8),
            ),
        )
    }

    /** La borne est inclusive : deux minutes pile reverrouillent. */
    @Test
    fun deuxMinutesPile_verrouille() {
        assertTrue(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.plusSeconds(120),
            ),
        )
    }

    /** Scénario « reprise après deux minutes » de US-B10. */
    @Test
    fun retourApresDeuxMinutes_verrouille() {
        assertTrue(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.plusSeconds(121),
            ),
        )
    }

    @Test
    fun retourLeLendemain_verrouille() {
        assertTrue(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.plus(Duration.ofDays(1)),
            ),
        )
    }

    /**
     * Horloge reculée pendant l'absence.
     *
     * Changer l'heure du téléphone est un aller-retour par les réglages Android, donc
     * exactement le geste que ce délai surveille : une durée négative ne doit pas valoir
     * « à l'instant », sinon elle offre un contournement gratuit.
     */
    @Test
    fun horlogeQuiRecule_verrouille() {
        assertTrue(
            fautIlReverrouiller(
                dernierAcces = depart,
                maintenant = depart.minusSeconds(3_600),
            ),
        )
    }

    /** Le délai est un paramètre : la valeur du CDC n'est qu'un défaut. */
    @Test
    fun delaiPersonnalise_estRespecte() {
        val delai = Duration.ofSeconds(30)

        assertFalse(fautIlReverrouiller(depart, depart.plusSeconds(29), delai))
        assertTrue(fautIlReverrouiller(depart, depart.plusSeconds(30), delai))
    }

    /** La valeur du §B8 n'a pas dérivé. */
    @Test
    fun delaiParDefaut_estDeDeuxMinutes() {
        assertEquals(Duration.ofMinutes(2), DELAI_VERROUILLAGE)
    }

    private companion object {
        const val ITERATIONS_TEST = 1_000
    }
}
