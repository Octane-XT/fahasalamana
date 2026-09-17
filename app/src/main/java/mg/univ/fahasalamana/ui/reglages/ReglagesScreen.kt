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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import mg.univ.fahasalamana.ui.components.EncartOubliCode
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.components.EtatErreur
import mg.univ.fahasalamana.ui.export.LigneExportCarnet
import mg.univ.fahasalamana.ui.importation.EtatReplanification
import mg.univ.fahasalamana.ui.importation.LigneImportCarnet
import mg.univ.fahasalamana.ui.importation.RappelsReplanifies
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Écran Réglages (tâches B15, B18 et B19, CDC §B7.2).
 *
 * Trois blocs viennent de B15 et B18 :
 *  - « Données de référence » : source, date et version du calendrier (US-B7), avec la
 *    mention de démonstration mise en évidence et non enfouie dans un paragraphe ;
 *  - « Confidentialité » : ce que l'application fait des données de santé (§B8, point 7) ;
 *  - « Sécurité » : le code de verrouillage (US-B10, B18).
 *
 * Le bloc « Carnet » est réel depuis B16 (export) et B17 (import) : ses deux lignes sont des
 * blocs autonomes, posés ici en un appel chacun, avec leur propre ViewModel. Le texte de
 * confidentialité décrit ce que le code fait aujourd'hui, et rien de plus : il est lu en
 * soutenance.
 *
 * (B18) **Le bloc « Sécurité » se lit avant d'être utilisé.** Trois phrases entourent
 * l'interrupteur : ce que le code déclenche, ce qu'il protège et ce qu'il ne protège pas, et
 * ce qui arrive si on l'oublie. Cette dernière est affichée **tant que le code n'est pas
 * activé**, c'est-à-dire au moment où la décision se prend, et non après.
 *
 * (B19) « Vérifier les mises à jour » est actif et rend un compte rendu ligne à ligne, un
 * contenu de référence par ligne. Il est posé **sous** la carte des données de référence et
 * non dedans, pour rester atteignable quand le calendrier est illisible. Ce bouton est le
 * **seul** endroit d'où l'application emprunte le réseau : il n'y a ni vérification au
 * démarrage, ni tâche de fond, et c'est ce que dit `maj_description` juste en dessous.
 *
 * (B10) Le bloc « Rappels » est ajouté en bas de page par `BlocRappelsDebug()`, qui n'existe
 * qu'en build debug : il porte la notification de test de la Definition of Done. L'interrupteur
 * d'activation des notifications, lui, reste à faire — TODO(B12), en même temps que le
 * branchement de `replanifier` sur ses déclencheurs, l'activation étant l'un d'eux (§B8).
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
        onVerifierMisesAJour = vm::onVerifierMisesAJour,
        onResultatMiseAJourFerme = vm::onResultatMiseAJourFerme,
        onReessayerRappels = vm::onReessayerRappels,
        onOuvrirCodeVerrouillage = onOuvrirCodeVerrouillage,
        onDesactiverVerrouillage = vm::onDesactiverVerrouillage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReglagesContenu(
    state: ReglagesUiState,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
    onReessayerRappels: () -> Unit,
    onOuvrirCodeVerrouillage: () -> Unit,
    onDesactiverVerrouillage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.reglages_titre),
                        // Titre d'écran : en-tête pour la navigation par titres de TalkBack.
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
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
                etat = state.reference,
                miseAJour = state.miseAJour,
                onVerifierMisesAJour = onVerifierMisesAJour,
                onResultatMiseAJourFerme = onResultatMiseAJourFerme,
                onReessayerRappels = onReessayerRappels,
            )
            BlocConfidentialite()
            BlocCarnet()
            BlocSecurite(
                verrouillageActif = state.verrouillageActif,
                onOuvrirCodeVerrouillage = onOuvrirCodeVerrouillage,
                onDesactiverVerrouillage = onDesactiverVerrouillage,
            )
            // (B10) Notification de test, en dernier et en build debug seulement : la
            // version publiée appelle le jumeau vide de `src/release/` et n'affiche rien.
            BlocRappelsDebug()
            // Respiration en bas de page : la barre d'onglets est juste en dessous.
            Spacer(Modifier.height(8.dp))
        }
    }
}

// --- Bloc « Données de référence » (US-B7) -----------------------------------

