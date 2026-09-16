package mg.univ.fahasalamana.ui.detailcentre

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.platform.ActionsCentre
import mg.univ.fahasalamana.platform.ResultatIntent
import mg.univ.fahasalamana.platform.rememberActionsCentre
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.components.EtiquetteType
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel

/*
 * Fiche d'un centre de santé (tâche B14, US-B6 scénarios 2 et 3).
 *
 * Ce que l'annuaire publie — nom, type, horaires, adresse, téléphone — et une action qui
 * sort de l'application : « Appeler » ouvre le composeur (`ACTION_DIAL`), numéro déjà
 * saisi, sans jamais passer l'appel.
 *
 * **Aucun octet ne part sur le réseau depuis cet écran.** La fiche vient de l'annuaire
 * enregistré en base (B04), et l'appel est délégué au composeur du téléphone : pas de clé
 * d'API, pas de permission `CALL_PHONE`, une application qui marche entièrement hors
 * ligne (§B1).
 *
 * **Le scénario 3 est le cœur de cet écran.** Sur un appareil sans application de
 * téléphonie — une tablette, un profil restreint — toucher « Appeler » ne doit ni planter,
 * ni ne rien faire. `ActionsCentre` renvoie alors `AUCUNE_APPLICATION`, un message apparaît
 * en bas, et il propose de copier le numéro dans le presse-papiers : on le colle dans un SMS.
 *
 * L'adresse est affichée mais n'est pas cliquable : l'itinéraire ne fait pas partie de
 * US-B6, et l'annuaire ne publie pas de coordonnées (§B5.1).
 *
 * Point à trancher avec le binôme (suivi n° 15) : le wireframe §B7.2 dessine un bouton
 * « Appeler » sous chaque carte de la **liste**, alors que §B10.2 attribue `ACTION_DIAL` à
 * cette fiche et que US-B6 scénario 2 part de « la fiche du CSB2 Ankirihiry ». B13 puis B14
 * ont suivi le plan : l'appel est ici, et nulle part ailleurs.
 */

/**
 * @param onRetour ferme la fiche et revient à la liste des centres.
 */
