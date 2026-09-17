package mg.univ.fahasalamana.ui.enfants

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.domain.AgeEnfant
import mg.univ.fahasalamana.domain.ResumeEnfant
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.components.EtiquettesTest
import mg.univ.fahasalamana.ui.components.texteAge
import mg.univ.fahasalamana.ui.theme.CouleurStatut
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran « Mes enfants » (tâche B06, US-B8, wireframe §B7.2).
 *
 * Une carte par enfant : prénom, âge, et le résumé de la règle R6 — combien de vaccins en
 * retard, combien à faire, quelle est la prochaine échéance. Les cartes sont triées par
 * nombre de retards décroissant : l'agent communautaire trouve en haut de liste les
 * enfants pour lesquels il y a quelque chose à faire aujourd'hui.
 *
 * Ton des libellés (R7) : on décrit une situation (« 1 en retard », « Prévu le… »), on ne
 * reproche rien. Le code couleur des statuts est toujours doublé d'une icône et d'un
 * libellé écrit : la couleur seule ne porte jamais l'information (§B7.2, accessibilité).
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2) et l'écran Réglages. */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/**
 * @param onAjouterEnfant vers `EditionEnfant` sans identifiant (création, B07).
 * @param onOuvrirEnfant vers `FicheEnfant` de l'enfant touché (B08).
 */
@Composable
fun MesEnfantsScreen(
    onAjouterEnfant: () -> Unit,
    onOuvrirEnfant: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: MesEnfantsViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    MesEnfantsContenu(
        state = state,
        onAjouterEnfant = onAjouterEnfant,
        onOuvrirEnfant = onOuvrirEnfant,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MesEnfantsContenu(
    state: MesEnfantsUiState,
    onAjouterEnfant: () -> Unit,
    onOuvrirEnfant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.mes_enfants_titre),
                        // Titre d'écran : en-tête pour la navigation par titres de TalkBack.
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
        floatingActionButton = {
            // Pas de bouton flottant sur l'état vide ni pendant le chargement : `EtatVide`
            // porte déjà le même bouton, au centre de l'écran, là où l'œil se pose.
            // Les deux portent aussi la même étiquette de test (B23) : un test qui ajoute un
            // enfant n'a pas à savoir dans quel état se trouve l'écran.
            if (state is MesEnfantsUiState.Pret) {
                ExtendedFloatingActionButton(
                    onClick = onAjouterEnfant,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.mes_enfants_action_ajouter)) },
                    modifier = Modifier.testTag(EtiquettesTest.AJOUTER_ENFANT),
                )
            }
        },
    ) { interieur ->
        when (state) {
            MesEnfantsUiState.Chargement ->
                EtatChargement(modifier = Modifier.padding(interieur))

            MesEnfantsUiState.Vide ->
                EtatVide(
                    titre = stringResource(R.string.mes_enfants_vide_titre),
                    modifier = Modifier.padding(interieur),
                    description = stringResource(R.string.mes_enfants_vide_description),
                    icone = Icons.Outlined.ChildCare,
                    libelleAction = stringResource(R.string.mes_enfants_action_ajouter),
                    etiquetteAction = EtiquettesTest.AJOUTER_ENFANT,
                    onAction = onAjouterEnfant,
                )

            MesEnfantsUiState.Erreur ->
                EtatErreur(
                    message = stringResource(R.string.mes_enfants_erreur),
                    modifier = Modifier.padding(interieur),
                )

            is MesEnfantsUiState.Pret ->
                ListeEnfants(
                    enfants = state.enfants,
                    onOuvrirEnfant = onOuvrirEnfant,
                    modifier = Modifier.padding(interieur),
                )
        }
    }
}

@Composable
private fun ListeEnfants(
    enfants: List<LigneEnfant>,
    onOuvrirEnfant: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Le bas laisse passer le bouton flottant : sans cela il recouvre la dernière carte.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = enfants, key = { it.id }) { enfant ->
            CarteEnfant(
                enfant = enfant,
                onClic = { onOuvrirEnfant(enfant.id) },
            )
        }
    }
}

/**
 * Une carte de la liste : identité à gauche, résumé R6 en dessous (wireframe §B7.2).
 *
 * La carte est **un seul bloc pour TalkBack** : `Modifier.clickable` fusionne la sémantique
 * de ses enfants, donc le prénom, l'âge, les puces de résumé et la prochaine échéance se
 * lisent d'une traite au lieu de cinq arrêts successifs. `onClickLabel` dit ce que fait
 * l'appui — « Ouvrir le carnet » — là où le `Card(onClick = …)` de Material se contentait
 * d'un « Appuyer deux fois pour activer » qui n'apprend rien (B23).
 */
