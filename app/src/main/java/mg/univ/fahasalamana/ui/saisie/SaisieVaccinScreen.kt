package mg.univ.fahasalamana.ui.saisie

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.domain.AvertissementSaisie
import mg.univ.fahasalamana.domain.MotifRefus
import mg.univ.fahasalamana.domain.ResultatSaisie
import mg.univ.fahasalamana.domain.VaccinReference
import mg.univ.fahasalamana.domain.validerSaisie
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.components.EtiquettesTest
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran Saisie d'un vaccin (tâche B09, CDC §B7.2, US-B3 et US-B4).
 *
 * Le même écran sert à enregistrer une dose, à corriger une saisie et à la supprimer : il
 * s'ouvre sur ce qui existe déjà en base pour le couple (enfant, vaccin) de la route.
 *
 * Trois principes tenus ligne à ligne :
 *
 * 1. **Un seul cas bloque : une date impossible.** La règle R5 vit dans
 *    `domain/ValidationSaisie.kt` ; l'écran ne fait qu'en afficher le verdict. Hors fenêtre
 *    de tolérance, la saisie passe : le bouton « Enregistrer » reste actif et un
 *    avertissement neutre explique l'écart constaté.
 * 2. **Règle R7, ton factuel.** Une mère qui fait vacciner son enfant avec six mois de
 *    retard doit pouvoir l'enregistrer sans que l'application s'y oppose ni lui fasse la
 *    leçon. Aucun message de cet écran ne dit ce qui aurait dû être fait.
 * 3. **L'en-tête rappelle ce que l'on enregistre** : quelle dose, pour quel enfant, prévue
 *    quand. C'est la seule protection contre une saisie faite sur la mauvaise ligne.
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2). */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

@Composable
fun SaisieVaccinScreen(
    onRetour: () -> Unit,
    onTermine: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SaisieVaccinViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Enregistrement ou suppression réussis : l'écran se referme sur la fiche, qui affiche
    // déjà le nouveau statut — le Flow Room a réémis sans qu'on ait rien à rafraîchir.
    val termine = (state as? SaisieVaccinUiState.Pret)?.termine == true
    LaunchedEffect(termine) {
        if (termine) onTermine()
    }

    SaisieVaccinContenu(
        state = state,
        onRetour = onRetour,
        onDateChoisie = vm::onDateChoisie,
        onLieuChange = vm::onLieuChange,
        onLotChange = vm::onLotChange,
        onEnregistrer = vm::onEnregistrer,
        onSupprimer = vm::onSupprimer,
        modifier = modifier,
    )
}

