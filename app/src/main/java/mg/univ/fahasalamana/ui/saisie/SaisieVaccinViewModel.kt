package mg.univ.fahasalamana.ui.saisie

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.local.nouvelIdentifiant
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.validerSaisie
import mg.univ.fahasalamana.platform.PlanificateurRappels
import mg.univ.fahasalamana.ui.navigation.SaisieVaccin
import java.time.LocalDate

/**
 * Saisie, correction et suppression d'une administration (CDC §B6 : VM4 → `EnfantRepository`
 * + `ReferenceRepository`, US-B3 et US-B4).
 *
 * Ce ViewModel **ne décide rien** : la règle R5 est une fonction pure de `domain`
 * (`validerSaisie`), la date prévue vient de `CalculateurEcheancier` (règle R1), et l'unicité
 * (enfant, vaccin) est tenue par la base. Ce qui reste ici est de l'assemblage.
 *
 * **Un seul écran pour deux user stories.** La route porte deux arguments, et la première
 * chose faite est de regarder si une administration existe déjà pour ce couple : si oui
 * l'écran s'ouvre sur cette saisie (date, lieu, lot) et propose de la supprimer (US-B4) ;
 * sinon il propose la date du jour (US-B3). L'enregistrement est donc un `upsert` : corriger
 * une dose ne crée jamais un doublon.
 *
 * **Recomposition instantanée** (US-B2, dernier scénario) : l'écriture passe par le `Flow`
 * Room du repository, donc la fiche enfant restée en pile derrière affiche la pastille verte
 * sans rafraîchissement au retour.
 *
 * L'horloge est injectée, jamais lue ici : `LocalDate.now()` dans ce fichier ferait dépendre
 * la borne haute de R5 de l'instant de composition (voir `platform/HorlogeJour.kt`).
 */
