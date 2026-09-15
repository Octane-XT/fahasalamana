package mg.univ.fahasalamana.di

import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.construireBase
import mg.univ.fahasalamana.data.repository.CentreRepository
import mg.univ.fahasalamana.data.repository.CentreRepositoryImpl
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepositoryImpl
import mg.univ.fahasalamana.ui.reglages.ReglagesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

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

    // TODO(B06) : `EnfantRepository`, écrit avec le premier écran qui enregistre un enfant.

    // --- Domaine --- TODO(B05)

    // --- Plateforme (rappels, notifications) --- TODO(B10), TODO(B11)

    // --- ViewModels ---
    // (B15) Réglages : lit la provenance du calendrier et les préférences locales.
    viewModel { ReglagesViewModel(get(), get()) }

    // TODO(B06) à TODO(B18) : un viewModel par écran restant.
}
