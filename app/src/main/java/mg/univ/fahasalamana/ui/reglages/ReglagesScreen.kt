package mg.univ.fahasalamana.ui.reglages

import android.content.res.Configuration
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.repository.IssueMiseAJour
import mg.univ.fahasalamana.data.repository.ResultatSync
import mg.univ.fahasalamana.platform.BlocRappelsDebug
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.export.LigneExportCarnet
import mg.univ.fahasalamana.ui.importation.LigneImportCarnet
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran Réglages, version 0 (tâche B15, CDC §B7.2).
 *
 * Deux blocs sont réels :
 *  - « Données de référence » : source, date et version du calendrier (US-B7), avec la
 *    mention de démonstration mise en évidence et non enfouie dans un paragraphe ;
 *  - « Confidentialité » : ce que l'application fait des données de santé (§B8, point 7).
 *
 * Le bloc « Carnet » est réel depuis B16 (export) et B17 (import) : ses deux lignes sont des
 * blocs autonomes, posés ici en un appel chacun, avec leur propre ViewModel. Seul le bloc
 * « Sécurité » reste une place réservée : son entrée est visiblement inactive et annoncée
 * comme telle à TalkBack, plutôt que branchée sur un écran vide. Le texte de confidentialité
 * décrit ce que le code fait aujourd'hui, et rien de plus : il est lu en soutenance.
 *
 * (B19) « Vérifier les mises à jour » est actif depuis B19 et rend un compte rendu ligne à
 * ligne, un contenu de référence par ligne. Ce bouton est le **seul** endroit d'où
 * l'application emprunte le réseau : il n'y a ni vérification au démarrage, ni tâche de fond,
 * et c'est ce que dit `maj_description` juste en dessous.
 *
 * (B10) Le bloc « Rappels » est ajouté en bas de page par `BlocRappelsDebug()`, qui n'existe
 * qu'en build debug : il porte la notification de test de la Definition of Done. L'interrupteur
 * d'activation des notifications, lui, reste à faire — TODO(B12), en même temps que le
 * branchement de `replanifier` sur ses déclencheurs, l'activation étant l'un d'eux (§B8).
 */

/** Dates affichées en jour/mois/année, comme dans les wireframes (§B7.2). */
private val FORMAT_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

@Composable
fun ReglagesScreen(
    modifier: Modifier = Modifier,
    vm: ReglagesViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ReglagesContenu(
        state = state,
        onVerifierMisesAJour = vm::onVerifierMisesAJour,
        onResultatMiseAJourFerme = vm::onResultatMiseAJourFerme,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReglagesContenu(
    state: ReglagesUiState,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
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
            BlocDonneesReference(
                state = state,
                onVerifierMisesAJour = onVerifierMisesAJour,
                onResultatMiseAJourFerme = onResultatMiseAJourFerme,
            )
            BlocConfidentialite()
            BlocCarnet()
            BlocSecurite()
            // (B10) Notification de test, en dernier et en build debug seulement : la
            // version publiée appelle le jumeau vide de `src/release/` et n'affiche rien.
            BlocRappelsDebug()
            // Respiration en bas de page : la barre d'onglets est juste en dessous.
            Spacer(Modifier.height(8.dp))
        }
    }
}

// --- Bloc « Données de référence » (US-B7) -----------------------------------

@Composable
private fun BlocDonneesReference(
    state: ReglagesUiState,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_reference))

        when (state) {
            ReglagesUiState.Chargement -> EtatChargement(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
            )

            ReglagesUiState.Erreur -> EtatErreur(
                message = stringResource(R.string.reglages_reference_erreur),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
            )

            is ReglagesUiState.Pret -> CarteDonneesReference(
                donnees = state.reference,
                miseAJour = state.miseAJour,
                onVerifierMisesAJour = onVerifierMisesAJour,
                onResultatMiseAJourFerme = onResultatMiseAJourFerme,
            )
        }
    }
}