/**
 * Section « Données de référence » : ce qui est en base, puis de quoi le mettre à jour.
 *
 * (B19) Le bouton et son compte rendu sont posés **sous** le `when` et non dans la carte :
 * un calendrier illisible est précisément le moment où l'on vient chercher une mise à jour,
 * et c'est elle qui peut réparer la situation. Les enfermer dans la branche [EtatReference.Pret]
 * les ferait disparaître au seul moment où ils servent vraiment — et ferait de l'erreur de
 * lecture du calendrier une panne de tout le bloc, ce que le `catch` du ViewModel refuse.
 */
@Composable
private fun BlocDonneesReference(
    etat: EtatReference,
    miseAJour: EtatMiseAJour,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
    onReessayerRappels: () -> Unit,
) {
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

        ActionMiseAJour(
            miseAJour = miseAJour,
            onVerifierMisesAJour = onVerifierMisesAJour,
            onResultatMiseAJourFerme = onResultatMiseAJourFerme,
            onReessayerRappels = onReessayerRappels,
        )
    }
}

/** Ce qui est chargé sur ce téléphone : calendrier, annuaire, dernière vérification. */
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
        }
    }
}

// --- Recherche de mise à jour (B19, US-B11) ----------------------------------

/**
 * Le bouton « Vérifier les mises à jour », son avancement et son compte rendu.
 *
 * Sous la carte et non dedans : le compte rendu doit rester lisible quand le bloc de
 * référence est en erreur — il est même la seule chose qui puisse encore expliquer pourquoi,
 * et le bouton la seule qui puisse y remédier.
 */
