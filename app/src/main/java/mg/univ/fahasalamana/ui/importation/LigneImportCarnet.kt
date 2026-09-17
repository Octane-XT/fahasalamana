package mg.univ.fahasalamana.ui.importation

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
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import mg.univ.fahasalamana.domain.ResultatImport
import mg.univ.fahasalamana.platform.DemandeNotificationsRappels
import mg.univ.fahasalamana.platform.MIMES_CARNET_IMPORT
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel

/*
 * Entrée « Importer un carnet » du bloc Carnet des Réglages (US-B9, scénario 2).
 *
 * Bloc autonome, comme l'export : une ligne à poser dans `ui/reglages/ReglagesScreen.kt`,
 * sans toucher ni à l'état ni au ViewModel de cet écran, sur lequel Dev B travaille (B18).
 *
 * Le contrat `OpenDocument` et le ViewModel restent **hors** du composable de contenu : un
 * `rememberLauncherForActivityResult` n'a pas de registre à qui s'adresser dans un aperçu
 * Android Studio, et les aperçus du bas ne rendent que le contenu.
 */

/**
 * L'entrée « Importer un carnet », branchée sur son ViewModel et sur le sélecteur de
 * documents du système.
 */
@Composable
fun LigneImportCarnet(
    modifier: Modifier = Modifier,
    vm: ImportCarnetViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    /*
     * `ACTION_OPEN_DOCUMENT` du §B8 : l'utilisateur choisit le fichier, l'application n'obtient
     * la lecture que de ce seul document et ne demande aucune permission de stockage. Un
     * retour `null` signifie que le sélecteur a été fermé sans rien choisir : il n'y a alors
     * rien à faire, et surtout rien à annoncer.
     */
    val selecteurDeDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { source -> source?.let(vm::onDocumentChoisi) }

    /*
     * Le §B8 place la demande de `POST_NOTIFICATIONS` « au premier ajout d'enfant ». Un
     * import **est** un premier ajout d'enfants sur le téléphone qui reçoit : sans cette
     * demande, `replanifierTout()` programmerait des rappels qu'Android 13+ n'afficherait
     * jamais, et le scénario 2 de US-B9 serait tenu à moitié.
     *
     * Le composable de B10 gère lui-même les cas « déjà accordée », « version d'Android
     * antérieure » et « explication déjà vue » : il n'affiche rien et rappelle `onTermine`.
     * Il n'est déclenché que si l'import a **réellement ajouté** des enfants — une fusion
     * qui n'a rien changé n'a aucune raison de poser une question.
     */
    var demandeNotifications by rememberSaveable { mutableStateOf(false) }

    /*
     * Clé sur le **rapport de fusion** et non sur l'issue entière : l'issue change aussi
     * quand seul l'état des rappels change (un second essai de replanification), et
     * reposer la question des notifications à ce moment-là n'aurait aucun sens. Le rapport,
     * lui, repasse par `null` au début de chaque import — deux imports de suite relancent
     * donc bien l'effet, comme avant.
     */
    val rapportFusion = (state.issue as? IssueImport.Reussi)?.rapport

    LaunchedEffect(rapportFusion) {
        if (rapportFusion != null && rapportFusion.enfantsAjoutes > 0) {
            demandeNotifications = true
        }
    }

    DemandeNotificationsRappels(
        declenchee = demandeNotifications,
        onTermine = { demandeNotifications = false },
    )

    LigneImportCarnetContenu(
        state = state,
        onImportDemande = { selecteurDeDocument.launch(MIMES_CARNET_IMPORT) },
        onIssueFermee = vm::onIssueFermee,
        onReessayerRappels = vm::onReessayerRappels,
        modifier = modifier,
    )
}

