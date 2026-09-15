package mg.univ.fahasalamana.ui.saisie

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.ResultatSaisie
import mg.univ.fahasalamana.domain.VaccinReference
import java.time.LocalDate

/**
 * État de l'écran Saisie d'un vaccin (CDC §B7.2, US-B3 et US-B4).
 *
 * États exclusifs plutôt qu'une `data class` à drapeaux : l'écran ne peut pas être à la fois
 * en chargement et introuvable, et le `when` de l'écran reste sans `else`.
 *
 * Aucun type Android, aucun `Flow` ici : ce fichier doit rester lisible sans SDK et
 * réutilisable tel quel par les tests UI (B21) et les `@Preview`.
 */
sealed interface SaisieVaccinUiState {

    /** Avant la première émission des flux (base, calendrier, horloge) et l'amorçage du formulaire. */
    data object Chargement : SaisieVaccinUiState

    /**
     * L'enfant n'existe plus en base, ou le vaccin ne figure pas au calendrier de référence.
     *
     * Les deux cas mènent au même écran : il n'y a plus rien à saisir, et la seule action
     * possible est de revenir en arrière. Le second cas arrive quand une mise à jour du
     * calendrier retire une dose pendant que l'écran est ouvert (§B5.2).
     */
    data object Introuvable : SaisieVaccinUiState

    /**
     * La lecture a échoué (base illisible).
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran et ne doit
     * contenir aucune donnée personnelle.
     */
    data object Erreur : SaisieVaccinUiState

    /**
     * Formulaire affichable, en création comme en correction.
     *
     * @param prenom prénom de l'enfant, pour l'en-tête : le parent doit voir **de qui** et
     *   **de quelle dose** il s'agit avant d'enregistrer quoi que ce soit.
     * @param dateNaissance borne basse de la règle R5, affichée dans le message de refus.
     * @param vaccin ligne du calendrier de référence : nom, dose, description, tolérance.
     * @param prevuLe date prévue calculée par la règle R1, `null` si la chaîne de
     *   dépendances est cassée (dose précédente retirée du calendrier).
     * @param aujourdHui jour de référence, venu de `horlogeJour` et jamais d'une horloge lue
     *   dans un composable : c'est la borne haute de R5 et la date proposée par défaut.
     * @param date date d'administration en cours de saisie.
     * @param lieu lieu facultatif, tel que tapé (jamais `null` ici : la chaîne vide est
     *   l'état naturel d'un champ de texte, le `null` est reconstitué à l'enregistrement).
     * @param lot numéro de lot facultatif, même convention.
     * @param dateSaisieExistante date de la dose déjà enregistrée pour ce couple
     *   (enfant, vaccin), ou `null` en création. C'est elle qui fait basculer l'écran en
     *   mode correction : en-tête rappelant la saisie en cours et bouton de suppression (US-B4).
     * @param validation verdict de la règle R5 sur [date], calculé par `domain.validerSaisie`.
     * @param enregistrementEnCours écriture en cours : évite un double envoi sur double clic.
     * @param echec la dernière écriture a échoué ; l'écran l'affiche sans quitter le formulaire.
     * @param termine l'écriture a réussi : l'écran peut se refermer sur la fiche.
     */
    @Immutable
    data class Pret(
        val prenom: String,
        val dateNaissance: LocalDate,
        val vaccin: VaccinReference,
        val prevuLe: LocalDate?,
        val aujourdHui: LocalDate,
        val date: LocalDate,
        val lieu: String,
        val lot: String,
        val dateSaisieExistante: LocalDate?,
        val validation: ResultatSaisie,
        val enregistrementEnCours: Boolean = false,
        val echec: Boolean = false,
        val termine: Boolean = false,
    ) : SaisieVaccinUiState {

        /** Correction d'une dose déjà enregistrée (US-B4) plutôt que première saisie (US-B3). */
        val correction: Boolean get() = dateSaisieExistante != null

        /**
         * Bouton « Enregistrer » actif.
         *
         * Seul un **refus** de R5 désactive le bouton : un avertissement de fenêtre laisse
         * l'enregistrement possible, c'est toute la règle.
         */
        val peutEnregistrer: Boolean
            get() = validation is ResultatSaisie.Acceptee && !enregistrementEnCours && !termine
    }
}
