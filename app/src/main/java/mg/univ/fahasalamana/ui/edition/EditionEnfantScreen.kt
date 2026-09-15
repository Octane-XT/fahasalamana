package mg.univ.fahasalamana.ui.edition

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.domain.ErreurDateNaissance
import mg.univ.fahasalamana.domain.ErreurPrenom
import mg.univ.fahasalamana.domain.Sexe
import mg.univ.fahasalamana.platform.DemandeNotificationsRappels
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran « Édition enfant » (tâche B07, US-B1).
 *
 * Une route pour deux usages : création quand `enfantId` est nul, modification sinon
 * (CDC §B7.1). Le titre, le libellé du bouton de validation et la présence du bouton de
 * suppression découlent tous du seul `ModeEdition`.
 *
 * Trois choses ne sont pas ici, et c'est voulu :
 * - **la validation**, qui est une fonction pure de `domain/ValidationEnfant.kt` ; l'écran
 *   ne fait qu'afficher les erreurs qu'elle renvoie et désactiver son bouton ;
 * - **les textes**, tous dans `res/values/strings_edition_enfant.xml`, ton factuel (R7) ;
 * - **la navigation**, qui arrive par des lambdas depuis `AppNavHost`.
 *
 * Le sélecteur de date ne bride volontairement pas la sélection au passé : c'est la
 * validation qui refuse une date future et affiche le message de US-B1. Un sélecteur qui
 * interdirait de la choisir rendrait ce message inatteignable — et la règle invisible.
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2) et l'écran « Mes enfants ». */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/**
 * Les gestes de l'écran, regroupés pour ne pas passer dix lambdas de composable en
 * composable. Chacune a un défaut vide : un `@Preview` s'écrit `ActionsEdition()`.
 */
@Immutable
data class ActionsEdition(
    val onPrenomChange: (String) -> Unit = {},
    val onSexeChange: (Sexe) -> Unit = {},
    val onOuvrirSelecteurDate: () -> Unit = {},
    val onFermerSelecteurDate: () -> Unit = {},
    val onDateChoisie: (LocalDate) -> Unit = {},
    val onEnregistrer: () -> Unit = {},
    val onDemanderSuppression: () -> Unit = {},
    val onAnnulerSuppression: () -> Unit = {},
    val onConfirmerSuppression: () -> Unit = {},
    val onEchecAffiche: () -> Unit = {},
)

/**
 * @param onRetour flèche de retour de la barre du haut : quitte sans enregistrer.
 * @param onEnregistre l'enfant vient d'être créé ou modifié — on revient d'où l'on vient.
 * @param onSupprime l'enfant vient d'être supprimé. **Ne peut pas être un simple retour** :
 *   la fiche de l'enfant supprimé est encore dans la pile, il faut remonter jusqu'à la liste.
 */
@Composable
fun EditionEnfantScreen(
    onRetour: () -> Unit,
    onEnregistre: () -> Unit,
    onSupprime: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EditionEnfantViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        ActionsEdition(
            onPrenomChange = vm::onPrenomChange,
            onSexeChange = vm::onSexeChange,
            onOuvrirSelecteurDate = vm::onOuvrirSelecteurDate,
            onFermerSelecteurDate = vm::onFermerSelecteurDate,
            onDateChoisie = vm::onDateChoisie,
            onEnregistrer = vm::onEnregistrer,
            onDemanderSuppression = vm::onDemanderSuppression,
            onAnnulerSuppression = vm::onAnnulerSuppression,
            onConfirmerSuppression = vm::onConfirmerSuppression,
            onEchecAffiche = vm::onEchecAffiche,
        )
    }

    EditionEnfantContenu(
        state = state,
        actions = actions,
        onRetour = onRetour,
        onEnregistre = onEnregistre,
        onSupprime = onSupprime,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditionEnfantContenu(
    state: EditionEnfantUiState,
    actions: ActionsEdition,
    onRetour: () -> Unit,
    onEnregistre: () -> Unit,
    onSupprime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoteSnackbar = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(titreEcran(state)) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_retour),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hoteSnackbar) },
    ) { interieur ->
        when (state) {
            is EditionEnfantUiState.Chargement ->
                EtatChargement(modifier = Modifier.padding(interieur))

            EditionEnfantUiState.Erreur ->
                EtatErreur(
                    message = stringResource(R.string.edition_erreur_lecture),
                    modifier = Modifier.padding(interieur),
                )

            EditionEnfantUiState.Introuvable ->
                EtatVide(
                    titre = stringResource(R.string.edition_introuvable_titre),
                    modifier = Modifier.padding(interieur),
                    description = stringResource(R.string.edition_introuvable_description),
                    icone = Icons.Outlined.SearchOff,
                    libelleAction = stringResource(R.string.edition_introuvable_action),
                    onAction = onRetour,
                )

            is EditionEnfantUiState.Formulaire ->
                FormulaireEnfant(
                    state = state,
                    actions = actions,
                    hoteSnackbar = hoteSnackbar,
                    onEnregistre = onEnregistre,
                    onSupprime = onSupprime,
                    modifier = Modifier.padding(interieur),
                )
        }
    }
}

