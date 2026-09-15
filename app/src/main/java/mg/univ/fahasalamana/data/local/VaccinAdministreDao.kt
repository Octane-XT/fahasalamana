package mg.univ.fahasalamana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Accès aux doses reçues (données personnelles). */
@Dao
interface VaccinAdministreDao {

    @Query("SELECT * FROM vaccins_administres WHERE enfantId = :enfantId ORDER BY date ASC")
    fun observerPourEnfant(enfantId: String): Flow<List<VaccinAdministreEntity>>

    /** La dose reçue par cet enfant pour ce vaccin, ou `null`. Lecture de l'écran `SaisieVaccin` en mode correction (B09). */
    @Query("SELECT * FROM vaccins_administres WHERE enfantId = :enfantId AND vaccinId = :vaccinId")
    suspend fun lire(enfantId: String, vaccinId: String): VaccinAdministreEntity?

    /**
     * Enregistre une dose reçue, en création comme en correction.
     *
     * `REPLACE` et non `@Upsert` : la règle R5 (une seule dose par couple enfant/vaccin) est
     * portée par un index unique qui n'est **pas** la clé primaire. `@Upsert` ne sait rattraper
     * qu'un conflit de clé primaire — il retomberait sur une mise à jour par `id` qui ne
     * toucherait aucune ligne, et la saisie serait perdue en silence. `INSERT OR REPLACE`
     * remplace bien la ligne en conflit, quelle que soit la contrainte violée.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(administre: VaccinAdministreEntity)

    /** Import d'un carnet (B17) : même sémantique de remplacement, en lot. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTous(administres: List<VaccinAdministreEntity>)

    @Query("DELETE FROM vaccins_administres WHERE id = :id")
    suspend fun supprimer(id: String)

    @Query("DELETE FROM vaccins_administres WHERE enfantId = :enfantId AND vaccinId = :vaccinId")
    suspend fun supprimerPour(enfantId: String, vaccinId: String)
}
