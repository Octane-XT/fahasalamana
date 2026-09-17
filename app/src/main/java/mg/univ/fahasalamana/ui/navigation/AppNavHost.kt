package mg.univ.fahasalamana.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.navigation.navDeepLink
import mg.univ.fahasalamana.platform.BASE_LIEN_ENFANT
import mg.univ.fahasalamana.ui.centres.CentresScreen
import mg.univ.fahasalamana.ui.detailcentre.DetailCentreScreen
import mg.univ.fahasalamana.platform.EtatVerrouillage
import mg.univ.fahasalamana.ui.fiche.FicheEnfantScreen
import mg.univ.fahasalamana.ui.saisie.SaisieVaccinScreen
import mg.univ.fahasalamana.ui.verrouillage.EcranAvantVerrouillage
import mg.univ.fahasalamana.ui.verrouillage.VerrouillageScreen
import mg.univ.fahasalamana.ui.verrouillage.etatVerrouillageCourant
import androidx.navigation.compose.rememberNavController
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
    // (B18) Verrou lu avant que le NavHost n'existe. Tant que la préférence n'est pas lue, on
    // n'affiche ni le carnet ni l'écran de code : sinon l'écran verrouillé clignoterait
    // par-dessus le carnet.
    val etatVerrou = etatVerrouillageCourant()
    if (etatVerrou == EtatVerrouillage.Indetermine) {
        EcranAvantVerrouillage(modifier)
        return
    }

    val entreeCourante by navController.currentBackStackEntryAsState()
    val destinationCourante = entreeCourante?.destination
    val surEcranDeCode = destinationCourante?.hasRoute(Verrouillage::class) == true

    // (B18) Tout le verrouillage tient dans un invariant : **carnet verrouillé => écran de
    // code au sommet de la pile**. L'écran de code est *empilé* par-dessus ce qui est là, jamais
    // substitué à la pile : au démarrage à froid il couvre la racine `MesEnfants` (§B7.1), et
    // après plus de deux minutes hors de l'application il couvre l'écran qu'on avait quitté,
    // qu'on retrouve intact — saisie en cours comprise — une fois le code saisi.
    //
    // L'effet est relancé à chaque changement de destination, et pas seulement à chaque
    // changement de verrou : sans cela, quitter l'écran de code sans déverrouiller laisserait
    // le carnet à découvert jusqu'à la prochaine bascule du gardien. Le cas existe — on
    // arrive aussi sur cet écran depuis les Réglages, où il porte une flèche de retour.
    LaunchedEffect(etatVerrou, entreeCourante) {
        val destination = navController.currentDestination ?: return@LaunchedEffect
        if (etatVerrou == EtatVerrouillage.Verrouille && !destination.hasRoute(Verrouillage::class)) {
            navController.navigate(Verrouillage) { launchSingleTop = true }
        }
    }

    // L'effet ci-dessus s'exécute après la composition, donc après une image dessinée : sans
    // ce masque, le carnet apparaîtrait le temps d'une image avant l'écran de code, au
    // démarrage à froid comme au reverrouillage. C'est exactement ce que la surface neutre
    // d'`EcranAvantVerrouillage` évite déjà pendant `Indetermine`, pour la même raison.
    val carnetMasque = etatVerrou == EtatVerrouillage.Verrouille && !surEcranDeCode

    // La barre du bas n'apparaît que sur les trois racines : les écrans de détail et de
    // saisie occupent tout l'écran et se referment par la flèche de retour (wireframes §B7.2).
    val barreVisible = onglets.any { destinationCourante.estDans(it) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                // (B18) La destination de départ ne dépend **pas** du verrou, et c'est ce qui fait
                // tenir ensemble les deux exigences du §B7.1. Calculée depuis `etatVerrou`, elle
                // changeait de valeur au reverrouillage ; le NavHost reconstruisait alors son
                // graphe, et un graphe neuf vide la pile de retour. Deux conséquences, contraires
                // au commentaire qui promettait le contraire : la saisie de vaccin en cours était
                // perdue au retour, et un lien profond reçu carnet verrouillé empilait
                // `Verrouillage -> FicheEnfant` au lieu de `MesEnfants -> FicheEnfant`.
                // `MesEnfants` reste donc la racine dans tous les cas : c'est elle que le lien
                // profond empile sous `FicheEnfant`, et c'est elle que vise le
                // `popUpTo(graph.findStartDestination())` du changement d'onglet.
                startDestination = MesEnfants,
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

                // (B10) Lien profond des notifications : fahasalamana://enfant/{enfantId}.
                // navDeepLink<FicheEnfant> ajoute lui-même le segment de l'argument obligatoire
                // de la route ; la pile MesEnfants -> FicheEnfant est reconstruite par le NavHost,
                // qui empile la destination de départ du graphe sous la cible (CDC §B7.1).
                composable<FicheEnfant>(
                    deepLinks = listOf(navDeepLink<FicheEnfant>(basePath = BASE_LIEN_ENFANT)),
                ) {
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
                    CentresScreen(
                        onOuvrirCentre = { centreId ->
                            navController.navigate(DetailCentre(centreId = centreId))
                        },
                    )
                }

                composable<DetailCentre> {
                    DetailCentreScreen(onRetour = { navController.navigateUp() })
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
                        // Une seule sortie, parce qu'il n'y a plus qu'une façon d'entrer : l'écran
                        // de code est toujours empilé par-dessus quelque chose — la racine
                        // `MesEnfants` au pire —, donc le dépiler rend la pile telle qu'elle
                        // était, y compris après un démarrage à froid verrouillé.
                        onTermine = { navController.popBackStack() },
                    )
                }
            }
        }

        // Posé par-dessus le carnet et non à sa place : le NavHost reste composé, donc la
        // pile de retour et les états d'écran survivent au verrouillage. Le masque disparaît
        // de lui-même dès que l'écran de code est au sommet, à la recomposition suivante.
        if (carnetMasque) {
            EcranAvantVerrouillage()
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
