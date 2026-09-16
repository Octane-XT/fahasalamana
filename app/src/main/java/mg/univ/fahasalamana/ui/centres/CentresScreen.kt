package mg.univ.fahasalamana.ui.centres

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.components.EtiquetteType
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel

/*
 * Écran « Centres de santé » (tâche B13, US-B6 scénario 1, wireframe §B7.2).
 *
 * Deux menus déroulants en cascade, puis la liste des centres du district : nom et type,
 * horaires, adresse. Aucun appel réseau et aucune carte — l'annuaire est en base depuis
 * B04, et l'écran ne lit jamais plus que le district affiché.
 *
 * Toucher une carte ouvre `DetailCentre(centreId)` par la lambda [CentresScreen.onOuvrirCentre] :
 * la navigation reste dans `AppNavHost`, jamais dans le ViewModel.
 *
 * Le bouton « Appeler » vit sur l'écran de détail (B14), pas ici. Le wireframe §B7.2 le
 * dessine pourtant sous chaque carte de cette liste, mais §B10.2 attribue `ACTION_DIAL` à
 * B14 et US-B6 scénario 2 part de « la fiche du centre ». B13 puis B14 ont suivi le plan ;
 * la divergence reste au suivi (n° 15) pour que le binôme tranche. Si le bouton doit
 * revenir dans la liste, il se branchera sur les mêmes `LigneCentre` (il faudra y rajouter
 * le téléphone) et appellera `ActionsCentre.appeler`, qui porte déjà le repli du scénario 3.
 */

/**
 * @param onOuvrirCentre vers `DetailCentre` du centre touché (B14).
 */
@Composable
fun CentresScreen(
    onOuvrirCentre: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: CentresViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    CentresContenu(
        state = state,
        onRegionChoisie = vm::onRegionChoisie,
        onDistrictChoisi = vm::onDistrictChoisi,
        onOuvrirCentre = onOuvrirCentre,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CentresContenu(
    state: CentresUiState,
    onRegionChoisie: (String) -> Unit,
    onDistrictChoisi: (String) -> Unit,
    onOuvrirCentre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.centres_titre)) }) },
    ) { interieur ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(interieur),
        ) {
            when (state) {
                CentresUiState.Chargement -> EtatChargement()

                CentresUiState.AnnuaireAbsent -> EtatVide(
                    titre = stringResource(R.string.centres_annuaire_absent_titre),
                    description = stringResource(R.string.centres_annuaire_absent_description),
                    icone = Icons.Outlined.LocalHospital,
                )

                CentresUiState.Erreur -> EtatErreur(
                    message = stringResource(R.string.centres_erreur),
                )

                is CentresUiState.Pret -> {
                    // Les menus restent en haut, hors de la zone qui défile : ils sont le
                    // seul moyen de sortir d'un district vide (wireframe §B7.2).
                    SelecteursAnnuaire(
                        state = state,
                        onRegionChoisie = onRegionChoisie,
                        onDistrictChoisi = onDistrictChoisi,
                    )
                    HorizontalDivider()
                    Box(modifier = Modifier.weight(1f)) {
                        ZoneResultat(
                            resultat = state.resultat,
                            onOuvrirCentre = onOuvrirCentre,
                        )
                    }
                }
            }
        }
    }
}

// --- Les deux menus en cascade -----------------------------------------------

@Composable
private fun SelecteursAnnuaire(
    state: CentresUiState.Pret,
    onRegionChoisie: (String) -> Unit,
    onDistrictChoisi: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenuAnnuaire(
            libelle = stringResource(R.string.centres_region_label),
            invite = stringResource(R.string.centres_region_invite),
            options = state.regions,
            choisie = state.regionChoisie,
            onChoisir = onRegionChoisie,
        )

        MenuAnnuaire(
            libelle = stringResource(R.string.centres_district_label),
            invite = stringResource(R.string.centres_district_invite),
            options = state.districts,
            choisie = state.districtChoisi,
            onChoisir = onDistrictChoisi,
            // Cascade : rien à proposer tant qu'aucune région n'est choisie (§B7.2).
            actif = state.districtActif,
            texteAide = if (state.districtActif) null else stringResource(R.string.centres_district_inactif),
        )
    }
}

