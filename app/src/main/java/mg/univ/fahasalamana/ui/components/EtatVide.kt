package mg.univ.fahasalamana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * État vide partagé : une liste sans contenu, avec une invitation à agir.
 *
 * Tous les écrans passent par ce composant plutôt que d'en réécrire un ; les textes
 * viennent de `strings.xml` et restent factuels, jamais culpabilisants (règle R7).
 *
 * @param titre phrase principale, par exemple « Ajoutez votre premier enfant ».
 * @param description complément facultatif d'une ou deux lignes.
 * @param icone illustration facultative, purement décorative (`contentDescription = null` :
 *   le titre porte déjà l'information pour TalkBack).
 * @param libelleAction libellé du bouton ; le bouton n'apparaît qu'avec [onAction].
 * @param etiquetteAction étiquette de test posée sur le bouton (B23). Elle permet à un écran
 *   de donner **la même** étiquette à son bouton d'état vide et à son bouton habituel — sur
 *   « Mes enfants », le bouton flottant et celui de l'état vide font la même chose et portent
 *   déjà le même libellé : un test n'a pas à savoir dans quel état se trouve l'écran.
 *   Voir [EtiquettesTest] : ce n'est pas une information d'accessibilité.
 */
@Composable
fun EtatVide(
    titre: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icone: ImageVector? = null,
    libelleAction: String? = null,
    etiquetteAction: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icone != null) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = titre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Titre de l'écran vide : marqué comme en-tête pour que TalkBack le trouve par
            // navigation par titres, comme les en-têtes de section des autres écrans.
            modifier = Modifier.semantics { heading() },
        )

        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (libelleAction != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                modifier = if (etiquetteAction != null) Modifier.testTag(etiquetteAction) else Modifier,
            ) {
                Text(libelleAction)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuEtatVide() {
    FahasalamanaTheme {
        EtatVide(
            titre = "Ajoutez votre premier enfant",
            description = "Le carnet se remplit tout seul à partir de la date de naissance.",
            icone = Icons.Outlined.ChildCare,
            libelleAction = "Ajouter un enfant",
            onAction = {},
        )
    }
}
