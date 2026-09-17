package mg.univ.fahasalamana.ui.verrouillage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.domain.EmpreintePin
import mg.univ.fahasalamana.domain.LONGUEUR_PIN_MAX
import mg.univ.fahasalamana.domain.creerEmpreintePin
import mg.univ.fahasalamana.domain.erreurDePin
import mg.univ.fahasalamana.domain.pinCorrespond
import mg.univ.fahasalamana.platform.EtatVerrouillage
import mg.univ.fahasalamana.platform.GardienVerrouillage

/**
 * Écran Verrouillage — US-B10, tâche B18.
 *
 * Ce ViewModel **ne décide rien** : le format d'un code, la comparaison d'une empreinte et
 * la durée de tolérance sont des fonctions pures de `domain/VerrouillagePin.kt`. Ce qui
 * reste ici est l'enchaînement des étapes et l'appel à DataStore.
 *
 * ## Un seul écran, trois usages
 *
 * La route `Verrouillage` est un `object` sans argument (§B7.1) et `Routes.kt` est un fichier
 * partagé : plutôt que d'y ajouter un paramètre, le mode est **déduit de l'état du verrou**
 * au moment où l'écran s'ouvre.
 *
 * | Verrou | Code enregistré | Mode |
 * |---|---|---|
 * | verrouillé | oui (forcément) | [ModeVerrouillage.Ouverture] |
 * | ouvert | oui | [ModeVerrouillage.Modification] |
 * | ouvert | non | [ModeVerrouillage.Creation] |
 *
 * Le mode est lu **une fois**, dans `init`, et jamais recalculé : c'est la même précaution
 * que l'amorçage du formulaire de `SaisieVaccinViewModel`. Sans cela, enregistrer un code
 * neuf ferait passer `verrouillage_actif` à `true` et l'écran basculerait de « Création » à
 * « Modification » sous les doigts de l'utilisateur, au lieu de se refermer.
 *
 * ## Rotation
 *
 * Tout ce que l'utilisateur a tapé vit dans [saisie] et dans [codeChoisi], donc dans le
 * ViewModel, qui survit au changement de configuration : tourner le téléphone pendant la
 * saisie ne perd rien (Definition of Done de B18). Rien n'est mis dans un `SavedStateHandle` :
 * son contenu est écrit dans le `Bundle` du système, où un code de verrouillage n'a rien à
 * faire.
 *
 * ## Pourquoi un `Dispatchers` explicite ici
 *
 * `CLAUDE.md` demande de ne pas choisir de dispatcher, parce que Room et Retrofit gèrent le
 * leur. PBKDF2, lui, ne gère rien : c'est un calcul qui occupe un cœur pendant 150 à 400 ms.
 * Le laisser sur le fil principal figerait l'écran à chaque tentative et déclencherait un ANR
 * sur un téléphone lent. `Dispatchers.Default` est donc explicite, et c'est la seule
 * exception du projet.
 */