@Composable
private fun CarteEnfant(
    enfant: LigneEnfant,
    onClic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forme = MaterialTheme.shapes.medium
    OutlinedCard(
        shape = forme,
        modifier = modifier
            .fillMaxWidth()
            .testTag(EtiquettesTest.carteEnfant(enfant.id))
            // `clip` avant `clickable` : sans lui l'ondulation du toucher déborderait des
            // coins arrondis de la carte, que seule la `Surface` interne découpe.
            .clip(forme)
            .clickable(
                onClickLabel = stringResource(R.string.mes_enfants_action_ouvrir),
                role = Role.Button,
                onClick = onClic,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Pastille(initiale = enfant.prenom.firstOrNull())
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = enfant.prenom,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = texteAge(enfant.age),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(10.dp))
                PucesResume(enfant = enfant)

                Spacer(Modifier.height(10.dp))
                Text(
                    text = texteProchaineEcheance(enfant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Les deux compteurs de la règle R6, « À jour » quand il n'y a rien à faire, ou l'attente du
 * calendrier quand il n'y a rien à calculer.
 *
 * Un compteur à zéro n'est pas affiché : une carte ne montre que ce qui demande une action,
 * et « 0 en retard » attire l'œil sur un chiffre qui ne veut rien dire.
 *
 * Le troisième cas n'est pas une nuance de couleur : les deux compteurs de R6 sont à zéro
 * aussi bien pour un carnet complet que pour un enfant dont rien n'a pu être calculé, et la
 * puce verte « À jour » affirmait alors, sur une application de santé, qu'un enfant jamais
 * vacciné était en règle. Gris : on ne sait pas encore, et la ligne en dessous le dit.
 */
@Composable
private fun PucesResume(enfant: LigneEnfant, modifier: Modifier = Modifier) {
    val couleurs = FahasalamanaTheme.couleursStatut

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enfant.sansEcheancier) {
            PuceStatut(
                texte = stringResource(R.string.mes_enfants_calendrier_absent_puce),
                icone = Icons.Outlined.EventNote,
                couleur = couleurs.aVenir,
            )
        } else if (enfant.aJour) {
            PuceStatut(
                texte = stringResource(R.string.mes_enfants_a_jour),
                icone = Icons.Outlined.CheckCircle,
                couleur = couleurs.fait,
            )
        } else {
            if (enfant.resume.nbEnRetard > 0) {
                PuceStatut(
                    texte = pluralStringResource(
                        R.plurals.mes_enfants_nb_en_retard,
                        enfant.resume.nbEnRetard,
                        enfant.resume.nbEnRetard,
                    ),
                    icone = Icons.Outlined.PriorityHigh,
                    couleur = couleurs.enRetard,
                )
            }
            if (enfant.resume.nbAFaire > 0) {
                PuceStatut(
                    texte = pluralStringResource(
                        R.plurals.mes_enfants_nb_a_faire,
                        enfant.resume.nbAFaire,
                        enfant.resume.nbAFaire,
                    ),
                    icone = Icons.Outlined.EventAvailable,
                    couleur = couleurs.aFaire,
                )
            }
        }
    }
}

/**
 * Une puce de statut : fond coloré, icône, libellé.
 *
 * L'icône est décorative (`contentDescription = null`) : le texte qui la suit porte déjà
 * l'information, et la répéter ferait bégayer TalkBack. Ce qui compte ici, c'est qu'un
 * écran daltonien ou en plein soleil garde l'icône et le mot même si la teinte se perd.
 */
@Composable
private fun PuceStatut(
    texte: String,
    icone: ImageVector,
    couleur: CouleurStatut,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(couleur.conteneur)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = couleur.surConteneur,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = texte,
            style = MaterialTheme.typography.labelLarge,
            color = couleur.surConteneur,
        )
    }
}

/**
 * Initiale du prénom dans un rond : repère visuel de la carte, sans image à charger.
 *
 * Purement décorative, et il faut le dire à la machine (B23) : la carte fusionne la
 * sémantique de tout son contenu, donc cette seule lettre se lisait devant le prénom —
 * « F, Faly, 8 mois… ». `clearAndSetSemantics` la retire de la lecture ; elle reste à
 * l'écran, où elle sert l'œil et lui seul.
 */
