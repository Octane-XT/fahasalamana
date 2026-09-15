package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mg.univ.fahasalamana.data.local.CentreDao
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.domain.Centre
import mg.univ.fahasalamana.domain.District
import mg.univ.fahasalamana.domain.Region

/**
 * Implémentation de [CentreRepository] : une traduction, et rien d'autre.
 *
 * Le tri par nom est fait en SQL (`ORDER BY nom COLLATE NOCASE`, voir [CentreDao]) et non
 * ici : trier 402 centres en Kotlin à chaque émission d'un `Flow` serait du travail refait
 * à chaque recomposition, alors que SQLite le fait une fois sur un index.
 *
 * Aucune fonction de chargement ici : l'annuaire est amorcé par
 * `ReferenceRepository.chargerEmbarqueSiVide()` et remplacé par B19. C'est la répartition
 * du schéma d'architecture du §B6, où `CentreRepository` n'est relié qu'à Room.
 */
class CentreRepositoryImpl(
    private val centreDao: CentreDao,
) : CentreRepository {

    override fun observerRegions(): Flow<List<Region>> =
        centreDao.observerRegions().map { it.toDomain() }

    override fun observerDistricts(regionId: String): Flow<List<District>> =
        centreDao.observerDistricts(regionId).map { it.toDomain() }

    override fun observerCentres(districtId: String): Flow<List<Centre>> =
        centreDao.observerCentres(districtId).map { it.toDomain() }

    override fun observerCentre(id: String): Flow<Centre?> =
        centreDao.observerCentre(id).map { it?.toDomain() }
}
