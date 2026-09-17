package mg.univ.fahasalamana.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Jumeau vide de `src/debug/java/.../BlocRappelsDebug.kt` (B10).
 *
 * L'écran Réglages appelle `BlocRappelsDebug()` sans condition ; c'est le source set choisi
 * par le type de build qui décide s'il se passe quelque chose. Dans la version publiée,
 * cette fonction ne compose rien, et ni le bouton de test, ni les textes de `src/debug/res/`
 * n'entrent dans l'APK.
 *
 * Pourquoi ce détour plutôt qu'un `if (BuildConfig.DEBUG)` dans l'écran : la condition
 * disparaît du code lu par le relecteur, et surtout aucune chaîne de démonstration
 * (« Faly », « Pentavalent 1re dose ») ne se retrouve dans les ressources d'une version
 * livrée à un utilisateur.
 *
 * **Sa signature doit rester identique à celle de la version debug.**
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BlocRappelsDebug(modifier: Modifier = Modifier) = Unit