@Composable
private fun FormulaireEnfant(
    state: EditionEnfantUiState.Formulaire,
    actions: ActionsEdition,
    hoteSnackbar: SnackbarHostState,
    onEnregistre: () -> Unit,
    onSupprime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // (B10) Un enfant vient d'être créé : c'est le moment prévu par le CDC (§B8) pour
    // demander l'autorisation d'envoyer des rappels — il y a désormais un échéancier à
    // rappeler. La demande est intercalée entre l'enregistrement et la sortie de l'écran,
    // et elle ne peut rien bloquer : `onTermine` quitte l'écran quelle que soit la réponse,
    // et l'enfant est déjà en base quand elle s'affiche. En modification, rien ne change :
    // la question a déjà été posée à la création.
    var demandeNotifications by remember { mutableStateOf(false) }

    // Le ViewModel signale qu'il a fini ; c'est ici qu'on quitte l'écran. Les deux sorties
    // ne mènent pas au même endroit (voir la documentation de `onSupprime`).
    LaunchedEffect(state.sortie) {
        when (state.sortie) {
            SortieEdition.ENREGISTRE ->
                if (state.mode == ModeEdition.CREATION) demandeNotifications = true else onEnregistre()

            SortieEdition.SUPPRIME -> onSupprime()
            null -> Unit
        }
    }

    DemandeNotificationsRappels(
        declenchee = demandeNotifications,
        onTermine = {
            demandeNotifications = false
            onEnregistre()
        },
    )

    // Le message est résolu hors du `LaunchedEffect` : `stringResource` n'est appelable que
    // dans une composition.
    val messageEchec = state.echec?.let { stringResource(messageDe(it)) }
    LaunchedEffect(messageEchec) {
        if (messageEchec != null) {
            hoteSnackbar.showSnackbar(messageEchec)
            actions.onEchecAffiche()
        }
    }

    val actif = !state.enCours

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChampPrenom(
            valeur = state.prenom,
            erreur = state.erreurPrenom,
            actif = actif,
            onChange = actions.onPrenomChange,
        )

        ChampDateNaissance(
            date = state.dateNaissance,
            erreur = state.erreurDateNaissance,
            actif = actif,
            onOuvrirSelecteur = actions.onOuvrirSelecteurDate,
        )

        ChoixSexe(
            selection = state.sexe,
            actif = actif,
            onChange = actions.onSexeChange,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = actions.onEnregistrer,
            modifier = Modifier.fillMaxWidth(),
            // Bouton désactivé tant que la saisie est invalide : la règle est visible avant
            // d'être expliquée, et l'utilisateur n'appuie jamais dans le vide.
            enabled = state.peutEnregistrer,
        ) {
            Text(
                stringResource(
                    when (state.mode) {
                        ModeEdition.CREATION -> R.string.edition_action_creer
                        ModeEdition.MODIFICATION -> R.string.edition_action_enregistrer
                    },
                ),
            )
        }

        if (state.suppressionPossible) {
            OutlinedButton(
                onClick = actions.onDemanderSuppression,
                modifier = Modifier.fillMaxWidth(),
                enabled = actif,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    // Le libellé du bouton porte déjà l'information pour TalkBack.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edition_action_supprimer))
            }
        }
    }

    if (state.selecteurDateOuvert) {
        SelecteurDateNaissance(
            dateInitiale = state.dateNaissance,
            anneeMinimum = state.anneeMinimum,
            anneeMaximum = state.anneeMaximum,
            onDateChoisie = actions.onDateChoisie,
            onFermer = actions.onFermerSelecteurDate,
        )
    }

    if (state.confirmationSuppression) {
        DialogueSuppression(
            prenom = state.prenomEnregistre,
            onConfirmer = actions.onConfirmerSuppression,
            onAnnuler = actions.onAnnulerSuppression,
        )
    }
}

