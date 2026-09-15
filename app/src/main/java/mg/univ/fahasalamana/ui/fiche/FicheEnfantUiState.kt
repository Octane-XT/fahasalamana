package mg.univ.fahasalamana.ui.fiche

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.GroupeEcheancier
import mg.univ.fahasalamana.domain.ResumeEnfant
import mg.univ.fahasalamana.domain.VaccinAdministre
import java.time.LocalDate

/**
 * État de l'écran Fiche enfant (CDC §B7.2, US-B2).
 *
 * États exclusifs plutôt qu'une `data class` à drapeaux : l'écran ne peut pas être à la fois
 * en chargement et introuvable, et le `when` de l'écran reste sans `else`.
 *
 * Aucun type Android, aucun `Flow` ici : ce fichier doit rester lisible sans SDK et
 * réutilisable tel quel par les tests UI (B21) et les `@Preview`.
 */
sealed interface FicheEnfantUiState {

    /** Avant la première émission des flux (base, calendrier, horloge). */
    data object Chargement : FicheEnfantUiState

    /**
     * L'enfant n'existe plus en base.
     *
     * Cas réel et non théorique : la fiche peut rester ouverte pendant que l'enfant est
     * supprimé depuis « Mes enfants », ou être rouverte par une notification dont l'enfant
     * a disparu entre-temps. La fiche ne se ferme pas toute seule : elle l'explique et
     * propose de revenir en arrière.
     */
    data object Introuvable : FicheEnfantUiState

    /**
     * La lecture a échoué (base illisible).
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran et ne doit
     * contenir aucune donnée personnelle.
     */
    data object Erreur : FicheEnfantUiState

    /**
     * Échéancier calculé et affichable.
     *
     * @param aujourdHui date qui a servi au calcul des statuts, venue de `horlogeJour` et
     *   jamais d'une horloge lue dans un composable. C'est elle qui donne l'âge affiché en
     *   en-tête, et elle change à minuit sans quitter l'écran.
     * @param groupes échéancier découpé en tranches d'âge (`domain.grouperParAge`), déjà trié.
     * @param resume compteurs de la règle R6, pour les puces de l'en-tête.
     * @param nbFaits nombre de doses reçues, absent de [ResumeEnfant]. Compte aussi les
     *   [dosesHorsCalendrier] : elles ont bien été reçues.
     * @param dosesHorsCalendrier doses saisies dont le vaccin ne figure plus au calendrier de
     *   référence (`domain.dosesHorsCalendrier`). Vide dans le cas normal ; non vide après une
     *   mise à jour des références (B19) qui a retiré une dose déjà saisie. Le CDC §B5.2 exige
     *   qu'elles soient conservées **et** affichées : elles forment la dernière section de la
     *   fiche plutôt que de disparaître sans explication.
     * @param infosSource provenance du calendrier pour le bandeau de pied de fiche (US-B7).
     *   `null` tant que le contenu de référence n'a pas été chargé sur ce téléphone :
     *   l'échéancier s'affiche quand même, seule la mention de version manque.
     */
    @Immutable
    data class Pret(
        val enfant: Enfant,
        val aujourdHui: LocalDate,
        val groupes: List<GroupeEcheancier>,
        val resume: ResumeEnfant,
        val nbFaits: Int,
        val dosesHorsCalendrier: List<VaccinAdministre>,
        val infosSource: InfosSource?,
    ) : FicheEnfantUiState
}
