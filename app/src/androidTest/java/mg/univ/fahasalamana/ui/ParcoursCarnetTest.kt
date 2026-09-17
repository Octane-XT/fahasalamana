package mg.univ.fahasalamana.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mg.univ.fahasalamana.MainActivity
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.di.BaseEnMemoireRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Tests d'interface du parcours principal (tâche B21, CDC §B9 — ligne « UI Compose »).
 *
 * Deux scénarios, ceux que la Definition of Done de B21 nomme :
 *
 * 1. **Ajout d'un enfant → la fiche affiche toutes les doses du calendrier**
 *    (US-B1 « création valide » enchaîné sur US-B2 « calcul de l'échéancier »).
 * 2. **Saisie d'une dose → la ligne passe à « Fait »** sans rafraîchissement manuel
 *    (US-B2, dernier scénario : « recomposition instantanée »).
 *
 * ## Trois partis pris, parce qu'ils décident de la solidité de ces tests
 *
 * **La base est en mémoire, injectée par Koin** — voir [BaseEnMemoireRule] pour le détail :
 * chaque test repart d'un carnet vide et n'écrit aucun enfant sur l'appareil.
 *
 * **Rien n'est cherché par un libellé écrit en dur.** Les textes affichés sont relus dans
 * `strings.xml` (`R.string.…`), et les lignes de l'échéancier sont trouvées par l'étiquette
 * de leur action de clic (`fiche_action_saisir`), pas par leur contenu. Reformuler un
 * libellé ne casse donc pas ces tests ; en revanche, rien ici ne remplace de vrais
 * `testTag` dans le code de production — ils sont listés dans le rapport de B21, ce sont
 * eux qui rendraient les sélecteurs à l'épreuve d'un remaniement d'écran.
 *
 * **Le nombre de doses n'est pas écrit en dur.** Il est lu dans `assets/calendrier.json`,
 * par le même code que l'application (`SourcesEmbarquees`). Le « 16 » de la DoD est la
 * taille du fichier livré en B03 ; le jour où le calendrier gagne une dose, ces tests
 * suivent au lieu de devenir faux.
 *
 * ## Ce que ces tests ne couvrent pas
 *
 * Ni rappels, ni export/import, ni verrouillage, ni annuaire des centres : ces branches ne
 * sont pas fusionnées, et un test d'interface qui les supposerait ne compilerait pas.
 *
 * Ils ne vérifient pas non plus la **couleur** de la pastille. Elle n'est portée par aucune
 * sémantique — l'icône est décorative (`contentDescription = null`) et il n'y a pas de
 * `testTag`. Ce qui est vérifié est son équivalent accessible, celui que le CDC §B7.2 exige
 * de toute façon à côté de la couleur : le **libellé écrit** du statut, qui passe de
 * « À faire » à « Fait ». Un daltonien lit la même chose que ce test.
 *
 * À lancer sur la machine du dev : `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ParcoursCarnetTest {

    private val regleBase = BaseEnMemoireRule()

    private val regleCompose = createAndroidComposeRule<MainActivity>()

    /**
     * L'ordre compte : la base de test doit être en place **avant** que `MainActivity` ne
     * soit lancée, sinon les ViewModels de l'écran de départ résolvent les définitions de
     * production. `RuleChain` garantit cet ordre, ce que deux `@get:Rule` indépendants ne
     * font pas.
     */
    @get:Rule
    val regles: RuleChain = RuleChain.outerRule(regleBase).around(regleCompose)

    private val contexte: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // --- Les deux tests de la DoD ---------------------------------------------

    /**
     * US-B1 « création valide » puis US-B2 « calcul de l'échéancier » : un enfant ajouté
     * depuis l'écran d'accueil ouvre une fiche qui porte une ligne par dose du calendrier.
     */
    @Test
    fun ajoutDUnEnfant_saFicheAfficheUneLigneParDoseDuCalendrier() {
        val attendues = lignesAttendues()
        assertTrue(
            "assets/calendrier.json ne contient aucune dose : le test ne prouverait rien",
            attendues.isNotEmpty(),
        )
        assertEquals(
            "deux doses du calendrier portent le même nom et la même dose : " +
                "elles ne sont plus distinguables à l'écran",
            regleBase.calendrier.vaccins.size,
            attendues.size,
        )

        ajouterUnEnfant(PRENOM)
        ouvrirLaFiche(PRENOM)

        val affichees = parcourirLaFicheEtReleverLesLignes()

        assertEquals(
            "l'échéancier doit porter exactement une ligne par dose du calendrier embarqué",
            attendues,
            affichees,
        )
    }

    /**
     * US-B2, scénario « recomposition instantanée » : la dose saisie depuis l'écran de
     * saisie est déjà marquée faite au retour sur la fiche.
     *
     * Aucun geste de rafraîchissement entre l'enregistrement et la vérification : toute la
     * chaîne passe par des `Flow` Room, c'est précisément ce que ce test démontre.
     */
    @Test
    fun saisieDUneDose_laLigneDeLaFichePasseAFaitSansRafraichissement() {
        ajouterUnEnfant(PRENOM)
        ouvrirLaFiche(PRENOM)

        val statutFait = texte(R.string.fiche_statut_fait)
        val premiereLigne = regleCompose
            .onAllNodes(estUneLigneDEcheancier())
            .onFirst()
        val textesAvant = textesDe(premiereLigne.fetchSemanticsNode())
        assertTrue(
            "une ligne d'échéancier porte au moins le titre du vaccin et son statut, " +
                "or celle-ci porte $textesAvant",
            textesAvant.size >= 2,
        )
        val titre = textesAvant.first()
        val statutAvant = requireNotNull(textesAvant.firstOrNull { it in libellesDeStatut() }) {
            "aucun des cinq libellés de statut n'apparaît sur la ligne : $textesAvant"
        }

        assertNotEquals(
            "la première ligne de la fiche d'un enfant neuf ne peut pas déjà être faite",
            statutFait,
            statutAvant,
        )

        premiereLigne.performClick()
        saisirLaDoseDuJour()

        // Pas de `waitForIdle` suivi d'une assertion : on attend la **condition**, jamais
        // un délai. Si la fiche demandait un rafraîchissement manuel, ce test expirerait.
        regleCompose.waitUntil(DELAI_MS) {
            regleCompose
                .onAllNodes(estUneLigneDEcheancier() and hasText(titre) and hasText(statutFait))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Et l'ancien statut a bien disparu de cette ligne : l'affichage a changé, il ne
        // s'est pas contenté de gagner une mention supplémentaire.
        regleCompose
            .onNode(estUneLigneDEcheancier() and hasText(titre) and hasText(statutAvant))
            .assertDoesNotExist()
    }

    // --- Étapes du parcours ---------------------------------------------------

    /**
     * Écran « Mes enfants » → bouton d'ajout → formulaire → validation.
     *
     * La date de naissance est le **1er du mois affiché** par le sélecteur Material. Ce
     * choix n'est pas anodin : le sélecteur ouvre sur le mois courant de l'appareil, et le
     * 1er de ce mois est la seule cellule qui soit à coup sûr présente **et** jamais dans le
     * futur, quel que soit le jour où le test tourne. L'enfant a donc entre 0 et 30 jours,
     * et aucune assertion de ce fichier ne dépend des statuts qui en découlent — ni le
     * nombre de lignes, ni le passage à « Fait ».
     */
    private fun ajouterUnEnfant(prenom: String) {
        val bouton = hasText(texte(R.string.mes_enfants_action_ajouter))
        attendre(bouton)
        regleCompose.onNode(bouton).performClick()

        // Le formulaire passe par un état de chargement avant d'être composé : on attend
        // le champ, on ne suppose pas qu'il est déjà là.
        val champPrenom = hasText(texte(R.string.edition_champ_prenom))
        attendre(champPrenom)
        regleCompose.onNode(champPrenom).performTextInput(prenom)
        // Le clavier virtuel recouvre le bas du formulaire : tant qu'il est ouvert, un clic
        // injecté au centre d'un bouton peut tomber dessus au lieu du bouton.
        Espresso.closeSoftKeyboard()

        regleCompose
            .onNode(aPourEtiquetteDeClic(texte(R.string.edition_action_choisir_date)))
            .performClick()
        val celluleDuJour = estLaCelluleDuJour(JOUR_DE_NAISSANCE)
        attendre(celluleDuJour)
        regleCompose.onNode(celluleDuJour).performClick()
        regleCompose.onNodeWithText(texte(R.string.edition_action_valider)).performClick()

        regleCompose
            .onNodeWithText(texte(R.string.edition_sexe_garcon))
            .performScrollTo()
            .performClick()

        val creer = hasText(texte(R.string.edition_action_creer))
        regleCompose
            .onNode(creer)
            .performScrollTo()
            // Le bouton n'est actif que si `ValidationEnfant` accepte prénom et date : une
            // assertion ici dit « le formulaire est mal rempli » plutôt que « le clic n'a
            // rien fait », trois étapes plus loin.
            .assertIsEnabled()
            .performClick()

        // On attend que le formulaire ait quitté la composition, et pas seulement que
        // l'enregistrement soit fait : pendant l'animation de retour, le champ « Prénom »
        // porte encore le prénom saisi et il est cliquable, ce qui rendrait la recherche de
        // la carte de l'enfant ambiguë à l'étape suivante.
        attendreLaDisparition(creer)
    }

    /** Liste des enfants → carte de [prenom] → fiche, attendue affichée. */
    private fun ouvrirLaFiche(prenom: String) {
        val carte = hasText(prenom) and hasClickAction()
        attendre(carte)
        regleCompose.onNode(carte).performClick()

        // La fiche est ouverte dès qu'une ligne d'échéancier est cliquable.
        attendre(estUneLigneDEcheancier())
    }

    /**
     * Écran de saisie : ouvrir le calendrier, valider la date proposée, enregistrer.
     *
     * La date proposée est le jour même (`SaisieVaccinViewModel` amorce le formulaire sur
     * `horlogeJour`), donc toujours postérieure à la naissance et jamais dans le futur : la
     * règle R5 l'accepte, quel que soit le jour où le test tourne. On passe quand même par
     * le sélecteur, parce que c'est le geste du scénario — sans choisir une cellule de jour,
     * qui ferait dépendre le test du format de date de la locale de l'appareil.
     */
    private fun saisirLaDoseDuJour() {
        val ouvrirCalendrier = hasContentDescription(texte(R.string.saisie_date_action))
        attendre(ouvrirCalendrier)
        regleCompose.onNode(ouvrirCalendrier).performClick()
        regleCompose.onNodeWithText(texte(R.string.saisie_date_valider)).performClick()

        regleCompose
            .onNodeWithText(texte(R.string.saisie_action_enregistrer))
            .performScrollTo()
            // Actif sauf refus de R5 : l'assertion distingue une date refusée d'un clic perdu.
            .assertIsEnabled()
            .performClick()
    }

    // --- Lecture de l'échéancier ----------------------------------------------

    /**
     * Fait défiler la fiche jusqu'à son bandeau de source et relève le titre de chaque
     * ligne d'échéancier rencontrée.
     *
     * Un `LazyColumn` ne compose que ce qui est visible : compter les lignes présentes à
     * l'écran donnerait cinq ou six lignes, pas seize. On avance donc d'un demi-écran à la
     * fois — la fenêtre visible recouvre toujours la précédente, aucune ligne ne peut être
     * sautée — et l'on s'arrête sur le bandeau de source, dernier élément de la liste.
     */
    private fun parcourirLaFicheEtReleverLesLignes(): Set<String> {
        val bandeauFinal = texte(R.string.fiche_source_mention)
        val liste = regleCompose.onNode(hasScrollToIndexAction())
        val titres = LinkedHashSet<String>()
        var basAtteint = false
        var passes = 0

        while (passes++ < PASSES_MAX) {
            titres += titresDesLignesVisibles()
            basAtteint = regleCompose
                .onAllNodesWithText(bandeauFinal)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (basAtteint) break

            liste.performTouchInput {
                // Glissement lent et court : pas d'élan, donc pas de saut incontrôlé.
                swipeUp(startY = centerY, endY = top + 1f, durationMillis = 400L)
            }
            regleCompose.waitForIdle()
        }

        assertTrue(
            "le bas de la fiche n'a pas été atteint en $PASSES_MAX défilements",
            basAtteint,
        )
        return titres
    }

    /** Titres des lignes d'échéancier actuellement composées. */
    private fun titresDesLignesVisibles(): List<String> =
        regleCompose
            .onAllNodes(estUneLigneDEcheancier())
            .fetchSemanticsNodes()
            .mapNotNull { noeud -> textesDe(noeud).firstOrNull() }

    /**
     * Les lignes attendues, lues dans `assets/calendrier.json` et mises en forme avec le
     * même texte que l'écran (`fiche_vaccin_titre`, « Pentavalent — 1re dose »).
     */
    private fun lignesAttendues(): Set<String> = regleBase.calendrier.vaccins
        .map { vaccin -> texte(R.string.fiche_vaccin_titre, vaccin.nom, vaccin.dose) }
        .toSet()

    /** Les cinq libellés de statut (règle R2), pour reconnaître celui que porte une ligne. */
    private fun libellesDeStatut(): Set<String> = setOf(
        texte(R.string.fiche_statut_fait),
        texte(R.string.fiche_statut_a_venir),
        texte(R.string.fiche_statut_a_faire),
        texte(R.string.fiche_statut_en_retard),
        texte(R.string.fiche_statut_en_attente),
    )

    // --- Attentes ---------------------------------------------------------------

    /*
     * Une condition, jamais un délai. `waitForIdle` ne suffit pas ici : entre deux écrans,
     * ce que l'on attend n'est pas la fin d'une animation mais la **première émission d'un
     * Flow** — la base, le calendrier, l'horloge du jour. Un `Thread.sleep` calibré sur un
     * poste de développement deviendrait un test qui échoue une fois sur dix en intégration.
     */

    /** Attend qu'au moins un nœud corresponde à [matcher]. */
    private fun attendre(matcher: SemanticsMatcher) {
        regleCompose.waitUntil(DELAI_MS) {
            regleCompose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Attend que plus aucun nœud ne corresponde à [matcher] (écran quitté, fenêtre fermée). */
    private fun attendreLaDisparition(matcher: SemanticsMatcher) {
        regleCompose.waitUntil(DELAI_MS) {
            regleCompose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }

    // --- Sélecteurs ------------------------------------------------------------

    /**
     * Une ligne de l'échéancier.
     *
     * Reconnue à l'étiquette de son action de clic, « Saisir ou corriger cette dose », que
     * `FicheEnfantScreen` pose sur chaque ligne et sur elle seule. C'est le seul repère
     * stable disponible tant que les lignes n'ont pas de `testTag` : plus stable que le nom
     * du vaccin, qui vient du calendrier, et que la position, qui vient des statuts du jour.
     */
    private fun estUneLigneDEcheancier(): SemanticsMatcher =
        aPourEtiquetteDeClic(texte(R.string.fiche_action_saisir))

    /** Un nœud dont l'action de clic est annoncée par [etiquette] (le `onClickLabel` de Compose). */
    private fun aPourEtiquetteDeClic(etiquette: String): SemanticsMatcher =
        SemanticsMatcher("action de clic annoncée « $etiquette »") { noeud ->
            noeud.config.getOrNull(SemanticsActions.OnClick)?.label == etiquette
        }

    /**
     * La cellule d'un jour dans un `DatePicker` Material 3.
     *
     * Le composant ne propose aucun repère stable : selon les versions, la cellule porte le
     * numéro du jour comme texte, une description complète de la date (« mardi 1 septembre
     * 2026 »), ou les deux. On accepte donc les deux formes, en exigeant que le nombre soit
     * **isolé**, zéro de tête éventuel compris — sinon le 1er serait trouvé dans « 21 »
     * comme dans « 2026 » — et que le nœud soit cliquable, ce qui écarte l'en-tête du
     * sélecteur comme les libellés de l'écran resté derrière la fenêtre.
     *
     * C'est le sélecteur le plus fragile de ce fichier, et c'est assumé : un formulaire dont
     * la date ne s'ouvre que par un `DatePicker` Material n'offre rien d'autre. Un `testTag`
     * posé sur le champ ne changerait rien — c'est la cellule du jour qu'il faudrait
     * atteindre, et elle appartient à Material.
     */
    private fun estLaCelluleDuJour(jour: Int): SemanticsMatcher {
        val isole = Regex("(^|\\D)0?$jour(\\D|${'$'})")
        return SemanticsMatcher("cellule du jour $jour") { noeud ->
            if (noeud.config.getOrNull(SemanticsActions.OnClick) == null) return@SemanticsMatcher false
            val libelles = textesDe(noeud) +
                noeud.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            libelles.any(isole::containsMatchIn)
        }
    }

    /**
     * Les textes portés par un nœud.
     *
     * Une ligne d'échéancier est un `Modifier.clickable`, qui fusionne la sémantique de ses
     * enfants : le nœud porte donc, dans l'ordre de composition, le titre du vaccin, le
     * libellé du statut, la phrase de détail et son complément éventuel.
     */
    private fun textesDe(noeud: SemanticsNode): List<String> =
        noeud.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun texte(@StringRes cle: Int): String = contexte.getString(cle)

    private fun texte(@StringRes cle: Int, vararg arguments: Any): String =
        contexte.getString(cle, *arguments)

    private companion object {

        /** Prénom de test : absent de `strings.xml`, il ne peut pas être confondu avec un libellé. */
        const val PRENOM = "Faly"

        /** Voir la documentation de `ajouterUnEnfant` : le 1er du mois affiché par le sélecteur. */
        const val JOUR_DE_NAISSANCE = 1

        /**
         * Attente maximale d'une condition. Large à dessein : sur un émulateur d'intégration
         * continue, la première composition d'un écran peut prendre plusieurs secondes.
         */
        const val DELAI_MS = 10_000L

        /** Garde-fou du défilement de la fiche : bien au-delà des ~25 éléments de la liste. */
        const val PASSES_MAX = 40
    }
}
