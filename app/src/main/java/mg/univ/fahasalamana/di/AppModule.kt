package mg.univ.fahasalamana.di

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.construireBase
import mg.univ.fahasalamana.data.remote.construireReferenceApi
import mg.univ.fahasalamana.data.repository.CentreRepository
import mg.univ.fahasalamana.data.repository.CentreRepositoryImpl
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.EnfantRepositoryImpl
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepositoryImpl
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.platform.horlogeJour
import mg.univ.fahasalamana.ui.edition.EditionEnfantViewModel
import mg.univ.fahasalamana.ui.enfants.MesEnfantsViewModel
import androidx.work.WorkManager
import mg.univ.fahasalamana.platform.EcrivainDocument
import mg.univ.fahasalamana.platform.LecteurDocument
import mg.univ.fahasalamana.platform.NotificationHelper
import mg.univ.fahasalamana.platform.PlanificateurRappels
import mg.univ.fahasalamana.platform.RappelWorker
import mg.univ.fahasalamana.ui.export.ExportCarnetViewModel
import mg.univ.fahasalamana.ui.importation.ImportCarnetViewModel
import mg.univ.fahasalamana.ui.centres.CentresViewModel
import mg.univ.fahasalamana.ui.detailcentre.DetailCentreViewModel
import mg.univ.fahasalamana.platform.GardienVerrouillage
import mg.univ.fahasalamana.ui.fiche.FicheEnfantViewModel
import mg.univ.fahasalamana.ui.reglages.ReglagesViewModel
import mg.univ.fahasalamana.ui.saisie.SaisieVaccinViewModel
import mg.univ.fahasalamana.ui.verrouillage.VerrouillageViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module
import java.time.LocalDate

/**
 * Définitions Koin de l'application.
 *
 * Convention du binôme : chaque tâche ajoute ses définitions **à la fin** de la section
 * qui la concerne, sans réordonner le fichier — c'est le fichier le plus exposé aux conflits.
 */
