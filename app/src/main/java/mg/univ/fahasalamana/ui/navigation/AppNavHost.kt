package mg.univ.fahasalamana.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import mg.univ.fahasalamana.R
import kotlin.reflect.KClass

/*
 * Graphe unique à trois branches (CDC §B7.1) : les huit destinations vivent dans le même
 * NavHost, et la barre du bas ne fait que basculer entre les trois racines d'onglet.
 *
 * Pile de retour par onglet : popUpTo(<racine du graphe>) { saveState = true } met de côté
 * la pile de l'onglet quitté, restoreState = true la remonte au retour, et
 * launchSingleTop = true évite d'empiler deux fois la même racine si l'on touche l'onglet
 * déjà sélectionné.
 *
 * Contrat pour les écrans réels (B06 à B18) : chaque écran porte son propre Scaffold et sa
 * TopAppBar. Le Scaffold ci-dessous ne fournit que la barre du bas ; il n'applique aucun
 * encart système en haut, pour ne pas les compter deux fois.
 */

/** Identifiants fictifs des liens de démonstration, le temps que les vrais écrans arrivent. */
private const val ENFANT_DEMO = "demo-enfant"
private const val VACCIN_DEMO = "demo-vaccin"
private const val CENTRE_DEMO = "demo-centre"

/** Un onglet de la barre du bas : sa racine, son libellé et ses deux icônes. */
private data class Onglet(
    val route: Any,
    val classeRoute: KClass<*>,
    @StringRes val libelle: Int,
    val icone: ImageVector,
    val iconeSelectionnee: ImageVector,
)

private val onglets = listOf(
    Onglet(
        route = MesEnfants,
        classeRoute = MesEnfants::class,
        libelle = R.string.onglet_enfants,
        icone = Icons.Outlined.ChildCare,
        iconeSelectionnee = Icons.Filled.ChildCare,
    ),
    Onglet(
        route = Centres,
        classeRoute = Centres::class,
        libelle = R.string.onglet_centres,
        icone = Icons.Outlined.LocalHospital,
        iconeSelectionnee = Icons.Filled.LocalHospital,
    ),
    Onglet(
        route = Reglages,
        classeRoute = Reglages::class,
        libelle = R.string.onglet_reglages,
        icone = Icons.Outlined.Settings,
        iconeSelectionnee = Icons.Filled.Settings,
    ),
)

