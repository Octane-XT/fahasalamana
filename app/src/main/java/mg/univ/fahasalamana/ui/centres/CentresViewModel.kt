package mg.univ.fahasalamana.ui.centres

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import mg.univ.fahasalamana.data.repository.CentreRepository

/**
 * Écran « Centres de santé » (tâche B13, §B6 : VM5 → `CentreRepository`, US-B6 scénario 1).
 *
 * **Une cascade, pas un chargement.** L'annuaire pèse 23 régions, 76 districts et 402
 * centres ; l'écran n'en lit jamais plus que ce qu'il montre. Le premier `Flow` remonte les
 * 23 régions ; choisir une région ouvre un `Flow` sur ses districts **seuls** ; choisir un
 * district ouvre un `Flow` sur ses centres **seuls** — au plus huit lignes. C'est
 * exactement le découpage en quatre lectures de `CentreRepository`, et la raison pour
 * laquelle il est découpé ainsi.
 *
 * `flatMapLatest` est ce qui tient cette promesse : à chaque nouveau choix, la collecte
 * précédente est **annulée** avant qu'une autre ne démarre. On ne garde jamais deux
 * requêtes ouvertes, même si l'on fait défiler le menu des régions de haut en bas.
 *
 * **Une seule collecte, quoi qu'il arrive.** Tout se termine dans un `stateIn` unique :
 * quel que soit le nombre de recompositions, chaque `Flow` du repository n'a qu'un seul
 * collecteur, partagé. C'est aussi pour cela que les régions ne sont pas relues à chaque
 * changement de district.
 *
 * **Aucune règle métier ici** (règle 1 de CLAUDE.md) : le tri est fait en SQL, l'écran ne
 * fait que choisir, filtrer et traduire. Et aucune navigation : l'identifiant du centre
 * touché remonte à `AppNavHost` par une lambda de l'écran, pas d'ici.
 *
 * La route `Centres` n'a pas d'argument ; le `SavedStateHandle` ne sert donc pas à lire une
 * route mais à **retenir les deux choix** — voir [regionChoisieId].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CentresViewModel(
    private val savedStateHandle: SavedStateHandle,
    annuaire: CentreRepository,
) : ViewModel() {

    /**
     * La région choisie, gardée dans le `SavedStateHandle` et non dans un simple
     * `MutableStateFlow`.
     *
     * Un `MutableStateFlow` suffirait à survivre à la rotation, puisque le ViewModel y
     * survit. Le `SavedStateHandle` va plus loin : il survit aussi à la **mort du
     * processus** — l'application renvoyée en arrière-plan puis tuée par le système. Le
     * parent qui revient sur l'onglet retrouve son district au lieu de deux menus vides.
     * Deux chaînes de caractères, c'est très en dessous de la limite du `Bundle`.
     */
    private val regionChoisieId: StateFlow<String?> =
        savedStateHandle.getStateFlow<String?>(CLE_REGION, null)

    /** Le district choisi, même mécanisme. Toujours remis à zéro avec la région. */
    private val districtChoisiId: StateFlow<String?> =
        savedStateHandle.getStateFlow<String?>(CLE_DISTRICT, null)

    /**
     * Les districts de la région choisie, et eux seuls.
     *
     * `onStart { emit(emptyList()) }` vide le menu **avant** que la nouvelle requête ne
     * réponde : sans lui, le second menu continuerait d'afficher les districts de la région
     * précédente pendant le temps de la requête, alors que son libellé annonce déjà la
     * nouvelle région.
     */
    private val districts: Flow<List<OptionAnnuaire>> = regionChoisieId
        .flatMapLatest { regionId ->
            if (regionId == null) {
                flowOf(emptyList<OptionAnnuaire>())
            } else {
                annuaire.observerDistricts(regionId)
                    .map { liste -> liste.map { it.enOption() } }
                    .onStart { emit(emptyList()) }
            }
        }

    /**
     * Les centres du district choisi.
     *
     * Le `Flow` produit directement l'état d'affichage : « liste vide » et « pas encore lu »
     * arrivent tous deux sous la forme d'une liste vide de Room, et seul cet endroit sait
     * les distinguer. Sans le [ResultatCentres.Chargement] de `onStart`, « Aucun centre dans
     * ce district » clignoterait à chaque changement de district.
     */
    private val resultat: Flow<ResultatCentres> = districtChoisiId
        .flatMapLatest { districtId ->
            if (districtId == null) {
                flowOf(ResultatCentres.SansDistrict)
            } else {
                annuaire.observerCentres(districtId)
                    .map { liste ->
                        if (liste.isEmpty()) {
                            ResultatCentres.Aucun
                        } else {
                            ResultatCentres.Liste(liste.map { it.enLigne() })
                        }
                    }
                    .onStart { emit(ResultatCentres.Chargement) }
            }
        }

    /**
     * Typé `Flow<CentresUiState>` et non `Flow<Pret>` : c'est ce qui permet à [catch]
     * d'émettre [CentresUiState.Erreur] sur la même chaîne.
     *
     * Les deux identifiants choisis sont recombinés avec les listes plutôt que gardés à
     * part : c'est ce qui permet de vérifier, à chaque émission, que le choix mémorisé
     * existe encore dans l'annuaire. Une mise à jour (B19) qui supprime une région pendant
     * que l'écran est ouvert ramène donc l'écran à son invitation, sans exception.
     */
    private val etat: Flow<CentresUiState> = combine(
        annuaire.observerRegions(),
        districts,
        resultat,
        regionChoisieId,
        districtChoisiId,
    ) { regions, districtsDeLaRegion, resultatCentres, regionId, districtId ->
        if (regions.isEmpty()) {
            CentresUiState.AnnuaireAbsent
        } else {
            val options = regions.map { it.enOption() }
            val regionChoisie = regionId?.let { id -> options.firstOrNull { it.id == id } }

            // Les districts affichés dépendent d'une région retenue : si elle vient de
            // disparaître, la liste précédente n'a plus rien à faire dans le menu.
            val districtsAffiches =
                if (regionChoisie == null) emptyList<OptionAnnuaire>() else districtsDeLaRegion
            val districtChoisi = districtId?.let { id -> districtsAffiches.firstOrNull { it.id == id } }

            CentresUiState.Pret(
                regions = options,
                regionChoisie = regionChoisie,
                districts = districtsAffiches,
                districtChoisi = districtChoisi,
                // L'ordre des tests est celui de la cascade : pas de région, donc pas de
                // district, donc pas de liste. Il évite d'afficher « aucun centre » pendant
                // l'instant où le district vient d'être remis à zéro.
                resultat = when {
                    regionChoisie == null -> ResultatCentres.SansRegion
                    districtChoisi == null -> ResultatCentres.SansDistrict
                    else -> resultatCentres
                },
            )
        }
    }

    val uiState: StateFlow<CentresUiState> = etat
        .catch { erreur ->
            // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
            if (erreur is CancellationException) throw erreur
            emit(CentresUiState.Erreur)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CentresUiState.Chargement,
        )

    /**
     * Choix d'une région : le district retenu est effacé dans le même geste.
     *
     * Le garder n'aurait aucun sens — un district appartient à une seule région, et
     * `observerCentres` renverrait les centres d'un district d'ailleurs. Le test d'égalité
     * en tête évite de réinitialiser le district quand on rouvre le menu pour reposer le
     * doigt sur la région déjà choisie.
     */
    fun onRegionChoisie(regionId: String) {
        if (savedStateHandle.get<String>(CLE_REGION) == regionId) return
        savedStateHandle[CLE_REGION] = regionId
        savedStateHandle.set<String>(CLE_DISTRICT, null)
    }

    /** Choix d'un district : seule action qui déclenche la lecture des centres. */
    fun onDistrictChoisi(districtId: String) {
        savedStateHandle[CLE_DISTRICT] = districtId
    }

    private companion object {

        /**
         * Clés du `SavedStateHandle`. Préfixées par l'écran : le `SavedStateHandle` d'une
         * destination porte aussi les arguments de la route, et un nom nu comme `regionId`
         * finirait par en heurter un.
         */
        const val CLE_REGION = "centres.regionId"
        const val CLE_DISTRICT = "centres.districtId"
    }
}
