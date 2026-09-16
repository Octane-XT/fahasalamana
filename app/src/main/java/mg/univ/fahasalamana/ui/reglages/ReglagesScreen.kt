package mg.univ.fahasalamana.ui.reglages

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran Réglages (tâches B15 et B18, CDC §B7.2).
 *
 * Trois blocs sont réels :
 *  - « Données de référence » : source, date et version du calendrier (US-B7), avec la
 *    mention de démonstration mise en évidence et non enfouie dans un paragraphe ;
 *  - « Confidentialité » : ce que l'application fait des données de santé (§B8, point 7) ;
 *  - « Sécurité » : le code de verrouillage (US-B10, B18).
 *
 * Le bloc « Carnet » n'existe que pour montrer la structure finale : ses entrées sont
 * visiblement inactives et annoncées comme telles à TalkBack, plutôt que branchées sur un
 * écran vide. Le texte de confidentialité décrit ce que le code fait aujourd'hui, et rien de
 * plus : il est lu en soutenance.
 *
 * **Le bloc « Sécurité » se lit avant d'être utilisé.** Trois phrases entourent
 * l'interrupteur : ce que le code déclenche, ce qu'il protège et ce qu'il ne protège pas, et
 * ce qui arrive si on l'oublie. Cette dernière est affichée **tant que le code n'est pas
 * activé**, c'est-à-dire au moment où la décision se prend, et non après.
 *
 * TODO(B10) : entrée « Rappels » (interrupteur de notifications, notification de test en
 * build debug), une fois NotificationHelper livré.
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2). */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/**
 * @param onOuvrirCodeVerrouillage navigation vers l'écran `Verrouillage`, qui décide seul
 *   s'il s'agit de créer ou de modifier le code (voir `VerrouillageViewModel`).
 */
@Composable
fun ReglagesScreen(
    onOuvrirCodeVerrouillage: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ReglagesViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ReglagesContenu(
        state = state,
        onOuvrirCodeVerrouillage = onOuvrirCodeVerrouillage,
        onDesactiverVerrouillage = vm::onDesactiverVerrouillage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReglagesContenu(
    state: ReglagesUiState,
    onOuvrirCodeVerrouillage: () -> Unit,
    onDesactiverVerrouillage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.reglages_titre)) }) },
    ) { interieur ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(interieur)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BlocDonneesReference(state.reference)
            BlocConfidentialite()
            BlocCarnet()
            BlocSecurite(
                verrouillageActif = state.verrouillageActif,
                onOuvrirCodeVerrouillage = onOuvrirCodeVerrouillage,
                onDesactiverVerrouillage = onDesactiverVerrouillage,
            )
            // Respiration en bas de page : la barre d'onglets est juste en dessous.
            Spacer(Modifier.height(8.dp))
        }
    }
}

// --- Bloc « Données de référence » (US-B7) -----------------------------------

@Composable
private fun BlocDonneesReference(etat: EtatReference) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_reference))

        when (etat) {
            EtatReference.Chargement -> EtatChargement(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
            )

            EtatReference.Erreur -> EtatErreur(
                message = stringResource(R.string.reglages_reference_erreur),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
            )

            is EtatReference.Pret -> CarteDonneesReference(etat.donnees)
        }
    }
}

