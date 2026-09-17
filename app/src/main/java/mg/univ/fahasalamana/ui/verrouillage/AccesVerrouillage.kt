package mg.univ.fahasalamana.ui.verrouillage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.platform.EtatVerrouillage
import mg.univ.fahasalamana.platform.GardienVerrouillage
import org.koin.compose.koinInject

/*
 * Les deux briques que `AppNavHost` utilise pour lever son `TODO(B18)` (tâche B18, §B7.1).
 *
 * Elles vivent ici et non dans `ui/navigation/` pour une raison simple : `AppNavHost.kt` est
 * le fichier le plus exposé aux conflits du binôme, et la modification qu'il reçoit en B18
 * se réduit ainsi à quelques lignes lisibles en revue, sans aucune logique de verrouillage
 * à l'intérieur.
 */

/**
 * État courant du verrou, lu depuis l'unique [GardienVerrouillage] du processus.
 *
 * `collectAsStateWithLifecycle` et non `collectAsState` : c'est la convention du projet
 * (CLAUDE.md, règle 3), et la collecte s'arrête quand l'activité n'est plus visible.
 */
@Composable
fun etatVerrouillageCourant(gardien: GardienVerrouillage = koinInject()): EtatVerrouillage {
    val etat by gardien.etat.collectAsStateWithLifecycle()
    return etat
}

/**
 * Ce qui est affiché tant que [EtatVerrouillage.Indetermine] dure, c'est-à-dire le temps de
 * lire une préférence DataStore au lancement.
 *
 * **Volontairement vide.** C'est la réponse au `TODO(B18)` de `AppNavHost` : afficher le
 * carnet puis poser le verrou par-dessus le montrerait une fraction de seconde, et afficher
 * l'écran de code puis le retirer le ferait clignoter chez tous ceux qui n'ont pas de code.
 * Une surface de la couleur du thème, sans indicateur de chargement, se confond avec l'écran
 * de démarrage et ne se remarque pas.
 */
@Composable
fun EcranAvantVerrouillage(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {}
}
