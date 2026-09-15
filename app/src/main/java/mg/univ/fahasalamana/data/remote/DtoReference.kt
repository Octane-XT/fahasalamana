package mg.univ.fahasalamana.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * ---------------------------------------------------------------------------
 * CONTRAT DES FICHIERS DE RÉFÉRENCE PUBLIÉS (§B5.1)
 * ---------------------------------------------------------------------------
 *
 * `calendrier.json` et `csb.json` ont **un seul format**, qu'ils soient lus dans
 * `assets/` au premier lancement (B04) ou téléchargés depuis
 * `raw.githubusercontent.com/<org>/fahasalamana-data/main/v1/…` (B19). Les types de
 * ce fichier sont donc les mêmes des deux côtés : B19 n'a rien à redéclarer, il
 * branche ces `@Serializable` sur le convertisseur kotlinx-serialization de Retrofit
 * et réutilise [jsonReference].
 *
 * Ce fichier est volontairement **sans Android et sans Room** : ce sont des objets de
 * transport, testables en JVM (voir `ContratReferenceTest`). La traduction vers les
 * entités Room vit à côté des entités, dans `data/local/MappageReference.kt`.
 *
 * Nommage : un DTO reprend **exactement** les noms de clés du JSON publié. Le contrat
 * fait autorité, pas le style de nommage interne — d'où `schemaVersion` et `publieLe`
 * tels quels, sans `@SerialName` (sauf pour `type`, voir [CentreDto]).
 */

/**
 * Le format de lecture des deux fichiers de référence.
 *
 * - `ignoreUnknownKeys = true` : c'est la clause de compatibilité ascendante du §B5.1.
 *   Une v1 du format qui **ajoute** un champ (« coordonnées GPS d'un centre ») reste
 *   lisible par les APK déjà installés ; seule une modification incompatible publie
 *   `v2/`. Sans cette option, ajouter un champ ferait planter toutes les installations.
 * - Les valeurs par défaut sont encodées à la sérialisation pour que le fichier écrit
 *   soit toujours complet et relisible par un humain.
 * - Pas de `isLenient` : un JSON mal formé doit échouer franchement, pas être deviné.
 */
val jsonReference: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * `LocalDate` <-> texte ISO `yyyy-MM-dd`, le format de `publieLe` dans les deux fichiers.
 *
 * Même règle que côté base (`Convertisseurs`, §B5.2) et que côté export (B16) : une date
 * métier est du texte ISO, jamais un epoch. Une date illisible lève ici plutôt que de
 * laisser passer une valeur approximative — l'appelant (B04, B19) traite l'échec.
 */
object SerialiseurLocalDate : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("mg.univ.fahasalamana.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}

// --- calendrier.json --------------------------------------------------------

/**
 * Le calendrier vaccinal publié.
 *
 * @param schemaVersion fige le format. L'URL porte déjà `v1/` ; ce champ permet de
 *   refuser un fichier d'un format inattendu avant de remplacer quoi que ce soit.
 * @param version entier strictement croissant : B19 ne remplace la base que si
 *   `distant.version > local.version` (§B5.1).
 * @param publieLe et @param source sont affichés tels quels dans « À propos des données ».
 */
@Serializable
data class CalendrierDto(
    val schemaVersion: Int,
    val version: Int,
    @Serializable(with = SerialiseurLocalDate::class) val publieLe: LocalDate,
    val source: String,
    val vaccins: List<VaccinDto>,
)

/**
 * Une dose du calendrier publié.
 *
 * @param id identifiant stable dans le temps : les doses déjà saisies par l'utilisateur
 *   y font référence (§B5.1). Le renuméroter casserait les carnets existants.
 * @param dependDe identifiant de la dose précédente de la même série, ou `null`.
 *   Explicitement nul dans le fichier ; le champ reste néanmoins optionnel côté lecture
 *   (défaut `null`) pour qu'un producteur qui omettrait la clé reste compatible.
 */
@Serializable
data class VaccinDto(
    val id: String,
    val nom: String,
    val dose: String,
    val ordre: Int,
    val ageJours: Int,
    val dependDe: String? = null,
    val toleranceJours: Int,
    val description: String,
)

// --- csb.json ---------------------------------------------------------------

/** L'annuaire publié : régions, districts et centres, imbriqués comme dans le fichier. */
@Serializable
data class AnnuaireDto(
    val schemaVersion: Int,
    val version: Int,
    @Serializable(with = SerialiseurLocalDate::class) val publieLe: LocalDate,
    val source: String,
    val regions: List<RegionDto>,
)

@Serializable
data class RegionDto(
    val id: String,
    val nom: String,
    val districts: List<DistrictDto> = emptyList(),
)

@Serializable
data class DistrictDto(
    val id: String,
    val nom: String,
    val centres: List<CentreDto> = emptyList(),
)

/**
 * Un centre de santé de base.
 *
 * `type` est le seul champ renommé : `type` est un mot suffisamment générique pour
 * mériter un nom plus explicite côté Kotlin, et `@SerialName` garde le lien avec la
 * clé publiée. Aucune énumération ici — un niveau de centre inconnu doit s'afficher
 * tel quel (voir `domain.Centre`).
 */
@Serializable
data class CentreDto(
    val id: String,
    val nom: String,
    @SerialName("type") val typeCentre: String,
    val telephone: String,
    val horaires: String,
    val adresse: String,
)