val appModule = module {

    // --- Base de données et DAO ---
    // (B02) Une seule instance de base pour tout le processus : Room y gère lui-même son pool
    // de connexions, et deux instances ouvriraient deux fois le même fichier.
    single { construireBase(androidContext()) }
    single { get<AppDatabase>().enfantDao() }
    single { get<AppDatabase>().vaccinAdministreDao() }
    single { get<AppDatabase>().vaccinReferenceDao() }
    single { get<AppDatabase>().centreDao() }
    single { get<AppDatabase>().referenceDao() }

    // --- Repositories ---
    // (B02) Réglages locaux (DataStore). Le stockage est un fichier unique : instance unique.
    single { PreferencesLocales(androidContext()) }

    // (B04) Lecture des JSON embarqués dans `assets/`. Sans état, mais déclarée `single`
    // pour ne pas recréer un accès à l'`AssetManager` à chaque injection.
    single { SourcesEmbarquees(androidContext()) }

    // (B04) Contenu de référence. Liés à l'interface et non à l'implémentation : les
    // ViewModels dépendent du contrat du §B6, et B19 remplacera l'implémentation du
    // calendrier sans toucher à un seul écran.
    // (B19) Client HTTP des fichiers de référence publiés (§B5.1). `single` : Retrofit et
    // OkHttp partagent un pool de connexions et de threads ; en construire un par appel
    // rouvrirait une socket à chaque vérification.
    single { construireReferenceApi() }

    single<ReferenceRepository> {
        ReferenceRepositoryImpl(
            vaccinReferenceDao = get(),
            centreDao = get(),
            referenceDao = get(),
            sources = get(),
            preferences = get(),
            // (B19) Mise à jour des contenus de référence depuis le réseau.
            api = get(),
        )
    }
    single<CentreRepository> { CentreRepositoryImpl(centreDao = get()) }

    // (B06) Données personnelles de santé : seul chemin d'écriture vers `enfants` et
    // `vaccins_administres`. Lié à l'interface, comme les deux précédents.
    single<EnfantRepository> {
        EnfantRepositoryImpl(
            // (B17) La fusion d'un carnet importé lit et écrit les deux tables personnelles
            // dans une seule transaction : le repository a besoin de la base, pas des seuls DAO.
            base = get(),
            enfantDao = get(),
            vaccinAdministreDao = get(),
        )
    }

    // --- Domaine ---
    // (B06) Calculateur d'échéancier (§B6 : `factory { CalculateurEcheancier() }`). La classe
    // existe depuis B05 mais n'avait pas d'écran pour la demander. `factory` et non `single` :
    // elle est sans état, donc la partager n'apporte rien, et une instance par ViewModel
    // supprime toute question de fuite ou de concurrence.
    factory { CalculateurEcheancier() }

    // --- Plateforme (rappels, notifications) ---
    // (B06) Jour courant, qui change à minuit et au retour au premier plan : c'est lui qui
    // fait basculer un vaccin de « à venir » à « à faire » sans rouvrir l'application.
    //
    // `single` et non `factory` : `horlogeJour()` renvoie un `Flow` **froid** et sans état —
    // chaque collecteur relance sa propre boucle —, donc la même instance sert tous les
    // ViewModels sans qu'ils se gênent. Cela évite surtout de rappeler
    // `ProcessLifecycleOwner.get()` à chaque injection.
    //
    // Attention à la portée du type : Koin indexe sur la classe effacée `Flow`. Le jour où
    // un second `Flow<…>` doit être déclaré, il faudra un qualificatif nommé sur les deux.
    single<Flow<LocalDate>> { horlogeJour() }

    // (B10) Notifications de rappel : canal, lien profond, permission. Sans état, mais
    // `single` pour ne pas reconstruire une façade à chaque injection. Consommée par
    // RappelWorker (B11) ; le canal, lui, est créé directement depuis App.onCreate.
    single { NotificationHelper(androidContext()) }

    // (B11) WorkManager. `workManagerFactory()` de `App.onCreate` l'a déjà initialisé avec la
    // fabrique de workers de Koin ; `getInstance` rend cette instance-là. Résolution
    // paresseuse, donc toujours après `startKoin`.
    single { WorkManager.getInstance(androidContext()) }

    // (B11) Planificateur des rappels (R3, R4) : le seul endroit qui enfile un WorkRequest.
    // Un écran ne sait pas comment un rappel est programmé, il déclenche replanifier().
    single { PlanificateurRappels(get(), get(), get(), get(), get()) }

    // (B11) Worker injecté par Koin (§B6) : `workerOf` fournit lui-même Context et
    // WorkerParameters, et résout les dépendances suivantes du constructeur.
    workerOf(::RappelWorker)

    // (B16) Écriture du carnet dans le document choisi par l'utilisateur (SAF, §B8 point 2).
    // Sans état : il ne porte qu'un Context, d'où `single`.
    single { EcrivainDocument(androidContext()) }

    // (B17) Lecture du document choisi par l'utilisateur (SAF, §B8 point 2).
    single { LecteurDocument(androidContext()) }
    // (B18) Verrou du carnet : instance unique pour le processus. Elle observe
    // ProcessLifecycleOwner dès sa construction et tient l'unique réponse à « le carnet
    // est-il ouvert ? » — deux instances donneraient deux réponses.
    single { GardienVerrouillage(preferences = get()) }

    // --- ViewModels ---
    // (B15) Réglages : lit la provenance du calendrier et les préférences locales.
    // (B19) + PlanificateurRappels : un nouveau calendrier déplace toutes les dates prévues.
    viewModel { ReglagesViewModel(get(), get(), get()) }

    // (B06) Mes enfants : carnet + calendrier + jour courant, résumés par le calculateur (R6).
    viewModel { MesEnfantsViewModel(get(), get(), get(), get()) }

    // (B08) Fiche enfant : enfantId lu par SavedStateHandle.toRoute<FicheEnfant>().
    viewModel {
        FicheEnfantViewModel(
            savedStateHandle = get(),
            enfants = get(),
            reference = get(),
            calc = get(),
            horlogeJour = get(),
        )
    }

    // (B07) Édition d'un enfant : enfantId (nullable) lu par
    // SavedStateHandle.toRoute<EditionEnfant>(). Pas de `ReferenceRepository` ni de
    // `CalculateurEcheancier` ici — le formulaire n'affiche aucun échéancier ; il a besoin
    // du jour courant pour la seule validation de la date de naissance.
    viewModel {
        EditionEnfantViewModel(
            savedStateHandle = get(),
            enfants = get(),
            horlogeJour = get(),
            // (B12) Créer, modifier ou supprimer un enfant reprogramme ses rappels.
            planificateur = get(),
        )
    }

    // (B09) Saisie d'un vaccin : enfantId et vaccinId lus par SavedStateHandle.toRoute<SaisieVaccin>().
    viewModel {
        SaisieVaccinViewModel(
            savedStateHandle = get(),
            enfants = get(),
            reference = get(),
            calc = get(),
            horlogeJour = get(),
            // (B12) Saisir ou supprimer une dose reprogramme les rappels de l'enfant.
            planificateur = get(),
        )
    }

    // (B16) Export du carnet : bloc « Carnet » de l'écran Réglages. ViewModel à part et non
    // ReglagesViewModel, pour garder l'export testable et l'écran des réglages inchangé.
    viewModel { ExportCarnetViewModel(enfants = get(), ecrivain = get(), horlogeJour = get()) }

    // (B17) Import du carnet : fusion par identifiant, puis replanifierTout().
    viewModel { ImportCarnetViewModel(enfants = get(), lecteur = get(), planificateur = get()) }
    // (B13) Centres de santé : la route n'a pas d'argument, mais le SavedStateHandle sert
    // tout de même — il retient la région et le district choisis, qui survivent ainsi à la
    // rotation et à la mort du processus.
    viewModel { CentresViewModel(savedStateHandle = get(), annuaire = get()) }

    // (B14) Fiche d'un centre : centreId lu par SavedStateHandle.toRoute<DetailCentre>().
    viewModel { DetailCentreViewModel(savedStateHandle = get(), annuaire = get()) }

    // TODO(B10) à TODO(B18) : un viewModel par écran restant.
    // (B18) Verrouillage : le mode (ouverture, création, modification) est déduit de l'état
    // du verrou, la route `Verrouillage` n'ayant pas d'argument.
    viewModel { VerrouillageViewModel(preferences = get(), gardien = get()) }

    // TODO(B14) et TODO(B17) : les deux écrans restants.
}
