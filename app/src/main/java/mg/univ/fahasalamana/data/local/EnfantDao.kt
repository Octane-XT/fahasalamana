package mg.univ.fahasalamana.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux enfants et à leurs doses reçues (données personnelles).
 *
 * Convention de toutes les DAO du projet (§B6) : **lecture en `Flow`**, pour que Room
 * reste la source de vérité et que l'écran se recompose tout seul après une saisie ;
 * **écriture en `suspend`**, jamais sur le fil principal.
 *
 * Les lectures « une fois » (`lireAvecVaccins`, `lireToutAvecVaccins`) existent pour les
 * appelants qui ne sont pas des écrans : le `RappelWorker` (B11), qui relit la base avant
 * de notifier, et l'export (B16).
 */
@Dao
interface EnfantDao {

    /**
     * Tous les enfants avec leurs doses reçues.
     *
     * L'ordre renvoyé ici est un ordre stable et prévisible ; le tri par nombre de retards
     * de l'écran « Mes enfants » (US-B8) se fait plus haut, à partir des `ResumeEnfant`
     * calculés par le domaine — la base ne connaît pas les statuts.
     */
    @Transaction
    @Query("SELECT * FROM enfants ORDER BY prenom COLLATE NOCASE ASC, creeLe ASC")
    fun observerTous(): Flow<List<EnfantAvecVaccins>>

    /** Un enfant et ses doses. Émet `null` si l'enfant vient d'être supprimé : l'écran affiche alors « Introuvable » (§B6). */
    @Transaction
    @Query("SELECT * FROM enfants WHERE id = :id")
    fun observer(id: String): Flow<EnfantAvecVaccins?>

    @Transaction
    @Query("SELECT * FROM enfants WHERE id = :id")
    suspend fun lireAvecVaccins(id: String): EnfantAvecVaccins?

    @Transaction
    @Query("SELECT * FROM enfants ORDER BY prenom COLLATE NOCASE ASC, creeLe ASC")
    suspend fun lireToutAvecVaccins(): List<EnfantAvecVaccins>

    /** Création et modification : l'identifiant est un UUID déjà fixé par l'appelant (`nouvelIdentifiant()`). */
    @Upsert
    suspend fun enregistrer(enfant: EnfantEntity)

    /** Fusion par identifiant à l'import d'un carnet (B17). */
    @Upsert
    suspend fun enregistrerTous(enfants: List<EnfantEntity>)

    /** Supprime l'enfant **et ses administrations**, par la cascade déclarée sur `vaccins_administres`. */
    @Query("DELETE FROM enfants WHERE id = :id")
    suspend fun supprimer(id: String)

    /** Nombre d'enfants enregistrés : sert à l'état vide et à la demande de permission de notification (B10). */
    @Query("SELECT COUNT(*) FROM enfants")
    suspend fun compter(): Int
}
