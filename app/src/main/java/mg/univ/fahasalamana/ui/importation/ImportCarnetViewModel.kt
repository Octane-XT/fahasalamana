package mg.univ.fahasalamana.ui.importation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.domain.LectureCarnet
import mg.univ.fahasalamana.domain.analyserCarnet
import mg.univ.fahasalamana.platform.ETIQUETTE_LOG_RAPPEL
import mg.univ.fahasalamana.platform.LecteurDocument
import mg.univ.fahasalamana.platform.PlanificateurRappels

/**
 * Import d'un carnet venu d'un autre téléphone (US-B9, scénario 2).
 *
 * **Aucune règle ici.** La validation du fichier et la fusion sont dans
 * `domain/ImportCarnet.kt`, la lecture du document dans `platform/ImportCarnetSaf.kt`,
 * l'écriture en base dans `EnfantRepository.importer()`, la programmation des rappels dans
 * `PlanificateurRappels`. Ce ViewModel enchaîne les quatre et tient l'état du bouton.
 *
 * **Le déroulé**, plus court que celui de l'export : le parent touche « Importer un
 * carnet », l'écran ouvre le sélecteur de documents (seul un composable peut lancer un
 * contrat `ActivityResult`), et l'`Uri` choisie revient à [onDocumentChoisi], qui fait tout
 * le reste. Il n'y a rien à vérifier avant d'ouvrir le sélecteur, donc pas d'aller-retour
 * préalable avec le ViewModel.
 *
 * **L'ordre des quatre étapes est la garantie « rien n'est écrit si le fichier est mauvais »** :
 * lire le texte, l'analyser, et seulement ensuite fusionner. Un fichier illisible ou d'un
 * format inconnu ressort avant d'avoir touché la base.
 *
 * **Un ViewModel à part et non `ReglagesViewModel`** : même raison qu'en B16 — l'écran
 * Réglages appartient à Dev B et B18 y travaille en parallèle. Le bloc est autonome et se
 * pose en une ligne dans la carte « Carnet ».
 */
class ImportCarnetViewModel(
    private val enfants: EnfantRepository,
    private val lecteur: LecteurDocument,
    private val planificateur: PlanificateurRappels,
) : ViewModel() {

    private val etat = MutableStateFlow(ImportCarnetUiState())
    val uiState: StateFlow<ImportCarnetUiState> = etat.asStateFlow()

    /**
     * Le parent a choisi un fichier : il est lu, analysé, puis fusionné.
     *
     * L'`Uri` n'est ni conservée ni mémorisée : elle vaut pour cette lecture seulement
     * (§B8, point 2). Une annulation du sélecteur n'arrive jamais ici — l'écran ne rappelle
     * ce ViewModel que si un document a été choisi.
     *
     * Le carnet sans aucun enfant est annoncé à part plutôt que comme une réussite à zéro :
     * le parent a sans doute désigné le mauvais fichier, et « 0 enfant ajouté » le
     * laisserait croire que l'import a marché.
     */
    fun onDocumentChoisi(source: Uri) {
        if (etat.value.enCours) return
        viewModelScope.launch {
            etat.update { it.copy(enCours = true, issue = null) }

            val issue = try {
                when (val lecture = analyserCarnet(lecteur.lire(source))) {
                    LectureCarnet.Illisible -> IssueImport.FichierIllisible

                    is LectureCarnet.VersionInconnue -> IssueImport.VersionInconnue(lecture.version)

                    is LectureCarnet.Lu -> if (lecture.carnet.enfants.isEmpty()) {
                        IssueImport.FichierSansContenu
                    } else {
                        val rapport = enfants.importer(lecture.carnet)
                        replanifierApresImport()
                        IssueImport.Reussi(rapport)
                    }
                }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                // Rien de l'erreur n'est repris dans l'état : un chemin de document ou un
                // message système n'a pas à traverser l'interface d'une application de santé.
                IssueImport.Echec
            }

            etat.update { it.copy(enCours = false, issue = issue) }
        }
    }

    /** Le rapport d'import a été fermé par l'utilisateur. */
    fun onIssueFermee() {
        etat.update { it.copy(issue = null) }
    }

    /**
     * Recalcule les rappels de tout le carnet après une fusion (§B8 : l'import est l'un des
     * déclencheurs de `replanifier`).
     *
     * `replanifierTout()` et non `replanifier(enfantId)` par enfant importé : les doses
     * ajoutées à un enfant **déjà présent** changent son échéancier tout autant, et le
     * calendrier n'est lu qu'une fois pour l'ensemble.
     *
     * Appelée même quand le rapport est vide de tout ajout : l'opération est idempotente
     * (R4), et c'est l'occasion de remettre d'aplomb des rappels qu'un redémarrage aurait
     * perdus. Elle n'ajoute pas de doublon.
     *
     * **Son échec ne remet pas en cause l'import**, qui est déjà écrit et validé : annoncer
     * un échec ferait croire au parent que ses enfants n'ont pas été importés, alors qu'ils
     * le sont. Seuls les rappels manqueraient — et la prochaine saisie, la prochaine
     * modification ou le prochain import les reprogrammera.
     */
    private suspend fun replanifierApresImport() {
        try {
            planificateur.replanifierTout()
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (erreur: Exception) {
            Log.w(ETIQUETTE_LOG_RAPPEL, "Replanification après import impossible", erreur)
        }
    }
}
