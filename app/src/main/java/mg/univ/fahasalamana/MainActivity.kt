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
 * **Lien profond (B10)** : rien à écrire ici, et c'est le but. L'intent-filter du manifeste
 * envoie `fahasalamana://enfant/{enfantId}` vers cette activité, et c'est le `NavHost` qui
 * le consomme au moment où il installe son graphe, en empilant `MesEnfants` sous
 * `FicheEnfant` (CDC §B7.1). Deux conditions à cela, tenues ailleurs :
 *  - la destination `FicheEnfant` déclare le lien (`navDeepLink`) dans `AppNavHost` ;
 *  - la notification ouvre une tâche neuve (`FLAG_ACTIVITY_CLEAR_TASK`, voir
 *    `NotificationHelper`), donc l'intent arrive toujours par `onCreate`.
 *
 * Sans cette seconde condition il faudrait intercepter `onNewIntent` et appeler
 * `navController.handleDeepLink(intent)` — or le `NavController` est créé dans `AppNavHost`,
 * hors de portée d'ici.
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
