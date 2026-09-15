package mg.univ.fahasalamana.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.Sexe
import mg.univ.fahasalamana.domain.VaccinAdministre
import java.time.LocalDate
import java.util.UUID

/*
 * ---------------------------------------------------------------------------
 * FAMILLE 2 — DONNÉES PERSONNELLES DE SANTÉ (§B5.2 et §B8, règles 7 et 8 de CLAUDE.md)
 * ---------------------------------------------------------------------------
 *
 * `enfants` et `vaccins_administres` sont les données saisies par l'utilisateur.
 * Elles ne partent jamais sur le réseau et ne sortent de l'appareil que par une
 * action explicite d'export via le SAF (B16).
 *
 * Elles ne sont **jamais touchées par une mise à jour du contenu de référence** :
 * aucune fonction de `ReferenceDao` n'écrit dans ces deux tables. Une administration
 * dont le `vaccinId` a disparu d'une nouvelle version du calendrier est conservée et
 * affichée « vaccin retiré du calendrier ».
 *
 * Les identifiants sont des **UUID générés côté application** (`nouvelIdentifiant()`)
 * et non des entiers auto-incrémentés : l'import d'un carnet venu d'un autre téléphone
 * (B17) fusionne alors par identifiant, sans collision ni renumérotation.
 */

/** Identifiant d'une donnée personnelle : UUID généré ici, conservé à l'export et à l'import. */
fun nouvelIdentifiant(): String = UUID.randomUUID().toString()

/**
 * Un enfant suivi dans le carnet.
 *
 * @param dateNaissance stockée en texte ISO `yyyy-MM-dd` par [Convertisseurs].
 * @param sexe nom de la constante [Sexe] ; une valeur inconnue relue depuis un
 *   fichier importé est ramenée à [Sexe.NON_PRECISE] plutôt que de faire échouer la lecture.
 * @param creeLe instant technique de création (epoch ms), utilisé comme départage de tri
 *   quand deux enfants portent le même prénom. Ce n'est pas une date métier : il ne s'affiche pas.
 */
@Entity(tableName = "enfants")
data class EnfantEntity(
    @PrimaryKey val id: String,
    val prenom: String,
    val dateNaissance: LocalDate,
    val sexe: String,
    val creeLe: Long,
)

/**
 * Une dose effectivement reçue par un enfant.
 *
 * Contraintes portées par la base plutôt que par le code appelant :
 * - suppression d'un enfant → suppression de ses administrations (`CASCADE`) ;
 * - **une seule administration par couple (enfant, vaccin)** (index unique, règle R5) ;
 * - le `vaccinId` référence le calendrier de référence.
 *
 * La clé étrangère vers `vaccins_reference` est **différée** (`deferred = true`) :
 * `ReferenceDao.remplacerCalendrier` vide puis réécrit la table de référence dans une
 * seule transaction, et sans report du contrôle au `COMMIT` le simple `DELETE` échouerait
 * alors qu'aucune administration n'est réellement orpheline à la fin de la transaction.
 *
 * TODO(B04) : point à trancher à deux. Tel qu'écrit, si une nouvelle version du calendrier
 * **retire** un vaccin déjà administré, le contrôle différé échoue au `COMMIT` et la mise à
 * jour est annulée en bloc — alors que §B5.2 demande de conserver l'administration et de
 * l'afficher « vaccin retiré du calendrier ». Deux sorties possibles : retirer cette clé
 * étrangère en ne gardant que l'index, ou conserver dans le calendrier les vaccins retirés
 * mais encore référencés. Ne pas trancher seul : le CDC se contredit sur ce point.
 */
@Entity(
    tableName = "vaccins_administres",
    foreignKeys = [
        ForeignKey(
            entity = EnfantEntity::class,
            parentColumns = ["id"],
            childColumns = ["enfantId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VaccinReferenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccinId"],
            deferred = true,
        ),
    ],
    indices = [
        // Unicité (enfant, vaccin) de R5. `enfantId` étant la colonne de tête de cet index,
        // il sert aussi d'index de la clé étrangère vers `enfants` : pas d'index séparé.
        Index(value = ["enfantId", "vaccinId"], unique = true),
        Index(value = ["vaccinId"]),
    ],
)
data class VaccinAdministreEntity(
    @PrimaryKey val id: String,
    val enfantId: String,
    val vaccinId: String,
    val date: LocalDate,
    val lieu: String?,
    val lot: String?,
)

/**
 * Un enfant et toutes ses doses reçues, lus en une fois (§B5.2).
 *
 * C'est la forme attendue par les ViewModels : le `CalculateurEcheancier` a besoin de
 * l'enfant **et** de ses administrations pour produire un échéancier, et les lire en deux
 * flux séparés ferait clignoter l'écran entre les deux émissions.
 *
 * Toute requête qui renvoie ce type est annotée `@Transaction` côté DAO : sans cela, Room
 * lit l'enfant puis ses administrations en deux requêtes non atomiques.
 */
data class EnfantAvecVaccins(
    @Embedded val enfant: EnfantEntity,
    @Relation(parentColumn = "id", entityColumn = "enfantId")
    val administres: List<VaccinAdministreEntity>,
)

// --- Conversions vers le domaine -------------------------------------------

fun EnfantEntity.toDomain(): Enfant = Enfant(
    id = id,
    prenom = prenom,
    dateNaissance = dateNaissance,
    sexe = sexeDepuisTexte(sexe),
)

@JvmName("enfantsToDomain")
fun List<EnfantEntity>.toDomain(): List<Enfant> = map { it.toDomain() }

/**
 * @param creeLe à ne préciser que pour conserver l'instant d'origine (modification d'un
 *   enfant existant, import d'un carnet). Par défaut, l'instant courant.
 */
fun Enfant.toEntity(creeLe: Long = System.currentTimeMillis()): EnfantEntity = EnfantEntity(
    id = id,
    prenom = prenom,
    dateNaissance = dateNaissance,
    sexe = sexe.name,
    creeLe = creeLe,
)

fun VaccinAdministreEntity.toDomain(): VaccinAdministre = VaccinAdministre(
    id = id,
    enfantId = enfantId,
    vaccinId = vaccinId,
    date = date,
    lieu = lieu,
    lot = lot,
)

@JvmName("vaccinsAdministresToDomain")
fun List<VaccinAdministreEntity>.toDomain(): List<VaccinAdministre> = map { it.toDomain() }

fun VaccinAdministre.toEntity(): VaccinAdministreEntity = VaccinAdministreEntity(
    id = id,
    enfantId = enfantId,
    vaccinId = vaccinId,
    date = date,
    lieu = lieu,
    lot = lot,
)

@JvmName("vaccinsAdministresToEntity")
fun List<VaccinAdministre>.toEntity(): List<VaccinAdministreEntity> = map { it.toEntity() }

/** Lecture tolérante : une valeur absente du jeu de constantes ne fait pas échouer la lecture de la base. */
private fun sexeDepuisTexte(texte: String): Sexe =
    Sexe.entries.firstOrNull { it.name == texte } ?: Sexe.NON_PRECISE
