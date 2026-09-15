package mg.univ.fahasalamana.ui.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.repository.ReferenceRepository

/**
 * Écran Réglages (CDC §B6 : VM7 → `PreferencesRepository` + `ReferenceRepository`).
 *
 * Aucun calcul ici : on assemble trois flux de lecture en un état d'affichage. La route
 * `Reglages` n'a pas d'argument, donc pas de `SavedStateHandle`.
 *
 * TODO(B19) : ajouter `fun onVerifierMisesAJour()` qui appellera
 * `reference.mettreAJour()` dans `viewModelScope`, puis `replanifierTout()`. Le
 * repository est déjà injecté pour ça ; le bouton correspondant est affiché désactivé.
 */
class ReglagesViewModel(
    reference: ReferenceRepository,
    preferences: PreferencesLocales,
) : ViewModel() {

    /**
     * Typé `Flow<ReglagesUiState>` et non `Flow<Pret>` : c'est ce qui permet à [catch]
     * d'émettre [ReglagesUiState.Erreur] sur la même chaîne.
     */
    private val etat: Flow<ReglagesUiState> = combine(
        reference.observerInfosSource(),
        preferences.annuaireVersion,
        preferences.derniereVerification,
    ) { infos, versionAnnuaire, derniereVerification ->
        ReglagesUiState.Pret(
            DonneesReference(
                sourceCalendrier = infos.source,
                calendrierPublieLe = infos.publieLe,
                versionCalendrier = infos.version,
                // VERSION_ABSENTE (0) signifie « rien de chargé », pas « version 0 ».
                versionAnnuaire = versionAnnuaire.takeIf { it != PreferencesLocales.VERSION_ABSENTE },
                derniereVerification = derniereVerification,
            ),
        )
    }

    val uiState: StateFlow<ReglagesUiState> = etat
        .catch { erreur ->
            // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
            if (erreur is CancellationException) throw erreur
            emit(ReglagesUiState.Erreur)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReglagesUiState.Chargement,
        )
}