@Composable
private fun CarteDonneesReference(donnees: DonneesReference) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reglages_calendrier_titre),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (donnees.versionCalendrier != null && donnees.calendrierPublieLe != null) {
                    stringResource(
                        R.string.reglages_calendrier_version,
                        donnees.versionCalendrier,
                        donnees.calendrierPublieLe.format(FORMAT_JOUR),
                    )
                } else {
                    stringResource(R.string.reglages_calendrier_absent)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MentionDemonstration()

            LigneInfo(
                libelle = stringResource(R.string.reglages_source_titre),
                valeur = donnees.sourceCalendrier
                    ?: stringResource(R.string.reglages_source_inconnue),
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.reglages_annuaire_titre),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = donnees.versionAnnuaire?.let {
                    stringResource(R.string.reglages_annuaire_version, it)
                } ?: stringResource(R.string.reglages_annuaire_absent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = donnees.derniereVerification?.let {
                    stringResource(R.string.reglages_derniere_verification, it.format(FORMAT_JOUR))
                } ?: stringResource(R.string.reglages_derniere_verification_jamais),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // TODO(B19) : activer ce bouton et appeler ReglagesViewModel.onVerifierMisesAJour().
            // Laissé visible mais désactivé : la place de la fonction est montrée, sans laisser
            // croire qu'elle marche déjà pendant la démonstration.
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reglages_action_verifier))
            }
            Text(
                text = stringResource(R.string.reglages_action_verifier_indisponible),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Mention de démonstration (CDC §B1, principe de responsabilité).
 *
 * Encart coloré au milieu de la carte, avant la source : impossible à manquer quand on
 * vient vérifier d'où sortent les dates affichées dans les fiches.
 */
@Composable
private fun MentionDemonstration() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                // Décoratif : le texte à côté porte toute l'information.
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.reglages_mention_demonstration),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.reglages_mention_demonstration_detail),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// --- Bloc « Confidentialité » (CDC §B8, point 7) -----------------------------

@Composable
private fun BlocConfidentialite() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_confidentialite))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    R.string.reglages_confidentialite_donnees_locales,
                    R.string.reglages_confidentialite_aucun_envoi,
                    R.string.reglages_confidentialite_sauvegarde,
                    R.string.reglages_confidentialite_export,
                ).forEach { texte ->
                    Text(
                        text = stringResource(texte),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// --- Bloc « Carnet » : encore inactif ----------------------------------------

@Composable
private fun BlocCarnet() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_carnet))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // TODO(B16) : export du carnet par ACTION_CREATE_DOCUMENT (SAF).
                LigneReservee(
                    icone = Icons.Outlined.FileUpload,
                    titre = stringResource(R.string.reglages_export),
                )
                // TODO(B17) : import par ACTION_OPEN_DOCUMENT, fusion par identifiant.
                LigneReservee(
                    icone = Icons.Outlined.FileDownload,
                    titre = stringResource(R.string.reglages_import),
                )
            }
        }
    }
}

// --- Bloc « Sécurité » (US-B10, B18) -----------------------------------------

/**
 * Code de verrouillage : l'interrupteur, ce qu'il fait, et ce qu'il ne fait pas.
 *
 * L'ordre des textes n'est pas décoratif. De haut en bas : l'interrupteur et son état, puis
 * le déclenchement (ouverture et deux minutes d'absence), puis la portée réelle du verrou,
 * puis — **uniquement quand le code n'est pas encore activé** — l'avertissement sur le code
 * oublié. Celui-là est lu avant de toucher l'interrupteur, ce qui est le seul moment où il
 * sert à quelque chose.
 */
@Composable
private fun BlocSecurite(
    verrouillageActif: Boolean,
    onOuvrirCodeVerrouillage: () -> Unit,
    onDesactiverVerrouillage: () -> Unit,
) {
    // État purement visuel, donc dans l'écran et non dans le ViewModel (même convention que
    // la confirmation de suppression de l'écran Saisie).
    var confirmationDesactivation by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_securite))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {

                // `toggleable` sur toute la ligne plutôt qu'un `Switch` isolé : TalkBack
                // annonce alors « Code de verrouillage, désactivé, interrupteur » d'un seul
                // tenant, et la cible tactile fait la largeur de l'écran.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = verrouillageActif,
                            role = Role.Switch,
                            onValueChange = { demande ->
                                if (demande) {
                                    onOuvrirCodeVerrouillage()
                                } else {
                                    confirmationDesactivation = true
                                }
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reglages_code_verrouillage),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(
                                if (verrouillageActif) {
                                    R.string.verrouillage_reglages_etat_actif
                                } else {
                                    R.string.verrouillage_reglages_etat_inactif
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // `null` : c'est la ligne entière qui bascule, le commutateur ne doit pas
                    // être un second point d'entrée pour TalkBack.
                    Switch(checked = verrouillageActif, onCheckedChange = null)
                }

                if (verrouillageActif) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOuvrirCodeVerrouillage)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Password,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.verrouillage_reglages_modifier),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.verrouillage_reglages_fonctionnement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // La phrase qui refuse de promettre un chiffrement. Elle reste affichée
                    // que le code soit actif ou non : c'est une limite du produit, pas un
                    // message d'installation.
                    Text(
                        text = stringResource(R.string.verrouillage_reglages_portee),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!verrouillageActif) {
                    AvertissementAvantActivation()
                }
            }
        }
    }

    if (confirmationDesactivation) {
        ConfirmationDesactivation(
            onConfirmer = {
                confirmationDesactivation = false
                onDesactiverVerrouillage()
            },
            onAnnuler = { confirmationDesactivation = false },
        )
    }
}