@Composable
private fun Pastille(initiale: Char?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initiale?.uppercase(Locale.FRENCH).orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * « Prochaine dose : Rougeole-Rubéole 1re dose, le 28/09/2026 ».
 *
 * Quatre cas : rien n'est calculable, et on le dit avec les mots de la fiche enfant ;
 * l'échéance et son vaccin sont connus ; la date est connue mais plus aucun vaccin du
 * calendrier ne la porte (le calendrier a été remplacé, B19) ; il ne reste plus rien à
 * faire, et là seulement le carnet est complet.
 */
@Composable
private fun texteProchaineEcheance(enfant: LigneEnfant): String {
    if (enfant.sansEcheancier) return stringResource(R.string.mes_enfants_calendrier_absent)

    val echeance = enfant.resume.prochaineEcheance ?: return stringResource(R.string.mes_enfants_prochain_aucun)
    val nom = enfant.prochainVaccinNom
    val dose = enfant.prochainVaccinDose
    val jour = echeance.format(FORMAT_JOUR)

    return if (nom != null && dose != null) {
        stringResource(R.string.mes_enfants_prochain, nom, dose, jour)
    } else {
        stringResource(R.string.mes_enfants_prochain_date_seule, jour)
    }
}

// --- Aperçus -----------------------------------------------------------------

private val FalyEnRetard = LigneEnfant(
    id = "faly",
    prenom = "Faly",
    age = AgeEnfant.Mois(8),
    resume = ResumeEnfant(nbEnRetard = 1, nbAFaire = 1, prochaineEcheance = LocalDate.of(2026, 9, 28)),
    prochainVaccinNom = "Rougeole-Rubéole",
    prochainVaccinDose = "1re dose",
    sansEcheancier = false,
)

private val SoaAJour = LigneEnfant(
    id = "soa",
    prenom = "Soa",
    age = AgeEnfant.Annees(2),
    resume = ResumeEnfant(nbEnRetard = 0, nbAFaire = 0, prochaineEcheance = null),
    prochainVaccinNom = null,
    prochainVaccinDose = null,
    sansEcheancier = false,
)

private val NouveauNe = LigneEnfant(
    id = "hasina",
    prenom = "Hasina",
    age = AgeEnfant.Jours(12),
    resume = ResumeEnfant(nbEnRetard = 0, nbAFaire = 2, prochaineEcheance = LocalDate.of(2026, 10, 27)),
    prochainVaccinNom = "Pentavalent",
    prochainVaccinDose = "1re dose",
    sansEcheancier = false,
)

/**
 * Calendrier de référence pas encore en base : les trois chiffres de R6 sont à zéro, comme
 * pour un carnet complet, et c'est tout l'intérêt de cet aperçu — les deux situations ne
 * doivent pas se ressembler à l'écran.
 */
private val KotoSansCalendrier = LigneEnfant(
    id = "koto",
    prenom = "Koto",
    age = AgeEnfant.Mois(3),
    resume = ResumeEnfant(nbEnRetard = 0, nbAFaire = 0, prochaineEcheance = null),
    prochainVaccinNom = null,
    prochainVaccinDose = null,
    sansEcheancier = true,
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuMesEnfantsPret() {
    FahasalamanaTheme {
        MesEnfantsContenu(
            state = MesEnfantsUiState.Pret(listOf(FalyEnRetard, NouveauNe, SoaAJour)),
            onAjouterEnfant = {},
            onOuvrirEnfant = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuMesEnfantsSansCalendrier() {
    FahasalamanaTheme {
        MesEnfantsContenu(
            state = MesEnfantsUiState.Pret(listOf(KotoSansCalendrier, SoaAJour)),
            onAjouterEnfant = {},
            onOuvrirEnfant = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuMesEnfantsVide() {
    FahasalamanaTheme {
        MesEnfantsContenu(
            state = MesEnfantsUiState.Vide,
            onAjouterEnfant = {},
            onOuvrirEnfant = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuMesEnfantsChargement() {
    FahasalamanaTheme {
        MesEnfantsContenu(
            state = MesEnfantsUiState.Chargement,
            onAjouterEnfant = {},
            onOuvrirEnfant = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuMesEnfantsErreur() {
    FahasalamanaTheme {
        MesEnfantsContenu(
            state = MesEnfantsUiState.Erreur,
            onAjouterEnfant = {},
            onOuvrirEnfant = {},
        )
    }
}
