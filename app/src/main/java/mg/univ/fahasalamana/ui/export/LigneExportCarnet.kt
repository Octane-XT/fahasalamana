package mg.univ.fahasalamana.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.platform.MIME_CARNET
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel

/*
 * Entrée « Exporter le carnet » du bloc Carnet des Réglages (US-B9, scénario 1).
 *
 * Ce n'est pas un écran : c'est une ligne posée dans une carte de l'écran Réglages, avec son
 * propre ViewModel. Elle est écrite comme un bloc autonome pour pouvoir être ajoutée à
 * `ui/reglages/ReglagesScreen.kt` en une ligne, sans toucher ni à l'état ni au ViewModel de
 * cet écran — Dev B y écrit le verrouillage par code (B18) en même temps.
 *
 * Deux choses sont volontairement **hors** du composable de contenu : le contrat
 * `CreateDocument` et le ViewModel. C'est la découpe habituelle du projet (un composable
 * branché, un composable pur), et elle a ici une raison de plus : un
 * `rememberLauncherForActivityResult` n'a pas de registre à qui s'adresser dans un aperçu
 * Android Studio. Les quatre aperçus du bas ne rendent que le contenu.
 */

/**
 * L'entrée « Exporter le carnet », branchée sur son ViewModel et sur le sélecteur de
 * documents du système.
 */
@Composable
fun LigneExportCarnet(
    modifier: Modifier = Modifier,
    vm: ExportCarnetViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    /*
     * `ACTION_CREATE_DOCUMENT` du §B8 : l'utilisateur choisit l'emplacement, l'application
     * n'obtient l'écriture que sur ce seul document et ne demande aucune permission de
     * stockage. Un retour `null` signifie que le sélecteur a été fermé sans rien choisir :
     * il n'y a alors rien à faire, et surtout rien à annoncer.
     */
    val selecteurDeDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_CARNET),
    ) { destination -> destination?.let(vm::onEmplacementChoisi) }

    LaunchedEffect(state.emplacementADemander) {
        val nomPropose = state.emplacementADemander ?: return@LaunchedEffect
        selecteurDeDocument.launch(nomPropose)
        // Consommé tout de suite : sans cela, une rotation rouvrirait le sélecteur.
        vm.onEmplacementDemande()
    }

    LigneExportCarnetContenu(
        state = state,
        onExportDemande = vm::onExportDemande,
        onResultatFerme = vm::onResultatFerme,
        modifier = modifier,
    )
}

@Composable
private fun LigneExportCarnetContenu(
    state: ExportCarnetUiState,
    onExportDemande: () -> Unit,
    onResultatFerme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !state.enCours,
                    role = Role.Button,
                    onClick = onExportDemande,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.FileUpload,
                // Décoratif : le libellé à côté porte toute l'information.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    // Libellé déjà écrit par B15 pour la place réservée : c'est le mot à mot
                    // du scénario 1 de US-B9, il n'y a pas lieu d'en créer un second.
                    text = stringResource(R.string.reglages_export),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // L'avancement passe par le texte et pas seulement par l'animation :
                    // un indicateur qui tourne ne s'annonce pas à TalkBack.
                    text = if (state.enCours) {
                        stringResource(R.string.export_en_cours)
                    } else {
                        stringResource(R.string.export_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.enCours) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        state.resultat?.let { resultat ->
            PanneauResultat(
                resultat = resultat,
                onFermer = onResultatFerme,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * Message affiché après un export.
 *
 * **Pas un `Snackbar`**, et c'est délibéré : le message de réussite porte la phrase de
 * confidentialité exigée par US-B9 (« un message confirme que le fichier ne contient aucune
 * donnée envoyée ailleurs »), qui est trop importante pour disparaître au bout de quatre
 * secondes. Un `Snackbar` aurait de plus demandé un `SnackbarHost` dans le `Scaffold` de
 * l'écran Réglages, donc une modification du fichier de Dev B.
 *
 * `liveRegion` : TalkBack annonce le message dès qu'il apparaît, sans que l'utilisateur ait
 * à repartir en exploration.
 */
@Composable
private fun PanneauResultat(
    resultat: ResultatExport,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val couleurFond = when (resultat) {
        is ResultatExport.Reussi -> MaterialTheme.colorScheme.secondaryContainer
        ResultatExport.CarnetVide -> MaterialTheme.colorScheme.surfaceVariant
        ResultatExport.Echec -> MaterialTheme.colorScheme.errorContainer
    }
    val couleurTexte = when (resultat) {
        is ResultatExport.Reussi -> MaterialTheme.colorScheme.onSecondaryContainer
        ResultatExport.CarnetVide -> MaterialTheme.colorScheme.onSurfaceVariant
        ResultatExport.Echec -> MaterialTheme.colorScheme.onErrorContainer
    }
    val titre = when (resultat) {
        is ResultatExport.Reussi -> stringResource(R.string.export_reussi_titre)
        ResultatExport.CarnetVide -> stringResource(R.string.export_vide_titre)
        ResultatExport.Echec -> stringResource(R.string.export_echec_titre)
    }
    val detail = when (resultat) {
        is ResultatExport.Reussi -> stringResource(
            R.string.export_reussi_detail,
            pluralStringResource(R.plurals.export_nb_enfants, resultat.nbEnfants, resultat.nbEnfants),
            pluralStringResource(R.plurals.export_nb_doses, resultat.nbDoses, resultat.nbDoses),
        )

        ResultatExport.CarnetVide -> stringResource(R.string.export_vide_detail)
        ResultatExport.Echec -> stringResource(R.string.export_echec_detail)
    }

    Surface(
        color = couleurFond,
        contentColor = couleurTexte,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = titre, style = MaterialTheme.typography.titleSmall)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)

            // La phrase du scénario 1 de US-B9 : le fichier est là où le parent l'a mis, et
            // nulle part ailleurs. Elle n'apparaît qu'après un export réussi — c'est le seul
            // moment où des données de santé viennent réellement de sortir de l'application.
            if (resultat is ResultatExport.Reussi) {
                Text(
                    text = stringResource(R.string.export_reussi_confidentialite),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(
                onClick = onFermer,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.export_fermer))
            }
        }
    }
}

// --- Aperçus -----------------------------------------------------------------

/** La carte du bloc « Carnet » des Réglages, pour voir la ligne dans son contenant réel. */
@Composable
private fun ApercuDansCarte(state: ExportCarnetUiState) {
    FahasalamanaTheme {
        OutlinedCard(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                LigneExportCarnetContenu(
                    state = state,
                    onExportDemande = {},
                    onResultatFerme = {},
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuExportAuRepos() {
    ApercuDansCarte(ExportCarnetUiState())
}

@Preview(showBackground = true)
@Composable
private fun ApercuExportEnCours() {
    ApercuDansCarte(ExportCarnetUiState(enCours = true))
}

@Preview(showBackground = true)
@Composable
private fun ApercuExportReussi() {
    ApercuDansCarte(
        ExportCarnetUiState(resultat = ResultatExport.Reussi(nbEnfants = 2, nbDoses = 11)),
    )
}

@Preview(showBackground = true)
@Composable
private fun ApercuExportEchec() {
    ApercuDansCarte(ExportCarnetUiState(resultat = ResultatExport.Echec))
}

@Preview(showBackground = true)
@Composable
private fun ApercuExportCarnetVide() {
    ApercuDansCarte(ExportCarnetUiState(resultat = ResultatExport.CarnetVide))
}