/** Sans ViewModel : prévisualisable, et pilotable tel quel par les tests UI de B21. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaisieVaccinContenu(
    state: SaisieVaccinUiState,
    onRetour: () -> Unit,
    onDateChoisie: (LocalDate) -> Unit,
    onLieuChange: (String) -> Unit,
    onLotChange: (String) -> Unit,
    onEnregistrer: () -> Unit,
    onSupprimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Le nom de la dose n'apparaît qu'une fois le calendrier lu : ni le
                        // chargement ni l'état introuvable n'affichent un titre vide.
                        text = if (state is SaisieVaccinUiState.Pret) {
                            stringResource(
                                R.string.saisie_vaccin_titre,
                                state.vaccin.nom,
                                state.vaccin.dose,
                            )
                        } else {
                            stringResource(R.string.saisie_titre)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Titre d'écran : en-tête pour la navigation par titres de TalkBack.
                        modifier = Modifier.semantics { heading() },
                    )
                },
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
    ) { interieur ->
        when (state) {
            SaisieVaccinUiState.Chargement -> EtatChargement(
                modifier = Modifier.padding(interieur),
            )

            SaisieVaccinUiState.Erreur -> EtatErreur(
                message = stringResource(R.string.saisie_erreur),
                modifier = Modifier.padding(interieur),
            )

            // Enfant supprimé pendant la saisie, ou dose retirée du calendrier par une mise
            // à jour : il n'y a plus rien à enregistrer, on l'explique plutôt que de refermer
            // l'écran sous les doigts de l'utilisateur.
            SaisieVaccinUiState.Introuvable -> EtatVide(
                titre = stringResource(R.string.saisie_introuvable_titre),
                description = stringResource(R.string.saisie_introuvable_detail),
                icone = Icons.Outlined.EventNote,
                libelleAction = stringResource(R.string.saisie_introuvable_action),
                onAction = onRetour,
                modifier = Modifier.padding(interieur),
            )

            is SaisieVaccinUiState.Pret -> Formulaire(
                state = state,
                onDateChoisie = onDateChoisie,
                onLieuChange = onLieuChange,
                onLotChange = onLotChange,
                onEnregistrer = onEnregistrer,
                onSupprimer = onSupprimer,
                modifier = Modifier.padding(interieur),
            )
        }
    }
}

// --- Formulaire --------------------------------------------------------------

@Composable
private fun Formulaire(
    state: SaisieVaccinUiState.Pret,
    onDateChoisie: (LocalDate) -> Unit,
    onLieuChange: (String) -> Unit,
    onLotChange: (String) -> Unit,
    onEnregistrer: () -> Unit,
    onSupprimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deux fenêtres, deux états purement visuels : ils vivent dans l'écran (`rememberSaveable`
    // pour survivre à la rotation) et non dans le ViewModel, qui ne porte que la saisie.
    var dialogueDate by rememberSaveable { mutableStateOf(false) }
    var confirmationSuppression by rememberSaveable { mutableStateOf(false) }

    val refus = (state.validation as? ResultatSaisie.Refusee)?.motif
    val avertissement = (state.validation as? ResultatSaisie.Acceptee)?.avertissement

    // Résolu ici, hors du `Modifier` : `stringResource` ne s'appelle que dans une composition.
    val texteRefus = refus?.let { messageRefus(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EnteteSaisie(state)

        OutlinedTextField(
            value = state.date.format(FORMAT_JOUR),
            // Champ en lecture seule : la date ne se tape pas, elle se choisit dans le
            // calendrier. Cela supprime d'un coup toutes les saisies « 32/13/2026 ».
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.saisie_date_label)) },
            isError = texteRefus != null,
            supportingText = {
                if (texteRefus != null) Text(texteRefus)
            },
            trailingIcon = {
                IconButton(
                    onClick = { dialogueDate = true },
                    modifier = Modifier.testTag(EtiquettesTest.SAISIE_CHOISIR_DATE),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(R.string.saisie_date_action),
                    )
                }
            },
            // `error()` : TalkBack annonce « saisie non valide » et lit le motif du refus,
            // au lieu de laisser la seule couleur rouge porter l'information (B23).
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (texteRefus != null) error(texteRefus) },
        )

        // Hors fenêtre de tolérance : informatif, jamais bloquant (R5). Volontairement dans
        // un bandeau neutre et non dans la couleur d'erreur du champ — ce n'est pas une faute.
        if (avertissement != null) {
            BandeauAvertissement(messageAvertissement(avertissement))
        }

        OutlinedTextField(
            value = state.lieu,
            onValueChange = onLieuChange,
            singleLine = true,
            label = { Text(stringResource(R.string.saisie_lieu_label)) },
            placeholder = { Text(stringResource(R.string.saisie_lieu_exemple)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.lot,
            onValueChange = onLotChange,
            singleLine = true,
            label = { Text(stringResource(R.string.saisie_lot_label)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.echec) {
            Text(
                text = stringResource(R.string.saisie_echec),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                // L'échec apparaît après un appui sur « Enregistrer », loin du doigt et sans
                // que le focus bouge : sans région active, un utilisateur de TalkBack ne
                // saurait pas que rien n'a été enregistré. `Assertive` parce que l'action
                // demandée n'a pas eu lieu (B23).
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        Button(
            onClick = onEnregistrer,
            enabled = state.peutEnregistrer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EtiquettesTest.SAISIE_ENREGISTRER),
        ) {
            Text(stringResource(R.string.saisie_action_enregistrer))
        }

        // Suppression proposée seulement en correction : tant qu'aucune dose n'est
        // enregistrée pour ce couple (enfant, vaccin), il n'y a rien à supprimer (US-B4).
        if (state.correction) {
            OutlinedButton(
                onClick = { confirmationSuppression = true },
                enabled = !state.enregistrementEnCours && !state.termine,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    // Décoratif : le libellé du bouton porte déjà l'action.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.saisie_action_supprimer))
            }
        }
    }

    if (dialogueDate) {
        DialogueDate(
            date = state.date,
            dateNaissance = state.dateNaissance,
            aujourdHui = state.aujourdHui,
            onDateChoisie = onDateChoisie,
            onFermer = { dialogueDate = false },
        )
    }

    if (confirmationSuppression) {
        ConfirmationSuppression(
            onConfirmer = {
                confirmationSuppression = false
                onSupprimer()
            },
            onAnnuler = { confirmationSuppression = false },
        )
    }
}

/**
 * En-tête : quelle dose, pour quel enfant, prévue quand (wireframe §B7.2).
 *
 * En correction, il rappelle en plus la saisie déjà enregistrée : c'est ce qui distingue,
 * à l'ouverture de l'écran, une première saisie (US-B3) d'une correction (US-B4).
 *
 * Bloc unique pour TalkBack (B23) : c'est une seule information — ce que l'on s'apprête à
 * enregistrer —, et c'est la seule protection contre une saisie faite sur la mauvaise ligne.
 * La découper en quatre arrêts revenait à la rendre facile à survoler.
 */
