package mg.univ.fahasalamana.di

import org.koin.dsl.module

/**
 * Définitions Koin de l'application.
 *
 * Convention du binôme : chaque tâche ajoute ses définitions **à la fin** de la section
 * qui la concerne, sans réordonner le fichier — c'est le fichier le plus exposé aux conflits.
 */
val appModule = module {

    // --- Base de données et DAO --- TODO(B02)

    // --- Repositories --- TODO(B02), TODO(B04)

    // --- Domaine --- TODO(B05)

    // --- Plateforme (rappels, notifications) --- TODO(B10), TODO(B11)

    // --- ViewModels --- TODO(B06) à TODO(B18)
}
