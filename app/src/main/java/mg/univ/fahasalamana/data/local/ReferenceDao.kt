package mg.univ.fahasalamana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Remplacement en bloc du contenu de référence (§B5.2, règle 8 de CLAUDE.md).
 *
 * Deux opérations seulement, et toutes les deux transactionnelles : soit la nouvelle version
 * du calendrier ou de l'annuaire est entièrement en base, soit l'ancienne est intacte. Un
 * échec de mise à jour (B19) ne doit jamais laisser un calendrier à moitié remplacé.
 *
 * **Aucune de ces fonctions n'écrit dans `enfants` ni dans `vaccins_administres`.** C'est la
 * séparation contenu de référence / données personnelles : les `DELETE` ci-dessous ne portent
 * que sur les quatre tables de référence. Toute évolution de cette DAO doit préserver cette
 * propriété — les tests instrumentés de B20 la vérifient.
 *
 * Classe abstraite et non interface : Room y génère les fonctions annotées `@Transaction` qui
 * ont un corps, ce qui permet d'enchaîner ici plusieurs requêtes dans une seule transaction.
 */
@Dao
abstract class ReferenceDao {

    // --- Calendrier vaccinal -------------------------------------------------

    /**
     * Remplace tout le calendrier par [vaccins].
     *
     * Les identifiants de vaccin étant stables (§B5.1), une dose déjà administrée retrouve sa
     * ligne de référence après le remplacement.
     */
    @Transaction
    open suspend fun remplacerCalendrier(vaccins: List<VaccinReferenceEntity>) {
        viderCalendrier()
        insererVaccins(vaccins)
    }

    @Query("DELETE FROM vaccins_reference")
    abstract suspend fun viderCalendrier()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insererVaccins(vaccins: List<VaccinReferenceEntity>)

    // --- Annuaire des centres ------------------------------------------------

    /**
     * Remplace tout l'annuaire.
     *
     * Les suppressions sont explicites et ordonnées des enfants vers les parents, plutôt que
     * confiées aux cascades : le résultat ne dépend alors pas de l'état du `PRAGMA foreign_keys`.
     * Les insertions suivent l'ordre inverse, pour qu'un district trouve toujours sa région.
     */
    @Transaction
    open suspend fun remplacerAnnuaire(
        regions: List<RegionEntity>,
        districts: List<DistrictEntity>,
        centres: List<CentreEntity>,
    ) {
        viderCentres()
        viderDistricts()
        viderRegions()
        insererRegions(regions)
        insererDistricts(districts)
        insererCentres(centres)
    }

    @Query("DELETE FROM centres")
    abstract suspend fun viderCentres()

    @Query("DELETE FROM districts")
    abstract suspend fun viderDistricts()

    @Query("DELETE FROM regions")
    abstract suspend fun viderRegions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insererRegions(regions: List<RegionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insererDistricts(districts: List<DistrictEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insererCentres(centres: List<CentreEntity>)
}
