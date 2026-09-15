package mg.univ.fahasalamana.ui.edition

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.ErreurDateNaissance
import mg.univ.fahasalamana.domain.ErreurPrenom
import mg.univ.fahasalamana.domain.Sexe
import java.time.LocalDate

/**
 * État de l'écran « Édition enfant » (B07, US-B1, route `EditionEnfant(enfantId: String?)`).
 *
 * Une seule route pour deux usages : création quand l'identifiant est nul, modification
 * sinon. [ModeEdition] porte cette différence de bout en bout — titre, libellé du bouton de
 * validation, présence du bouton de suppression — plutôt que de la faire deviner à l'écran
 * à partir d'un champ nul.
 *
 * Le mode est connu **dès la route**, avant toute lecture de la base : il est donc porté
 * par tous les états, y compris [Chargement], pour que le titre de la barre du haut ne
 * change pas sous les yeux de l'utilisateur pendant la lecture de la fiche.
 */
sealed interface EditionEnfantUiState {

    /** Création ou modification : connu avant la première émission. */
    val mode: ModeEdition

    /** Lecture de l'enfant à modifier. N'apparaît qu'un instant, et jamais en création. */
    data class Chargement(override val mode: ModeEdition) : EditionEnfantUiState

    /**
     * L'enfant à modifier n'est plus dans le carnet : il a été supprimé depuis un autre
     * écran, ou le lien suivi est périmé. Ce n'est pas une panne, d'où un état vide et non
     * un message d'erreur.
     */
    data object Introuvable : EditionEnfantUiState {
        /** On ne cherche un enfant existant qu'en modification. */
        override val mode: ModeEdition get() = ModeEdition.MODIFICATION
    }

    /**
     * La fiche n'a pas pu être lue.
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran, et ne
     * doit contenir aucune donnée personnelle (règle 7 de CLAUDE.md).
     */
    data object Erreur : EditionEnfantUiState {
        override val mode: ModeEdition get() = ModeEdition.MODIFICATION
    }

    /**
     * Le formulaire, prêt à être rempli ou corrigé.
     *
     * Les deux champs d'erreur sont **déjà filtrés** par le ViewModel : ils restent nuls
     * tant que l'utilisateur n'a pas touché le champ concerné. Un formulaire de création
     * s'ouvre donc vierge et non couvert de rouge, alors même que la saisie vide est
     * invalide et que [peutEnregistrer] est faux dès la première composition.
     *
     * @param prenomEnregistre prénom tel qu'il est en base, `null` en création. Sert au
     *   titre et au dialogue de suppression : contrairement à [prenom], il ne bouge pas
     *   pendant la frappe.
     * @param peutEnregistrer validation satisfaite et aucune écriture en cours : c'est la
     *   seule condition d'activation du bouton de validation.
     * @param enCours écriture en base en cours ; les champs et les boutons sont gelés le
     *   temps de l'aller-retour, pour qu'un double appui ne produise pas deux écritures.
     * @param anneeMinimum et [anneeMaximum] bornes des années proposées par le sélecteur de
     *   date. Elles cadrent la molette, elles ne valident rien : c'est `ValidationEnfant`
     *   qui refuse une date, et le sélecteur laisse volontairement atteindre une date future
     *   pour que le message de US-B1 puisse s'afficher.
     * @param echec écriture qui vient d'échouer, à annoncer une fois puis à oublier.
     * @param sortie l'écran a fini son travail et peut être quitté ; c'est l'écran qui
     *   navigue, jamais le ViewModel.
     */
    @Immutable
    data class Formulaire(
        override val mode: ModeEdition,
        val prenomEnregistre: String?,
        val prenom: String,
        val dateNaissance: LocalDate?,
        val sexe: Sexe,
        val erreurPrenom: ErreurPrenom?,
        val erreurDateNaissance: ErreurDateNaissance?,
        val peutEnregistrer: Boolean,
        val enCours: Boolean,
        val selecteurDateOuvert: Boolean,
        val confirmationSuppression: Boolean,
        val anneeMinimum: Int,
        val anneeMaximum: Int,
        val echec: EchecEdition?,
        val sortie: SortieEdition?,
    ) : EditionEnfantUiState {

        /** La suppression n'a de sens que sur un enfant déjà enregistré (US-B1, scénario 3). */
        val suppressionPossible: Boolean get() = mode == ModeEdition.MODIFICATION
    }
}

/** Les deux usages de la route `EditionEnfant` (CDC §B7.1). */
enum class ModeEdition { CREATION, MODIFICATION }

/** Écriture qui a échoué, annoncée par un message temporaire. */
enum class EchecEdition { ENREGISTREMENT, SUPPRESSION }

/**
 * Raison pour laquelle l'écran se ferme.
 *
 * Les deux cas ne mènent pas au même endroit : après un enregistrement on revient d'où l'on
 * vient, après une suppression on ne peut pas — la fiche de l'enfant supprimé est encore
 * dans la pile de retour. C'est `AppNavHost` qui tranche, pas cet écran.
 */
enum class SortieEdition { ENREGISTRE, SUPPRIME }