// --- Champs -----------------------------------------------------------------

@Composable
private fun ChampPrenom(
    valeur: String,
    erreur: ErreurPrenom?,
    actif: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = texteErreurPrenom(erreur)
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        enabled = actif,
        label = { Text(stringResource(R.string.edition_champ_prenom)) },
        singleLine = true,
        isError = message != null,
        // Un texte d'accompagnement en permanence, message d'erreur ou explication : sans
        // cela, l'apparition de l'erreur ferait sauter tout le formulaire d'une ligne.
        supportingText = { Text(message ?: stringResource(R.string.edition_champ_prenom_aide)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
    )
}

/**
 * Date de naissance : champ en lecture seule, qui ouvre le sélecteur Material au toucher.
 *
 * Pas de saisie au clavier, donc pas de date à analyser ni de format à deviner — le
 * sélecteur est la seule porte d'entrée, et `LocalDate` la seule forme manipulée
 * (règle 2 de CLAUDE.md).
 */
@Composable
private fun ChampDateNaissance(
    date: LocalDate?,
    erreur: ErreurDateNaissance?,
    actif: Boolean,
    onOuvrirSelecteur: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = texteErreurDate(erreur)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = actif,
                onClickLabel = stringResource(R.string.edition_action_choisir_date),
                onClick = onOuvrirSelecteur,
            ),
    ) {
        OutlinedTextField(
            value = date?.format(FORMAT_JOUR).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = actif,
            readOnly = true,
            label = { Text(stringResource(R.string.edition_champ_date)) },
            placeholder = { Text(stringResource(R.string.edition_champ_date_format)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    // L'action est portée par le champ entier, annoncée par `onClickLabel`.
                    contentDescription = null,
                )
            },
            singleLine = true,
            isError = message != null,
            supportingText = { Text(message ?: stringResource(R.string.edition_champ_date_aide)) },
        )

        // Un `OutlinedTextField` consomme lui-même les touchers : sans cette surface
        // transparente au-dessus, le `clickable` du parent ne serait jamais appelé et le
        // champ se contenterait de prendre le focus. `pointerInput` plutôt qu'un second
        // `clickable` : il n'ajoute aucun nœud d'accessibilité, donc TalkBack continue
        // d'annoncer le champ, sa valeur et l'action « choisir la date de naissance ».
        if (actif) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(onOuvrirSelecteur) {
                        detectTapGestures { onOuvrirSelecteur() }
                    },
            )
        }
    }
}

