package mg.univ.fahasalamana.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lecture de l'annuaire des centres de santé de base (§B5.1).
 *
 * Lecture seule, pour la même raison que [VaccinReferenceDao] : l'annuaire est remplacé
 * en bloc par `ReferenceDao.remplacerAnnuaire`.
 */
@Dao
interface CentreDao {

    @Query("SELECT * FROM regions ORDER BY nom COLLATE NOCASE ASC")
    fun observerRegions(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM districts WHERE regionId = :regionId ORDER BY nom COLLATE NOCASE ASC")
    fun observerDistricts(regionId: String): Flow<List<DistrictEntity>>

    @Query("SELECT * FROM centres WHERE districtId = :districtId ORDER BY nom COLLATE NOCASE ASC")
    fun observerCentres(districtId: String): Flow<List<CentreEntity>>

    @Query("SELECT * FROM centres WHERE id = :id")
    fun observerCentre(id: String): Flow<CentreEntity?>

    /** Sert à `chargerEmbarqueSiVide()` (B04) : environ 400 centres après le premier chargement. */
    @Query("SELECT COUNT(*) FROM centres")
    suspend fun compterCentres(): Int
}
