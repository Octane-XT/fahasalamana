package mg.univ.fahasalamana.ui.fiche

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.GroupeEcheancier
import mg.univ.fahasalamana.domain.LigneEcheancier
import mg.univ.fahasalamana.domain.Sexe
import mg.univ.fahasalamana.domain.StatutVaccin
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.VaccinReference
import mg.univ.fahasalamana.domain.grouperParAge
import mg.univ.fahasalamana.domain.nbFaits
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.components.EtatVide
import mg.univ.fahasalamana.ui.theme.CouleurStatut
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Écran Fiche enfant (tâche B08, CDC §B7.2, US-B2). C'est l'écran qui porte la valeur du
 * projet : tout ce que le parent vient chercher tient en une page qui se lit sans explication.
 *
 * Trois principes tenus ligne à ligne :
 *
 * 1. **Un seul `when` sur `StatutVaccin`** (`apparenceDe`), sans `else` : couleur, icône,
 *    libellé et phrase de détail y sont décidés d'un bloc. Un sixième statut ferait échouer la
 *    compilation ici, au lieu de laisser une ligne muette à l'écran.
 * 2. **La couleur n'est jamais seule** (CDC §B7.2, accessibilité et daltonisme) : chaque ligne
 *    porte une pastille colorée *et* une icône *et* un libellé écrit.
 * 3. **Règle R7, ton factuel.** « Prévu le… », « À faire dès que possible ». Jamais un
 *    reproche : une mère qui a manqué une dose doit lire une date et une action, pas un jugement.
 *
 * Les sections d'âge viennent de `domain.grouperParAge`, calculées depuis le calendrier seul :
 * elles ne bougent pas quand une dose est saisie en retard (voir la doc de la fonction).
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2). */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/** En dessous de deux semaines, un âge se dit en jours ; au-delà de six mois, en mois. */
private const val JOURS_MIN_SEMAINES = 14
private const val JOURS_MIN_MOIS = 183
private const val JOURS_PAR_SEMAINE = 7.0
private const val JOURS_PAR_MOIS = 30.4375

