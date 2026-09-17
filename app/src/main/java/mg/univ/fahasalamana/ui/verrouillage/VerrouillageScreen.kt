package mg.univ.fahasalamana.ui.verrouillage

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.domain.ErreurPin
import mg.univ.fahasalamana.domain.LONGUEUR_PIN_MAX
import mg.univ.fahasalamana.domain.LONGUEUR_PIN_MIN
import mg.univ.fahasalamana.ui.components.EtatChargement
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme
import org.koin.androidx.compose.koinViewModel

/*
 * Écran Verrouillage (tâche B18, CDC §B7.1, US-B10).
 *
 * Trois usages, un seul écran, le mode étant décidé par le ViewModel (voir sa documentation) :
 * ouvrir le carnet verrouillé, choisir un premier code, changer le code existant.
 *
 * Ce que cet écran tient, point par point :
 *
 * 1. **Rien du carnet n'est visible derrière.** En mode ouverture il occupe tout l'écran,
 *    sans barre d'onglets ni flèche de retour, et le geste de retour arrière met
 *    l'application en arrière-plan au lieu de le contourner (voir [BackHandler] plus bas).
 * 2. **La rotation ne perd rien.** Les chiffres saisis vivent dans le ViewModel ; seul
 *    l'œil « afficher le code », qui n'est qu'un état visuel, est gardé par `rememberSaveable`.
 * 3. **L'avertissement sur le code oublié est au-dessus du champ**, à l'étape où l'on choisit
 *    le code — donc avant de l'avoir choisi, et non après l'avoir enregistré.
 * 4. **Aucun décompte de tentatives, aucun reproche** (R7) : un code faux donne « Ce code ne
 *    correspond pas », le champ est vidé, et c'est tout.
 */

@Composable
fun VerrouillageScreen(
    onRetour: () -> Unit,
    onTermine: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VerrouillageViewModel = koinViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    val termine = (state as? VerrouillageUiState.Saisie)?.termine == true
    LaunchedEffect(termine) {
        if (termine) onTermine()
    }

    VerrouillageContenu(
        state = state,
        onRetour = onRetour,
        onCodeChange = vm::onCodeChange,
        onValider = vm::onValider,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerrouillageContenu(
    state: VerrouillageUiState,
    onRetour: () -> Unit,
    onCodeChange: (String) -> Unit,
    onValider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saisie = state as? VerrouillageUiState.Saisie
    val modeOuverture = saisie?.mode == ModeVerrouillage.Ouverture

    // Carnet verrouillé : le retour arrière ne doit pas révéler ce qui est derrière. On met
    // l'application en arrière-plan, comme le ferait n'importe quel écran de verrouillage ;
    // `finish()` serait plus brutal sans rien protéger de plus, et un gestionnaire qui ne
    // fait rien du tout donnerait l'impression que le téléphone est bloqué.
    val contexte = LocalContext.current
    BackHandler(enabled = modeOuverture) {
        contexte.activiteHote()?.moveTaskToBack(true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(saisie?.let { stringResource(titreDe(it.mode)) }.orEmpty()) },
                navigationIcon = {
                    // Pas de flèche quand le carnet est verrouillé : il n'y a nulle part où
                    // revenir, et une flèche inerte se lit comme une panne.
                    if (saisie != null && !modeOuverture) {
                        IconButton(onClick = onRetour) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_retour),
                            )
                        }
                    }
                },
            )
        },
    ) { interieur ->
        when (state) {
            VerrouillageUiState.Chargement -> EtatChargement(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(interieur),
            )

            is VerrouillageUiState.Saisie -> FormulaireCode(
                state = state,
                onCodeChange = onCodeChange,
                onValider = onValider,
                modifier = Modifier.padding(interieur),
            )
        }
    }
}

// --- Formulaire --------------------------------------------------------------