@Composable
fun DetailCentreScreen(
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DetailCentreViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DetailCentreContenu(state = state, onRetour = onRetour, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailCentreContenu(
    state: DetailCentreUiState,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ActionsCentre = rememberActionsCentre(),
) {
    val hoteSnackbar = remember { SnackbarHostState() }

    // Le repli du scénario 3, porté par un état plutôt que lancé impérativement depuis un
    // `onClick` : la copie survit ainsi à une recomposition, et deux appuis rapprochés
    // remplacent le message au lieu d'en empiler deux.
    var repli by remember { mutableStateOf<DemandeRepli?>(null) }

    LaunchedEffect(repli) {
        val demande = repli ?: return@LaunchedEffect
        val reponse = hoteSnackbar.showSnackbar(
            message = demande.message,
            actionLabel = demande.copie?.libelleAction,
            withDismissAction = true,
            // Long : le message annonce une absence et propose une action ; le temps court
            // de Material suffit à peine à lire la première moitié.
            duration = SnackbarDuration.Long,
        )

        val copie = demande.copie
        if (reponse == SnackbarResult.ActionPerformed && copie != null) {
            val reussie = actions.copier(copie.etiquette, copie.texte)
            val confirmation = when {
                !reussie -> copie.echec
                // Android 13 et au-delà affichent leur propre confirmation de copie : un
                // second message dirait deux fois la même chose au même endroit.
                actions.systemeConfirmeLaCopie -> null
                else -> copie.confirmation
            }
            if (confirmation != null) hoteSnackbar.showSnackbar(confirmation)
        }
        repli = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state) {
                            is DetailCentreUiState.Pret -> state.centre.nom
                            else -> stringResource(R.string.detail_centre_titre)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        snackbarHost = { SnackbarHost(hoteSnackbar) },
    ) { interieur ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(interieur),
        ) {
            when (state) {
                DetailCentreUiState.Chargement -> EtatChargement()

                // Pas une erreur : l'identifiant vient d'une liste affichée il y a un
                // instant, ou d'une pile restaurée. Entre-temps, une mise à jour de
                // l'annuaire a pu retirer ce centre (B19).
                DetailCentreUiState.Introuvable -> EtatVide(
                    titre = stringResource(R.string.detail_centre_introuvable_titre),
                    description = stringResource(R.string.detail_centre_introuvable_description),
                    icone = Icons.Outlined.LocalHospital,
                    libelleAction = stringResource(R.string.detail_centre_introuvable_action),
                    onAction = onRetour,
                )

                DetailCentreUiState.Erreur -> EtatErreur(
                    message = stringResource(R.string.detail_centre_erreur),
                )

                is DetailCentreUiState.Pret -> FicheCentreChargee(
                    fiche = state.centre,
                    actions = actions,
                    onRepli = { demande -> repli = demande },
                )
            }
        }
    }
}

// --- L'action « Appeler » ----------------------------------------------------

/**
 * La fiche affichée, et le bouton branché sur la plateforme.
 *
 * Ce niveau existe pour une raison précise : tous les textes du repli sont des ressources,
 * et `stringResource` ne s'appelle que dans une fonction composable — jamais depuis un
 * `onClick`. Ils sont donc lus ici, puis capturés par la lambda.
 *
 * En dessous, [FicheCentreContenu] ne connaît plus ni `Intent`, ni presse-papiers : c'est
 * de l'affichage pur, prévisualisable et testable.
 */
@Composable
private fun FicheCentreChargee(
    fiche: FicheCentre,
    actions: ActionsCentre,
    onRepli: (DemandeRepli?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val libelleCopierNumero = stringResource(R.string.detail_centre_repli_copier_numero)
    val etiquetteNumero = stringResource(R.string.detail_centre_presse_papier_numero)
    val messageSansTelephone = stringResource(R.string.detail_centre_repli_appel)
    val confirmationNumero = stringResource(R.string.detail_centre_repli_numero_copie)
    val echecCopie = stringResource(R.string.detail_centre_repli_echec)
    val sansNumero = stringResource(R.string.detail_centre_sans_numero)

    FicheCentreContenu(
        fiche = fiche,
        modifier = modifier,
        onAppeler = {
            val telephone = fiche.telephone
            onRepli(
                if (telephone == null) {
                    DemandeRepli(message = sansNumero)
                } else {
                    when (actions.appeler(telephone)) {
                        // Le composeur est ouvert, le numéro est déjà saisi : rien à dire.
                        ResultatIntent.OUVERT -> null
                        ResultatIntent.RIEN_A_OUVRIR -> DemandeRepli(message = sansNumero)
                        // US-B6 scénario 3 : aucune application de téléphonie.
                        ResultatIntent.AUCUNE_APPLICATION -> DemandeRepli(
                            message = messageSansTelephone,
                            copie = ContenuACopier(
                                libelleAction = libelleCopierNumero,
                                etiquette = etiquetteNumero,
                                // Le numéro tel qu'il est publié, avec ses espaces : c'est
                                // celui qu'on relit et qu'on recopie, pas l'URI `tel:`.
                                texte = telephone,
                                confirmation = confirmationNumero,
                                echec = echecCopie,
                            ),
                        )
                    }
                },
            )
        },
    )
}

/**
 * Ce qu'un message de repli doit contenir : une phrase, et de quoi copier si une copie a
 * du sens.
 *
 * `copie` nul couvre le cas « l'annuaire ne publie rien à ouvrir » : on explique, mais on
 * ne propose pas de copier un champ vide.
 */
@Immutable
private data class DemandeRepli(
    val message: String,
    val copie: ContenuACopier? = null,
)

/** Le contenu proposé à la copie, et les deux phrases qui suivent l'appui. */
@Immutable
private data class ContenuACopier(
    val libelleAction: String,
    val etiquette: String,
    val texte: String,
    val confirmation: String,
    val echec: String,
)

// --- Affichage pur -----------------------------------------------------------

/**
 * La fiche telle qu'elle s'affiche, sans aucune dépendance à la plateforme.
 *
 * Ordre repris du wireframe §B7.2 pour une carte de la liste — nom et type, horaires,
 * adresse — complété du téléphone et du bouton « Appeler », que le plan attribue à cette
 * fiche. L'adresse est affichée pour elle-même : c'est une information du wireframe, pas
 * l'amorce d'un itinéraire.
 */
@Composable
private fun FicheCentreContenu(
    fiche: FicheCentre,
    onAppeler: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fiche.nom,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            EtiquetteType(type = fiche.type)
        }

        Spacer(Modifier.height(24.dp))

        LigneInformation(
            icone = Icons.Outlined.Schedule,
            libelle = stringResource(R.string.centres_horaires_label),
            valeur = fiche.horaires,
        )
        Spacer(Modifier.height(16.dp))
        LigneInformation(
            icone = Icons.Outlined.Place,
            libelle = stringResource(R.string.centres_adresse_label),
            valeur = fiche.adresse,
        )
        Spacer(Modifier.height(16.dp))
        LigneInformation(
            icone = Icons.Outlined.Call,
            libelle = stringResource(R.string.detail_centre_telephone_label),
            valeur = fiche.telephone,
        )

        // Le bouton disparaît — plutôt que d'être grisé — quand l'annuaire ne publie pas
        // de numéro : un bouton inerte sans explication ne se comprend pas, alors que la
        // ligne « Téléphone : non publié » juste au-dessus, si.
        if (fiche.appelPossible) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAppeler,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    // Le libellé du bouton porte déjà l'information.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.detail_centre_action_appeler))
            }
        }

        Spacer(Modifier.height(28.dp))
        MentionDelegation()
    }
}

