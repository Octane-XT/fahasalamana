package mg.univ.fahasalamana.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base locale unique de l'application (§B5.2). Room est la source de vérité : tout ce qui
 * s'affiche vient d'un `Flow` de cette base, jamais d'un état gardé en mémoire par un écran.
 *
 * Six tables, deux familles :
 * - contenu de référence, remplaçable en bloc : `vaccins_reference`, `regions`, `districts`, `centres` ;
 * - données personnelles de santé, jamais touchées par une mise à jour : `enfants`, `vaccins_administres`.
 *
 * `version = 1` pour la soutenance. **`fallbackToDestructiveMigration` est interdit** (§B5.2) :
 * effacer le carnet de vaccination d'un enfant parce qu'un développeur a changé une colonne
 * n'est pas une option. Une v2 du schéma s'accompagnera d'une `Migration` écrite à la main et
 * testée avec `MigrationTestHelper` — d'où `exportSchema = true`, qui fige le schéma v1 en JSON
 * comme point de départ de cette migration.
 */
@Database(
    entities = [
        // Contenu de référence
        VaccinReferenceEntity::class,
        RegionEntity::class,
        DistrictEntity::class,
        CentreEntity::class,
        // Données personnelles
        EnfantEntity::class,
        VaccinAdministreEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun enfantDao(): EnfantDao

    abstract fun vaccinAdministreDao(): VaccinAdministreDao

    abstract fun vaccinReferenceDao(): VaccinReferenceDao

    abstract fun centreDao(): CentreDao

    abstract fun referenceDao(): ReferenceDao

    companion object {
        /** Nom du fichier de base. Exclu de la sauvegarde cloud par `dataExtractionRules` (§B8, B24). */
        const val NOM_FICHIER: String = "fahasalamana.db"
    }
}

/**
 * Construit la base. Appelée une seule fois, par le module Koin.
 *
 * Aucune `allowMainThreadQueries()` : toutes les écritures sont `suspend` et toutes les
 * lectures d'écran sont des `Flow`.
 */
fun construireBase(context: Context): AppDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.NOM_FICHIER,
    ).build()
