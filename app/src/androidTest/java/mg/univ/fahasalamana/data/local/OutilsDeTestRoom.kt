package mg.univ.fahasalamana.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/*
 * ---------------------------------------------------------------------------
 * Outils communs aux tests DAO instrumentés (B20)
 * ---------------------------------------------------------------------------
 *
 * Trois garanties du CDC sont vérifiées ici, chacune dans son fichier :
 *  - `CascadeSuppressionTest`     : supprimer un enfant emporte ses doses, et rien d'autre ;
 *  - `UniciteAdministrationTest`  : une seule dose par couple (enfant, vaccin) — règle R5 ;
 *  - `RemplacementReferenceTest`  : remplacer le calendrier ne touche pas aux données de santé.
 *
 * `EnfantDaoTest` (B02) reste ce qu'il est : l'insertion et la relation `EnfantAvecVaccins`.
 */

/**
 * Ouvre une base **en mémoire** : chaque test repart d'une base vide et rien n'est écrit sur
 * l'appareil. Pas d'`allowMainThreadQueries()` — les tests instrumentés ne tournent pas sur le
 * fil principal, `runBlocking` suffit, et les DAO restent testées telles qu'elles sont
 * réellement utilisées par l'application.
 *
 * Point important pour les tests de cascade : le schéma déclarant des clés étrangères, Room
 * exécute `PRAGMA foreign_keys = ON` à l'ouverture de la base, y compris en mémoire. Les
 * `ON DELETE CASCADE` de `vaccins_administres`, `districts` et `centres` sont donc réellement
 * actifs ici ; si l'un d'eux disparaissait du schéma, les tests échoueraient au lieu de passer
 * en silence.
 */
fun ouvrirBaseEnMemoire(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).build()

/**
 * Nombre de lignes d'une table, lu en SQL brut.
 *
 * Aucune DAO ne compte les lignes de `vaccins_administres` ni de `districts` : l'application
 * n'en a pas besoin. Plutôt que d'ajouter à la production des requêtes que seuls les tests
 * appelleraient, on lit ici la table directement — c'est aussi la seule façon de vérifier
 * qu'une table est vide **ou** intacte sans passer par une conversion d'entité.
 */
fun AppDatabase.compterLignes(table: String): Int =
    query("SELECT COUNT(*) FROM $table", emptyArray<Any>()).use { curseur ->
        curseur.moveToFirst()
        curseur.getInt(0)
    }

/**
 * Le contenu brut d'une colonne, tel qu'il est écrit dans le fichier de base, **avant** tout
 * passage par [Convertisseurs]. C'est la seule façon de voir qu'une `LocalDate` est bien
 * stockée en texte ISO `yyyy-MM-dd` et non en nombre : relue par la DAO, la colonne
 * redeviendrait une `LocalDate` correcte quel que soit son format de stockage.
 *
 * @return `null` si aucune ligne ne porte cet identifiant.
 */
fun AppDatabase.texteBrut(table: String, colonne: String, id: String): String? =
    query("SELECT $colonne FROM $table WHERE id = ?", arrayOf<Any>(id)).use { curseur ->
        if (curseur.moveToFirst()) curseur.getString(0) else null
    }

/**
 * Photographie comparable des deux tables de données personnelles.
 *
 * Sert aux tests « inchangées » : on compare l'état avant et après un remplacement de contenu
 * de référence. Le tri par identifiant rend la comparaison indépendante de l'ordre dans lequel
 * SQLite renvoie les lignes — ni `enfants` ni la relation `EnfantAvecVaccins` ne garantissent
 * un ordre sur les administrations, et un test qui dépendrait de cet ordre deviendrait
 * capricieux sans que le code ait changé.
 */
fun List<EnfantAvecVaccins>.photographie(): List<Pair<EnfantEntity, List<VaccinAdministreEntity>>> =
    sortedBy { it.enfant.id }
        .map { ligne -> ligne.enfant to ligne.administres.sortedBy { dose -> dose.id } }
