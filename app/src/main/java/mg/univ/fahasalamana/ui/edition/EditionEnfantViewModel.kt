package mg.univ.fahasalamana.ui.edition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.local.nouvelIdentifiant
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.domain.SaisieEnfant
import mg.univ.fahasalamana.domain.Sexe
import mg.univ.fahasalamana.domain.dateNaissanceMinimum
import mg.univ.fahasalamana.domain.enfantValide
import mg.univ.fahasalamana.domain.valider
import mg.univ.fahasalamana.platform.PlanificateurRappels
import mg.univ.fahasalamana.ui.navigation.EditionEnfant
import java.time.LocalDate

/**
 * Édition d'un enfant (B07, US-B1 — §B6 : VM2 → `EnfantRepository`).
 *
 * **Aucune règle métier ici** (règle 1 de CLAUDE.md) : ce qui fait qu'une saisie est
 * acceptable est écrit dans `domain/ValidationEnfant.kt`, en fonctions pures. Ce ViewModel
 * tient l'état du formulaire, appelle la validation à chaque frappe et traduit son résultat
 * en état d'écran.
 *
 * **L'état d'édition vit ici et pas dans l'écran** : c'est ce qui le fait survivre à une
 * rotation, un `ViewModel` n'étant pas recréé au changement de configuration. Ce qui est
 * dans [EtatEdition] est exactement ce qu'on ne veut pas perdre en tournant le téléphone :
 * les trois champs, les champs déjà touchés, et les deux dialogues ouverts.
 *
 * L'état exposé est recalculé à partir du **jour courant** (`platform/HorlogeJour.kt`) :
 * la validation dépend de la date d'aujourd'hui, et un formulaire laissé ouvert jusqu'à
 * minuit doit basculer avec elle plutôt que de rester sur la veille. D'où le `combine` :
 * `LocalDate.now()` écrit ici rendrait la validation dépendante de l'instant de composition
 * et intestable.
 *
 * La navigation n'est pas de son ressort : il signale par [EditionEnfantUiState.Formulaire.sortie]
 * qu'il a fini, l'écran décide où aller.
 */