class SaisieVaccinViewModel(
    savedStateHandle: SavedStateHandle,
    private val enfants: EnfantRepository,
    reference: ReferenceRepository,
    private val calc: CalculateurEcheancier,
    private val horlogeJour: Flow<LocalDate>,
    private val planificateur: PlanificateurRappels,
) : ViewModel() {

    /** Les deux arguments de navigation, lus par la route typée (CLAUDE.md, règle 4). */
    private val route = savedStateHandle.toRoute<SaisieVaccin>()

    /**
     * Ce que l'utilisateur a saisi, plus l'avancement de l'écriture.
     *
     * Un seul état mutable pour tout ce qui ne vient pas de la base : le `combine` en dessous
     * reste une transformation sans effet de bord, et l'ensemble survit à la rotation puisque
     * le ViewModel survit.
     *
     * `null` tant que le formulaire n'a pas été amorcé — l'écran affiche « Chargement… ».
     */
    private val edition = MutableStateFlow<Edition?>(null)

    /**
     * Amorçage du formulaire : une lecture, une seule fois.
     *
     * Volontairement hors du `combine` : y initialiser l'état d'édition en écrirait la valeur
     * à chaque réémission de la base et écraserait la saisie en cours dès qu'un `Flow`
     * remonterait (une autre écriture, le passage de minuit).
     *
     * Une base illisible n'est pas traitée ici : le formulaire s'amorce sur ses valeurs par
     * défaut, et la même erreur est rattrapée par le `catch` de la chaîne d'affichage, qui
     * montre [SaisieVaccinUiState.Erreur].
     */
    init {
        viewModelScope.launch {
            val aujourdHui = horlogeJour.first()
            val existante = administrationExistante()
            edition.value = Edition(
                date = existante?.date ?: aujourdHui,
                lieu = existante?.lieu.orEmpty(),
                lot = existante?.lot.orEmpty(),
            )
        }
    }

    /**
     * Typé `Flow<SaisieVaccinUiState>` et non `Flow<Pret>` : c'est ce qui permet à [catch]
     * d'émettre [SaisieVaccinUiState.Erreur] sur la même chaîne.
     */
    private val etat: Flow<SaisieVaccinUiState> = combine(
        enfants.observer(route.enfantId),
        reference.observerCalendrier(),
        horlogeJour,
        edition,
    ) { enfantAvecVaccins, calendrier, aujourdHui, editionCourante ->
        val vaccin = calendrier.firstOrNull { it.id == route.vaccinId }

        when {
            editionCourante == null -> SaisieVaccinUiState.Chargement
            enfantAvecVaccins == null || vaccin == null -> SaisieVaccinUiState.Introuvable

            else -> {
                val enfant = enfantAvecVaccins.enfant.toDomain()
                val administres = enfantAvecVaccins.administres.toDomain()

                // Date prévue par la règle R1 : pour une dose enchaînée, elle se compte depuis
                // la date réelle de la dose précédente. C'est cette date que l'en-tête affiche
                // et que l'avertissement de fenêtre prend pour référence.
                val prevuLe = calc
                    .echeancier(enfant, calendrier, administres, aujourdHui)
                    .firstOrNull { it.vaccin.id == vaccin.id }
                    ?.prevuLe

                SaisieVaccinUiState.Pret(
                    prenom = enfant.prenom,
                    dateNaissance = enfant.dateNaissance,
                    vaccin = vaccin,
                    prevuLe = prevuLe,
                    aujourdHui = aujourdHui,
                    date = editionCourante.date,
                    lieu = editionCourante.lieu,
                    lot = editionCourante.lot,
                    dateSaisieExistante = administres
                        .firstOrNull { it.vaccinId == vaccin.id }
                        ?.date,
                    validation = validerSaisie(
                        dateSaisie = editionCourante.date,
                        dateNaissance = enfant.dateNaissance,
                        aujourdHui = aujourdHui,
                        prevuLe = prevuLe,
                        toleranceJours = vaccin.toleranceJours,
                    ),
                    enregistrementEnCours = editionCourante.enCours,
                    echec = editionCourante.echec,
                    termine = editionCourante.termine,
                )
            }
        }
    }

    val uiState: StateFlow<SaisieVaccinUiState> = etat
        .catch { erreur ->
            // L'annulation du scope n'est pas une erreur d'affichage : elle doit remonter.
            if (erreur is CancellationException) throw erreur
            emit(SaisieVaccinUiState.Erreur)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SaisieVaccinUiState.Chargement,
        )

    // --- Événements du formulaire ---------------------------------------------

    /** Date choisie dans le `DatePickerDialog`. La validation R5 se recalcule par le `combine`. */
    fun onDateChoisie(date: LocalDate) {
        edition.update { it?.copy(date = date, echec = false) }
    }

    fun onLieuChange(lieu: String) {
        edition.update { it?.copy(lieu = lieu, echec = false) }
    }

    fun onLotChange(lot: String) {
        edition.update { it?.copy(lot = lot, echec = false) }
    }

    /**
     * Enregistre la dose, en création comme en correction (US-B3, US-B4).
     *
     * L'identifiant de l'administration existante est relu **au moment de l'écriture** plutôt
     * que gardé dans l'état : il est technique, il n'a rien à faire dans un `UiState`, et la
     * relecture garantit qu'une correction écrit bien sur la ligne présente en base.
     *
     * Le garde-fou sur un refus de R5 double le bouton désactivé : l'état a pu
     * changer entre la composition et le clic (passage de minuit sur une date « demain »).
     */
    fun onEnregistrer() {
        val etatCourant = uiState.value as? SaisieVaccinUiState.Pret ?: return
        if (!etatCourant.peutEnregistrer) return

        edition.update { it?.copy(enCours = true, echec = false) }
        viewModelScope.launch {
            try {
                val existante = administrationExistante()
                enfants.enregistrerAdministration(
                    VaccinAdministre(
                        // Correction : on réécrit la ligne existante. Création : nouvel UUID.
                        id = existante?.id ?: nouvelIdentifiant(),
                        enfantId = route.enfantId,
                        vaccinId = route.vaccinId,
                        date = etatCourant.date,
                        // Champs facultatifs : la chaîne vide d'un champ de texte n'est pas
                        // une valeur, elle redevient `null` en base.
                        lieu = etatCourant.lieu.trim().ifBlank { null },
                        lot = etatCourant.lot.trim().ifBlank { null },
                    ),
                )

                // (B12) Scénario « vaccin saisi avant le rappel » de US-B5 : le rappel de
                // cette dose disparaît, et ceux des doses qui en dépendent sont recalculés
                // depuis sa date **réelle** (R1). Un seul appel : `replanifier` annule les
                // travaux de l'enfant avant de réenfiler ce que R3 produit, et retire du
                // volet les notifications des doses devenues faites. Aucun `WorkRequest`
                // n'est programmé depuis un écran (CLAUDE.md, règle 9).
                //
                // Dans le `try` : une replanification impossible laisse l'écran ouvert sur
                // son message d'échec plutôt que de refermer sur un carnet dont les rappels
                // ne correspondent plus. La dose est déjà écrite et l'enregistrement est un
                // `upsert` idempotent (R5), donc réessayer rejoue les deux sans doublon.
                planificateur.replanifier(route.enfantId)

                edition.update { it?.copy(enCours = false, termine = true) }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Throwable) {
                // La saisie reste à l'écran : rien n'est perdu, l'utilisateur peut réessayer.
                edition.update { it?.copy(enCours = false, echec = true) }
            }
        }
    }

    /**
     * Supprime la saisie existante (US-B4), après confirmation demandée par l'écran.
     *
     * Sans administration en base, l'appel ne fait rien : le bouton n'est affiché qu'en
     * correction, et deux clics rapides ne doivent pas produire une seconde suppression.
     */
    fun onSupprimer() {
        val etatCourant = uiState.value as? SaisieVaccinUiState.Pret ?: return
        if (!etatCourant.correction || etatCourant.enregistrementEnCours || etatCourant.termine) return

        edition.update { it?.copy(enCours = true, echec = false) }
        viewModelScope.launch {
            try {
                val existante = administrationExistante()
                if (existante != null) {
                    enfants.supprimerAdministration(existante.id)
                }

                // (B12) Même appel qu'à l'enregistrement, pour la raison inverse : la dose
                // supprimée redevient à faire, son rappel doit être reprogrammé — et les
                // doses qui en dépendent repartent de leur date théorique (R1).
                planificateur.replanifier(route.enfantId)

                edition.update { it?.copy(enCours = false, termine = true) }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Throwable) {
                edition.update { it?.copy(enCours = false, echec = true) }
            }
        }
    }

    /**
     * La dose déjà enregistrée pour le couple (enfant, vaccin) de la route, s'il y en a une.
     *
     * Lecture ponctuelle sur le `Flow` du repository : `first()` prend la valeur courante du
     * cache Room et se termine. L'interface `EnfantRepository` n'expose pas de lecture
     * `suspend` unitaire, et lui en ajouter une pour ce seul besoin toucherait un fichier de
     * Dev A.
     */
    private suspend fun administrationExistante(): VaccinAdministre? = try {
        enfants.observer(route.enfantId).first()
            ?.administres
            ?.firstOrNull { it.vaccinId == route.vaccinId }
            ?.toDomain()
    } catch (annulation: CancellationException) {
        throw annulation
    } catch (erreur: Throwable) {
        // Base illisible : l'écran d'erreur est déjà produit par le `catch` de `uiState`.
        null
    }
}

/**
 * Le formulaire tel que l'utilisateur l'a rempli, plus l'avancement de l'écriture.
 *
 * Interne au ViewModel : l'écran n'en voit que la projection dans
 * [SaisieVaccinUiState.Pret].
 */
private data class Edition(
    val date: LocalDate,
    val lieu: String,
    val lot: String,
    val enCours: Boolean = false,
    val echec: Boolean = false,
    val termine: Boolean = false,
)
