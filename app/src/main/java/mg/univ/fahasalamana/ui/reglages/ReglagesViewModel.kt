package mg.univ.fahasalamana.ui.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.data.repository.ReferenceRepository

/**
 * Écran Réglages (CDC §B6 : VM7 → `PreferencesRepository` + `ReferenceRepository`).
 *
 * Aucun calcul ici : on assemble des flux de lecture en un état d'affichage. La route
 * `Reglages` n'a pas d'argument, donc pas de `SavedStateHandle`.
 *
 * La seule écriture est [onDesactiverVerrouillage] (B18) ; l'activation et la modification du
 * code passent, elles, par l'écran `Verrouillage`, parce qu'elles demandent une saisie.
 *
 * TODO(B19) : ajouter `fun onVerifierMisesAJour()` qui appellera
 * `reference.mettreAJour()` dans `viewModelScope`, puis `replanifierTout()`. Le
 * repository est déjà injecté pour ça ; le bouton correspondant est affiché désactivé.
 */
class ReglagesViewModel(
    reference: ReferenceRepository,
    private val preferences: PreferencesLocales,
) : ViewModel() {

    /**
     * Bloc « Données de référence », avec son propre `catch`.
     *
     * L'erreur est **cantonnée à ce bloc** : elle ne doit pas emporter l'état du verrouillage,
     * qui vient d'une autre source et dont l'utilisateur a besoin même quand le calendrier
     * est illisible. Typé `Flow<EtatReference>` et non `Flow<Pret>` : c'est ce qui permet à
     * [catch] d'émettre [EtatReference.Erreur] sur la même chaîne.
     */
    private val etatReference: Flow<EtatReference> = combine(
        // `observerInfosSource()` n'émet rien tant que rien n'est chargé : sans cette
        // première valeur nulle, la combinaison ne produirait jamais d'état et l'écran
        // resterait en chargement pour toujours. Même parade que FicheEnfantViewModel.
        reference.observerInfosSource().onStart<InfosSource?> { emit(null) },
        preferences.annuaireVersion,
        preferences.derniereVerification,
    ) { infos, versionAnnuaire, derniereVerification ->
        EtatReference.Pret(
            DonneesReference(
                sourceCalendrier = infos?.source,
                calendrierPublieLe = infos?.publieLe,
                versionCalendrier = infos?.version,
                // VERSION_ABSENTE (0) signifie « rien de chargé », pas « version 0 ».
                versionAnnuaire = versionAnnuaire.takeIf { it != PreferencesLocales.VERSION_ABSENTE },
                derniereVerification = derniereVerification,
            ),
        )
    }.catch { erreur ->
        // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
        if (erreur is CancellationException) throw erreur
        emit(EtatReference.Erreur)
    }

    val uiState: StateFlow<ReglagesUiState> = combine(
        etatReference,
        preferences.verrouillageActif,
    ) { blocReference, verrouillageActif ->
        ReglagesUiState(reference = blocReference, verrouillageActif = verrouillageActif)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReglagesUiState(),
    )

    /**
     * Efface le code de verrouillage (B18), après confirmation demandée par l'écran.
     *
     * **Sans redemander le code**, et c'est un choix assumé : pour arriver sur cet écran il a
     * déjà fallu ouvrir le carnet, donc franchir le verrou. Redemander le code ici ne
     * protégerait rien qui ne soit déjà visible à l'écran — la liste des enfants et leurs
     * vaccins — et ajouterait une saisie de plus à qui veut simplement arrêter d'en faire.
     *
     * `effacerPin` retire l'empreinte **et** le sel, et repose `verrouillage_actif` à faux :
     * le `GardienVerrouillage` voit passer le réglage et rouvre le carnet de lui-même, sans
     * que cet écran ait à le prévenir.
     *
     * Un échec d'écriture est avalé : l'interrupteur restera simplement affiché « activé »,
     * puisqu'il reflète la préférence et non l'intention.
     */
    fun onDesactiverVerrouillage() {
        viewModelScope.launch {
            try {
                preferences.effacerPin()
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Throwable) {
                // Rien à afficher : l'état affiché reste celui de la préférence réelle.
            }
        }
    }
}
