package mg.univ.fahasalamana.platform

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.domain.DELAI_VERROUILLAGE
import mg.univ.fahasalamana.domain.fautIlReverrouiller
import java.time.Duration
import java.time.Instant

/**
 * Ce que l'interface doit afficher avant d'afficher quoi que ce soit d'autre.
 *
 * Trois cas et non deux : [Indetermine] existe parce que le réglage vit dans DataStore,
 * donc derrière un `Flow` asynchrone. Sans cet état, la première composition devrait parier
 * — et se tromper une fois sur deux : soit le carnet apparaît une fraction de seconde avant
 * que le verrou ne se pose, soit l'écran de code clignote chez les utilisateurs qui n'en ont
 * pas. C'est exactement ce que redoutait le `TODO(B18)` de `AppNavHost.kt`.
 */
sealed interface EtatVerrouillage {

    /** Le réglage n'est pas encore lu. L'interface n'affiche ni le carnet, ni l'écran de code. */
    data object Indetermine : EtatVerrouillage

    /** Un code est configuré et il n'a pas été saisi : rien du carnet ne doit être affiché. */
    data object Verrouille : EtatVerrouillage

    /** Pas de code, ou code déjà saisi dans cette session : le carnet est accessible. */
    data object Ouvert : EtatVerrouillage
}

/**
 * Verrouillage du carnet à la reprise — §B8 point 4, US-B10, tâche B18.
 *
 * Instance unique pour le processus (déclarée `single` dans Koin) : c'est elle qui tient la
 * réponse à « le carnet est-il ouvert ? », et il ne peut y en avoir deux.
 *
 * ## Deux informations, pas une
 *
 * - **[deverrouille]**, en mémoire seulement : « le code a été saisi depuis le démarrage de
 *   ce processus ». Il naît à `false` et meurt avec le processus. C'est lui, et lui seul,
 *   qui fait qu'un **démarrage à froid demande le code** (§B7.1 : `[*] --> Verrouillage : si
 *   PIN activé`) : aucune durée n'a besoin d'être consultée pour cela.
 * - **[dernierAcces]**, l'instant du dernier passage en arrière-plan, qui sert à décider s'il
 *   faut reverrouiller **au sein d'une même session**.
 *
 * ## Pourquoi rien de tout cela n'est écrit sur le disque
 *
 * Les deux valeurs vivent en mémoire et meurent avec le processus. Ce n'est pas un oubli :
 *
 * 1. **Rien ne les relirait.** Un `dernierAcces` persisté ne pourrait servir qu'au démarrage
 *    à froid, et il dirait alors « le processus a été tué il y a trente secondes, donc on
 *    peut rouvrir sans code » — l'inverse exact du §B7.1 (`[*] --> Verrouillage : si PIN
 *    activé`). La seule lecture possible est une lecture dont on ne veut pas.
 * 2. **Un horodatage d'usage n'est pas une donnée neutre.** Sur une application de santé, il
 *    dit quand un parent a consulté le carnet de son enfant, et à quelle fréquence. Le
 *    conserver dans un fichier que personne ne relit, c'est une trace gratuite ; la règle du
 *    §B8 (« aucune donnée de santé ne sort de l'appareil », `allowBackup=false`) vaut aussi
 *    pour ce qui reste dessus.
 *
 * Le §B8, point 4, se lit comme s'il décrivait une valeur persistée (« l'écran de
 * verrouillage s'affiche à la reprise si `dernierAcces` date de plus de 2 minutes »). C'est
 * le comportement qui est spécifié, pas le support : il est tenu à l'identique ici, en
 * mémoire. **Ne pas réintroduire d'écriture dans DataStore sans un lecteur en face.**
 *
 * ## Deux minutes, pas chaque bascule
 *
 * `ProcessLifecycleOwner` observe le **processus** et non une activité : une rotation, une
 * boîte de dialogue système ou le passage d'un écran à l'autre ne le font pas bouger. Seul un
 * vrai départ en arrière-plan (accueil, autre application, écran éteint) met [dernierAcces]
 * à jour. Et même là, revenir en moins de deux minutes ne
 * redemande rien : aller lire le SMS du centre de santé en pleine saisie ne doit pas coûter
 * une ressaisie du code (US-B10, scénario « reprise immédiate »).
 *
 * ## Pourquoi un `Flow` du cycle de vie plutôt qu'un `LifecycleObserver`
 *
 * `lifecycle.currentStateFlow` est le même mécanisme que celui de `HorlogeJour.kt`, déjà en
 * place dans le projet. Il se collecte depuis n'importe quel fil, là où `addObserver` exige
 * le fil principal — contrainte fragile pour un objet construit paresseusement par Koin, à
 * un moment que ce fichier ne maîtrise pas.
 *
 * @param preferences accès à la clé `verrouillage_actif` (B02) ; c'est la seule qui soit lue
 *   ici, et rien n'est écrit en retour.
 * @param portee portée de vie du processus. Jamais annulée : le gardien doit continuer
 *   d'observer le cycle de vie tant que l'application existe. `Dispatchers.Default` suffit,
 *   rien ici ne touche à l'interface.
 * @param horloge source de l'instant présent, injectable pour les tests instrumentés.
 * @param delai durée de tolérance en arrière-plan ([DELAI_VERROUILLAGE] par défaut).
 * @param lifecycle cycle de vie observé, celui du processus par défaut.
 */
