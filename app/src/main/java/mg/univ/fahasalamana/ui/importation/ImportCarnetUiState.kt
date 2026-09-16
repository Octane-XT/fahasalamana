package mg.univ.fahasalamana.ui.importation

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.ResultatImport

/*
 * Package `ui/importation/` et non `ui/import/` : `import` est un mot-clé du langage, et un
 * paquet qui le porte doit être échappé par des accents graves à chaque déclaration comme à
 * chaque import. Le nom français levait la question, autant le prendre.
 */

/**
 * État de l'entrée « Importer un carnet » du bloc Carnet des Réglages (US-B9, scénario 2).
 *
 * Comme pour l'export (B16), ce n'est pas un écran mais un bloc posé dans l'écran Réglages :
 * pas de route, pas d'argument de navigation, un état minuscule.
 *
 * Un seul événement à consommer ici, contre deux pour l'export : l'ouverture du sélecteur de
 * documents n'a pas à passer par le ViewModel, parce qu'il n'y a **rien à vérifier avant**
 * d'ouvrir un fichier (l'export, lui, refuse d'ouvrir le sélecteur pour un carnet vide).
 * Le composable lance donc le contrat directement, et le ViewModel n'entre en jeu qu'une
 * fois le document choisi.
 *
 * @param enCours lecture du fichier et fusion en cours : l'action est inactive pendant ce temps.
 * @param issue ce qui est annoncé au parent, ou `null` si rien n'est à annoncer.
 *   **Événement à consommer**, effacé quand l'utilisateur ferme le message : sans cela, une
 *   rotation réafficherait le rapport d'un import déjà lu.
 */
@Immutable
data class ImportCarnetUiState(
    val enCours: Boolean = false,
    val issue: IssueImport? = null,
)

/**
 * Ce qui est annoncé au parent après un import.
 *
 * Une annulation dans le sélecteur de documents **n'est pas un cas ici** : l'utilisateur a
 * renoncé, il le sait, lui afficher un message serait du bruit.
 *
 * Les quatre cas d'échec sont distincts parce qu'ils n'appellent pas la même action : un
 * fichier non reconnu se remplace par un autre fichier, une version inconnue demande une
 * mise à jour de l'application, un échec de lecture se retente. Un message unique
 * « l'import a échoué » laisserait le parent sans rien à faire.
 */
sealed interface IssueImport {

    /**
     * Le carnet a été fusionné. [rapport] dit exactement ce qui a changé — c'est ce qui
     * permet au parent de vérifier que son carnet est complet (US-B9).
     */
    data class Reussi(val rapport: ResultatImport) : IssueImport

    /** Le fichier est un carnet valide, mais il ne contient aucun enfant : rien à importer. */
    data object FichierSansContenu : IssueImport

    /** Le fichier n'est pas un carnet exporté par l'application, ou il est abîmé. Rien n'a été écrit. */
    data object FichierIllisible : IssueImport

    /**
     * Le fichier vient d'un format que cette version ne sait pas lire. Rien n'a été écrit.
     *
     * @param version le `schemaVersion` trouvé dans le fichier, affiché tel quel.
     */
    data class VersionInconnue(val version: Int) : IssueImport

    /**
     * Le document n'a pas pu être lu, ou la fusion a échoué en base.
     *
     * Aucun détail technique n'est porté par l'état : le message vient de
     * `strings_export.xml` et ne doit contenir **aucune donnée personnelle** — ni prénom, ni
     * chemin de fichier (§B8).
     */
    data object Echec : IssueImport
}