@Composable
private fun LigneImportCarnetContenu(
    state: ImportCarnetUiState,
    onImportDemande: () -> Unit,
    onIssueFermee: () -> Unit,
    onReessayerRappels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !state.enCours,
                    role = Role.Button,
                    onClick = onImportDemande,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
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
                    // du scénario 2 de US-B9, il n'y a pas lieu d'en créer un second.
                    text = stringResource(R.string.reglages_import),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // L'avancement passe par le texte et pas seulement par l'animation :
                    // un indicateur qui tourne ne s'annonce pas à TalkBack.
                    text = if (state.enCours) {
                        stringResource(R.string.import_en_cours)
                    } else {
                        stringResource(R.string.import_description)
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

        state.issue?.let { issue ->
            PanneauIssue(
                issue = issue,
                onFermer = onIssueFermee,
                onReessayerRappels = onReessayerRappels,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * Le rapport d'import, ou la raison du refus.
 *
 * **Pas un `Snackbar`**, pour la même raison qu'en B16 et une de plus : le rapport se lit
 * ligne à ligne (« 2 enfants ajoutés, 1 déjà enregistré ») et c'est le seul moyen pour le
 * parent de vérifier que son carnet est complet. Quatre secondes ne suffisent pas, et un
 * `SnackbarHost` aurait demandé de modifier le `Scaffold` de l'écran Réglages, donc le
 * fichier de Dev B.
 *
 * `liveRegion` : TalkBack annonce le rapport dès qu'il apparaît — y compris quand la seule
 * chose qui change est l'issue de la replanification après un second essai.
 *
 * **L'apparence reste celle d'une réussite même quand les rappels ont manqué** : l'import,
 * lui, a bien eu lieu et rien n'est à refaire de ce côté. Passer le panneau en rouge ferait
 * croire que les enfants ne sont pas enregistrés, ce qui serait le mensonge inverse.
 */
@Composable
private fun PanneauIssue(
    issue: IssueImport,
    onFermer: () -> Unit,
    onReessayerRappels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val couleurFond = when (issue) {
        is IssueImport.Reussi -> MaterialTheme.colorScheme.secondaryContainer
        IssueImport.FichierSansContenu -> MaterialTheme.colorScheme.surfaceVariant
        IssueImport.FichierIllisible,
        is IssueImport.VersionInconnue,
        IssueImport.Echec -> MaterialTheme.colorScheme.errorContainer
    }
    val couleurTexte = when (issue) {
        is IssueImport.Reussi -> MaterialTheme.colorScheme.onSecondaryContainer
        IssueImport.FichierSansContenu -> MaterialTheme.colorScheme.onSurfaceVariant
        IssueImport.FichierIllisible,
        is IssueImport.VersionInconnue,
        IssueImport.Echec -> MaterialTheme.colorScheme.onErrorContainer
    }

    val titre = when (issue) {
        is IssueImport.Reussi -> if (issue.rapport.rienAjoute) {
            stringResource(R.string.import_rien_ajoute_titre)
        } else {
            stringResource(R.string.import_reussi_titre)
        }

        IssueImport.FichierSansContenu -> stringResource(R.string.import_vide_titre)
        IssueImport.FichierIllisible -> stringResource(R.string.import_illisible_titre)
        is IssueImport.VersionInconnue -> stringResource(R.string.import_version_titre)
        IssueImport.Echec -> stringResource(R.string.import_echec_titre)
    }

    val paragraphes: List<String> = when (issue) {
        is IssueImport.Reussi -> lignesDuRapport(issue.rapport)
        IssueImport.FichierSansContenu -> listOf(stringResource(R.string.import_vide_detail))
        IssueImport.FichierIllisible -> listOf(stringResource(R.string.import_illisible_detail))
        is IssueImport.VersionInconnue ->
            listOf(stringResource(R.string.import_version_detail, issue.version))

        IssueImport.Echec -> listOf(stringResource(R.string.import_echec_detail))
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
            paragraphes.forEach { paragraphe ->
                Text(text = paragraphe, style = MaterialTheme.typography.bodyMedium)
            }

            // Les rappels, en dernière ligne du rapport comme avant — mais d'après ce que la
            // replanification a réellement donné, et non plus inconditionnellement. C'est la
            // phrase qui dit au parent qu'il sera prévenu : elle ne s'écrit que si c'est vrai.
            if (issue is IssueImport.Reussi) {
                RappelsReplanifies(
                    etat = issue.rappels,
                    detailEchec = stringResource(R.string.import_rappels_echec_carnet),
                    onReessayer = onReessayerRappels,
                )
            }

            // Ce que l'application vient de faire du fichier, et ce qu'elle n'en a pas fait.
            // Affiché seulement après un import réel : c'est le seul moment où des données de
            // santé viennent d'entrer dans l'application (§B8).
            if (issue is IssueImport.Reussi) {
                Text(
                    text = stringResource(R.string.import_reussi_confidentialite),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // `export_fermer` est réutilisé tel quel : même mot, même geste, et un second
            // libellé identique laisserait deux clés à traduire pour un seul mot.
            TextButton(
                onClick = onFermer,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.export_fermer))
            }
        }
    }
}

/**
 * Le rapport chiffré, en phrases.
 *
 * Dans l'ordre de ce que le parent cherche à vérifier : d'abord, si rien n'a été ajouté, la
 * phrase qui le dit ; puis les enfants, puis les vaccins ; puis — seulement s'il y en a —
 * les lignes que le fichier contenait en double.
 *
 * La phrase sur les rappels **n'est plus ici** : elle dépend de ce que la replanification a
 * donné, pas du seul rapport de fusion, et elle peut s'accompagner d'une action. Elle est
 * écrite par `RappelsReplanifies`, juste après ces lignes.
 *
 * Les compteurs à zéro ne sont **pas** masqués : « 0 ajouté, 3 déjà enregistrés » est
 * précisément l'information qui évite de réimporter dix fois le même fichier en croyant
 * qu'il ne se passe rien.
 */
@Composable
private fun lignesDuRapport(rapport: ResultatImport): List<String> {
    // Pas de `buildList` : le compilateur Compose refuse les appels @Composable dans le
    // corps de son constructeur. Des variables et un `listOfNotNull` font la même chose.
    val introduction = if (rapport.rienAjoute) {
        stringResource(R.string.import_rien_ajoute_detail)
    } else {
        null
    }

    val enfants = stringResource(
        R.string.import_reussi_enfants,
        pluralStringResource(
            R.plurals.import_nb_enfants_ajoutes,
            rapport.enfantsAjoutes,
            rapport.enfantsAjoutes,
        ),
        pluralStringResource(
            R.plurals.import_nb_enfants_fusionnes,
            rapport.enfantsFusionnes,
            rapport.enfantsFusionnes,
        ),
    )

    val doses = stringResource(
        R.string.import_reussi_doses,
        pluralStringResource(
            R.plurals.import_nb_doses_ajoutees,
            rapport.dosesAjoutees,
            rapport.dosesAjoutees,
        ),
        pluralStringResource(
            R.plurals.import_nb_doses_fusionnees,
            rapport.dosesFusionnees,
            rapport.dosesFusionnees,
        ),
    )

    val enDouble = if (rapport.lignesIgnorees > 0) {
        pluralStringResource(
            R.plurals.import_nb_lignes_ignorees,
            rapport.lignesIgnorees,
            rapport.lignesIgnorees,
        )
    } else {
        null
    }

    return listOfNotNull(introduction, enfants, doses, enDouble)
}

// --- Aperçus -----------------------------------------------------------------

/** La carte du bloc « Carnet » des Réglages, pour voir la ligne dans son contenant réel. */
@Composable
private fun ApercuDansCarte(state: ImportCarnetUiState) {
    FahasalamanaTheme {
        OutlinedCard(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                LigneImportCarnetContenu(
                    state = state,
                    onImportDemande = {},
                    onIssueFermee = {},
                    onReessayerRappels = {},
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportAuRepos() {
    ApercuDansCarte(ImportCarnetUiState())
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportEnCours() {
    ApercuDansCarte(ImportCarnetUiState(enCours = true))
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportReussi() {
    ApercuDansCarte(
        ImportCarnetUiState(
            issue = IssueImport.Reussi(
                ResultatImport(
                    enfantsAjoutes = 2,
                    enfantsFusionnes = 1,
                    dosesAjoutees = 11,
                    dosesFusionnees = 4,
                ),
                rappels = EtatReplanification.Reussie,
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportSansRienDeNouveau() {
    ApercuDansCarte(
        ImportCarnetUiState(
            issue = IssueImport.Reussi(
                ResultatImport(enfantsFusionnes = 2, dosesFusionnees = 6, dosesIgnorees = 1),
                rappels = EtatReplanification.Reussie,
            ),
        ),
    )
}

/**
 * Le cas que le défaut cachait : la fusion est écrite, la replanification a échoué. Le
 * panneau reste vert — les enfants sont bien là — mais il le dit, et propose le seul geste
 * qui y remédie.
 */
@Preview(showBackground = true)
@Composable
private fun ApercuImportRappelsEnEchec() {
    ApercuDansCarte(
        ImportCarnetUiState(
            issue = IssueImport.Reussi(
                ResultatImport(enfantsAjoutes = 2, dosesAjoutees = 11),
                rappels = EtatReplanification.Echouee,
            ),
        ),
    )
}

/** Second essai en cours : le rapport reste lisible, la ligne des rappels dit ce qu'elle fait. */
@Preview(showBackground = true)
@Composable
private fun ApercuImportRappelsEnCours() {
    ApercuDansCarte(
        ImportCarnetUiState(
            issue = IssueImport.Reussi(
                ResultatImport(enfantsAjoutes = 2, dosesAjoutees = 11),
                rappels = EtatReplanification.EnCours,
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportIllisible() {
    ApercuDansCarte(ImportCarnetUiState(issue = IssueImport.FichierIllisible))
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportVersionInconnue() {
    ApercuDansCarte(ImportCarnetUiState(issue = IssueImport.VersionInconnue(version = 2)))
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportFichierSansContenu() {
    ApercuDansCarte(ImportCarnetUiState(issue = IssueImport.FichierSansContenu))
}

@Preview(showBackground = true)
@Composable
private fun ApercuImportEchec() {
    ApercuDansCarte(ImportCarnetUiState(issue = IssueImport.Echec))
}