@Composable
private fun CarteDonneesReference(
    donnees: DonneesReference,
    miseAJour: EtatMiseAJour,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
) {
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

            // (B19) Le bouton est actif. Il reste inactif pendant une vérification en
            // cours : un second appui relancerait un téléchargement de 115 Ko pour rien.
            OutlinedButton(
                onClick = onVerifierMisesAJour,
                enabled = !miseAJour.enCours,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (miseAJour.enCours) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringResource(R.string.reglages_action_verifier))
            }

            Text(
                // L'avancement passe par le texte et pas seulement par l'indicateur qui
                // tourne : une animation ne s'annonce pas à TalkBack (même règle qu'en B17).
                text = if (miseAJour.enCours) {
                    stringResource(R.string.maj_en_cours)
                } else {
                    stringResource(R.string.maj_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            miseAJour.resultat?.let { resultat ->
                PanneauMiseAJour(resultat = resultat, onFermer = onResultatMiseAJourFerme)
            }
        }
    }
}

// --- Compte rendu de la mise à jour (B19, US-B11) ----------------------------

/**
 * Ce qui vient de se passer, un contenu de référence par ligne.
 *
 * **Pas un `Snackbar`**, pour les mêmes raisons qu'en B16 et B17 : le compte rendu se lit
 * ligne à ligne (« calendrier mis à jour, annuaire injoignable »), quatre secondes n'y
 * suffisent pas, et le `Scaffold` de cet écran n'a pas de `SnackbarHost`.
 *
 * `liveRegion` : TalkBack annonce le compte rendu dès qu'il apparaît, sans que le parent
 * ait à partir à sa recherche.
 */