@Composable
private fun EnteteSaisie(state: SaisieVaccinUiState.Pret) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.saisie_vaccin_titre,
                    state.vaccin.nom,
                    state.vaccin.dose,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.prevuLe?.let { prevu ->
                    stringResource(R.string.saisie_entete_prevu, state.prenom, prevu.format(FORMAT_JOUR))
                }
                // Chaîne de dépendances cassée : aucune date n'est calculable, on n'en invente pas.
                    ?: stringResource(R.string.saisie_entete_sans_date, state.prenom),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.vaccin.description.isNotBlank()) {
                Text(
                    text = state.vaccin.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.dateSaisieExistante?.let { date ->
                Text(
                    text = stringResource(R.string.saisie_entete_deja_saisi, date.format(FORMAT_JOUR)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Avertissement de fenêtre : neutre, informatif, jamais bloquant (R5 et R7).
 *
 * Région active `Polite` (B23) : le bandeau apparaît et change quand l'utilisateur choisit
 * une autre date, sans que le focus bouge — il faut donc qu'il soit annoncé de lui-même.
 * `Polite` et non `Assertive` : c'est un constat, il attend la fin de la phrase en cours, et
 * il ne doit surtout pas prendre le ton d'une alerte (R7).
 */
@Composable
private fun BandeauAvertissement(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                // Décoratif : le texte qui suit porte toute l'information.
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Sélecteur de date Material 3 (§B7.2 : `[ 28 / 09 / 2026 📅 ]`).
 *
 * Aucune date n'est rendue insélectionnable : c'est `validerSaisie` qui tranche, et le
 * message de refus explique **pourquoi** une date ne convient pas. Un calendrier qui grise
 * silencieusement la moitié des jours laisserait l'utilisateur sans explication.
 *
 * Les années proposées vont de la naissance à l'année courante : une dose ne peut de toute
 * façon pas être reçue en dehors de cet intervalle (R5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogueDate(
    date: LocalDate,
    dateNaissance: LocalDate,
    aujourdHui: LocalDate,
    onDateChoisie: (LocalDate) -> Unit,
    onFermer: () -> Unit,
) {
    val etatCalendrier = rememberDatePickerState(
        initialSelectedDateMillis = date.versMillisUtc(),
        yearRange = IntRange(dateNaissance.year, maxOf(aujourdHui.year, dateNaissance.year)),
    )

    DatePickerDialog(
        onDismissRequest = onFermer,
        confirmButton = {
            TextButton(
                onClick = {
                    etatCalendrier.selectedDateMillis?.let { millis ->
                        onDateChoisie(millis.versDateUtc())
                    }
                    onFermer()
                },
                enabled = etatCalendrier.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.saisie_date_valider))
            }
        },
        dismissButton = {
            TextButton(onClick = onFermer) {
                Text(stringResource(R.string.saisie_annuler))
            }
        },
    ) {
        DatePicker(state = etatCalendrier)
    }
}

/** Confirmation avant suppression (US-B4) : texte factuel, qui décrit l'effet de l'action. */
@Composable
private fun ConfirmationSuppression(
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(stringResource(R.string.saisie_suppression_titre)) },
        text = { Text(stringResource(R.string.saisie_suppression_detail)) },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(stringResource(R.string.saisie_suppression_confirmer))
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) {
                Text(stringResource(R.string.saisie_annuler))
            }
        },
    )
}

// --- Messages de la règle R5 -------------------------------------------------

/**
 * Les deux seuls refus possibles. `when` sans `else` : un troisième motif ajouté à
 * [MotifRefus] casse la compilation ici, au lieu de laisser un champ en erreur sans message.
 */
@Composable
private fun messageRefus(motif: MotifRefus): String = when (motif) {
    is MotifRefus.AvantLaNaissance -> stringResource(
        R.string.saisie_refus_avant_naissance,
        motif.dateNaissance.format(FORMAT_JOUR),
    )

    is MotifRefus.DansLeFutur -> stringResource(
        R.string.saisie_refus_futur,
        motif.aujourdHui.format(FORMAT_JOUR),
    )
}

/** Écart constaté à la fenêtre du calendrier. Les deux textes disent que la saisie est conservée. */
@Composable
private fun messageAvertissement(avertissement: AvertissementSaisie): String = when (avertissement) {
    is AvertissementSaisie.AvantLaDatePrevue -> pluralStringResource(
        R.plurals.saisie_avertissement_avance,
        avertissement.joursAvance.toInt(),
        avertissement.joursAvance,
        avertissement.prevuLe.format(FORMAT_JOUR),
    )

    is AvertissementSaisie.ApresLaFenetre -> pluralStringResource(
        R.plurals.saisie_avertissement_retard,
        avertissement.joursApres.toInt(),
        avertissement.joursApres,
        avertissement.prevuLe.format(FORMAT_JOUR),
    )
}

// --- Conversions du sélecteur de date ----------------------------------------

/*
 * `DatePickerState` ne connaît que des millisecondes UTC à minuit : c'est son contrat, pas
 * un fuseau d'affichage. Les deux conversions se font donc en UTC et nulle part ailleurs —
 * passer par le fuseau du téléphone ferait glisser la date d'un jour à chaque manipulation.
 */

private fun LocalDate.versMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.versDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

// --- Aperçus -----------------------------------------------------------------

/*
 * Les aperçus font tourner la vraie règle R5 (`validerSaisie`) : ce que montre Android Studio
 * est ce que le téléphone affichera, messages de refus et avertissements compris.
 */

private val RR1 = VaccinReference(
    id = "rr1",
    nom = "Rougeole-Rubéole",
    dose = "1re dose",
    ordre = 7,
    ageJours = 270,
    dependDe = null,
    toleranceJours = 30,
    description = "9 mois",
)

private fun etatDApercu(
    date: LocalDate,
    prevuLe: LocalDate? = LocalDate.of(2026, 9, 28),
    aujourdHui: LocalDate = LocalDate.of(2026, 10, 5),
    dateSaisieExistante: LocalDate? = null,
    lieu: String = "",
    lot: String = "",
): SaisieVaccinUiState.Pret {
    val naissance = LocalDate.of(2026, 1, 1)
    return SaisieVaccinUiState.Pret(
        prenom = "Faly",
        dateNaissance = naissance,
        vaccin = RR1,
        prevuLe = prevuLe,
        aujourdHui = aujourdHui,
        date = date,
        lieu = lieu,
        lot = lot,
        dateSaisieExistante = dateSaisieExistante,
        validation = validerSaisie(
            dateSaisie = date,
            dateNaissance = naissance,
            aujourdHui = aujourdHui,
            prevuLe = prevuLe,
            toleranceJours = RR1.toleranceJours,
        ),
    )
}

/** Première saisie, date dans la fenêtre : aucun message, bouton actif. */
@Preview(showBackground = true, heightDp = 900)
@Preview(showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuSaisieDansLaFenetre() {
    FahasalamanaTheme {
        SaisieVaccinContenu(
            state = etatDApercu(date = LocalDate.of(2026, 10, 2)),
            onRetour = {},
            onDateChoisie = {},
            onLieuChange = {},
            onLotChange = {},
            onEnregistrer = {},
            onSupprimer = {},
        )
    }
}

/**
 * Le cas qui porte la règle R5 : six mois de retard, avertissement affiché, **bouton actif**.
 * Si cet aperçu montrait un jour un bouton grisé, la règle serait cassée.
 */
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ApercuSaisieTresEnRetard() {
    FahasalamanaTheme {
        SaisieVaccinContenu(
            state = etatDApercu(
                date = LocalDate.of(2027, 3, 28),
                aujourdHui = LocalDate.of(2027, 4, 2),
                lieu = "CSB2 Ankirihiry",
            ),
            onRetour = {},
            onDateChoisie = {},
            onLieuChange = {},
            onLotChange = {},
            onEnregistrer = {},
            onSupprimer = {},
        )
    }
}

/** Correction d'une saisie existante : en-tête rappelant la dose enregistrée, bouton de suppression. */
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ApercuCorrection() {
    FahasalamanaTheme {
        SaisieVaccinContenu(
            state = etatDApercu(
                date = LocalDate.of(2026, 9, 30),
                dateSaisieExistante = LocalDate.of(2026, 9, 30),
                lieu = "CSB2 Ankirihiry",
                lot = "RR-2026-114",
            ),
            onRetour = {},
            onDateChoisie = {},
            onLieuChange = {},
            onLotChange = {},
            onEnregistrer = {},
            onSupprimer = {},
        )
    }
}

/** Date refusée (postérieure à aujourd'hui) : champ en erreur, bouton inactif. */
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ApercuDateRefusee() {
    FahasalamanaTheme {
        SaisieVaccinContenu(
            state = etatDApercu(date = LocalDate.of(2026, 10, 12)),
            onRetour = {},
            onDateChoisie = {},
            onLieuChange = {},
            onLotChange = {},
            onEnregistrer = {},
            onSupprimer = {},
        )
    }
}
