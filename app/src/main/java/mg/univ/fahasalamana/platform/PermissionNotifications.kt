package mg.univ.fahasalamana.platform

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.data.local.PreferencesLocales
import org.koin.compose.koinInject

/**
 * Explication puis demande de `POST_NOTIFICATIONS` (Android 13+), tâche B10.
 *
 * **Où cette demande est placée, et pourquoi** — le CDC (§B8) dit « au premier ajout
 * d'enfant » ; concrètement, l'écran `EditionEnfant` l'appelle **après l'enregistrement
 * réussi d'une création**, juste avant de revenir à la liste. Trois raisons :
 *
 * 1. *Le moment a un sens.* À cette seconde précise, l'utilisateur vient de créer
 *    l'échéancier sur lequel porteront les rappels : il y a quelque chose à rappeler, et la
 *    question « voulez-vous être prévenu ? » se répond toute seule. Posée au démarrage de
 *    l'application, devant un carnet vide, elle n'aurait aucun contexte.
 * 2. *Un refus est presque définitif.* Depuis Android 13, le système n'affiche plus sa boîte
 *    après deux refus : la demande est un coup à ne pas gâcher. On ne la dépense donc pas
 *    sur un écran de démarrage, où l'on refuse par réflexe.
 * 3. *L'enfant est déjà enregistré.* La demande ne bloque rien : quelle que soit la réponse,
 *    et même si l'utilisateur ferme la boîte, l'écran se referme normalement et l'enfant est
 *    en base. La permission est un bonus, jamais un péage.
 *
 * L'explication maison précède toujours la boîte système, conformément au §B8 : l'utilisateur
 * lit d'abord *ce que* l'application enverra et *ce qui reste masqué sur l'écran verrouillé*,
 * et seulement ensuite une boîte Android à deux boutons, qui n'explique rien par elle-même.
 *
 * @param declenchee passe à `true` quand le parcours atteint le point de demande. Tant que
 *   c'est `false`, ce composable n'affiche rien, ne lit rien et n'injecte rien — ce qui
 *   permet aussi aux `@Preview` de l'écran appelant de fonctionner sans Koin.
 * @param onTermine appelé exactement une fois par déclenchement, quelle que soit l'issue —
 *   permission accordée, refusée, demande inutile ou reportée. C'est l'appelant qui décide
 *   de la suite (ici : quitter l'écran).
 */
@Composable
fun DemandeNotificationsRappels(
    declenchee: Boolean,
    onTermine: () -> Unit,
) {
    if (!declenchee) return
    DialogueEtDemande(onTermine = onTermine)
}

/**
 * Le corps de la demande, séparé pour n'entrer en composition qu'une fois déclenché.
 *
 * Tout est neuf à chaque entrée — le drapeau du dialogue, le lanceur de permission, la
 * lecture des préférences —, ce qui rend le déroulement lisible : une entrée, une question,
 * un [onTermine].
 */
@Composable
private fun DialogueEtDemande(
    onTermine: () -> Unit,
    preferences: PreferencesLocales = koinInject(),
) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()

    // `null` tant que DataStore n'a pas rendu sa première valeur : on ne décide rien avant,
    // sinon on redemanderait le temps de la lecture du fichier à quelqu'un qui a déjà répondu.
    val dejaProposee: Boolean? by preferences.notificationsDemandeFaite
        .collectAsStateWithLifecycle(initialValue = null)

    // `rememberSaveable` : une rotation pendant que la boîte est ouverte ne doit pas la
    // faire disparaître — ni, pire, relancer la demande une seconde fois.
    var dialogueVisible by rememberSaveable { mutableStateOf(false) }

    val lanceur = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Accordée ou refusée, on continue : l'application fonctionne sans notifications,
        // simplement sans rappels. Rien à annoncer à quelqu'un qui vient de répondre.
        dialogueVisible = false
        onTermine()
    }

    LaunchedEffect(dejaProposee) {
        if (dialogueVisible) return@LaunchedEffect
        val proposee = dejaProposee ?: return@LaunchedEffect

        val inutile = !permissionNotificationsRequise() ||
            permissionNotificationsAccordee(contexte) ||
            proposee
        if (inutile) onTermine() else dialogueVisible = true
    }

    if (!dialogueVisible) return

    AlertDialog(
        // Toucher à côté de la boîte, c'est « pas maintenant » : on n'insiste pas, et on ne
        // marque rien, donc la question pourra revenir au prochain enfant ajouté.
        onDismissRequest = {
            dialogueVisible = false
            onTermine()
        },
        icon = { Icon(imageVector = Icons.Outlined.NotificationsActive, contentDescription = null) },
        title = { Text(stringResource(R.string.notif_permission_titre)) },
        text = { Text(stringResource(R.string.notif_permission_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    // Marqué **avant** d'ouvrir la boîte système, et sur ce chemin-là
                    // seulement : c'est le seul instant où la composition survivra sûrement
                    // à l'écriture (la boîte Android la maintient à l'écran), et « Plus
                    // tard » ne doit pas consommer la question pour toujours.
                    portee.launch { preferences.marquerDemandeNotificationsFaite() }
                    lanceur.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            ) {
                Text(stringResource(R.string.notif_permission_activer))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dialogueVisible = false
                    onTermine()
                },
            ) {
                Text(stringResource(R.string.notif_permission_plus_tard))
            }
        },
    )
}
