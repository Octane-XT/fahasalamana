package mg.univ.fahasalamana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * État d'erreur partagé : ce qui n'a pas marché, et quoi faire ensuite.
 *
 * Ton factuel (règle R7) : on décrit la situation, on ne reproche rien à l'utilisateur.
 * Aucune donnée personnelle (prénom, date de naissance) ne doit apparaître dans [message].
 *
 * @param message description de la panne, par exemple « Le fichier n'a pas pu être lu ».
 * @param titre phrase d'accroche ; « Une erreur est survenue » par défaut.
 * @param onReessayer si fourni, affiche un bouton de nouvelle tentative.
 *
 *   **Ne le fournir que si l'écran sait vraiment relancer quelque chose.** Les états
 *   d'erreur du projet naissent tous d'un `catch` posé sur un `Flow` Room, et ce `catch`
 *   termine la chaîne : l'amont est annulé, il ne réémettra pas de lui-même. Un bouton
 *   branché sur une lambda vide serait donc pire que pas de bouton — il promettrait une
 *   relance qui n'a pas lieu. Relancer demande de rouvrir une collecte, ce que fait
 *   `MesEnfantsViewModel.onReessayer` (`flatMapLatest` sur un compteur de relances).
 *
 *   Les autres écrans l'omettent volontairement, et pour une raison qui leur est propre :
 *   leur état d'erreur laisse une sortie — la flèche de retour de leur barre du haut, ou
 *   l'onglet voisin. « Mes enfants » est le seul où l'erreur retire aussi le bouton
 *   d'ajout, donc la seule impasse, et c'est le seul qui passe cette lambda. Un écran qui
 *   gagnerait une vraie relance peut la fournir à son tour ; le paramètre est là pour ça,
 *   pas pour être rempli par habitude.
 */
@Composable
fun EtatErreur(
    message: String,
    modifier: Modifier = Modifier,
    titre: String = stringResource(R.string.erreur_titre),
    libelleReessayer: String = stringResource(R.string.action_reessayer),
    onReessayer: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = titre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Même traitement que le titre de l'état vide : en-tête pour la navigation TalkBack.
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (onReessayer != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onReessayer) {
                Text(libelleReessayer)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuEtatErreur() {
    FahasalamanaTheme {
        EtatErreur(
            message = "Le carnet n'a pas pu être lu.",
            onReessayer = {},
        )
    }
}
