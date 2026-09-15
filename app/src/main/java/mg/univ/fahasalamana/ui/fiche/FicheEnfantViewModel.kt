package mg.univ.fahasalamana.ui.fiche

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.dosesHorsCalendrier
import mg.univ.fahasalamana.domain.grouperParAge
import mg.univ.fahasalamana.domain.nbFaits
import mg.univ.fahasalamana.ui.navigation.FicheEnfant
import java.time.LocalDate

/**
 * Fiche enfant (CDC §B6 : VM3 → `EnfantRepository` + `ReferenceRepository` + `CalculateurEcheancier`).
 *
 * Ce ViewModel **ne calcule rien** : il assemble quatre flux de lecture et délègue tout le
 * métier à `domain` (statuts par [CalculateurEcheancier], découpage en tranches d'âge par
 * `grouperParAge`). C'est ce qui rend l'échéancier testable en JVM sans émulateur (B05, 37 tests).
 *
 * **Recomposition instantanée** (DoD de B08) : les trois sources sont des `Flow` Room ou
 * DataStore. Une saisie faite depuis l'écran `SaisieVaccin` réémet la relation `EnfantAvecVaccins`,
 * `combine` relance le calcul et la pastille est déjà verte au retour sur la fiche — sans
 * `refresh()`, sans `LaunchedEffect`, sans rien à rafraîchir à la main.
 *
 * L'horloge est injectée, jamais lue ici : `LocalDate.now()` dans ce fichier rendrait les
 * statuts dépendants de l'instant de composition, et la fiche resterait figée à la veille
 * après minuit (voir `platform/HorlogeJour.kt`).
 *
 * La navigation vers `SaisieVaccin` n'est pas de son ressort : l'écran reçoit des lambdas.
 */
class FicheEnfantViewModel(
    savedStateHandle: SavedStateHandle,
    enfants: EnfantRepository,
    reference: ReferenceRepository,
    private val calc: CalculateurEcheancier,
    horlogeJour: Flow<LocalDate>,
) : ViewModel() {

    /** Argument de navigation lu par la route typée, jamais parsé dans l'écran (CLAUDE.md, règle 4). */
    private val route = savedStateHandle.toRoute<FicheEnfant>()

    /**
     * Provenance du calendrier, rendue facultative **avant** le `combine`.
     *
     * `ReferenceRepository.observerInfosSource()` filtre les valeurs incomplètes et n'émet donc
     * rien tant que le contenu embarqué n'a pas été chargé. Sans ce `null` initial, un
     * `combine` à quatre sources n'émettrait jamais et la fiche resterait bloquée sur
     * « Chargement… » : le bandeau de source est un complément, il ne doit pas retenir
     * l'échéancier en otage.
     */
    private val infosSource: Flow<InfosSource?> =
        reference.observerInfosSource().onStart<InfosSource?> { emit(null) }

    /**
     * Typé `Flow<FicheEnfantUiState>` et non `Flow<Pret>` : c'est ce qui permet à [catch]
     * d'émettre [FicheEnfantUiState.Erreur] sur la même chaîne.
     */
    private val etat: Flow<FicheEnfantUiState> = combine(
        enfants.observer(route.enfantId),
        reference.observerCalendrier(),
        horlogeJour,
        infosSource,
    ) { enfantAvecVaccins, calendrier, aujourdHui, infos ->
        if (enfantAvecVaccins == null) {
            FicheEnfantUiState.Introuvable
        } else {
            val enfant = enfantAvecVaccins.enfant.toDomain()
            val administres = enfantAvecVaccins.administres.toDomain()
            val lignes = calc.echeancier(
                enfant = enfant,
                calendrier = calendrier,
                administres = administres,
                aujourdHui = aujourdHui,
            )
            // Les doses dont le vaccin a quitté le calendrier ne sont dans aucune ligne :
            // l'échéancier suit le calendrier, elles suivent les saisies (CDC §B5.2).
            val horsCalendrier = dosesHorsCalendrier(enfant, administres, calendrier)
            FicheEnfantUiState.Pret(
                enfant = enfant,
                aujourdHui = aujourdHui,
                groupes = grouperParAge(lignes),
                resume = calc.resume(lignes),
                nbFaits = nbFaits(lignes, horsCalendrier),
                dosesHorsCalendrier = horsCalendrier,
                infosSource = infos,
            )
        }
    }

    val uiState: StateFlow<FicheEnfantUiState> = etat
        .catch { erreur ->
            // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
            if (erreur is CancellationException) throw erreur
            emit(FicheEnfantUiState.Erreur)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FicheEnfantUiState.Chargement,
        )
}