class GardienVerrouillage(
    private val preferences: PreferencesLocales,
    private val portee: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val horloge: () -> Instant = Instant::now,
    private val delai: Duration = DELAI_VERROUILLAGE,
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) {

    /** Le code a été saisi depuis le démarrage du processus. Faux au démarrage, par construction. */
    private val deverrouille = MutableStateFlow(false)

    /**
     * Instant du dernier passage en arrière-plan, `null` tant que l'application n'y est pas
     * allée dans ce processus.
     *
     * `@Volatile` : écrit par la coroutine d'observation, relu par la même — mais rien ne
     * garantit qu'elle reste sur le même fil du pool `Default` d'une reprise à l'autre.
     */
    @Volatile
    private var dernierAcces: Instant? = null

    /**
     * État à afficher.
     *
     * `SharingStarted.Eagerly` et non `WhileSubscribed` : la lecture de DataStore doit
     * commencer dès la construction du gardien, pas à la première composition, sans quoi
     * [EtatVerrouillage.Indetermine] durerait le temps d'un aller-retour disque **après**
     * l'apparition de l'écran. C'est la différence entre un démarrage net et un clignotement.
     */
    val etat: StateFlow<EtatVerrouillage> = combine(
        preferences.verrouillageActif,
        deverrouille,
    ) { actif, ouvert ->
        if (!actif || ouvert) EtatVerrouillage.Ouvert else EtatVerrouillage.Verrouille
    }.stateIn(
        scope = portee,
        started = SharingStarted.Eagerly,
        initialValue = EtatVerrouillage.Indetermine,
    )

    init {
        observerLePremierPlan()
    }

    /**
     * Le bon code vient d'être saisi, ou un code vient d'être choisi.
     *
     * Appelée par `VerrouillageViewModel` dans les deux cas. Le second n'est pas un détail :
     * sans lui, choisir un code depuis les réglages activerait `verrouillage_actif` alors que
     * [deverrouille] vaut encore `false`, et l'utilisateur se retrouverait enfermé dehors à
     * la seconde où il vient de créer son code.
     */
    fun deverrouiller() {
        deverrouille.value = true
    }

    /**
     * Suit les passages au premier plan et en arrière-plan du **processus**.
     *
     * `distinctUntilChanged` sur le booléen « au premier plan » : les états intermédiaires de
     * `Lifecycle` (CREATED, STARTED, RESUMED) ne doivent produire qu'une bascule, pas trois.
     *
     * Les toutes premières émissions au démarrage du processus — `INITIALIZED` avant
     * `STARTED` — ne demandent aucun traitement particulier : elles tombent sur un
     * [deverrouille] déjà à `false` et sur un [dernierAcces] tout juste noté, donc sur des
     * opérations neutres. C'est plus sûr qu'un cas particulier à écrire et à maintenir.
     */
    private fun observerLePremierPlan() {
        portee.launch {
            lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.STARTED) }
                .distinctUntilChanged()
                .collect { auPremierPlan ->
                    if (auPremierPlan) reprendre() else quitter()
                }
        }
    }

    /**
     * Départ en arrière-plan : on note l'heure, **en mémoire seulement**.
     *
     * Une affectation, pas d'entrée-sortie : rien à attendre, rien qui puisse échouer, rien à
     * relire au prochain démarrage. Le « pourquoi pas dans DataStore » est en tête de fichier.
     */
    private fun quitter() {
        dernierAcces = horloge()
    }

    /** Retour au premier plan : la décision est prise par `domain`, pas ici. */
    private fun reprendre() {
        if (fautIlReverrouiller(dernierAcces = dernierAcces, maintenant = horloge(), delai = delai)) {
            deverrouille.value = false
        }
    }
}
