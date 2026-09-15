package mg.univ.fahasalamana.di

import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.construireBase
import org.koin.android.ext.koin.androidContext
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

    // --- Repositories --- TODO(B04)
    // (B02) Réglages locaux (DataStore). Le stockage est un fichier unique : instance unique.
    single { PreferencesLocales(androidContext()) }

    // --- Domaine --- TODO(B05)

    // --- Plateforme (rappels, notifications) --- TODO(B10), TODO(B11)

    // --- ViewModels --- TODO(B06) à TODO(B18)
}