@Composable
private fun PanneauMiseAJour(
    resultat: ResultatSync,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Trois apparences, dans l'ordre où on les regarde : quelque chose a changé, rien
    // n'avait changé, rien n'a pu être vérifié. Le cas mixte (un contenu remplacé, l'autre
    // en échec) prend le premier : c'est la nouvelle, et le détail est juste en dessous.
    val couleurFond = when {
        resultat.contenuRemplace -> MaterialTheme.colorScheme.secondaryContainer
        resultat.toutEtaitAJour -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val couleurTexte = when {
        resultat.contenuRemplace -> MaterialTheme.colorScheme.onSecondaryContainer
        resultat.toutEtaitAJour -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val titre = when {
        resultat.contenuRemplace -> stringResource(R.string.maj_titre_installee)
        resultat.toutEtaitAJour -> stringResource(R.string.maj_titre_a_jour)
        else -> stringResource(R.string.maj_titre_echec)
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

            // Les deux contenus sont annoncés séparément, toujours, et dans l'ordre de la
            // carte au-dessus : l'un peut réussir et l'autre échouer.
            Text(
                text = ligneMiseAJour(
                    nomContenu = stringResource(R.string.reglages_calendrier_titre),
                    issue = resultat.calendrier,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = ligneMiseAJour(
                    nomContenu = stringResource(R.string.reglages_annuaire_titre),
                    issue = resultat.annuaire,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Un nouveau calendrier déplace les dates prévues : le parent doit savoir que
            // ses rappels ont suivi et qu'il n'a rien à faire de plus. Phrase réutilisée
            // telle quelle de B17, où elle dit exactement la même chose après un import.
            if (resultat.calendrierRemplace) {
                Text(
                    text = stringResource(R.string.import_rappels_recalcules),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Ce que la mise à jour a touché, et ce qu'elle n'a pas touché (§B8, règle 8 de
            // CLAUDE.md). Seulement après un remplacement réel : c'est le seul moment où la
            // question se pose.
            if (resultat.contenuRemplace) {
                Text(
                    text = stringResource(R.string.maj_donnees_personnelles),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // `export_fermer` réutilisé tel quel : même mot, même geste (B16, B17).
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
 * La phrase qui décrit ce qu'est devenu **un** contenu de référence.
 *
 * `when` sans `else` sur un `sealed interface` (règle 5 de CLAUDE.md) : le jour où une
 * sixième issue sera ajoutée à `IssueMiseAJour`, le compilateur signalera cet endroit au
 * lieu de laisser une ligne vide à l'écran.
 */
@Composable
private fun ligneMiseAJour(nomContenu: String, issue: IssueMiseAJour): String = when (issue) {
    is IssueMiseAJour.DejaAJour ->
        stringResource(R.string.maj_ligne_a_jour, nomContenu, issue.version)

    is IssueMiseAJour.Remplace -> if (issue.versionPrecedente == PreferencesLocales.VERSION_ABSENTE) {
        // Aucun contenu n'était chargé : parler d'une « version 0 » remplacée n'aurait
        // aucun sens pour le parent, et 0 n'est pas une version publiée (§B5.1).
        stringResource(R.string.maj_ligne_premiere_installation, nomContenu, issue.version)
    } else {
        stringResource(
            R.string.maj_ligne_installee,
            nomContenu,
            issue.version,
            issue.versionPrecedente,
        )
    }

    IssueMiseAJour.Echec -> stringResource(R.string.maj_ligne_echec, nomContenu)

    IssueMiseAJour.FormatInvalide ->
        stringResource(R.string.maj_ligne_format_invalide, nomContenu)

    is IssueMiseAJour.SchemaInconnu ->
        stringResource(R.string.maj_ligne_schema_inconnu, nomContenu, issue.schemaVersion)
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

// --- Carnet et sécurité ------------------------------------------------------

@Composable
private fun BlocCarnet() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_carnet))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                LigneExportCarnet()
                LigneImportCarnet()
            }
        }
    }
}

@Composable
private fun BlocSecurite() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_securite))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // TODO(B18) : création/modification du code, puis navigation vers Verrouillage.
                LigneReservee(
                    icone = Icons.Outlined.Lock,
                    titre = stringResource(R.string.reglages_code_verrouillage),
                )
            }
        }
    }
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

/** Les aperçus ne branchent rien : les deux lambdas de B19 y sont vides. */
@Composable
private fun ApercuReglages(state: ReglagesUiState) {
    FahasalamanaTheme {
        ReglagesContenu(
            state = state,
            onVerifierMisesAJour = {},
            onResultatMiseAJourFerme = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Preview(showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuReglagesPret() {
    ApercuReglages(ReglagesUiState.Pret(ReferenceDApercu))
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ApercuReglagesAnnuaireAbsent() {
    ApercuReglages(
        ReglagesUiState.Pret(
            ReferenceDApercu.copy(
                versionAnnuaire = null,
                derniereVerification = LocalDate.of(2026, 9, 15),
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ApercuReglagesChargement() {
    ApercuReglages(ReglagesUiState.Chargement)
}

@Preview(showBackground = true)
@Composable
private fun ApercuReglagesErreur() {
    ApercuReglages(ReglagesUiState.Erreur)
}

// --- Aperçus de la mise à jour (B19) -----------------------------------------

/** La carte des données de référence seule, pour voir le compte rendu sans dérouler la page. */
@Composable
private fun ApercuCarte(miseAJour: EtatMiseAJour) {
    FahasalamanaTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CarteDonneesReference(
                donnees = ReferenceDApercu,
                miseAJour = miseAJour,
                onVerifierMisesAJour = {},
                onResultatMiseAJourFerme = {},
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 620)
@Composable
private fun ApercuMiseAJourEnCours() {
    ApercuCarte(EtatMiseAJour(enCours = true))
}

/** Le cas de la soutenance : nouveau calendrier, annuaire déjà à jour. */
@Preview(showBackground = true, heightDp = 760)
@Preview(showBackground = true, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuMiseAJourCalendrierRemplace() {
    ApercuCarte(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.Remplace(
                    versionPrecedente = 3,
                    version = 4,
                    publieLe = LocalDate.of(2026, 10, 20),
                ),
                annuaire = IssueMiseAJour.DejaAJour(version = 2),
            ),
        ),
    )
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuMiseAJourRienDeNouveau() {
    ApercuCarte(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.DejaAJour(version = 3),
                annuaire = IssueMiseAJour.DejaAJour(version = 2),
            ),
        ),
    )
}

/** Téléphone hors réseau : les deux lignes disent que rien n'a changé. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuMiseAJourEchec() {
    ApercuCarte(EtatMiseAJour(resultat = ResultatSync.echecTotal()))
}

/** Le cas mixte, celui qui justifie deux issues plutôt qu'une. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ApercuMiseAJourPartielle() {
    ApercuCarte(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.FormatInvalide,
                annuaire = IssueMiseAJour.Remplace(
                    versionPrecedente = 2,
                    version = 3,
                    publieLe = LocalDate.of(2026, 10, 20),
                ),
            ),
        ),
    )
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun ApercuMiseAJourSchemaInconnu() {
    ApercuCarte(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.SchemaInconnu(schemaVersion = 2),
                annuaire = IssueMiseAJour.SchemaInconnu(schemaVersion = 2),
            ),
        ),
    )
}
