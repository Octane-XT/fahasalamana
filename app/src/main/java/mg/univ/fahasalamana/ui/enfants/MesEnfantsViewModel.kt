package mg.univ.fahasalamana.ui.enfants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import java.time.LocalDate

/**
 * Écran « Mes enfants » (§B6 : VM1 → `EnfantRepository` + `CalculateurEcheancier`).
 *
 * Trois sources assemblées en un seul état : les enfants et leurs doses, le calendrier de
 * référence, et le jour courant. Les trois peuvent changer sans que l'utilisateur quitte
 * l'écran — une saisie faite depuis la fiche, une mise à jour du calendrier (B19), le
 * passage de minuit — et `combine` recalcule alors la liste tout seul.
 *
 * **Aucune règle métier ici** (règle 1 de CLAUDE.md) : les statuts viennent de
 * `CalculateurEcheancier.echeancier()` (R1, R2) et les trois chiffres du résumé de
 * `CalculateurEcheancier.resume()` (R6). Ce ViewModel ne fait que brancher, trier et
 * exposer. Le tri, lui, appartient bien à l'écran : c'est l'ordre d'affichage demandé par
 * US-B8, pas une propriété des données.
 *
 * La route `MesEnfants` n'a pas d'argument : pas de `SavedStateHandle`.
 *
 * @param horlogeJour `Flow<LocalDate>` de `platform/HorlogeJour.kt`, qui émet à minuit et
 *   au retour au premier plan. Injecté plutôt que lu par `LocalDate.now()` : c'est ce qui
 *   rend l'état de l'écran reproductible et testable sans toucher à l'horloge du système.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MesEnfantsViewModel(
    enfants: EnfantRepository,
    reference: ReferenceRepository,
    private val calculateur: CalculateurEcheancier,
    horlogeJour: Flow<LocalDate>,
) : ViewModel() {

    /**
     * Compteur de relances demandées depuis l'état d'erreur (voir [onReessayer]).
     *
     * Une `Int` qui s'incrémente et non un `Unit` : c'est le **changement** de valeur qui
     * relance la lecture, et deux appuis successifs doivent produire deux relances.
     */
    private val relances = MutableStateFlow(0)

    /**
     * Typé `Flow<MesEnfantsUiState>` et non `Flow<Pret>` : c'est ce qui permet au [catch]
     * de [etat] d'émettre [MesEnfantsUiState.Erreur] sur la même chaîne. Enchaînée
     * directement, `combine { ... }.catch { ... }` ferait inférer `Flow<Pret>` au récepteur
     * de [catch], qui ne pourrait plus y émettre autre chose.
     */
    private val listeLue: Flow<MesEnfantsUiState> = combine(
        enfants.observerTous(),
        reference.observerCalendrier(),
        horlogeJour,
    ) { carnets, calendrier, aujourdHui ->
        if (carnets.isEmpty()) {
            MesEnfantsUiState.Vide
        } else {
            // Le calendrier de référence peut être vide — amorçage des assets pas encore
            // terminé, ou en échec. L'état reste [MesEnfantsUiState.Pret] : les enfants
            // existent, et la liste doit s'afficher pour qu'on puisse en ajouter ou en ouvrir
            // un. Ce que chaque carte a alors le droit d'annoncer est décidé par le résumé
            // lui-même (`ProchaineEcheance.Indeterminable`), et « À jour » n'en fait pas
            // partie.
            MesEnfantsUiState.Pret(
                carnets
                    .map { carnet ->
                        val enfant = carnet.enfant.toDomain()
                        val echeancier = calculateur.echeancier(
                            enfant = enfant,
                            calendrier = calendrier,
                            administres = carnet.administres.toDomain(),
                            aujourdHui = aujourdHui,
                        )
                        ligneEnfant(
                            enfant = enfant,
                            echeancier = echeancier,
                            resume = calculateur.resume(echeancier),
                            aujourdHui = aujourdHui,
                        )
                    }
                    .sortedWith(ORDRE_URGENCE),
            )
        }
    }

    /**
     * La lecture ci-dessus, relançable, et dont l'échec devient un état d'affichage.
     *
     * `flatMapLatest` sur [relances] et non un simple [catch] posé une fois : **le `catch`
     * termine la chaîne**. Une fois l'exception attrapée, le `Flow` Room amont est annulé et
     * ne réémettra plus rien ; sans ce `flatMapLatest`, l'écran resterait en erreur jusqu'à
     * ce que le dernier collecteur disparaisse plus de cinq secondes et que [stateIn]
     * reprenne l'amont de lui-même. Ici, l'appui sur « Réessayer » rouvre une collecte
     * neuve, ce qui est exactement ce que l'utilisateur croit demander.
     *
     * `onStart` repose [MesEnfantsUiState.Chargement] à chaque relance : sans lui, l'état
     * d'erreur resterait affiché, immobile, jusqu'à la première émission de Room, et le
     * bouton semblerait n'avoir rien fait.
     */
    private val etat: Flow<MesEnfantsUiState> = relances.flatMapLatest {
        listeLue
            .onStart { emit(MesEnfantsUiState.Chargement) }
            .catch { erreur ->
                // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
                if (erreur is CancellationException) throw erreur
                emit(MesEnfantsUiState.Erreur)
            }
    }

    val uiState: StateFlow<MesEnfantsUiState> = etat.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MesEnfantsUiState.Chargement,
    )

    /**
     * Le parent a touché « Réessayer » depuis l'état d'erreur.
     *
     * Ne relit rien lui-même : il incrémente [relances], et c'est le `flatMapLatest` de
     * [etat] qui reprend la lecture à zéro. Appuyer sans qu'il y ait eu d'erreur est sans
     * conséquence — l'écran repasse par « Chargement… » puis réaffiche la même liste.
     */
    fun onReessayer() {
        relances.update { it + 1 }
    }

    private companion object {

        /**
         * Ordre d'affichage de la liste (US-B8) : **nombre de retards décroissant**, puis
         * nombre de vaccins à faire décroissant. L'agent communautaire voit en haut les
         * enfants pour lesquels il y a quelque chose à faire aujourd'hui.
         *
         * Rien de plus dans le comparateur : `sortedWith` est stable, donc à égalité de
         * retards et de vaccins à faire les enfants gardent l'ordre alphabétique déjà
         * établi en SQL par `EnfantDao.observerTous()`. Un enfant ne change donc jamais de
         * place tout seul, ce qui compte pour une liste qu'on touche du doigt.
         */
        val ORDRE_URGENCE: Comparator<LigneEnfant> =
            compareByDescending<LigneEnfant> { it.resume.nbEnRetard }
                .thenByDescending { it.resume.nbAFaire }
    }
}
