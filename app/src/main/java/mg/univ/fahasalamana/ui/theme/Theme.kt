package mg.univ.fahasalamana.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

internal val SchemaClair = lightColorScheme(
    primary = PrimaireClair,
    onPrimary = SurPrimaireClair,
    primaryContainer = PrimaireConteneurClair,
    onPrimaryContainer = SurPrimaireConteneurClair,
    secondary = SecondaireClair,
    onSecondary = SurSecondaireClair,
    secondaryContainer = SecondaireConteneurClair,
    onSecondaryContainer = SurSecondaireConteneurClair,
    tertiary = TertiaireClair,
    onTertiary = SurTertiaireClair,
    tertiaryContainer = TertiaireConteneurClair,
    onTertiaryContainer = SurTertiaireConteneurClair,
    error = ErreurClair,
    onError = SurErreurClair,
    errorContainer = ErreurConteneurClair,
    onErrorContainer = SurErreurConteneurClair,
    background = FondClair,
    onBackground = SurFondClair,
    surface = SurfaceClair,
    onSurface = SurSurfaceClair,
    surfaceVariant = SurfaceVarianteClair,
    onSurfaceVariant = SurSurfaceVarianteClair,
    outline = ContourClair,
    outlineVariant = ContourVarianteClair,
    inverseSurface = SurfaceInverseClair,
    inverseOnSurface = SurSurfaceInverseClair,
    inversePrimary = PrimaireInverseClair,
)

internal val SchemaSombre = darkColorScheme(
    primary = PrimaireSombre,
    onPrimary = SurPrimaireSombre,
    primaryContainer = PrimaireConteneurSombre,
    onPrimaryContainer = SurPrimaireConteneurSombre,
    secondary = SecondaireSombre,
    onSecondary = SurSecondaireSombre,
    secondaryContainer = SecondaireConteneurSombre,
    onSecondaryContainer = SurSecondaireConteneurSombre,
    tertiary = TertiaireSombre,
    onTertiary = SurTertiaireSombre,
    tertiaryContainer = TertiaireConteneurSombre,
    onTertiaryContainer = SurTertiaireConteneurSombre,
    error = ErreurSombre,
    onError = SurErreurSombre,
    errorContainer = ErreurConteneurSombre,
    onErrorContainer = SurErreurConteneurSombre,
    background = FondSombre,
    onBackground = SurFondSombre,
    surface = SurfaceSombre,
    onSurface = SurSurfaceSombre,
    surfaceVariant = SurfaceVarianteSombre,
    onSurfaceVariant = SurSurfaceVarianteSombre,
    outline = ContourSombre,
    outlineVariant = ContourVarianteSombre,
    inverseSurface = SurfaceInverseSombre,
    inverseOnSurface = SurSurfaceInverseSombre,
    inversePrimary = PrimaireInverseSombre,
)

/**
 * Couleurs de statut du composant courant, injectées par [FahasalamanaTheme].
 *
 * Les écrans passent par `FahasalamanaTheme.couleursStatut` plutôt que par ce
 * `CompositionLocal` directement.
 */
val LocalCouleursStatut = staticCompositionLocalOf { CouleursStatutClair }

/**
 * Thème de l'application : schéma Material 3 clair ou sombre, typographie, et couleurs
 * de statut des vaccins.
 *
 * @param themeSombre suit le réglage du système par défaut ; forcé dans les `@Preview`.
 * @param couleursDynamiques Material You (Android 12+). **Désactivé par défaut** : le
 *   code couleur des statuts (vert / orange / rouge) doit rester lisible et identique
 *   d'un téléphone à l'autre, y compris pendant la démonstration de soutenance.
 */
@Composable
fun FahasalamanaTheme(
    themeSombre: Boolean = isSystemInDarkTheme(),
    couleursDynamiques: Boolean = false,
    content: @Composable () -> Unit,
) {
    val schema = when {
        couleursDynamiques && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexte = LocalContext.current
            if (themeSombre) dynamicDarkColorScheme(contexte) else dynamicLightColorScheme(contexte)
        }

        themeSombre -> SchemaSombre
        else -> SchemaClair
    }
    val couleursStatut = if (themeSombre) CouleursStatutSombre else CouleursStatutClair

    CompositionLocalProvider(LocalCouleursStatut provides couleursStatut) {
        MaterialTheme(
            colorScheme = schema,
            typography = Typographie,
            content = content,
        )
    }
}

/**
 * Accès aux valeurs du thème propres à l'application, sur le modèle de `MaterialTheme`.
 *
 * Usage dans un écran : `FahasalamanaTheme.couleursStatut.enRetard.conteneur`.
 */
object FahasalamanaTheme {
    val couleursStatut: CouleursStatut
        @Composable
        @ReadOnlyComposable
        get() = LocalCouleursStatut.current
}
