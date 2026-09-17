package mg.univ.fahasalamana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * « Un code oublié ne peut pas être récupéré » : l'avertissement du verrouillage par code
 * (B18), écrit une seule fois.
 *
 * Deux écrans l'affichent, aux deux seuls moments où il sert — **avant** que le code
 * n'existe, jamais après :
 *
 *  - `ReglagesScreen`, sous l'interrupteur tant que le verrouillage n'est pas activé ;
 *  - `VerrouillageScreen`, au-dessus du champ à l'étape « choisir un nouveau code ».
 *
 * Il était écrit en double, avec les mêmes clés de texte et la même surface, et pour seule
 * différence l'icône, présente d'un côté et pas de l'autre. C'est le motif qui avait déjà
 * fait diverger l'écriture de l'âge (voir [texteAge]) et l'étiquette de type d'un centre
 * (voir [EtiquetteType]), tous deux remontés ici pour la même raison : deux copies d'un
 * texte finissent par ne plus dire la même chose, et celui-ci est le seul de l'application
 * dont l'inexactitude coûterait à l'utilisateur ses données.
 *
 * L'icône fait partie du composant, elle n'est pas un paramètre : un avertissement qui
 * change d'apparence d'un écran à l'autre se lit comme deux avertissements différents.
 *
 * @param modifier laissé à l'appelant pour la seule mise en page — l'écran Réglages le
 *   loge dans une carte et lui donne sa marge, l'écran Verrouillage le pose dans une
 *   colonne déjà espacée.
 */
@Composable
fun EncartOubliCode(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                // Décoratif : le titre juste à côté porte déjà l'avertissement, et TalkBack
                // annoncerait sinon « avertissement » avant de lire la même chose en clair.
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

@Preview(showBackground = true)
@Composable
private fun ApercuEncartOubliCode() {
    FahasalamanaTheme {
        EncartOubliCode(modifier = Modifier.padding(16.dp))
    }
}