class EditionEnfantViewModel(
    savedStateHandle: SavedStateHandle,
    private val enfants: EnfantRepository,
    private val horlogeJour: Flow<LocalDate>,
    private val planificateur: PlanificateurRappels,
) : ViewModel() {

    /** Argument de navigation lu par la route typée, jamais parsé dans l'écran (CLAUDE.md, règle 4). */
    private val route = savedStateHandle.toRoute<EditionEnfant>()

    /** Création ou modification : l'identifiant nul de la route est le seul critère (§B7.1). */
    private val mode: ModeEdition =
        if (route.enfantId == null) ModeEdition.CREATION else ModeEdition.MODIFICATION

    /**
     * Identifiant de l'enfant édité : celui de la route en modification, un UUID neuf en
     * création.
     *
     * Fixé une fois pour toutes à la construction du ViewModel, donc stable à la rotation :
     * deux appuis successifs sur « Ajouter » ne peuvent pas créer deux enfants, puisque
     * `EnfantRepository.enregistrer` est un `Upsert` sur cet identifiant.
     */
    private val identifiant: String = route.enfantId ?: nouvelIdentifiant()

    /** Le seul état mutable de l'écran. Tout le reste en est dérivé. */
    private val etat = MutableStateFlow(EtatEdition(chargement = mode == ModeEdition.MODIFICATION))

    val uiState: StateFlow<EditionEnfantUiState> =
        combine(etat, horlogeJour) { edition, aujourdHui -> edition.versUiState(aujourdHui) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = EditionEnfantUiState.Chargement(mode),
            )

    init {
        val id = route.enfantId
        if (id != null) viewModelScope.launch { charger(id) }
    }

    // --- Saisie ---------------------------------------------------------------

    fun onPrenomChange(valeur: String) = etat.update {
        it.copy(saisie = it.saisie.copy(prenom = valeur), prenomTouche = true)
    }

    fun onSexeChange(sexe: Sexe) = etat.update {
        it.copy(saisie = it.saisie.copy(sexe = sexe))
    }

    fun onOuvrirSelecteurDate() = etat.update { it.copy(selecteurDateOuvert = true) }

    fun onFermerSelecteurDate() = etat.update { it.copy(selecteurDateOuvert = false) }

    fun onDateChoisie(date: LocalDate) = etat.update {
        it.copy(
            saisie = it.saisie.copy(dateNaissance = date),
            dateTouchee = true,
            selecteurDateOuvert = false,
        )
    }

    /** Message d'échec effacé une fois affiché, pour qu'il ne revienne pas à la rotation. */
    fun onEchecAffiche() = etat.update { it.copy(echec = null) }

    // --- Enregistrement -------------------------------------------------------

    /**
     * Enregistre l'enfant, en création comme en modification : même identifiant, même
     * `Upsert` côté repository.
     *
     * La validation est refaite ici alors que le bouton est déjà désactivé quand la saisie
     * est invalide. Ce n'est pas de la méfiance envers l'écran : c'est le seul endroit où
     * l'on sait quel jour il est au moment de l'appui, et c'est ce qui permet de marquer les
     * champs restés vierges comme « touchés » pour que leurs erreurs deviennent visibles.
     */
    fun onEnregistrer() {
        if (etat.value.enCours) return
        viewModelScope.launch {
            val aujourdHui = horlogeJour.first()
            val enfant = etat.value.saisie.enfantValide(id = identifiant, aujourdHui = aujourdHui)
            if (enfant == null) {
                etat.update { it.copy(prenomTouche = true, dateTouchee = true) }
                return@launch
            }

            etat.update { it.copy(enCours = true, echec = null) }
            val ecriture = tenter { enfants.enregistrer(enfant) }
            if (ecriture.isFailure) {
                etat.update { it.copy(enCours = false, echec = EchecEdition.ENREGISTREMENT) }
                return@launch
            }

            // (B12) Rappels des échéances futures, dernier « Et » du scénario « création
            // valide » de US-B1. Un seul appel, en création **comme en modification** :
            // changer une date de naissance décale tout l'échéancier, donc tous les rappels.
            // `replanifier` annule les travaux existants avant de réenfiler (R4, politique
            // `REPLACE`), il n'y a donc rien à annuler ici — et l'écran ne programme jamais
            // de `WorkRequest` lui-même (règle 9 de CLAUDE.md).
            //
            // Enveloppé dans `tenter`, et son résultat volontairement ignoré : l'enfant est
            // déjà enregistré, une replanification qui échoue ne doit ni faire tomber le
            // `viewModelScope` ni transformer un enregistrement réussi en échec à l'écran.
            // L'opération étant idempotente, la prochaine écriture la rejouera.
            tenter { planificateur.replanifier(enfant.id) }

            etat.update { it.copy(enCours = false, sortie = SortieEdition.ENREGISTRE) }
        }
    }

    // --- Suppression ----------------------------------------------------------

    fun onDemanderSuppression() = etat.update { it.copy(confirmationSuppression = true) }

    fun onAnnulerSuppression() = etat.update { it.copy(confirmationSuppression = false) }

    /**
     * Supprime l'enfant et, par la cascade déclarée sur `vaccins_administres`, toutes ses
     * doses saisies (US-B1, scénario « suppression »).
     *
     * N'est appelée qu'après confirmation explicite : ce sont des données de santé, et le
     * dialogue dit ce qui part avec l'enfant avant que ce code ne s'exécute.
     */
    fun onConfirmerSuppression() {
        val id = route.enfantId ?: return
        if (etat.value.enCours) return
        viewModelScope.launch {
            etat.update { it.copy(enCours = true, confirmationSuppression = false, echec = null) }
            val ecriture = tenter { enfants.supprimer(id) }
            if (ecriture.isFailure) {
                etat.update { it.copy(enCours = false, echec = EchecEdition.SUPPRESSION) }
                return@launch
            }

            // (B12) Troisième « Alors » du scénario « suppression » de US-B1 : « ses vaccins
            // administrés et ses rappels sont supprimés avec lui ». Les doses partent avec
            // l'enfant par la cascade SQL ; les `WorkRequest` uniques
            // "rappel-<enfantId>-<vaccinId>" (R4), eux, survivent à la base et doivent être
            // annulés explicitement — sans cela le travail resterait programmé pour un enfant
            // effacé (le worker relit la base avant de notifier, donc rien ne s'afficherait,
            // mais le réveil aurait quand même lieu).
            //
            // `annulerTous` et non `replanifier` : c'est le chemin de la suppression documenté
            // par `PlanificateurRappels`, l'enfant n'étant plus lisible. Les deux font la même
            // chose ici — `replanifier` sur un enfant absent dégénère en annulation — mais
            // celui-ci évite une lecture de la base qui ne peut plus rien rendre, et n'est pas
            // `suspend` : rien ne peut s'intercaler avant la sortie de l'écran.
            planificateur.annulerTous(id)

            etat.update { it.copy(enCours = false, sortie = SortieEdition.SUPPRIME) }
        }
    }

    // --- Interne --------------------------------------------------------------

    /**
     * Lecture unique de l'enfant à modifier, et non un `Flow` collecté en continu : une
     * écriture faite ailleurs ne doit pas écraser ce que l'utilisateur est en train de
     * taper. Le formulaire est une photographie prise à l'ouverture, pas un miroir.
     */
    private suspend fun charger(id: String) {
        val lecture = tenter { enfants.observer(id).first() }
        val carnet = lecture.getOrNull()
        etat.update { courant ->
            when {
                lecture.isFailure -> courant.copy(chargement = false, erreurLecture = true)
                carnet == null -> courant.copy(chargement = false, introuvable = true)
                else -> {
                    val enfant = carnet.enfant.toDomain()
                    courant.copy(
                        chargement = false,
                        prenomEnregistre = enfant.prenom,
                        saisie = SaisieEnfant(
                            prenom = enfant.prenom,
                            dateNaissance = enfant.dateNaissance,
                            sexe = enfant.sexe,
                        ),
                    )
                }
            }
        }
    }

    /**
     * `runCatching` qui laisse passer l'annulation : sans ce `throw`, l'arrêt du
     * `viewModelScope` serait confondu avec une panne de la base et afficherait un message
     * d'erreur à un écran déjà en train de disparaître.
     */
    private inline fun <T> tenter(bloc: () -> T): Result<T> = try {
        Result.success(bloc())
    } catch (annulation: CancellationException) {
        throw annulation
    } catch (erreur: Throwable) {
        Result.failure(erreur)
    }

    private fun EtatEdition.versUiState(aujourdHui: LocalDate): EditionEnfantUiState = when {
        chargement -> EditionEnfantUiState.Chargement(mode)
        erreurLecture -> EditionEnfantUiState.Erreur
        introuvable -> EditionEnfantUiState.Introuvable
        else -> {
            val validation = saisie.valider(aujourdHui)
            EditionEnfantUiState.Formulaire(
                mode = mode,
                prenomEnregistre = prenomEnregistre,
                prenom = saisie.prenom,
                dateNaissance = saisie.dateNaissance,
                sexe = saisie.sexe,
                // Une erreur ne s'affiche qu'une fois le champ touché : un formulaire de
                // création s'ouvre vierge, pas déjà en faute.
                erreurPrenom = validation.prenom.takeIf { prenomTouche },
                erreurDateNaissance = validation.dateNaissance.takeIf { dateTouchee },
                peutEnregistrer = validation.valide && !enCours,
                enCours = enCours,
                selecteurDateOuvert = selecteurDateOuvert,
                confirmationSuppression = confirmationSuppression,
                anneeMinimum = dateNaissanceMinimum(aujourdHui).year,
                // Une année au-delà de l'année courante : le sélecteur doit pouvoir atteindre
                // une date future, sans quoi le message « La date de naissance ne peut pas
                // être dans le futur » (US-B1) serait inatteignable — et il le deviendrait
                // aussi un 31 décembre si l'on s'arrêtait à l'année en cours.
                anneeMaximum = aujourdHui.year + 1,
                echec = echec,
                sortie = sortie,
            )
        }
    }
}

/**
 * État interne du formulaire, invisible de l'écran.
 *
 * Séparé de [EditionEnfantUiState] parce qu'il ne contient que ce que l'utilisateur a fait :
 * la validation, les bornes du sélecteur et l'activation du bouton sont recalculées à chaque
 * émission à partir de lui et du jour courant. Rien n'est donc à tenir synchronisé.
 *
 * @param prenomTouche et [dateTouchee] : champs déjà édités, ou marqués comme tels par une
 *   tentative d'enregistrement. Ils commandent l'affichage des erreurs, jamais la validité.
 */
private data class EtatEdition(
    val chargement: Boolean = false,
    val erreurLecture: Boolean = false,
    val introuvable: Boolean = false,
    val prenomEnregistre: String? = null,
    val saisie: SaisieEnfant = SaisieEnfant(),
    val prenomTouche: Boolean = false,
    val dateTouchee: Boolean = false,
    val selecteurDateOuvert: Boolean = false,
    val confirmationSuppression: Boolean = false,
    val enCours: Boolean = false,
    val echec: EchecEdition? = null,
    val sortie: SortieEdition? = null,
)