class VerrouillageViewModel(
    private val preferences: PreferencesLocales,
    private val gardien: GardienVerrouillage,
) : ViewModel() {

    private val saisie = MutableStateFlow<VerrouillageUiState>(VerrouillageUiState.Chargement)

    val uiState: StateFlow<VerrouillageUiState> = saisie.asStateFlow()

    /**
     * Le code choisi à l'étape [EtapeCode.NouveauCode], en attente de sa confirmation.
     *
     * **Hors de l'`UiState`** : l'écran n'a aucun besoin de le connaître, et un état
     * d'affichage se retrouve dans des traces de recomposition. Il est effacé dès qu'il a
     * servi.
     */
    private var codeChoisi: String? = null

    init {
        viewModelScope.launch {
            val mode = modeDOuverture()
            saisie.value = VerrouillageUiState.Saisie(mode = mode, etape = premiereEtape(mode))
        }
    }

    // --- Événements de l'écran ------------------------------------------------

    /**
     * Frappe de l'utilisateur.
     *
     * Le filtrage est ici et non dans `domain` : ce n'est pas une règle, c'est la contrainte
     * d'un champ de saisie. Un clavier numérique peut malgré tout produire un espace ou un
     * signe suivant les claviers installés, et un collage peut produire n'importe quoi.
     *
     * L'erreur affichée disparaît dès la première frappe : la laisser sous un champ qu'on est
     * en train de corriger ne sert qu'à faire douter.
     */
    fun onCodeChange(frappe: String) {
        val chiffres = frappe.filter { it in '0'..'9' }.take(LONGUEUR_PIN_MAX)
        saisie.update { etat ->
            (etat as? VerrouillageUiState.Saisie)?.copy(code = chiffres, erreur = null) ?: etat
        }
    }

    /**
     * Validation de l'étape courante.
     *
     * Le format est revérifié ici et pas seulement à travers `peutValider` : la touche
     * « Terminé » du clavier appelle cette fonction même quand le bouton est grisé. Sans ce
     * contrôle, valider deux chiffres au clavier lancerait une dérivation sur un code que le
     * bouton refusait, et l'utilisateur n'aurait aucun message.
     *
     * Le `when` est sans `else` : une cinquième étape ajoutée à [EtapeCode] casse la
     * compilation ici, au lieu de produire un bouton silencieux.
     */
    fun onValider() {
        val etat = saisie.value as? VerrouillageUiState.Saisie ?: return
        // Ré-entrance : double appui, ou « Terminé » pendant une vérification déjà lancée.
        if (etat.verificationEnCours || etat.termine) return

        val formatFautif = erreurDePin(etat.code)
        if (formatFautif != null) {
            saisie.value = etat.copy(erreur = ErreurSaisiePin.Format(formatFautif))
            return
        }

        when (etat.etape) {
            EtapeCode.CodeActuel -> ouvrirLeCarnet(etat)
            EtapeCode.AncienCode -> autoriserLeChangement(etat)
            EtapeCode.NouveauCode -> passerALaConfirmation(etat)
            EtapeCode.Confirmation -> enregistrerLeNouveauCode(etat)
        }
    }

    // --- Étapes ---------------------------------------------------------------

    /** Ouverture du carnet : le code saisi est comparé à l'empreinte enregistrée. */
    private fun ouvrirLeCarnet(etat: VerrouillageUiState.Saisie) {
        verifierPuis(etat) { correct ->
            if (correct) {
                gardien.deverrouiller()
                saisie.value = etat.copy(code = "", verificationEnCours = false, termine = true)
            } else {
                saisie.value = etat.copy(
                    code = "",
                    erreur = ErreurSaisiePin.CodeIncorrect,
                    verificationEnCours = false,
                )
            }
        }
    }

    /** Modification : on ne change de code qu'après avoir redonné l'ancien. */
    private fun autoriserLeChangement(etat: VerrouillageUiState.Saisie) {
        verifierPuis(etat) { correct ->
            saisie.value = if (correct) {
                etat.copy(etape = EtapeCode.NouveauCode, code = "", verificationEnCours = false)
            } else {
                etat.copy(
                    code = "",
                    erreur = ErreurSaisiePin.CodeIncorrect,
                    verificationEnCours = false,
                )
            }
        }
    }

    /** Le code choisi est mis de côté, le champ est vidé, on le redemande. */
    private fun passerALaConfirmation(etat: VerrouillageUiState.Saisie) {
        codeChoisi = etat.code
        saisie.value = etat.copy(etape = EtapeCode.Confirmation, code = "")
    }

    /**
     * Confirmation : si les deux saisies concordent, l'empreinte part dans DataStore.
     *
     * En cas de divergence on revient au choix du code, champ vide, plutôt que de faire
     * deviner laquelle des deux saisies était la bonne.
     *
     * [GardienVerrouillage.deverrouiller] est appelé **après** l'écriture : le réglage
     * `verrouillage_actif` vient de passer à `true`, et sans cet appel l'utilisateur serait
     * verrouillé dehors à l'instant même où il vient de choisir son code.
     */
    private fun enregistrerLeNouveauCode(etat: VerrouillageUiState.Saisie) {
        val attendu = codeChoisi
        if (attendu == null || attendu != etat.code) {
            codeChoisi = null
            saisie.value = etat.copy(
                etape = EtapeCode.NouveauCode,
                code = "",
                erreur = ErreurSaisiePin.ConfirmationDifferente,
            )
            return
        }

        saisie.value = etat.copy(verificationEnCours = true, erreur = null)
        viewModelScope.launch {
            try {
                val empreinte = withContext(Dispatchers.Default) { creerEmpreintePin(etat.code) }
                preferences.enregistrerPin(hash = empreinte.hash, sel = empreinte.sel)
                gardien.deverrouiller()
                codeChoisi = null
                saisie.value = etat.copy(code = "", verificationEnCours = false, termine = true)
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Throwable) {
                // La saisie reste à l'écran : rien n'est perdu, l'utilisateur peut réessayer.
                saisie.value = etat.copy(
                    verificationEnCours = false,
                    erreur = ErreurSaisiePin.EchecEnregistrement,
                )
            }
        }
    }

    // --- Outils ---------------------------------------------------------------

    /**
     * Compare le code saisi à l'empreinte enregistrée, hors du fil principal, puis rend la
     * main à [suite] sur le fil du ViewModel.
     *
     * Aucune limite au nombre d'essais, et c'est un choix. Un compteur de tentatives avec
     * blocage n'aurait personne à protéger : la seule attaque qu'il arrêterait est celle par
     * l'écran, où la lenteur de PBKDF2 impose déjà des heures pour 10 000 codes, et il
     * fabriquerait en revanche un second moyen de s'enfermer dehors — sans recours, comme le
     * code oublié lui-même.
     */
    private fun verifierPuis(
        etat: VerrouillageUiState.Saisie,
        suite: (correct: Boolean) -> Unit,
    ) {
        saisie.value = etat.copy(verificationEnCours = true, erreur = null)
        viewModelScope.launch {
            val empreinte = empreinteEnregistree()
            val correct = when {
                empreinte == null -> false
                else -> withContext(Dispatchers.Default) { pinCorrespond(etat.code, empreinte) }
            }
            suite(correct)
        }
    }

    /**
     * Le mode de l'écran, décidé à l'ouverture.
     *
     * On attend que le gardien ait lu le réglage : tant qu'il répond
     * [EtatVerrouillage.Indetermine], aucune des trois réponses ne serait fiable.
     */
    private suspend fun modeDOuverture(): ModeVerrouillage {
        val verrou = gardien.etat.first { it != EtatVerrouillage.Indetermine }
        if (verrou == EtatVerrouillage.Verrouille) return ModeVerrouillage.Ouverture
        val actif = preferences.verrouillageActif.first()
        return if (actif) ModeVerrouillage.Modification else ModeVerrouillage.Creation
    }

    /**
     * Empreinte enregistrée, ou `null` si aucune n'est lisible.
     *
     * Un `null` fait échouer la vérification plutôt que l'inverse : un DataStore illisible
     * ne doit pas ouvrir le carnet.
     */
    private suspend fun empreinteEnregistree(): EmpreintePin? = try {
        val hash = preferences.pinHash.first()
        val sel = preferences.pinSel.first()
        if (hash != null && sel != null) EmpreintePin(hash = hash, sel = sel) else null
    } catch (annulation: CancellationException) {
        throw annulation
    } catch (erreur: Throwable) {
        null
    }
}

/** Première étape de chaque mode. */
private fun premiereEtape(mode: ModeVerrouillage): EtapeCode = when (mode) {
    ModeVerrouillage.Ouverture -> EtapeCode.CodeActuel
    ModeVerrouillage.Creation -> EtapeCode.NouveauCode
    ModeVerrouillage.Modification -> EtapeCode.AncienCode
}