/**
 * Sexe : trois choix exclusifs, « Non précisé » compris.
 *
 * `FlowRow` et non une rangée de segments : « Non précisé » ne tient pas sur un écran
 * étroit à côté des deux autres, et une puce qui passe à la ligne reste lisible là où un
 * segment tronqué ne l'est plus. La sélection est doublée d'une coche : la couleur seule ne
 * porte jamais l'information (§B7.2, accessibilité).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoixSexe(
    selection: Sexe,
    actif: Boolean,
    onChange: (Sexe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.edition_champ_sexe),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sexe.entries.forEach { sexe ->
                val choisi = sexe == selection
                FilterChip(
                    selected = choisi,
                    onClick = { onChange(sexe) },
                    label = { Text(libelleSexe(sexe)) },
                    enabled = actif,
                    leadingIcon = {
                        if (choisi) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.edition_champ_sexe_aide),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Dialogues ---------------------------------------------------------------

/**
 * Sélecteur de date Material 3.
 *
 * Les années proposées sont bornées par l'état (`anneeMinimum`..`anneeMaximum`), mais
 * **aucune date n'est rendue insélectionnable** : c'est `ValidationEnfant` qui refuse, et
 * l'écran qui explique pourquoi. Brider le sélecteur au passé rendrait le message
 * « La date de naissance ne peut pas être dans le futur » impossible à voir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelecteurDateNaissance(
    dateInitiale: LocalDate?,
    anneeMinimum: Int,
    anneeMaximum: Int,
    onDateChoisie: (LocalDate) -> Unit,
    onFermer: () -> Unit,
) {
    val etatSelecteur = rememberDatePickerState(
        initialSelectedDateMillis = dateInitiale?.let(::enMillisUtc),
        yearRange = anneeMinimum..anneeMaximum,
    )

    DatePickerDialog(
        onDismissRequest = onFermer,
        confirmButton = {
            TextButton(
                onClick = {
                    etatSelecteur.selectedDateMillis?.let { onDateChoisie(enDateLocale(it)) }
                },
                enabled = etatSelecteur.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.edition_action_valider))
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) {
                Text(stringResource(R.string.edition_action_annuler))
            }
        },
    ) {
        DatePicker(
            state = etatSelecteur,
            title = {
                Text(
                    text = stringResource(R.string.edition_selecteur_titre),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

/**
 * Confirmation de suppression (US-B1, scénario « suppression »).
 *
 * Le dialogue dit **ce qui disparaît avec l'enfant** : les vaccins déjà saisis. C'est une
 * donnée de santé qu'on efface, et elle n'existe nulle part ailleurs — l'application est
 * hors ligne, il n'y a ni corbeille ni sauvegarde automatique (§B8). D'où un texte explicite
 * sur le caractère définitif, sans dramatisation ni point d'exclamation (R7).
 */
@Composable
private fun DialogueSuppression(
    prenom: String?,
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        icon = {
            Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
        },
        title = {
            Text(
                if (prenom != null) {
                    stringResource(R.string.edition_suppression_titre, prenom)
                } else {
                    stringResource(R.string.edition_suppression_titre_sans_nom)
                },
            )
        },
        text = {
            Text(
                if (prenom != null) {
                    stringResource(R.string.edition_suppression_message, prenom)
                } else {
                    stringResource(R.string.edition_suppression_message_sans_nom)
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmer,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.edition_suppression_confirmer))
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) {
                Text(stringResource(R.string.edition_suppression_annuler))
            }
        },
    )
}

// --- Libellés ----------------------------------------------------------------

@Composable
private fun titreEcran(state: EditionEnfantUiState): String {
    val prenom = (state as? EditionEnfantUiState.Formulaire)?.prenomEnregistre
    return when (state.mode) {
        ModeEdition.CREATION -> stringResource(R.string.edition_titre_creation)
        ModeEdition.MODIFICATION ->
            if (prenom != null) {
                stringResource(R.string.edition_titre_modification, prenom)
            } else {
                stringResource(R.string.edition_titre_modification_sans_nom)
            }
    }
}

@Composable
private fun texteErreurPrenom(erreur: ErreurPrenom?): String? = when (erreur) {
    null -> null
    ErreurPrenom.Vide -> stringResource(R.string.edition_erreur_prenom_vide)
    is ErreurPrenom.TropLong ->
        stringResource(R.string.edition_erreur_prenom_trop_long, erreur.maximum)
}

@Composable
private fun texteErreurDate(erreur: ErreurDateNaissance?): String? = when (erreur) {
    null -> null
    ErreurDateNaissance.Absente -> stringResource(R.string.edition_erreur_date_absente)
    ErreurDateNaissance.DansLeFutur -> stringResource(R.string.edition_erreur_date_futur)
    is ErreurDateNaissance.TropAncienne ->
        stringResource(R.string.edition_erreur_date_trop_ancienne, erreur.limite.format(FORMAT_JOUR))
}

@Composable
private fun libelleSexe(sexe: Sexe): String = when (sexe) {
    Sexe.GARCON -> stringResource(R.string.edition_sexe_garcon)
    Sexe.FILLE -> stringResource(R.string.edition_sexe_fille)
    Sexe.NON_PRECISE -> stringResource(R.string.edition_sexe_non_precise)
}

@StringRes
private fun messageDe(echec: EchecEdition): Int = when (echec) {
    EchecEdition.ENREGISTREMENT -> R.string.edition_echec_enregistrement
    EchecEdition.SUPPRESSION -> R.string.edition_echec_suppression
}

// --- Conversions du sélecteur ------------------------------------------------

private const val MILLIS_PAR_JOUR: Long = 24L * 60L * 60L * 1000L

