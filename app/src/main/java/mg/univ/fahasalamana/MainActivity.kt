package mg.univ.fahasalamana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mg.univ.fahasalamana.ui.navigation.AppNavHost
import mg.univ.fahasalamana.ui.theme.FahasalamanaTheme

/**
 * Activité unique : l'application est entièrement en Compose.
 *
 * Elle ne fait que poser le thème et la navigation ; tout le reste vit dans les écrans.
 *
 * TODO(B10) : traiter l'intent de deep link fahasalamana://enfant/{id} venant d'une
 * notification de rappel, en reconstruisant la pile MesEnfants -> FicheEnfant.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord : obligatoire à partir de targetSdk 35, et le Scaffold de chaque
        // écran gère alors lui-même les encarts système.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FahasalamanaTheme {
                AppNavHost()
            }
        }
    }
}
