package mg.univ.fahasalamana.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import mg.univ.fahasalamana.domain.VaccinReference

/*
 * ---------------------------------------------------------------------------
 * FAMILLE 1 — CONTENU DE RÉFÉRENCE (§B5.2, règle 8 de CLAUDE.md)
 * ---------------------------------------------------------------------------
 *
 * `vaccins_reference`, `regions`, `districts`, `centres` sont du contenu publié,
 * identique sur tous les appareils : il provient des JSON embarqués dans `assets/`
 * au premier lancement (B04), puis d'une mise à jour distante (B19).
 *
 * Ces quatre tables sont **remplaçables en bloc** : `ReferenceDao.remplacerCalendrier`
 * et `remplacerAnnuaire` les vident et les réécrivent dans une seule transaction.
 * Aucune de ces opérations ne touche `enfants` ni `vaccins_administres`, qui sont
 * l'autre famille (voir `EntitesPersonnelles.kt`).
 *
 * Les identifiants viennent des fichiers de référence et sont stables dans le temps
 * (« penta1 », « analamanga ») : ce ne sont pas des UUID, et les données personnelles
 * s'appuient dessus.
 */

/**
 * Une ligne du calendrier vaccinal publié (`calendrier.json`, §B5.1).
 *
 * @param dependDe identifiant de la dose précédente de la même série, ou nul.
 *   Volontairement **sans clé étrangère auto-référente** : le calendrier est inséré
 *   en bloc et rien ne garantit que la dose parente précède sa dépendante dans la
 *   liste publiée ; le `CalculateurEcheancier` (B05) traite déjà le cas d'une chaîne
 *   cassée en produisant `LigneEcheancier.prevuLe = null`. L'index reste utile au
 *   parcours de la chaîne de dépendances.
 */
@Entity(
    tableName = "vaccins_reference",
    indices = [Index(value = ["dependDe"]), Index(value = ["ordre"])],
)
data class VaccinReferenceEntity(
    @PrimaryKey val id: String,
    val nom: String,
    val dose: String,
    val ordre: Int,
    val ageJours: Int,
    val dependDe: String?,
    val toleranceJours: Int,
    val description: String,
)

/** Une région de l'annuaire (`csb.json`, §B5.1). */
@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey val id: String,
    val nom: String,
)

/** Un district, rattaché à une région. Supprimer la région supprime ses districts (contenu de référence, remplacé en bloc). */
@Entity(
    tableName = "districts",
    foreignKeys = [
        ForeignKey(
            entity = RegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["regionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["regionId"])],
)
data class DistrictEntity(
    @PrimaryKey val id: String,
    val regionId: String,
    val nom: String,
)

/** Un centre de santé de base, rattaché à un district. */
@Entity(
    tableName = "centres",
    foreignKeys = [
        ForeignKey(
            entity = DistrictEntity::class,
            parentColumns = ["id"],
            childColumns = ["districtId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["districtId"])],
)
data class CentreEntity(
    @PrimaryKey val id: String,
    val districtId: String,
    val nom: String,
    val type: String,
    val telephone: String,
    val horaires: String,
    val adresse: String,
)

// --- Conversions vers le domaine -------------------------------------------
//
// Les entités ci-dessus sont des types de persistance : elles ne sortent pas de
// `data/`. Les ViewModels et le `CalculateurEcheancier` ne manipulent que les
// modèles de `domain/`.

fun VaccinReferenceEntity.toDomain(): VaccinReference = VaccinReference(
    id = id,
    nom = nom,
    dose = dose,
    ordre = ordre,
    ageJours = ageJours,
    dependDe = dependDe,
    toleranceJours = toleranceJours,
    description = description,
)

@JvmName("vaccinsReferenceToDomain")
fun List<VaccinReferenceEntity>.toDomain(): List<VaccinReference> = map { it.toDomain() }

fun VaccinReference.toEntity(): VaccinReferenceEntity = VaccinReferenceEntity(
    id = id,
    nom = nom,
    dose = dose,
    ordre = ordre,
    ageJours = ageJours,
    dependDe = dependDe,
    toleranceJours = toleranceJours,
    description = description,
)

@JvmName("vaccinsReferenceToEntity")
fun List<VaccinReference>.toEntity(): List<VaccinReferenceEntity> = map { it.toEntity() }

// TODO(B04) : `RegionEntity`, `DistrictEntity` et `CentreEntity` n'ont pas encore de
// modèle correspondant dans `domain/` (`Region`, `District`, `Centre` sont attendus par
// `CentreRepository`, §B6, mais absents du paquet `domain` livré en B05). Les conversions
// `toDomain()` de l'annuaire seront ajoutées ici en même temps que `CentreRepositoryImpl`.
