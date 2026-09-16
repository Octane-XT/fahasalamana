package mg.univ.fahasalamana.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.domain.ecrireCarnet
import mg.univ.fahasalamana.domain.nbDoses
import mg.univ.fahasalamana.domain.nbEnfants
import mg.univ.fahasalamana.domain.nomFichierCarnet
import mg.univ.fahasalamana.platform.EcrivainDocument
import java.time.LocalDate

/**
 * Export du carnet en fichier (US-B9, scénario 1 ; §B6 : `EXP → SAF` et `EXP → REPOE`).
 *
 * **Aucune règle ici.** Le format du fichier est dans `domain/ExportImportCarnet.kt`, la
 * lecture de la base dans `EnfantRepository.exporter()`, l'écriture du document dans
 * `platform/ExportCarnetSaf.kt`. Ce ViewModel enchaîne les trois et tient l'état du bouton.
 *
 * **Le déroulé en trois temps**, imposé par le Storage Access Framework :
 * 1. le parent touche « Exporter le carnet » → [onExportDemande] décide s'il y a quelque
 *    chose à exporter et, si oui, demande l'ouverture du sélecteur de documents ;
 * 2. l'écran ouvre le sélecteur (seul un composable peut lancer un contrat
 *    `ActivityResult`) et l'utilisateur choisit un emplacement ;
 * 3. l'`Uri` revient à [onEmplacementChoisi], qui lit le carnet et écrit le fichier.
 *
 * Le jour courant est **capturé à l'étape 1** et réutilisé à l'étape 3 : le nom du fichier
 * et la date inscrite à l'intérieur doivent désigner le même jour, même si le parent laisse
 * le sélecteur ouvert au passage de minuit.
 *
 * **Un ViewModel à part et non `ReglagesViewModel`** : le §B6 rattache `ExportImportCarnet`
 * à VM7, mais l'écran Réglages appartient à Dev B et B18 (verrouillage par code) y travaille
 * en même temps. Un bloc autonome, injecté par Koin et posé en une ligne dans le bloc
 * « Carnet », évite de toucher au fichier d'un autre développeur ; il garde aussi l'état de
 * l'export séparé de la lecture des informations de référence, qui n'ont rien à voir.
 * **Écart au §B6 à confirmer en relecture.**
 */
class ExportCarnetViewModel(
    private val enfants: EnfantRepository,
    private val ecrivain: EcrivainDocument,
    private val horlogeJour: Flow<LocalDate>,
) : ViewModel() {

    private val etat = MutableStateFlow(ExportCarnetUiState())
    val uiState: StateFlow<ExportCarnetUiState> = etat.asStateFlow()

    /**
     * Jour retenu au moment où le parent a demandé l'export.
     *
     * Une propriété privée plutôt qu'un champ d'état : elle ne s'affiche pas, et la mettre
     * dans l'état exposerait une valeur que l'écran n'a pas à connaître. Écrite et lue
     * depuis le fil principal (les deux méthodes publiques sont appelées par l'interface).
     */
    private var jourDeLaDemande: LocalDate? = null

    /**
     * Étape 1 : le parent touche « Exporter le carnet ».
     *
     * Le carnet est lu **avant** d'ouvrir le sélecteur : proposer un emplacement, puis
     * écrire un fichier sans aucun enfant, serait une fausse réussite. Un carnet vide
     * s'annonce tout de suite, sans sortir de l'écran.
     */
    fun onExportDemande() {
        if (etat.value.enCours || etat.value.emplacementADemander != null) return
        viewModelScope.launch {
            val jour = horlogeJour.first()
            val carnetVide = try {
                enfants.observerTous().first().isEmpty()
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                // Base illisible : même message d'échec que pour une écriture ratée.
                etat.update { it.copy(resultat = ResultatExport.Echec) }
                return@launch
            }

            if (carnetVide) {
                etat.update { it.copy(resultat = ResultatExport.CarnetVide) }
                return@launch
            }

            jourDeLaDemande = jour
            etat.update {
                it.copy(emplacementADemander = nomFichierCarnet(jour), resultat = null)
            }
        }
    }

    /** L'écran a ouvert le sélecteur de documents : l'événement est consommé. */
    fun onEmplacementDemande() {
        etat.update { it.copy(emplacementADemander = null) }
    }

    /**
     * Étape 3 : le parent a choisi un emplacement, le fichier est écrit.
     *
     * L'`Uri` n'est ni conservée ni mémorisée : elle vaut pour cette écriture seulement
     * (§B8, point 2). Une annulation du sélecteur n'arrive jamais ici — l'écran ne rappelle
     * ce ViewModel que si une destination a été choisie.
     */
    fun onEmplacementChoisi(destination: Uri) {
        if (etat.value.enCours) return
        viewModelScope.launch {
            etat.update { it.copy(enCours = true, resultat = null) }
            val resultat = try {
                val jour = jourDeLaDemande ?: horlogeJour.first()
                val carnet = enfants.exporter(jour)
                ecrivain.ecrire(destination, ecrireCarnet(carnet))
                ResultatExport.Reussi(nbEnfants = carnet.nbEnfants, nbDoses = carnet.nbDoses)
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                // Rien de l'erreur n'est repris dans l'état : un chemin de document ou un
                // message système n'a pas à traverser l'interface d'une application de santé.
                ResultatExport.Echec
            }
            jourDeLaDemande = null
            etat.update { it.copy(enCours = false, resultat = resultat) }
        }
    }

    /** Le message de confirmation ou d'échec a été fermé par l'utilisateur. */
    fun onResultatFerme() {
        etat.update { it.copy(resultat = null) }
    }
}
