package mg.univ.fahasalamana.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lecture du calendrier de référence.
 *
 * Cette DAO ne sait que **lire** : le calendrier n'est jamais modifié ligne à ligne, il est
 * remplacé en bloc par `ReferenceDao.remplacerCalendrier`.
 */
@Dao
interface VaccinReferenceDao {

    /** Le calendrier complet, dans l'ordre d'affichage publié (§B5.1). */
    @Query("SELECT * FROM vaccins_reference ORDER BY ordre ASC")
    fun observerCalendrier(): Flow<List<VaccinReferenceEntity>>

    /** Lecture « une fois » pour le planificateur de rappels (B11) et l'export (B16). */
    @Query("SELECT * FROM vaccins_reference ORDER BY ordre ASC")
    suspend fun lireCalendrier(): List<VaccinReferenceEntity>

    /** `null` si ce vaccin a été retiré d'une version ultérieure du calendrier (§B5.2). */
    @Query("SELECT * FROM vaccins_reference WHERE id = :id")
    suspend fun lireVaccin(id: String): VaccinReferenceEntity?

    /** Sert à `chargerEmbarqueSiVide()` (B04) : 0 au tout premier lancement. */
    @Query("SELECT COUNT(*) FROM vaccins_reference")
    suspend fun compter(): Int
}