@Composable
fun FicheEnfantScreen(
    onRetour: () -> Unit,
    onModifierEnfant: (enfantId: String) -> Unit,
    onSaisirVaccin: (enfantId: String, vaccinId: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: FicheEnfantViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    FicheEnfantContenu(
        state = state,
        onRetour = onRetour,
        onModifierEnfant = onModifierEnfant,
        onSaisirVaccin = onSaisirVaccin,
        modifier = modifier,
    )
}

/** Sans ViewModel : prévisualisable, et pilotable tel quel par les tests UI de B21. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicheEnfantContenu(
    state: FicheEnfantUiState,
    onRetour: () -> Unit,
    onModifierEnfant: (String) -> Unit,
    onSaisirVaccin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Le prénom n'apparaît qu'une fois l'enfant lu : ni le chargement ni
                        // l'état introuvable ne doivent afficher une donnée personnelle vide.
                        text = if (state is FicheEnfantUiState.Pret) {
                            state.enfant.prenom
                        } else {
                            stringResource(R.string.fiche_titre)
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
                actions = {
                    if (state is FicheEnfantUiState.Pret) {
                        IconButton(onClick = { onModifierEnfant(state.enfant.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.fiche_action_modifier),
                            )
                        }
                    }
                },
            )
        },
    ) { interieur ->
        when (state) {
            FicheEnfantUiState.Chargement -> EtatChargement(
                modifier = Modifier.padding(interieur),
            )

            FicheEnfantUiState.Erreur -> EtatErreur(
                message = stringResource(R.string.fiche_erreur),
                modifier = Modifier.padding(interieur),
            )

            // L'enfant a été supprimé pendant que la fiche était ouverte : on l'explique au
            // lieu de refermer l'écran sous les doigts de l'utilisateur.
            FicheEnfantUiState.Introuvable -> EtatVide(
                titre = stringResource(R.string.fiche_introuvable_titre),
                description = stringResource(R.string.fiche_introuvable_detail),
                icone = Icons.Outlined.ChildCare,
                libelleAction = stringResource(R.string.fiche_introuvable_action),
                onAction = onRetour,
                modifier = Modifier.padding(interieur),
            )

            is FicheEnfantUiState.Pret -> Echeancier(
                state = state,
                onSaisirVaccin = onSaisirVaccin,
                modifier = Modifier.padding(interieur),
            )
        }
    }
}

// --- Échéancier --------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Echeancier(
    state: FicheEnfantUiState.Pret,
    onSaisirVaccin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `StatutVaccin.EnAttente` ne porte que l'identifiant de la dose attendue : l'échéancier
    // lui-même sert d'annuaire pour l'écrire en toutes lettres.
    val dosesParId = remember(state.groupes) {
        state.groupes.flatMap(GroupeEcheancier::lignes).associate { it.vaccin.id to it.vaccin }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "entete") { EnteteEnfant(state) }

        // Calendrier de référence absent : l'enfant existe, mais il n'y a rien à échelonner.
        if (state.groupes.isEmpty()) {
            item(key = "calendrier-absent") {
                EtatVide(
                    titre = stringResource(R.string.fiche_calendrier_absent_titre),
                    description = stringResource(R.string.fiche_calendrier_absent_detail),
                    icone = Icons.Outlined.EventNote,
                    modifier = Modifier.heightIn(min = 240.dp),
                )
            }
        }

        state.groupes.forEach { groupe ->
            stickyHeader(key = "groupe-${groupe.ageJours}") {
                EnTeteGroupe(libelleTranche(groupe.ageJours))
            }
            items(items = groupe.lignes, key = { ligne -> ligne.vaccin.id }) { ligne ->
                LigneVaccin(
                    ligne = ligne,
                    doseAttendue = (ligne.statut as? StatutVaccin.EnAttente)
                        ?.let { dosesParId[it.dependDe] },
                    onClic = { onSaisirVaccin(state.enfant.id, ligne.vaccin.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        item(key = "bandeau-source") { BandeauSource(state.infosSource) }
    }
}

/**
 * En-tête de l'enfant : date de naissance, âge du jour et compteurs de la règle R6.
 *
 * L'âge se recalcule tout seul au passage de minuit : il vient de `aujourdHui`, qui est une
 * valeur de l'état et non une lecture d'horloge faite ici.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnteteEnfant(state: FicheEnfantUiState.Pret) {
    val couleurs = FahasalamanaTheme.couleursStatut
    val naissance = stringResource(
        when (state.enfant.sexe) {
            Sexe.GARCON -> R.string.fiche_naissance_garcon
            Sexe.FILLE -> R.string.fiche_naissance_fille
            Sexe.NON_PRECISE -> R.string.fiche_naissance_neutre
        },
        state.enfant.dateNaissance.format(FORMAT_JOUR),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                R.string.fiche_entete_naissance_age,
                naissance,
                libelleAge(state.enfant.dateNaissance, state.aujourdHui),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BadgeResume(
                couleur = couleurs.fait,
                icone = Icons.Outlined.CheckCircle,
                libelle = pluralStringResource(
                    R.plurals.fiche_badge_faits,
                    state.nbFaits,
                    state.nbFaits,
                ),
            )
            if (state.resume.nbAFaire > 0) {
                BadgeResume(
                    couleur = couleurs.aFaire,
                    icone = Icons.Outlined.EventAvailable,
                    libelle = stringResource(R.string.fiche_badge_a_faire, state.resume.nbAFaire),
                )
            }
            if (state.resume.nbEnRetard > 0) {
                BadgeResume(
                    couleur = couleurs.enRetard,
                    icone = Icons.Outlined.PriorityHigh,
                    libelle = stringResource(R.string.fiche_badge_en_retard, state.resume.nbEnRetard),
                )
            }
            // Rien à faire ni en retard : on le dit, plutôt que de laisser un vide à interpréter.
            if (state.resume.nbAFaire == 0 && state.resume.nbEnRetard == 0) {
                BadgeResume(
                    couleur = couleurs.fait,
                    icone = Icons.Outlined.CheckCircle,
                    libelle = stringResource(R.string.fiche_badge_a_jour),
                )
            }
        }

        state.resume.prochaineEcheance?.let { date ->
            Text(
                text = stringResource(R.string.fiche_prochaine_echeance, date.format(FORMAT_JOUR)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** En-tête de tranche d'âge, collant en haut de liste pendant le défilement (`stickyHeader`). */
@Composable
private fun EnTeteGroupe(libelle: String) {
    Surface(
        // Opaque : les lignes défilent dessous, pas au travers.
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = libelle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
        )
    }
}