/**
 * Le sélecteur Material raisonne en millisecondes depuis l'époque, **à minuit UTC** : il
 * n'a pas de fuseau. La conversion passe donc par `ZoneOffset.UTC` et non par
 * `ZONE_MADAGASCAR` — appliquer ici le fuseau local décalerait la date d'un jour.
 */
private fun enMillisUtc(date: LocalDate): Long = date.toEpochDay() * MILLIS_PAR_JOUR

private fun enDateLocale(millisUtc: Long): LocalDate =
    Instant.ofEpochMilli(millisUtc).atZone(ZoneOffset.UTC).toLocalDate()

// --- Aperçus -----------------------------------------------------------------

private fun formulaire(
    mode: ModeEdition = ModeEdition.CREATION,
    prenomEnregistre: String? = null,
    prenom: String = "",
    dateNaissance: LocalDate? = null,
    sexe: Sexe = Sexe.NON_PRECISE,
    erreurPrenom: ErreurPrenom? = null,
    erreurDateNaissance: ErreurDateNaissance? = null,
    peutEnregistrer: Boolean = false,
    enCours: Boolean = false,
    confirmationSuppression: Boolean = false,
) = EditionEnfantUiState.Formulaire(
    mode = mode,
    prenomEnregistre = prenomEnregistre,
    prenom = prenom,
    dateNaissance = dateNaissance,
    sexe = sexe,
    erreurPrenom = erreurPrenom,
    erreurDateNaissance = erreurDateNaissance,
    peutEnregistrer = peutEnregistrer,
    enCours = enCours,
    selecteurDateOuvert = false,
    confirmationSuppression = confirmationSuppression,
    anneeMinimum = 2008,
    anneeMaximum = 2027,
    echec = null,
    sortie = null,
)

@Preview(showBackground = true, name = "Création — formulaire vierge")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuEditionCreation() {
    FahasalamanaTheme {
        EditionEnfantContenu(
            state = formulaire(),
            actions = ActionsEdition(),
            onRetour = {},
            onEnregistre = {},
            onSupprime = {},
        )
    }
}

@Preview(showBackground = true, name = "Création — date dans le futur")
@Composable
private fun ApercuEditionDateFuture() {
    FahasalamanaTheme {
        EditionEnfantContenu(
            state = formulaire(
                prenom = "Faly",
                dateNaissance = LocalDate.of(2027, 1, 1),
                sexe = Sexe.GARCON,
                erreurDateNaissance = ErreurDateNaissance.DansLeFutur,
            ),
            actions = ActionsEdition(),
            onRetour = {},
            onEnregistre = {},
            onSupprime = {},
        )
    }
}

@Preview(showBackground = true, name = "Modification")
@Composable
private fun ApercuEditionModification() {
    FahasalamanaTheme {
        EditionEnfantContenu(
            state = formulaire(
                mode = ModeEdition.MODIFICATION,
                prenomEnregistre = "Faly",
                prenom = "Faly",
                dateNaissance = LocalDate.of(2026, 1, 1),
                sexe = Sexe.GARCON,
                peutEnregistrer = true,
            ),
            actions = ActionsEdition(),
            onRetour = {},
            onEnregistre = {},
            onSupprime = {},
        )
    }
}

@Preview(showBackground = true, name = "Confirmation de suppression")
@Composable
private fun ApercuEditionSuppression() {
    FahasalamanaTheme {
        EditionEnfantContenu(
            state = formulaire(
                mode = ModeEdition.MODIFICATION,
                prenomEnregistre = "Faly",
                prenom = "Faly",
                dateNaissance = LocalDate.of(2026, 1, 1),
                sexe = Sexe.GARCON,
                peutEnregistrer = true,
                confirmationSuppression = true,
            ),
            actions = ActionsEdition(),
            onRetour = {},
            onEnregistre = {},
            onSupprime = {},
        )
    }
}

@Preview(showBackground = true, name = "Enfant introuvable")
@Composable
private fun ApercuEditionIntrouvable() {
    FahasalamanaTheme {
        EditionEnfantContenu(
            state = EditionEnfantUiState.Introuvable,
            actions = ActionsEdition(),
            onRetour = {},
            onEnregistre = {},
            onSupprime = {},
        )
    }
}
