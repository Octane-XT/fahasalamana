package mg.univ.fahasalamana.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * État de chargement partagé : un indicateur circulaire et un message.
 *
 * Le message est lu par TalkBack ; l'indicateur lui-même est purement décoratif, sa
 * sémantique est effacée pour ne pas annoncer deux fois la même chose.
 *
 * @param message texte affiché sous l'indicateur ; « Chargement… » par défaut.
 */
@Composable
fun EtatChargement(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.chargement_en_cours),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.clearAndSetSemantics { },
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ApercuEtatChargement() {
    FahasalamanaTheme {
        EtatChargement()
    }
}
