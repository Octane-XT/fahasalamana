package mg.univ.fahasalamana.data.local

import mg.univ.fahasalamana.data.remote.AnnuaireDto
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.remote.CentreDto
import mg.univ.fahasalamana.data.remote.DistrictDto
import mg.univ.fahasalamana.data.remote.RegionDto
import mg.univ.fahasalamana.data.remote.VaccinDto

/*
 * ---------------------------------------------------------------------------
 * DU CONTRAT PUBLIÉ VERS LES ENTITÉS ROOM (§B5.1 → §B5.2)
 * ---------------------------------------------------------------------------
 *
 * Un seul sens de traduction : les fichiers de référence entrent en base, ils n'en
 * ressortent jamais. Ces fonctions sont **pures** (aucune E/S, aucun Android) et
 * servent aussi bien au chargement embarqué de B04 qu'à la mise à jour distante de
 * B19, puisque les deux lisent le même format.
 *
 * Elles vivent ici, à côté des entités, pour que `data/remote/` reste un paquet de
 * pur transport que Retrofit peut utiliser sans dépendre de Room.
 */

// --- Calendrier -------------------------------------------------------------

/** Les lignes de `vaccins_reference` correspondant au calendrier publié. */
fun CalendrierDto.versEntites(): List<VaccinReferenceEntity> = vaccins.map { it.versEntite() }

fun VaccinDto.versEntite(): VaccinReferenceEntity = VaccinReferenceEntity(
    id = id,
    nom = nom,
    dose = dose,
    ordre = ordre,
    ageJours = ageJours,
    dependDe = dependDe,
    toleranceJours = toleranceJours,
    description = description,
)

// --- Annuaire ---------------------------------------------------------------

/**
 * Les trois listes que `ReferenceDao.remplacerAnnuaire` insère dans une seule transaction.
 *
 * Le fichier publié est imbriqué (région → districts → centres), la base est à plat avec
 * des clés étrangères : ce type porte le résultat de l'aplatissement en un seul objet,
 * pour qu'on ne puisse pas insérer les districts d'un annuaire et les centres d'un autre.
 */
data class AnnuaireEntites(
    val regions: List<RegionEntity>,
    val districts: List<DistrictEntity>,
    val centres: List<CentreEntity>,
)

/**
 * Aplatit l'annuaire publié.
 *
 * Le rattachement (`regionId`, `districtId`) n'est **pas** lu dans le fichier : il est
 * déduit de l'imbrication, seule source possible. Un district ne peut donc pas désigner
 * une région absente du même fichier, et les clés étrangères de `data/local` sont
 * satisfaites par construction.
 */
fun AnnuaireDto.versEntites(): AnnuaireEntites {
    val regionsPlates = ArrayList<RegionEntity>(regions.size)
    val districtsPlats = ArrayList<DistrictEntity>()
    val centresPlats = ArrayList<CentreEntity>()

    for (region in regions) {
        regionsPlates += region.versEntite()
        for (district in region.districts) {
            districtsPlats += district.versEntite(regionId = region.id)
            for (centre in district.centres) {
                centresPlats += centre.versEntite(districtId = district.id)
            }
        }
    }

    return AnnuaireEntites(regionsPlates, districtsPlats, centresPlats)
}

fun RegionDto.versEntite(): RegionEntity = RegionEntity(id = id, nom = nom)

fun DistrictDto.versEntite(regionId: String): DistrictEntity = DistrictEntity(
    id = id,
    regionId = regionId,
    nom = nom,
)

fun CentreDto.versEntite(districtId: String): CentreEntity = CentreEntity(
    id = id,
    districtId = districtId,
    nom = nom,
    type = typeCentre,
    telephone = telephone,
    horaires = horaires,
    adresse = adresse,
)
