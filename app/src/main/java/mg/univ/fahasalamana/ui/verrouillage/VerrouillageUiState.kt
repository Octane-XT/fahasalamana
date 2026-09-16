package mg.univ.fahasalamana.ui.verrouillage

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.ErreurPin
import mg.univ.fahasalamana.domain.pinValide

/**
 * État de l'écran Verrouillage (CDC §B7.1, US-B10, tâche B18).
 *
 * Aucun type Android, aucun `Flow` : le fichier se lit sans SDK et sert tel quel aux
 * `@Preview` et aux tests UI (B21).
 */
sealed interface VerrouillageUiState {

    /** Le mode n'est pas encore décidé : on attend la première lecture du réglage. */
    data object Chargement : VerrouillageUiState

    /**
     * Un code est demandé.
     *
     * @param mode ce que l'écran est en train de faire ; fixé une fois pour toutes à
     *   l'ouverture (voir `VerrouillageViewModel`).
     * @param etape quel code est demandé à cet instant.
     * @param code chiffres saisis. **Seule copie du code en clair dans l'application**, et
     *   elle vit le temps de l'écran.
     * @param erreur résultat de la dernière validation, effacé dès la frappe suivante.
     * @param verificationEnCours dérivation PBKDF2 en cours : le bouton est neutralisé et
     *   l'écran montre une progression, parce que l'opération est lente **par conception**
     *   (voir `ITERATIONS_PBKDF2`).
     * @param termine le code est accepté ou enregistré : l'écran peut se refermer.
     */
    @Immutable
    data class Saisie(
        val mode: ModeVerrouillage,
        val etape: EtapeCode,
        val code: String = "",
        val erreur: ErreurSaisiePin? = null,
        val verificationEnCours: Boolean = false,
        val termine: Boolean = false,
    ) : VerrouillageUiState {

        /**
         * Bouton de validation actif.
         *
         * Même critère de format aux quatre étapes : l'application n'a jamais pu enregistrer
         * un code hors de `LONGUEUR_PIN_MIN..LONGUEUR_PIN_MAX`, donc exiger cette longueur
         * pour **redonner** un code existant n'écarte aucune saisie légitime, et cela évite
         * de lancer une dérivation PBKDF2 de 120 000 itérations sur deux chiffres.
         */
        val peutValider: Boolean
            get() = !verificationEnCours && !termine && pinValide(code)

        /**
         * `toString()` masqué : le code saisi ne doit apparaître dans **aucune** trace.
         *
         * Un `data class` en imprime tous les champs, et un `UiState` finit tôt ou tard dans
         * un message de journal, un rapport de plantage ou l'inspecteur de recomposition.
         * Cette surcharge est le seul endroit où cela se règle une fois pour toutes.
         */
        override fun toString(): String =
            "Saisie(mode=$mode, etape=$etape, code=${"•".repeat(code.length)}, " +
                "erreur=$erreur, verificationEnCours=$verificationEnCours, termine=$termine)"
    }
}

/**
 * Ce que l'écran est venu faire.
 *
 * Déduit de l'état du verrou à l'ouverture plutôt que porté par la route : `Verrouillage`
 * est un `object` sans argument (§B7.1), et il n'y a pas lieu de modifier un fichier de
 * navigation partagé pour une information que l'application connaît déjà.
 */
enum class ModeVerrouillage {

    /** Le carnet est verrouillé : on redemande le code pour l'ouvrir. */
    Ouverture,

    /** Aucun code n'existe : on en choisit un, puis on le confirme. */
    Creation,

    /** Un code existe et le carnet est ouvert : on redonne l'ancien, puis on en choisit un nouveau. */
    Modification,
}

/** Quel code est demandé à cet instant. */
enum class EtapeCode {

    /** Le code enregistré, pour ouvrir le carnet. */
    CodeActuel,

    /** Le code enregistré, pour avoir le droit d'en changer. */
    AncienCode,

    /** Le code que l'on est en train de choisir. */
    NouveauCode,

    /** Le même, une seconde fois, pour écarter la faute de frappe. */
    Confirmation,
}

/** Ce que l'écran a à reprocher à la dernière saisie. Des types, pas des phrases. */
sealed interface ErreurSaisiePin {

    /** Le code ne correspond pas à celui qui est enregistré. Message neutre (US-B10, scénario « code faux »). */
    data object CodeIncorrect : ErreurSaisiePin

    /** Le format ne convient pas (longueur, caractères). */
    data class Format(val cause: ErreurPin) : ErreurSaisiePin

    /** La confirmation diffère du code choisi. */
    data object ConfirmationDifferente : ErreurSaisiePin

    /** L'écriture dans DataStore a échoué ; la saisie reste à l'écran et peut être retentée. */
    data object EchecEnregistrement : ErreurSaisiePin
}