/** Le code oublié n'a aucun recours ; c'est dit avant l'activation, pas après. */
@Composable
private fun AvertissementAvantActivation() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.verrouillage_oubli_titre),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.verrouillage_oubli_detail),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Confirmation avant de retirer le code : texte factuel, qui décrit l'effet de l'action. */
@Composable
private fun ConfirmationDesactivation(
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(stringResource(R.string.verrouillage_desactiver_titre)) },
        text = { Text(stringResource(R.string.verrouillage_desactiver_detail)) },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(stringResource(R.string.verrouillage_desactiver_confirmer))
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) {
                Text(stringResource(R.string.verrouillage_annuler))
            }
        },
    )
}

// --- Briques communes --------------------------------------------------------

@Composable
private fun TitreSection(texte: String) {
    Text(
        text = texte,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun LigneInfo(libelle: String, valeur: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valeur,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Entrée de réglage dont la fonction n'est pas encore écrite.
 *
 * Ni cliquable, ni trompeuse : couleur atténuée, sous-titre explicite, et `disabled()` pour
 * que TalkBack l'annonce comme indisponible au lieu de la présenter comme un bouton.
 */
@Composable
private fun LigneReservee(icone: ImageVector, titre: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { disabled() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = titre,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reglages_a_venir),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Aperçus -----------------------------------------------------------------

/** Les valeurs du `calendrier.json` embarqué (§B5.1), pour voir le vrai texte de source. */
private val ReferenceDApercu = DonneesReference(
    sourceCalendrier = "Calendrier de démonstration — projet universitaire. " +
        "À valider auprès du Ministère de la Santé Publique.",
    calendrierPublieLe = LocalDate.of(2026, 9, 14),
    versionCalendrier = 3,
    versionAnnuaire = 2,
    derniereVerification = null,
)

@Preview(showBackground = true, heightDp = 1800)
@Preview(showBackground = true, heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuReglagesPret() {
    FahasalamanaTheme {
        ReglagesContenu(
            state = ReglagesUiState(reference = EtatReference.Pret(ReferenceDApercu)),
            onOuvrirCodeVerrouillage = {},
            onDesactiverVerrouillage = {},
        )
    }
}

/** Le bloc « Sécurité » une fois le code posé : plus d'avertissement, et « Modifier le code ». */
@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun ApercuReglagesVerrouillageActif() {
    FahasalamanaTheme {
        ReglagesContenu(
            state = ReglagesUiState(
                reference = EtatReference.Pret(
                    ReferenceDApercu.copy(
                        versionAnnuaire = null,
                        derniereVerification = LocalDate.of(2026, 9, 15),
                    ),
                ),
                verrouillageActif = true,
            ),
            onOuvrirCodeVerrouillage = {},
            onDesactiverVerrouillage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuReglagesChargement() {
    FahasalamanaTheme {
        ReglagesContenu(
            state = ReglagesUiState(reference = EtatReference.Chargement),
            onOuvrirCodeVerrouillage = {},
            onDesactiverVerrouillage = {},
        )
    }
}

/** Calendrier illisible : le bloc « Sécurité » reste utilisable, c'est tout l'intérêt. */
@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ApercuReglagesErreur() {
    FahasalamanaTheme {
        ReglagesContenu(
            state = ReglagesUiState(reference = EtatReference.Erreur, verrouillageActif = true),
            onOuvrirCodeVerrouillage = {},
            onDesactiverVerrouillage = {},
        )
    }
}
