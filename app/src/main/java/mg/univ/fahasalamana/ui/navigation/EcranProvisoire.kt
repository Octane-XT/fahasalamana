package mg.univ.fahasalamana.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * Un bouton de l'écran provisoire, qui mène à une autre destination.
 *
 * Sert uniquement à vérifier à la main les piles de retour tant que les vrais écrans
 * n'existent pas.
 */
@Immutable
data class LienProvisoire(
    val libelle: String,
    val onClic: () -> Unit,
)

/**
 * Écran bouche-trou : affiche le nom de la destination, ses arguments de navigation et la
 * tâche qui la remplacera.
 *
 * Provisoire par construction. Chaque écran réel (B06 à B18) supprime son appel dans
 * [AppNavHost] ; ce fichier disparaît quand le dernier `EcranProvisoire` a été remplacé.
 *
 * @param nomEcran nom de la route, affiché tel quel (identifiant de développement).
 * @param tache identifiant de la tâche du CDC §B10.2 qui livrera l'écran, par exemple `B06`.
 * @param arguments couples nom/valeur lus depuis la route typée, pour vérifier le passage
 *   d'arguments de bout en bout.
 * @param liens destinations joignables depuis ici, pour empiler des écrans dans l'onglet.
 * @param onRetour affiche une flèche de retour quand la destination n'est pas une racine
 *   d'onglet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranProvisoire(
    nomEcran: String,
    tache: String,
    modifier: Modifier = Modifier,
    arguments: List<Pair<String, String?>> = emptyList(),
    liens: List<LienProvisoire> = emptyList(),
    onRetour: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(nomEcran) },
                navigationIcon = {
                    if (onRetour != null) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(interieur)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ecran_provisoire_tache, tache),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.ecran_provisoire_arguments),
                style = MaterialTheme.typography.titleMedium,
            )
            if (arguments.isEmpty()) {
                Text(
                    text = stringResource(R.string.ecran_provisoire_sans_argument),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                arguments.forEach { (nom, valeur) ->
                    Text(
                        text = nom + " = " + (valeur ?: "null"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (liens.isNotEmpty()) {
                HorizontalDivider()
                liens.forEach { lien ->
                    Button(
                        onClick = lien.onClic,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(lien.libelle)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuEcranProvisoire() {
    FahasalamanaTheme {
        EcranProvisoire(
            nomEcran = "FicheEnfant",
            tache = "B08",
            arguments = listOf("enfantId" to "demo-enfant"),
            liens = listOf(LienProvisoire(libelle = "Ouvrir SaisieVaccin", onClic = {})),
            onRetour = {},
        )
    }
}
