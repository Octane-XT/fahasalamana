package mg.univ.fahasalamana.ui.export

import androidx.compose.runtime.Immutable

/**
 * État de l'entrée « Exporter le carnet » du bloc Carnet des Réglages (US-B9, scénario 1).
 *
 * Ce n'est pas un écran mais un bloc posé dans l'écran Réglages : pas de route, pas
 * d'argument de navigation, et un état volontairement minuscule.
 *
 * Deux champs sont des **événements à consommer une fois**, sur le modèle déjà utilisé par
 * l'écran Édition enfant (`sortie` / `echec`) : l'écran les traite dans un `LaunchedEffect`
 * puis prévient le ViewModel, qui les remet à `null`. Sans cela, une rotation rouvrirait le
 * sélecteur de documents ou réafficherait le message de confirmation.
 *
 * @param enCours écriture du fichier en cours : l'action est inactive pendant ce temps.
 * @param emplacementADemander nom de fichier à proposer au sélecteur de documents
 *   (`carnet-fahasalamana-AAAAMMJJ.json`), ou `null` s'il n'y a rien à demander.
 *   **Événement à consommer** : c'est le ViewModel qui décide d'ouvrir le sélecteur, mais
 *   seul un composable peut lancer le contrat `CreateDocument`.
 * @param resultat issue du dernier export, ou `null` si rien n'est à annoncer.
 *   **Événement à consommer**, effacé quand l'utilisateur ferme le message.
 */
@Immutable
data class ExportCarnetUiState(
    val enCours: Boolean = false,
    val emplacementADemander: String? = null,
    val resultat: ResultatExport? = null,
)

/**
 * Ce qui est annoncé au parent après un export.
 *
 * Une annulation dans le sélecteur de documents **n'est pas un cas ici** : l'utilisateur a
 * renoncé, il le sait, et lui afficher un message serait du bruit.
 */
sealed interface ResultatExport {

    /**
     * Le fichier est écrit. Les compteurs viennent du carnet réellement exporté, pas de
     * l'état de l'écran : c'est ce qui est dans le fichier qui est annoncé.
     */
    data class Reussi(val nbEnfants: Int, val nbDoses: Int) : ResultatExport

    /** Aucun enfant enregistré : le sélecteur n'est même pas ouvert, il n'y a rien à écrire. */
    data object CarnetVide : ResultatExport

    /**
     * L'écriture a échoué (document devenu indisponible, support retiré, espace insuffisant).
     *
     * Aucun détail technique n'est porté par l'état : le message vient de `strings_export.xml`
     * et ne doit contenir **aucune donnée personnelle** — ni prénom, ni chemin de fichier.
     */
    data object Echec : ResultatExport
}
