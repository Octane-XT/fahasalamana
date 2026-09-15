package mg.univ.fahasalamana.di

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.construireBase
import mg.univ.fahasalamana.data.repository.CentreRepository
import mg.univ.fahasalamana.data.repository.CentreRepositoryImpl
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.EnfantRepositoryImpl
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepositoryImpl
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.platform.horlogeJour
import mg.univ.fahasalamana.ui.enfants.MesEnfantsViewModel
import mg.univ.fahasalamana.ui.reglages.ReglagesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
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
    single<ReferenceRepository> {
        ReferenceRepositoryImpl(
            vaccinReferenceDao = get(),
            centreDao = get(),
            referenceDao = get(),
            sources = get(),
            preferences = get(),
        )
    }
    single<CentreRepository> { CentreRepositoryImpl(centreDao = get()) }

    // (B06) Données personnelles de santé : seul chemin d'écriture vers `enfants` et
    // `vaccins_administres`. Lié à l'interface, comme les deux précédents.
    single<EnfantRepository> {
        EnfantRepositoryImpl(
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

    // --- Plateforme (rappels, notifications) --- TODO(B10), TODO(B11)
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

    // --- ViewModels ---
    // (B15) Réglages : lit la provenance du calendrier et les préférences locales.
    viewModel { ReglagesViewModel(get(), get()) }

    // (B06) Mes enfants : carnet + calendrier + jour courant, résumés par le calculateur (R6).
    viewModel { MesEnfantsViewModel(get(), get(), get(), get()) }

    // TODO(B07) à TODO(B18) : un viewModel par écran restant.
}
