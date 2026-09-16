package mg.univ.fahasalamana.ui.detailcentre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mg.univ.fahasalamana.data.repository.CentreRepository
import mg.univ.fahasalamana.ui.navigation.DetailCentre

/**
 * Fiche d'un centre (tâche B14, §B6 : VM6 → `CentreRepository`, US-B6 scénarios 2 et 3).
 *
 * Le ViewModel le plus simple du projet, et c'est normal : tout ce que cet écran fait de
 * remarquable — ouvrir le composeur, ouvrir une carte, copier des coordonnées — est du
 * ressort de la plateforme, pas de l'état. Ces actions ne passent donc pas par ici : elles
 * vivent dans `platform/ActionsCentre.kt`, appelées par l'écran. Mettre un `Context` dans
 * un ViewModel pour lancer un `Intent` serait la fuite classique, et rendrait cet état
 * impossible à tester.
 *
 * Ce qui reste est une lecture observée : `observerCentre(id)` émet `null` quand le centre
 * a disparu de l'annuaire (mise à jour B19 pendant que l'écran est ouvert), ce qui devient
 * [DetailCentreUiState.Introuvable] **sans exception et sans écran vide**.
 *
 * L'identifiant est lu par `SavedStateHandle.toRoute<DetailCentre>()`, jamais parsé dans
 * l'écran (CLAUDE.md, règle 4).
 */
class DetailCentreViewModel(
    savedStateHandle: SavedStateHandle,
    annuaire: CentreRepository,
) : ViewModel() {

    /** Argument de navigation, lu par la route typée. */
    private val route = savedStateHandle.toRoute<DetailCentre>()

    /**
     * Typé `Flow<DetailCentreUiState>` et non `Flow<Pret>` : c'est ce qui permet à [catch]
     * d'émettre [DetailCentreUiState.Erreur] sur la même chaîne.
     */
    private val etat: Flow<DetailCentreUiState> = annuaire.observerCentre(route.centreId)
        .map { centre ->
            if (centre == null) {
                DetailCentreUiState.Introuvable
            } else {
                DetailCentreUiState.Pret(centre.enFiche())
            }
        }

    val uiState: StateFlow<DetailCentreUiState> = etat
        .catch { erreur ->
            // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
            if (erreur is CancellationException) throw erreur
            emit(DetailCentreUiState.Erreur)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailCentreUiState.Chargement,
        )
}