/**
 * Un menu déroulant de l'annuaire : même composable pour les régions et les districts.
 *
 * `ExposedDropdownMenu` empile ses entrées dans une colonne qui défile — ce n'est pas une
 * liste paresseuse. Ce n'est pas un problème ici **parce que la cascade borne les
 * volumes** : 23 régions au premier menu, et au second les seuls districts de la région
 * choisie (2 à 6), jamais les 76. `key(option.id)` donne malgré tout une identité stable à
 * chaque entrée, pour que l'ouverture d'un menu ne recompose pas des lignes inchangées.
 *
 * @param choisie option sélectionnée, `null` tant qu'il n'y en a pas : le champ affiche
 *   alors son [invite] en gris, jamais un choix par défaut qui n'a pas été fait.
 * @param actif quand il est faux, le champ est grisé et ne s'ouvre pas.
 * @param texteAide phrase sous le champ, qui dit pourquoi il est inactif.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuAnnuaire(
    libelle: String,
    invite: String,
    options: List<OptionAnnuaire>,
    choisie: OptionAnnuaire?,
    onChoisir: (String) -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    texteAide: String? = null,
) {
    // `rememberSaveable` : le menu laissé ouvert se retrouve ouvert après la rotation, et
    // surtout il ne se rouvre pas tout seul quand la liste des districts est remplacée.
    var ouvert by rememberSaveable { mutableStateOf(false) }

    // Un menu inactif est un menu fermé : `actif` peut retomber à faux sous le doigt (la
    // région disparaît d'une mise à jour) alors que `ouvert` est encore vrai.
    val deploye = ouvert && actif

    ExposedDropdownMenuBox(
        expanded = deploye,
        onExpandedChange = { if (actif) ouvert = !ouvert },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = choisie?.nom.orEmpty(),
            onValueChange = { },
            modifier = Modifier
                // PrimaryNotEditable : le champ n'est pas saisissable, il ne sert qu'à
                // ouvrir le menu — TalkBack l'annonce comme une liste déroulante, pas
                // comme une zone de texte, et le clavier ne se lève jamais.
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = actif)
                .fillMaxWidth(),
            enabled = actif,
            readOnly = true,
            label = { Text(libelle) },
            placeholder = { Text(invite) },
            supportingText = if (texteAide == null) null else {
                { Text(texteAide) }
            },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deploye) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )

        ExposedDropdownMenu(
            expanded = deploye,
            onDismissRequest = { ouvert = false },
        ) {
            options.forEach { option ->
                key(option.id) {
                    DropdownMenuItem(
                        text = { Text(option.nom) },
                        onClick = {
                            ouvert = false
                            onChoisir(option.id)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

// --- La zone sous les menus --------------------------------------------------

@Composable
private fun ZoneResultat(
    resultat: ResultatCentres,
    onOuvrirCentre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (resultat) {
        ResultatCentres.SansRegion -> EtatVide(
            titre = stringResource(R.string.centres_invite_region_titre),
            modifier = modifier,
            description = stringResource(R.string.centres_invite_region_description),
            icone = Icons.Outlined.TravelExplore,
        )

        ResultatCentres.SansDistrict -> EtatVide(
            titre = stringResource(R.string.centres_invite_district_titre),
            modifier = modifier,
            description = stringResource(R.string.centres_invite_district_description),
            icone = Icons.Outlined.TravelExplore,
        )

        ResultatCentres.Chargement -> EtatChargement(modifier = modifier)

        ResultatCentres.Aucun -> EtatVide(
            titre = stringResource(R.string.centres_vide_titre),
            modifier = modifier,
            description = stringResource(R.string.centres_vide_description),
            icone = Icons.Outlined.LocalHospital,
        )

        is ResultatCentres.Liste -> ListeCentres(
            centres = resultat.centres,
            onOuvrirCentre = onOuvrirCentre,
            modifier = modifier,
        )
    }
}

@Composable
private fun ListeCentres(
    centres: List<LigneCentre>,
    onOuvrirCentre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "nombre") {
            Text(
                text = pluralStringResource(R.plurals.centres_nombre, centres.size, centres.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        // `key = { it.id }` : les identifiants de l'annuaire sont stables dans le temps
        // (§B5.1), donc une mise à jour qui ajoute un centre ne recompose que sa carte et
        // ne fait pas sauter la position de défilement.
        items(items = centres, key = { it.id }) { centre ->
            CarteCentre(
                centre = centre,
                onClic = { onOuvrirCentre(centre.id) },
            )
        }
    }
}

/**
 * Une carte de la liste, dans l'ordre du wireframe §B7.2 : nom (avec son type), horaires,
 * adresse.
 *
 * Les deux icônes portent chacune le libellé de ce qu'elles introduisent (« Horaires »,
 * « Adresse ») : à l'œil elles évitent deux préfixes écrits, et TalkBack lit tout de même
 * « Horaires, lundi à vendredi… » plutôt qu'une ligne de texte sans contexte. La carte est
 * cliquable, donc lue d'un bloc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarteCentre(
    centre: LigneCentre,
    onClic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClic,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = centre.nom,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    EtiquetteType(type = centre.type)
                }

                Spacer(Modifier.height(10.dp))
                LigneDetail(
                    texte = centre.horaires,
                    icone = Icons.Outlined.Schedule,
                    libelleIcone = stringResource(R.string.centres_horaires_label),
                )

                Spacer(Modifier.height(6.dp))
                LigneDetail(
                    texte = centre.adresse,
                    icone = Icons.Outlined.Place,
                    libelleIcone = stringResource(R.string.centres_adresse_label),
                )
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                // Décoratif : la carte entière est le bouton, TalkBack l'annonce déjà.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// (B14) `EtiquetteType` vivait ici, en privé. La fiche d'un centre affiche le même badge :
// il est remonté dans `ui/components/EtiquetteType.kt` plutôt que recopié, pour ne pas
// rejouer la divergence qu'avait connue l'affichage de l'âge entre la liste et la fiche.

@Composable
private fun LigneDetail(
    texte: String,
    icone: ImageVector,
    libelleIcone: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = libelleIcone,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = texte,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Aperçus -----------------------------------------------------------------
//
// Les données sont celles de `assets/csb.json` pour Toamasina I : c'est le district du
// scénario 1 de US-B6, donc ce que le jury verra pendant la démonstration.

private val RegionsDApercu = listOf(
    OptionAnnuaire(id = "alaotra-mangoro", nom = "Alaotra-Mangoro"),
    OptionAnnuaire(id = "amoron-i-mania", nom = "Amoron'i Mania"),
    OptionAnnuaire(id = "analamanga", nom = "Analamanga"),
    OptionAnnuaire(id = "atsinanana", nom = "Atsinanana"),
)

private val DistrictsDApercu = listOf(
    OptionAnnuaire(id = "toamasina-i", nom = "Toamasina I"),
    OptionAnnuaire(id = "toamasina-ii", nom = "Toamasina II"),
    OptionAnnuaire(id = "vatomandry", nom = "Vatomandry"),
)

private val CentresDApercu = listOf(
    LigneCentre(
        id = "toamasina-i-csb1-andramanga",
        nom = "CSB1 Andramanga",
        type = "CSB1",
        horaires = "Lun–Sam 8h00–15h00, vaccination lundi matin",
        adresse = "Andramanga, Toamasina I",
    ),
    LigneCentre(
        id = "toamasina-i-csb2-ambatorano",
        nom = "CSB2 Ambatorano",
        type = "CSB2",
        horaires = "Lun–Ven 7h00–15h30, vaccination vendredi matin",
        adresse = "Ambatorano, Toamasina I",
    ),
    LigneCentre(
        id = "toamasina-i-csb2-antantsoa",
        nom = "CSB2 Antantsoa",
        type = "CSB2",
        horaires = "Lun–Sam 8h00–15h00, vaccination lundi matin",
        adresse = "Antantsoa, Toamasina I",
    ),
)

private val PretDApercu = CentresUiState.Pret(
    regions = RegionsDApercu,
    regionChoisie = RegionsDApercu.last(),
    districts = DistrictsDApercu,
    districtChoisi = DistrictsDApercu.first(),
    resultat = ResultatCentres.Liste(CentresDApercu),
)

/** Le scénario 1 de US-B6, tel qu'il doit s'afficher : Atsinanana puis Toamasina I. */
@Preview(showBackground = true, heightDp = 900)
@Preview(showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuCentresListe() {
    FahasalamanaTheme {
        CentresContenu(
            state = PretDApercu,
            onRegionChoisie = {},
            onDistrictChoisi = {},
            onOuvrirCentre = {},
        )
    }
}

/** Ouverture de l'onglet : le second menu est grisé, la zone du bas invite à choisir. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuCentresSansChoix() {
    FahasalamanaTheme {
        CentresContenu(
            state = PretDApercu.copy(
                regionChoisie = null,
                districts = emptyList(),
                districtChoisi = null,
                resultat = ResultatCentres.SansRegion,
            ),
            onRegionChoisie = {},
            onDistrictChoisi = {},
            onOuvrirCentre = {},
        )
    }
}

/** District choisi, mais l'annuaire ne publie aucun centre pour lui. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuCentresDistrictVide() {
    FahasalamanaTheme {
        CentresContenu(
            state = PretDApercu.copy(resultat = ResultatCentres.Aucun),
            onRegionChoisie = {},
            onDistrictChoisi = {},
            onOuvrirCentre = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuCentresErreur() {
    FahasalamanaTheme {
        CentresContenu(
            state = CentresUiState.Erreur,
            onRegionChoisie = {},
            onDistrictChoisi = {},
            onOuvrirCentre = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuCentresAnnuaireAbsent() {
    FahasalamanaTheme {
        CentresContenu(
            state = CentresUiState.AnnuaireAbsent,
            onRegionChoisie = {},
            onDistrictChoisi = {},
            onOuvrirCentre = {},
        )
    }
}
