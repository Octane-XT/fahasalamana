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
import androidx.compose.runtime.LaunchedEffect
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
import mg.univ.fahasalamana.platform.EtatVerrouillage
import mg.univ.fahasalamana.ui.fiche.FicheEnfantScreen
import mg.univ.fahasalamana.ui.saisie.SaisieVaccinScreen
import mg.univ.fahasalamana.ui.verrouillage.EcranAvantVerrouillage
import mg.univ.fahasalamana.ui.verrouillage.VerrouillageScreen
import mg.univ.fahasalamana.ui.verrouillage.etatVerrouillageCourant
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.ui.edition.EditionEnfantScreen
import mg.univ.fahasalamana.ui.enfants.MesEnfantsScreen
import mg.univ.fahasalamana.ui.reglages.ReglagesScreen
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
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // (B18) Verrou lu avant que le NavHost n'existe, donc avant que sa destination de départ
    // ne soit figée. Tant que la préférence n'est pas lue, on n'affiche ni le carnet ni
    // l'écran de code : sinon l'écran verrouillé clignoterait par-dessus le carnet.
    val etatVerrou = etatVerrouillageCourant()
    if (etatVerrou == EtatVerrouillage.Indetermine) {
        EcranAvantVerrouillage(modifier)
        return
    }

    val entreeCourante by navController.currentBackStackEntryAsState()
    val destinationCourante = entreeCourante?.destination

    // (B18) Reprise après plus de deux minutes hors de l'application : le gardien repasse à
    // Verrouille et l'écran de code se pose par-dessus la pile, sans la vider — on retombe
    // donc sur l'écran qu'on avait quitté, une fois le code saisi.
    LaunchedEffect(etatVerrou) {
        val destination = navController.currentDestination ?: return@LaunchedEffect
        if (etatVerrou == EtatVerrouillage.Verrouille && !destination.hasRoute(Verrouillage::class)) {
            navController.navigate(Verrouillage) { launchSingleTop = true }
        }
    }

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
            startDestination = if (etatVerrou == EtatVerrouillage.Verrouille) Verrouillage else MesEnfants,
            modifier = Modifier
                .padding(interieur)
                .consumeWindowInsets(interieur),
        ) {
            // --- Onglet Enfants ---

            // (B06) Premier écran réel de l'onglet Enfants. Il ne navigue pas lui-même :
            // il reçoit deux lambdas, la navigation restant l'affaire de ce fichier.
            composable<MesEnfants> {
                MesEnfantsScreen(
                    onAjouterEnfant = { navController.navigate(EditionEnfant()) },
                    onOuvrirEnfant = { enfantId ->
                        navController.navigate(FicheEnfant(enfantId = enfantId))
                    },
                )
            }

            // (B07) Création quand `enfantId` est nul, modification sinon. L'argument n'est pas
            // lu ici : le ViewModel le récupère par SavedStateHandle.toRoute<EditionEnfant>().
            //
            // La sortie après suppression ne peut pas être un simple `navigateUp()` : on
            // arrive sur cet écran depuis la fiche de l'enfant, qui est encore dans la pile et
            // afficherait « Introuvable ». On remonte donc jusqu'à la liste.
            composable<EditionEnfant> {
                EditionEnfantScreen(
                    onRetour = { navController.navigateUp() },
                    onEnregistre = { navController.navigateUp() },
                    onSupprime = { navController.popBackStack(route = MesEnfants, inclusive = false) },
                )
            }

            // TODO(B10) : deep link fahasalamana://enfant/{enfantId} sur cette destination,
            // avec l'intent-filter correspondant dans AndroidManifest.xml et la reconstruction
            // de la pile MesEnfants -> FicheEnfant depuis la notification (CDC §B7.1).
            composable<FicheEnfant> {
                FicheEnfantScreen(
                    onRetour = { navController.navigateUp() },
                    onModifierEnfant = { enfantId -> navController.navigate(EditionEnfant(enfantId)) },
                    onSaisirVaccin = { enfantId, vaccinId ->
                        navController.navigate(SaisieVaccin(enfantId = enfantId, vaccinId = vaccinId))
                    },
                )
            }

            composable<SaisieVaccin> {
                SaisieVaccinScreen(
                    onRetour = { navController.navigateUp() },
                    onTermine = { navController.navigateUp() },
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
                ReglagesScreen(
                    onOuvrirCodeVerrouillage = { navController.navigate(Verrouillage) },
                )
            }

            // --- Hors onglets ---

            composable<Verrouillage> {
                VerrouillageScreen(
                    onRetour = { navController.navigateUp() },
                    onTermine = {
                        // Deux sorties : l'écran a été empilé (verrou en cours de session, ou
                        // arrivée depuis les Réglages) et il suffit de le dépiler ; ou il est
                        // la destination de départ d'un démarrage verrouillé, et il n'y a rien
                        // derrière — on le remplace alors, pour qu'un retour n'y ramène pas.
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(MesEnfants) {
                                popUpTo(Verrouillage) { inclusive = true }
                            }
                        }
                    },
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
