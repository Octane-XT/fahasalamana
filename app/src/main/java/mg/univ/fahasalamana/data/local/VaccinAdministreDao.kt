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

    /**
     * Insère en lot les doses d'un carnet importé (B17).
     *
     * **Sans `REPLACE`, et c'est tout l'intérêt de cette fonction** (point de vigilance n° 11
     * du suivi). Sur un conflit, `INSERT OR REPLACE` supprime la ligne en place puis insère
     * la nouvelle : c'est l'identifiant du fichier importé qui survivrait et l'identifiant
     * local qui disparaîtrait. Or c'est lui, et lui seul, qui permet aux imports suivants de
     * fusionner sans doublon — et le fichier peut venir d'un autre téléphone, donc décrire
     * une saisie plus ancienne que celle qu'il écraserait.
     *
     * `fusionner()` (`domain/ImportCarnet.kt`) ne transmet donc ici que des doses dont ni
     * l'identifiant ni le couple (enfantId, vaccinId) n'existent en base. La stratégie par
     * défaut (`ABORT`) est le garde-fou de cette promesse : si un conflit survenait malgré
     * tout, la transaction d'import est annulée en entier et l'utilisateur voit un échec,
     * au lieu de perdre une dose en silence.
     */
    @Insert
    suspend fun insererTous(administres: List<VaccinAdministreEntity>)

    @Query("DELETE FROM vaccins_administres WHERE id = :id")
    suspend fun supprimer(id: String)

    @Query("DELETE FROM vaccins_administres WHERE enfantId = :enfantId AND vaccinId = :vaccinId")
    suspend fun supprimerPour(enfantId: String, vaccinId: String)
}