/**
 * Navigation de l'application : Scaffold avec la barre du bas à trois onglets, et NavHost
 * portant les huit destinations.
 *
 * TODO(B18) : démarrer sur [Verrouillage] quand un code est configuré, en lisant la
 * préférence avant la première composition (sinon l'écran verrouillé clignote).
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val entreeCourante by navController.currentBackStackEntryAsState()
    val destinationCourante = entreeCourante?.destination

    // La barre du bas n'apparaît que sur les trois racines : les écrans de détail et de
    // saisie occupent tout l'écran et se referment par la flèche de retour (wireframes §B7.2).
    val barreVisible = onglets.any { destinationCourante.estDans(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (barreVisible) {
                BarreOnglets(
                    destinationCourante = destinationCourante,
                    onOngletChoisi = { onglet -> navController.allerAOnglet(onglet) },
                )
            }
        },
        // Les encarts système sont laissés aux écrans, qui ont leur propre Scaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { interieur ->
        NavHost(
            navController = navController,
            startDestination = MesEnfants,
            modifier = Modifier
                .padding(interieur)
                .consumeWindowInsets(interieur),
        ) {
            // --- Onglet Enfants ---

            composable<MesEnfants> {
                EcranProvisoire(
                    nomEcran = "MesEnfants",
                    tache = "B06",
                    liens = listOf(
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "EditionEnfant"),
                            onClic = { navController.navigate(EditionEnfant()) },
                        ),
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "FicheEnfant"),
                            onClic = { navController.navigate(FicheEnfant(enfantId = ENFANT_DEMO)) },
                        ),
                    ),
                )
            }

            composable<EditionEnfant> { entree ->
                val route = entree.toRoute<EditionEnfant>()
                EcranProvisoire(
                    nomEcran = "EditionEnfant",
                    tache = "B07",
                    arguments = listOf("enfantId" to route.enfantId),
                    onRetour = { navController.navigateUp() },
                )
            }

            // TODO(B10) : deep link fahasalamana://enfant/{enfantId} sur cette destination,
            // avec l'intent-filter correspondant dans AndroidManifest.xml et la reconstruction
            // de la pile MesEnfants -> FicheEnfant depuis la notification (CDC §B7.1).
            composable<FicheEnfant> { entree ->
                val route = entree.toRoute<FicheEnfant>()
                EcranProvisoire(
                    nomEcran = "FicheEnfant",
                    tache = "B08",
                    arguments = listOf("enfantId" to route.enfantId),
                    liens = listOf(
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "SaisieVaccin"),
                            onClic = {
                                navController.navigate(
                                    SaisieVaccin(enfantId = route.enfantId, vaccinId = VACCIN_DEMO),
                                )
                            },
                        ),
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "EditionEnfant"),
                            onClic = { navController.navigate(EditionEnfant(enfantId = route.enfantId)) },
                        ),
                    ),
                    onRetour = { navController.navigateUp() },
                )
            }

            composable<SaisieVaccin> { entree ->
                val route = entree.toRoute<SaisieVaccin>()
                EcranProvisoire(
                    nomEcran = "SaisieVaccin",
                    tache = "B09",
                    arguments = listOf(
                        "enfantId" to route.enfantId,
                        "vaccinId" to route.vaccinId,
                    ),
                    onRetour = { navController.navigateUp() },
                )
            }

            // --- Onglet Centres ---

            composable<Centres> {
                EcranProvisoire(
                    nomEcran = "Centres",
                    tache = "B13",
                    liens = listOf(
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "DetailCentre"),
                            onClic = { navController.navigate(DetailCentre(centreId = CENTRE_DEMO)) },
                        ),
                    ),
                )
            }

            composable<DetailCentre> { entree ->
                val route = entree.toRoute<DetailCentre>()
                EcranProvisoire(
                    nomEcran = "DetailCentre",
                    tache = "B14",
                    arguments = listOf("centreId" to route.centreId),
                    onRetour = { navController.navigateUp() },
                )
            }

            // --- Onglet Réglages ---

            composable<Reglages> {
                EcranProvisoire(
                    nomEcran = "Reglages",
                    tache = "B15",
                    liens = listOf(
                        LienProvisoire(
                            libelle = stringResource(R.string.ecran_provisoire_ouvrir, "Verrouillage"),
                            onClic = { navController.navigate(Verrouillage) },
                        ),
                    ),
                )
            }

            // --- Hors onglets ---

            composable<Verrouillage> {
                EcranProvisoire(
                    nomEcran = "Verrouillage",
                    tache = "B18",
                    onRetour = { navController.navigateUp() },
                )
            }
        }
    }
}

@Composable
private fun BarreOnglets(
    destinationCourante: NavDestination?,
    onOngletChoisi: (Onglet) -> Unit,
) {
    NavigationBar {
        onglets.forEach { onglet ->
            val selectionne = destinationCourante.estDans(onglet)
            NavigationBarItem(
                selected = selectionne,
                onClick = { onOngletChoisi(onglet) },
                icon = {
                    Icon(
                        imageVector = if (selectionne) onglet.iconeSelectionnee else onglet.icone,
                        // Le libellé sous l'icône porte déjà l'information pour TalkBack.
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(onglet.libelle)) },
                alwaysShowLabel = true,
            )
        }
    }
}

/** Vrai quand cette destination est la racine de [onglet] ou l'une de ses filles. */
private fun NavDestination?.estDans(onglet: Onglet): Boolean =
    this?.hierarchy?.any { it.hasRoute(onglet.classeRoute) } == true

/**
 * Bascule vers la racine d'un onglet en gardant une pile de retour par onglet
 * (CDC §B7.1 : saveState / restoreState).
 */
private fun NavHostController.allerAOnglet(onglet: Onglet) {
    navigate(onglet.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