@Composable
private fun ActionMiseAJour(
    miseAJour: EtatMiseAJour,
    onVerifierMisesAJour: () -> Unit,
    onResultatMiseAJourFerme: () -> Unit,
    onReessayerRappels: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            PanneauMiseAJour(
                resultat = resultat,
                rappels = miseAJour.rappels,
                onFermer = onResultatMiseAJourFerme,
                onReessayerRappels = onReessayerRappels,
            )
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
    rappels: EtatReplanification?,
    onFermer: () -> Unit,
    onReessayerRappels: () -> Unit,
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

            // Un nouveau calendrier déplace les dates prévues : le parent doit savoir ce que
            // ses rappels sont devenus. Bloc réutilisé tel quel de B17, où il dit exactement
            // la même chose après un import.
            //
            // Piloté par `rappels` et non par `resultat.calendrierRemplace` : ce dernier dit
            // seulement qu'une replanification a été **tentée**, et c'est précisément la
            // confusion qui faisait annoncer des rappels recalculés que le ViewModel savait
            // ratés. `null` — aucune replanification à faire — n'écrit rien du tout.
            RappelsReplanifies(
                etat = rappels,
                detailEchec = stringResource(R.string.maj_rappels_echec_calendrier),
                onReessayer = onReessayerRappels,
            )

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
 *
 * La mention et son explication se lisent d'un bloc pour TalkBack (B23) : c'est une seule
 * phrase de responsabilité (§B1), la couper en deux arrêts laisserait entendre la mention
 * sans l'explication qui la justifie.
 */
@Composable
private fun MentionDemonstration() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
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

// --- Bloc « Carnet » : export (B16) et import (B17) --------------------------

@Composable
private fun BlocCarnet() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitreSection(stringResource(R.string.reglages_section_carnet))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Les apercus n'ont ni Koin ni LocalActivityResultRegistryOwner : ces deux
                // lignes resolvent un ViewModel et un lanceur d'activite, elles leveraient
                // dans le volet Design. Meme garde que BlocRappelsDebug.
                if (!LocalInspectionMode.current) {
                    LigneExportCarnet()
                    LigneImportCarnet()
                }
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
                    // Même encart, au mot près, que l'étape « choisir un code » de l'écran
                    // Verrouillage : il est écrit une seule fois, dans `ui/components`.
                    EncartOubliCode(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
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

// L'encart « un code oublié ne se récupère pas » vivait ici et, aux mêmes clés de texte,
// dans `VerrouillageScreen`. Il est remonté dans `ui/components/EncartOubliCode.kt`, où il
// a aussi gagné l'icône que seule l'autre copie portait.

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

/** Les aperçus ne branchent rien : les quatre lambdas de B18 et B19 y sont vides. */
@Composable
private fun ApercuReglages(state: ReglagesUiState) {
    FahasalamanaTheme {
        ReglagesContenu(
            state = state,
            onVerifierMisesAJour = {},
            onResultatMiseAJourFerme = {},
            onReessayerRappels = {},
            onOuvrirCodeVerrouillage = {},
            onDesactiverVerrouillage = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 2000)
@Preview(showBackground = true, heightDp = 2000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuReglagesPret() {
    ApercuReglages(ReglagesUiState(reference = EtatReference.Pret(ReferenceDApercu)))
}

/** Le bloc « Sécurité » une fois le code posé : plus d'avertissement, et « Modifier le code ». */
@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun ApercuReglagesVerrouillageActif() {
    ApercuReglages(
        ReglagesUiState(
            reference = EtatReference.Pret(ReferenceDApercu),
            verrouillageActif = true,
        ),
    )
}

/** Annuaire jamais chargé et une vérification déjà passée : les deux lignes changent. */
@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun ApercuReglagesAnnuaireAbsent() {
    ApercuReglages(
        ReglagesUiState(
            reference = EtatReference.Pret(
                ReferenceDApercu.copy(
                    versionAnnuaire = null,
                    derniereVerification = LocalDate.of(2026, 9, 15),
                ),
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ApercuReglagesChargement() {
    ApercuReglages(ReglagesUiState(reference = EtatReference.Chargement))
}

/**
 * Calendrier illisible : le bloc « Sécurité » reste utilisable, c'est tout l'intérêt — et le
 * bouton de mise à jour aussi, qui est ce qui peut réparer le calendrier.
 */
@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun ApercuReglagesErreur() {
    ApercuReglages(
        ReglagesUiState(reference = EtatReference.Erreur, verrouillageActif = true),
    )
}

// --- Aperçus de la mise à jour (B19) -----------------------------------------

/** La section « Données de référence » seule, pour voir le compte rendu sans dérouler la page. */
@Composable
private fun ApercuBlocReference(miseAJour: EtatMiseAJour) {
    FahasalamanaTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            BlocDonneesReference(
                etat = EtatReference.Pret(ReferenceDApercu),
                miseAJour = miseAJour,
                onVerifierMisesAJour = {},
                onResultatMiseAJourFerme = {},
                onReessayerRappels = {},
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 620)
@Composable
private fun ApercuMiseAJourEnCours() {
    ApercuBlocReference(EtatMiseAJour(enCours = true))
}

/** Le cas de la soutenance : nouveau calendrier, annuaire déjà à jour. */
@Preview(showBackground = true, heightDp = 760)
@Preview(showBackground = true, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuMiseAJourCalendrierRemplace() {
    ApercuBlocReference(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.Remplace(
                    versionPrecedente = 3,
                    version = 4,
                    publieLe = LocalDate.of(2026, 10, 20),
                ),
                annuaire = IssueMiseAJour.DejaAJour(version = 2),
            ),
            rappels = EtatReplanification.Reussie,
        ),
    )
}

/**
 * Le cas que le défaut cachait : le calendrier est installé, la replanification a échoué. Le
 * panneau reste celui d'une réussite — la mise à jour, elle, a bien eu lieu — mais il le dit
 * et propose le seul geste qui y remédie.
 */
@Preview(showBackground = true, heightDp = 820)
@Composable
private fun ApercuMiseAJourRappelsEnEchec() {
    ApercuBlocReference(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.Remplace(
                    versionPrecedente = 3,
                    version = 4,
                    publieLe = LocalDate.of(2026, 10, 20),
                ),
                annuaire = IssueMiseAJour.DejaAJour(version = 2),
            ),
            rappels = EtatReplanification.Echouee,
        ),
    )
}

/** Second essai en cours : le compte rendu reste lisible et dit ce qu'il est en train de faire. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ApercuMiseAJourRappelsEnCours() {
    ApercuBlocReference(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.Remplace(
                    versionPrecedente = 3,
                    version = 4,
                    publieLe = LocalDate.of(2026, 10, 20),
                ),
                annuaire = IssueMiseAJour.DejaAJour(version = 2),
            ),
            rappels = EtatReplanification.EnCours,
        ),
    )
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ApercuMiseAJourRienDeNouveau() {
    ApercuBlocReference(
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
    ApercuBlocReference(EtatMiseAJour(resultat = ResultatSync.echecTotal()))
}

/** Le cas mixte, celui qui justifie deux issues plutôt qu'une. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ApercuMiseAJourPartielle() {
    ApercuBlocReference(
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
    ApercuBlocReference(
        EtatMiseAJour(
            resultat = ResultatSync(
                calendrier = IssueMiseAJour.SchemaInconnu(schemaVersion = 2),
                annuaire = IssueMiseAJour.SchemaInconnu(schemaVersion = 2),
            ),
        ),
    )
}
