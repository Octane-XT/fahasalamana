package mg.univ.fahasalamana.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * Le niveau d'un centre (« CSB1 », « CSB2 », « CHRD »…), affiché tel qu'il est publié.
 *
 * Partagé par la liste des centres (B13) et par la fiche d'un centre (B14). Écrit d'abord
 * en double dans les deux écrans, puis remonté ici : c'est exactement la divergence qu'a
 * connue l'affichage de l'âge (voir le commentaire de `age_mois` dans `strings.xml`), où
 * deux copies du même texte avaient fini par ne plus dire la même chose.
 *
 * Le type est un `String` et non une énumération (voir `domain/Annuaire.kt`) : l'annuaire
 * peut publier un niveau inconnu de cette version de l'application, et il doit alors
 * s'afficher tel quel plutôt que faire échouer la lecture.
 *
 * `clearAndSetSemantics` remplace « CSB2 », que TalkBack épellerait, par une phrase
 * lisible ; le texte visible, lui, ne change pas.
 */
@Composable
fun EtiquetteType(type: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.centres_type_description, type)

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = type,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuEtiquetteType() {
    FahasalamanaTheme {
        EtiquetteType(type = "CSB2")
    }
}