/**
 * Une dose : pastille, nom, statut écrit et phrase de détail.
 *
 * Toucher la ligne mène à `SaisieVaccin` — y compris pour une dose déjà faite, qui s'y corrige
 * ou s'y supprime (US-B4). `Modifier.clickable` fusionne la sémantique des enfants : TalkBack
 * annonce « Pentavalent — 1re dose, En retard, Prévu le… », puis l'action.
 */
@Composable
private fun LigneVaccin(
    ligne: LigneEcheancier,
    doseAttendue: VaccinReference?,
    onClic: () -> Unit,
) {
    val apparence = apparenceDe(ligne, doseAttendue)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.fiche_action_saisir),
                role = Role.Button,
                onClick = onClic,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pastille(couleur = apparence.couleur, icone = apparence.icone)
        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.fiche_vaccin_titre,
                    ligne.vaccin.nom,
                    ligne.vaccin.dose,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = apparence.libelle,
                style = MaterialTheme.typography.labelLarge,
                // `principale` est la nuance prévue pour un contenu posé sur la surface.
                color = apparence.couleur.principale,
            )
            Text(
                text = apparence.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            apparence.complement?.let { complement ->
                Text(
                    text = complement,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // Décoratif : l'action est déjà annoncée par `onClickLabel` sur la ligne.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Pastille(couleur: CouleurStatut, icone: ImageVector) {
    Surface(
        color = couleur.conteneur,
        contentColor = couleur.surConteneur,
        shape = CircleShape,
        modifier = Modifier.size(40.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icone,
                // Décoratif : le libellé écrit à côté porte la même information.
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BadgeResume(couleur: CouleurStatut, icone: ImageVector, libelle: String) {
    Surface(
        color = couleur.conteneur,
        contentColor = couleur.surConteneur,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = libelle, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Provenance du calendrier en pied de fiche (US-B7) : version, date, mention de démonstration. */
@Composable
private fun BandeauSource(infos: InfosSource?) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.fiche_source_mention),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = infos?.let {
                        stringResource(
                            R.string.fiche_source_version,
                            it.version,
                            it.publieLe.format(FORMAT_JOUR),
                        )
                    } ?: stringResource(R.string.fiche_source_inconnue),
                    style = MaterialTheme.typography.bodySmall,
                )
                infos?.let {
                    Text(text = it.source, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// --- Statuts : le seul endroit qui décide d'une couleur, d'une icône et d'un mot ---------

/**
 * Ce qu'une ligne d'échéancier montre d'un statut.
 *
 * @param libelle nom de l'état, écrit — c'est lui qui rend la couleur facultative.
 * @param detail phrase factuelle avec la date (règle R7).
 * @param complement seconde ligne facultative (fin de fenêtre, délai restant).
 */
@Immutable
private data class ApparenceStatut(
    val couleur: CouleurStatut,
    val icone: ImageVector,
    val libelle: String,
    val detail: String,
    val complement: String? = null,
)

/**
 * **Le `when` exhaustif du projet.** Cinq cas, pas d'`else` : ajouter un sixième
 * `StatutVaccin` casse la compilation ici, et nulle part ailleurs.
 *
 * Règle R7, mot pour mot : « En retard » décrit l'état — c'est le vocabulaire du CDC et du
 * résumé — et la phrase qui l'accompagne dit quoi faire, « Prévu le 12/02/2026 — à faire dès
 * que possible », sans jamais dire à qui la faute.
 *
 * @param doseAttendue dose dont dépend celle-ci, si elle est connue du calendrier : sert à
 *   écrire « après Pentavalent 1re dose » plutôt qu'un identifiant technique.
 */
@Composable
private fun apparenceDe(ligne: LigneEcheancier, doseAttendue: VaccinReference?): ApparenceStatut {
    val couleurs = FahasalamanaTheme.couleursStatut

    return when (val statut = ligne.statut) {
        is StatutVaccin.Fait -> ApparenceStatut(
            couleur = couleurs.fait,
            icone = Icons.Outlined.CheckCircle,
            libelle = stringResource(R.string.fiche_statut_fait),
            detail = stringResource(R.string.fiche_detail_fait, statut.date.format(FORMAT_JOUR)),
        )

        is StatutVaccin.AVenir -> ApparenceStatut(
            couleur = couleurs.aVenir,
            icone = Icons.Outlined.Schedule,
            libelle = stringResource(R.string.fiche_statut_a_venir),
            detail = stringResource(
                R.string.fiche_detail_prevu_le,
                statut.prevuLe.format(FORMAT_JOUR),
            ),
            complement = pluralStringResource(
                R.plurals.fiche_dans_jours,
                statut.dansJours.toInt(),
                statut.dansJours,
            ),
        )

        is StatutVaccin.AFaire -> ApparenceStatut(
            couleur = couleurs.aFaire,
            icone = Icons.Outlined.EventAvailable,
            libelle = stringResource(R.string.fiche_statut_a_faire),
            detail = stringResource(
                R.string.fiche_detail_prevu_le,
                statut.prevuLe.format(FORMAT_JOUR),
            ),
            complement = stringResource(
                R.string.fiche_detail_fenetre,
                statut.jusquAu.format(FORMAT_JOUR),
            ),
        )

        is StatutVaccin.EnRetard -> ApparenceStatut(
            couleur = couleurs.enRetard,
            icone = Icons.Outlined.PriorityHigh,
            libelle = stringResource(R.string.fiche_statut_en_retard),
            detail = stringResource(
                R.string.fiche_detail_en_retard,
                statut.prevuLe.format(FORMAT_JOUR),
            ),
        )

        is StatutVaccin.EnAttente -> {
            val nomDose = doseAttendue?.let {
                stringResource(R.string.fiche_vaccin_titre_court, it.nom, it.dose)
            } ?: stringResource(R.string.fiche_dose_precedente)

            ApparenceStatut(
                couleur = couleurs.enAttente,
                icone = Icons.Outlined.HourglassEmpty,
                libelle = stringResource(R.string.fiche_statut_en_attente),
                // Sans date calculable (chaîne de dépendances cassée), on n'en invente pas.
                detail = ligne.prevuLe?.let {
                    stringResource(
                        R.string.fiche_detail_en_attente,
                        it.format(FORMAT_JOUR),
                        nomDose,
                    )
                } ?: stringResource(R.string.fiche_detail_en_attente_sans_date, nomDose),
            )
        }
    }
}

// --- Durées écrites ----------------------------------------------------------

/**
 * Libellé d'une tranche d'âge : « Naissance », « 6 semaines », « 9 mois » (§B7.2).
 *
 * @param ageJours âge théorique du groupe, `null` quand il n'est pas calculable.
 */
@Composable
private fun libelleTranche(ageJours: Int?): String = when {
    ageJours == null -> stringResource(R.string.fiche_groupe_autres)
    ageJours <= 0 -> stringResource(R.string.fiche_groupe_naissance)
    ageJours < JOURS_MIN_SEMAINES -> pluralStringResource(
        R.plurals.fiche_duree_jours,
        ageJours,
        ageJours,
    )

    ageJours < JOURS_MIN_MOIS -> {
        val semaines = (ageJours / JOURS_PAR_SEMAINE).roundToInt()
        pluralStringResource(R.plurals.fiche_duree_semaines, semaines, semaines)
    }

    else -> {
        val mois = (ageJours / JOURS_PAR_MOIS).roundToInt()
        pluralStringResource(R.plurals.fiche_duree_mois, mois, mois)
    }
}

/** Âge de l'enfant au jour de calcul : « 3 jours », « 8 mois », « 2 ans ». */
@Composable
private fun libelleAge(naissance: LocalDate, aujourdHui: LocalDate): String {
    val jours = ChronoUnit.DAYS.between(naissance, aujourdHui).coerceAtLeast(0L).toInt()
    val mois = ChronoUnit.MONTHS.between(naissance, aujourdHui).coerceAtLeast(0L).toInt()
    val ans = mois / 12

    return when {
        jours == 0 -> stringResource(R.string.fiche_age_aujourdhui)
        ans >= 2 -> pluralStringResource(R.plurals.fiche_duree_ans, ans, ans)
        mois >= 1 -> pluralStringResource(R.plurals.fiche_duree_mois, mois, mois)
        jours >= JOURS_MIN_SEMAINES -> {
            val semaines = (jours / JOURS_PAR_SEMAINE).roundToInt()
            pluralStringResource(R.plurals.fiche_duree_semaines, semaines, semaines)
        }

        else -> pluralStringResource(R.plurals.fiche_duree_jours, jours, jours)
    }
}

// --- Aperçus -----------------------------------------------------------------

/*
 * Les aperçus font tourner la vraie chaîne de calcul (CalculateurEcheancier + grouperParAge)
 * sur un extrait du calendrier embarqué : ce que montre Android Studio est ce que le téléphone
 * affichera, statuts compris.
 */

private val CALENDRIER_APERCU = listOf(
    VaccinReference("bcg", "BCG", "dose unique", 1, 0, null, 30, "À la naissance"),
    VaccinReference("vpo0", "Polio oral", "dose 0", 2, 0, null, 14, "À la naissance"),
    VaccinReference("penta1", "Pentavalent", "1re dose", 3, 42, null, 14, "6 semaines"),
    VaccinReference("vpo1", "Polio oral", "1re dose", 4, 42, null, 14, "6 semaines"),
    VaccinReference(
        "penta2", "Pentavalent", "2e dose", 5, 28, "penta1", 14,
        "4 semaines après la 1re dose",
    ),
    VaccinReference("vpi", "Polio injectable", "dose unique", 6, 98, null, 14, "14 semaines"),
    VaccinReference("rr1", "Rougeole-Rubéole", "1re dose", 7, 270, null, 30, "9 mois"),
    VaccinReference("rr2", "Rougeole-Rubéole", "2e dose", 8, 450, null, 30, "15 mois"),
)

private val SOURCE_APERCU = InfosSource(
    source = "Calendrier de démonstration — projet universitaire. " +
        "À valider auprès du Ministère de la Santé Publique.",
    publieLe = LocalDate.of(2026, 9, 14),
    version = 3,
)

private fun etatDApercu(
    enfant: Enfant,
    administres: List<VaccinAdministre>,
    aujourdHui: LocalDate,
): FicheEnfantUiState.Pret {
    val calc = CalculateurEcheancier()
    val lignes = calc.echeancier(enfant, CALENDRIER_APERCU, administres, aujourdHui)
    return FicheEnfantUiState.Pret(
        enfant = enfant,
        aujourdHui = aujourdHui,
        groupes = grouperParAge(lignes),
        resume = calc.resume(lignes),
        nbFaits = nbFaits(lignes),
        infosSource = SOURCE_APERCU,
    )
}

/** Faly, 9 mois : deux doses faites à la naissance, une série en retard, une fenêtre ouverte. */
private val FALY = Enfant("apercu-faly", "Faly", LocalDate.of(2026, 1, 1), Sexe.GARCON)

private val ETAT_RETARDS = etatDApercu(
    enfant = FALY,
    administres = listOf(
        VaccinAdministre("adm-1", FALY.id, "bcg", LocalDate.of(2026, 1, 2)),
        VaccinAdministre(
            "adm-2", FALY.id, "vpo0", LocalDate.of(2026, 1, 2),
            lieu = "CSB2 Ankirihiry",
        ),
    ),
    aujourdHui = LocalDate.of(2026, 10, 5),
)

/** Soa, 14 jours : rien de saisi, les premières doses sont ouvertes et la suite attend. */
private val ETAT_NOURRISSON = etatDApercu(
    enfant = Enfant("apercu-soa", "Soa", LocalDate.of(2026, 9, 1), Sexe.FILLE),
    administres = emptyList(),
    aujourdHui = LocalDate.of(2026, 9, 15),
)

@Preview(showBackground = true, heightDp = 1200)
@Preview(showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuFicheRetards() {
    FahasalamanaTheme {
        FicheEnfantContenu(
            state = ETAT_RETARDS,
            onRetour = {},
            onModifierEnfant = {},
            onSaisirVaccin = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ApercuFicheNourrisson() {
    FahasalamanaTheme {
        FicheEnfantContenu(
            state = ETAT_NOURRISSON,
            onRetour = {},
            onModifierEnfant = {},
            onSaisirVaccin = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuFicheIntrouvable() {
    FahasalamanaTheme {
        FicheEnfantContenu(
            state = FicheEnfantUiState.Introuvable,
            onRetour = {},
            onModifierEnfant = {},
            onSaisirVaccin = { _, _ -> },
        )
    }
}