@Composable
private fun FormulaireCode(
    state: VerrouillageUiState.Saisie,
    onCodeChange: (String) -> Unit,
    onValider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // État purement visuel : il vit dans l'écran, pas dans le ViewModel qui ne porte que la
    // saisie. `rememberSaveable` suffit — un booléen d'affichage n'est pas un secret.
    var codeVisible by rememberSaveable { mutableStateOf(false) }

    // Le clavier s'ouvre sur le champ dès l'arrivée et à chaque changement d'étape : sur cet
    // écran, il n'y a rien d'autre à faire que taper.
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.etape) { focus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            // Décoratif : le titre de la barre du haut et la consigne portent l'information.
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = consigneDe(state.etape),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )

        // Avertissement au moment de choisir le code, donc avant de l'avoir choisi : c'est la
        // seule place utile pour dire qu'un code oublié ne se récupère pas.
        if (state.etape == EtapeCode.NouveauCode) {
            AvertissementOubli()
        }

        val messageErreur = state.erreur?.let { messageDe(it) }

        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            singleLine = true,
            enabled = !state.verificationEnCours && !state.termine,
            label = { Text(stringResource(R.string.verrouillage_champ_label)) },
            isError = messageErreur != null,
            visualTransformation = if (codeVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onValider() }),
            trailingIcon = {
                IconButton(onClick = { codeVisible = !codeVisible }) {
                    Icon(
                        imageVector = if (codeVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (codeVisible) {
                                R.string.verrouillage_champ_masquer
                            } else {
                                R.string.verrouillage_champ_afficher
                            },
                        ),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )

        if (messageErreur != null) {
            Text(
                text = messageErreur,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                // TalkBack annonce le message sans qu'il faille repartir explorer l'écran :
                // le champ vient d'être vidé, l'utilisateur ne sait pas pourquoi.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Button(
            onClick = onValider,
            enabled = state.peutValider,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.verificationEnCours) {
                // La dérivation du code prend quelques centaines de millisecondes par
                // construction (voir `ITERATIONS_PBKDF2`) : sans cet indicateur, l'utilisateur
                // croirait que son appui n'a pas été pris en compte et appuierait à nouveau.
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.verrouillage_verification_en_cours))
            } else {
                Text(stringResource(libelleActionDe(state.etape)))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Encart d'avertissement : ce que l'utilisateur perd s'il oublie son code. */
@Composable
private fun AvertissementOubli() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
}

// --- Correspondances texte ---------------------------------------------------

/** `when` sans `else` : un quatrième mode casserait la compilation ici plutôt qu'à l'exécution. */
@StringRes
private fun titreDe(mode: ModeVerrouillage): Int = when (mode) {
    ModeVerrouillage.Ouverture -> R.string.verrouillage_titre_ouverture
    ModeVerrouillage.Creation -> R.string.verrouillage_titre_creation
    ModeVerrouillage.Modification -> R.string.verrouillage_titre_modification
}

@Composable
private fun consigneDe(etape: EtapeCode): String = when (etape) {
    EtapeCode.CodeActuel -> stringResource(R.string.verrouillage_consigne_code_actuel)
    EtapeCode.AncienCode -> stringResource(R.string.verrouillage_consigne_ancien_code)
    EtapeCode.NouveauCode -> stringResource(
        R.string.verrouillage_consigne_nouveau_code,
        LONGUEUR_PIN_MIN,
        LONGUEUR_PIN_MAX,
    )

    EtapeCode.Confirmation -> stringResource(R.string.verrouillage_consigne_confirmation)
}

@StringRes
private fun libelleActionDe(etape: EtapeCode): Int = when (etape) {
    EtapeCode.CodeActuel -> R.string.verrouillage_action_ouvrir
    EtapeCode.AncienCode -> R.string.verrouillage_action_continuer
    EtapeCode.NouveauCode -> R.string.verrouillage_action_continuer
    EtapeCode.Confirmation -> R.string.verrouillage_action_enregistrer
}

@Composable
private fun messageDe(erreur: ErreurSaisiePin): String = when (erreur) {
    ErreurSaisiePin.CodeIncorrect -> stringResource(R.string.verrouillage_erreur_incorrect)
    ErreurSaisiePin.ConfirmationDifferente -> stringResource(R.string.verrouillage_erreur_confirmation)
    ErreurSaisiePin.EchecEnregistrement -> stringResource(R.string.verrouillage_erreur_enregistrement)
    is ErreurSaisiePin.Format -> messageDeFormat(erreur.cause)
}

@Composable
private fun messageDeFormat(cause: ErreurPin): String = when (cause) {
    ErreurPin.Vide -> stringResource(R.string.verrouillage_erreur_vide)
    ErreurPin.CaracteresInterdits -> stringResource(R.string.verrouillage_erreur_chiffres)
    is ErreurPin.TropCourt -> stringResource(R.string.verrouillage_erreur_trop_court, cause.minimum)
    is ErreurPin.TropLong -> stringResource(R.string.verrouillage_erreur_trop_long, cause.maximum)
}

/**
 * L'activité qui héberge ce composable, à travers les éventuels `ContextWrapper` posés par le
 * thème.
 *
 * `LocalActivity` rendrait ce détour inutile, mais il n'existe qu'à partir de
 * `activity-compose` 1.10 et le catalogue de versions du projet est en 1.9.3 (B00).
 */
private tailrec fun Context.activiteHote(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activiteHote()
    else -> null
}

// --- Aperçus -----------------------------------------------------------------

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApercuVerrouillageOuverture() {
    FahasalamanaTheme {
        VerrouillageContenu(
            state = VerrouillageUiState.Saisie(
                mode = ModeVerrouillage.Ouverture,
                etape = EtapeCode.CodeActuel,
                code = "12",
            ),
            onRetour = {},
            onCodeChange = {},
            onValider = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuVerrouillageCodeIncorrect() {
    FahasalamanaTheme {
        VerrouillageContenu(
            state = VerrouillageUiState.Saisie(
                mode = ModeVerrouillage.Ouverture,
                etape = EtapeCode.CodeActuel,
                erreur = ErreurSaisiePin.CodeIncorrect,
            ),
            onRetour = {},
            onCodeChange = {},
            onValider = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ApercuVerrouillageCreation() {
    FahasalamanaTheme {
        VerrouillageContenu(
            state = VerrouillageUiState.Saisie(
                mode = ModeVerrouillage.Creation,
                etape = EtapeCode.NouveauCode,
                code = "1234",
            ),
            onRetour = {},
            onCodeChange = {},
            onValider = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuVerrouillageConfirmationDifferente() {
    FahasalamanaTheme {
        VerrouillageContenu(
            state = VerrouillageUiState.Saisie(
                mode = ModeVerrouillage.Modification,
                etape = EtapeCode.Confirmation,
                code = "123456",
                verificationEnCours = true,
            ),
            onRetour = {},
            onCodeChange = {},
            onValider = {},
        )
    }
}