/**
 * Une information publiée : son icône, son intitulé, sa valeur.
 *
 * L'icône est décorative (`contentDescription = null`) parce que l'intitulé est écrit juste
 * à côté ; sans cela TalkBack dirait « Horaires » deux fois. La ligne entière est fusionnée
 * en un seul nœud, pour être lue d'un trait — « Horaires, lundi à vendredi 7h30 à 16h » —
 * plutôt qu'en deux arrêts du curseur.
 *
 * @param valeur `null` quand l'annuaire ne publie pas cette information ; la ligne reste
 *   affichée et le dit, ce qui vaut mieux qu'une information qui disparaît sans raison.
 */
@Composable
private fun LigneInformation(
    icone: ImageVector,
    libelle: String,
    valeur: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = libelle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = valeur ?: stringResource(R.string.detail_centre_non_publie),
                style = MaterialTheme.typography.bodyLarge,
                color = if (valeur == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontStyle = if (valeur == null) FontStyle.Italic else null,
            )
        }
    }
}

/**
 * La phrase qui explique où mène le bouton.
 *
 * Elle est là pour une raison de fond, pas de décoration : l'application n'appelle
 * personne, elle passe la main au composeur. Le dire rassure avant l'appui — rien ne part
 * tout seul —, et c'est aussi la garantie de confidentialité : rien ne part sur le réseau
 * depuis cet écran (§B8).
 */
@Composable
private fun MentionDelegation(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.detail_centre_mention_delegation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// --- Aperçus -----------------------------------------------------------------
//
// Le centre est celui du scénario 2 de US-B6 : « la fiche du CSB2 Ankirihiry ».

private val FicheDApercu = FicheCentre(
    id = "toamasina-i-csb2-ankirihiry",
    nom = "CSB2 Ankirihiry",
    type = "CSB2",
    horaires = "Lun–Ven 7h30–16h00, vaccination mardi et jeudi matin",
    adresse = "Ankirihiry, Toamasina I",
    telephone = "+261 34 00 000 00",
)

/** Le scénario 2 tel qu'il doit s'afficher, en clair et en sombre. */
@Preview(showBackground = true, heightDp = 700)
@Preview(showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuDetailCentre() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Pret(FicheDApercu),
            onRetour = {},
        )
    }
}

/** Nom long : l'en-tête doit se replier sans pousser l'étiquette de type hors de l'écran. */
@Preview(showBackground = true, heightDp = 700, widthDp = 320)
@Composable
private fun ApercuDetailCentreNomLong() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Pret(
                FicheDApercu.copy(nom = "CSB2 Ambohitr'Antsahasoa Atsimondrano"),
            ),
            onRetour = {},
        )
    }
}

/** Annuaire incomplet : pas de numéro, donc pas de bouton « Appeler ». */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuDetailCentreSansNumero() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Pret(FicheDApercu.copy(telephone = null)),
            onRetour = {},
        )
    }
}

/** Centre retiré de l'annuaire par une mise à jour pendant que l'écran était ouvert. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuDetailCentreIntrouvable() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Introuvable,
            onRetour = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuDetailCentreErreur() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Erreur,
            onRetour = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuDetailCentreChargement() {
    FahasalamanaTheme {
        DetailCentreContenu(
            state = DetailCentreUiState.Chargement,
            onRetour = {},
        )
    }
}
