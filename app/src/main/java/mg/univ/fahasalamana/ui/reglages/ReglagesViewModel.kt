package mg.univ.fahasalamana.ui.reglages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ResultatSync
import mg.univ.fahasalamana.platform.ETIQUETTE_LOG_RAPPEL
import mg.univ.fahasalamana.platform.PlanificateurRappels

/**
 * Écran Réglages (CDC §B6 : VM7 → `PreferencesRepository` + `ReferenceRepository`).
 *
 * Aucun calcul ici : on assemble des flux de lecture en un état d'affichage. La route
 * `Reglages` n'a pas d'argument, donc pas de `SavedStateHandle`.
 *
 * Deux écritures seulement, et elles n'ont rien à voir l'une avec l'autre :
 *
 *  - (B18) [onDesactiverVerrouillage] efface le code de verrouillage. L'activation et la
 *    modification du code passent, elles, par l'écran `Verrouillage`, parce qu'elles
 *    demandent une saisie.
 *  - (B19) [onVerifierMisesAJour] va chercher les contenus de référence publiés. Elle ne
 *    contient elle non plus **aucune règle** : la comparaison de version, la validation du
 *    format et le remplacement transactionnel sont dans `SynchroniseurReference` (couche
 *    `data`), la programmation des rappels dans `PlanificateurRappels` (couche `platform`).
 *    Ce ViewModel enchaîne les deux et tient l'état du bouton.
 */
class ReglagesViewModel(
    private val reference: ReferenceRepository,
    private val preferences: PreferencesLocales,
    private val planificateur: PlanificateurRappels,
) : ViewModel() {

    /**
     * (B19) État du bouton « Vérifier les mises à jour ».
     *
     * Un `MutableStateFlow` combiné aux flux de lecture plutôt qu'un second `StateFlow`
     * exposé : l'écran n'a ainsi qu'un seul état à collecter, et le compte rendu ne peut pas
     * annoncer des versions que la carte au-dessus n'affiche pas encore.
     *
     * Il est combiné **après** [etatReference] et non dedans : le compte rendu doit survivre
     * à un calendrier illisible, qui est précisément la situation où l'on vient chercher une
     * mise à jour.
     */
    private val miseAJour = MutableStateFlow(EtatMiseAJour())

    /**
     * Bloc « Données de référence », avec son propre `catch`.
     *
     * L'erreur est **cantonnée à ce bloc** : elle ne doit emporter ni l'état du verrouillage,
     * qui vient d'une autre source et dont l'utilisateur a besoin même quand le calendrier
     * est illisible, ni le compte rendu de mise à jour. Typé `Flow<EtatReference>` et non
     * `Flow<Pret>` : c'est ce qui permet à [catch] d'émettre [EtatReference.Erreur] sur la
     * même chaîne.
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
        miseAJour,
    ) { blocReference, verrouillageActif, etatMiseAJour ->
        ReglagesUiState(
            reference = blocReference,
            verrouillageActif = verrouillageActif,
            miseAJour = etatMiseAJour,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReglagesUiState(),
    )

    // --- Verrouillage par code (B18, US-B10) ---------------------------------

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

    // --- Mise à jour des contenus de référence (B19, US-B11) ------------------

    /**
     * Le parent a touché « Vérifier les mises à jour ».
     *
     * Trois choses, dans cet ordre : appeler le repository, replanifier les rappels si le
     * calendrier a changé, publier le compte rendu.
     *
     * **Aucun `try` autour de `mettreAJour()` n'est nécessaire** — elle s'engage à ne pas
     * lever (§B0, couche 2) — mais il y en a un quand même : l'interface ne doit pas
     * dépendre du respect d'une promesse pour ne pas planter. Le repli est un
     * [ResultatSync.echecTotal], c'est-à-dire exactement ce que le parent verrait si son
     * téléphone était hors réseau, ce qui est vrai dans les faits : rien n'a été mis à jour.
     *
     * Le garde sur `enCours` évite qu'un double appui lance deux téléchargements de 115 Ko.
     */
    fun onVerifierMisesAJour() {
        if (miseAJour.value.enCours) return
        viewModelScope.launch {
            miseAJour.update { it.copy(enCours = true, resultat = null) }

            val resultat = try {
                reference.mettreAJour()
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                ResultatSync.echecTotal()
            }

            // Uniquement si le **calendrier** a changé : un nouveau calendrier déplace les
            // dates prévues de tous les enfants, donc leurs rappels (§B8). Une nouvelle
            // version du seul annuaire ne change aucune date — replanifier serait du travail
            // pour rien, et ferait clignoter des notifications sans raison.
            if (resultat.calendrierRemplace) replanifierApresMiseAJour()

            miseAJour.update { it.copy(enCours = false, resultat = resultat) }
        }
    }

    /** Le compte rendu de mise à jour a été fermé par le parent. */
    fun onResultatMiseAJourFerme() {
        miseAJour.update { it.copy(resultat = null) }
    }

    /**
     * Recalcule les rappels de tout le carnet après un changement de calendrier (§B8 : la
     * mise à jour du calendrier est l'un des déclencheurs de `replanifier`).
     *
     * `replanifierTout()` et non `replanifier(enfantId)` : ce n'est pas un enfant qui a
     * changé, ce sont les données qui servent à calculer l'échéancier de tous. L'opération
     * est idempotente (R4), elle ne crée aucun doublon.
     *
     * **Son échec ne remet pas en cause la mise à jour**, qui est déjà en base : annoncer un
     * échec ferait croire au parent que le nouveau calendrier n'a pas été installé, alors
     * qu'il l'est. Seuls les rappels manqueraient, et la prochaine saisie ou la prochaine
     * modification les reprogrammera. Même arbitrage qu'en B17 après un import.
     */
    private suspend fun replanifierApresMiseAJour() {
        try {
            planificateur.replanifierTout()
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (erreur: Exception) {
            Log.w(ETIQUETTE_LOG_RAPPEL, "Replanification après mise à jour du calendrier impossible", erreur)
        }
    }
}
