package mg.univ.fahasalamana.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mg.univ.fahasalamana.data.remote.SerialiseurLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * ---------------------------------------------------------------------------
 * FORMAT DU CARNET EXPORTÉ (US-B9, §B6 `ExportImportCarnet`, §B8 point 2)
 * ---------------------------------------------------------------------------
 *
 * Ce fichier est un **contrat entre deux téléphones**, au même titre que `calendrier.json`
 * et `csb.json` (§B5.1) : un carnet écrit par une installation doit être relu par une
 * autre, éventuellement d'une version différente de l'application. D'où les trois
 * mêmes précautions que pour les fichiers de référence :
 *
 * 1. **`schemaVersion`** fige le format. B17 refusera un fichier dont le format est
 *    inconnu plutôt que d'en deviner le contenu.
 * 2. **`ignoreUnknownKeys`** : un champ ajouté par une version ultérieure (« poids à la
 *    naissance », « remarques ») reste lisible par les APK déjà installés.
 * 3. **Identifiants stables** : les UUID générés à la création (`nouvelIdentifiant()`)
 *    partent tels quels et reviennent tels quels. C'est eux, et rien d'autre, qui
 *    permettront à l'import (B17) de fusionner sans créer de doublon ni renuméroter.
 *
 * **Ce que le fichier ne contient pas, volontairement** : aucun identifiant d'appareil,
 * de compte ou d'installation, aucun horodatage technique, aucune donnée de localisation.
 * Un carnet de santé qui sort de l'appareil ne doit rien transporter de plus que ce que
 * le parent y a saisi — les trois champs d'en-tête sont `schemaVersion`, `exporteLe` et
 * la liste des enfants, et c'est tout (§B8).
 *
 * **Structure imbriquée** (les doses sous leur enfant) et non deux listes à plat. Le §B10.4
 * décrit le format en une parenthèse — « version, liste d'enfants, liste d'administrations » —
 * qui suggérait deux listes parallèles ; le choix fait ici s'en écarte pour deux raisons, à
 * signaler en relecture :
 * - le fichier est **lu par l'utilisateur** (§B8 point 3 : « en clair, lisibilité ») et un
 *   parent qui l'ouvre veut voir un enfant et ses doses ensemble, pas deux tableaux à
 *   recouper à la main ;
 * - c'est déjà la forme de `csb.json` (régions → districts → centres) : un seul style de
 *   fichier dans le projet.
 * Conséquence assumée : `enfantId` n'est pas répété dans chaque dose. L'enfant qui la
 * contient fait autorité, et il n'y a donc aucun moyen d'écrire un fichier où une dose
 * désigne un enfant absent.
 *
 * Ce fichier est **pur Kotlin, sans Android ni Room** (règle 1 de CLAUDE.md) : il se teste
 * en JVM (`ExportImportCarnetTest`). L'écriture dans le document choisi par l'utilisateur
 * vit dans `platform/ExportCarnetSaf.kt`, la lecture du carnet en base dans
 * `EnfantRepository.exporter()`.
 *
 * Le seul import hors de `domain` est [SerialiseurLocalDate] : sa propre documentation
 * prévoit explicitement cette réutilisation (« Même règle que côté base et que côté export
 * (B16) »), et c'est un objet de transport sans Android. Le redéclarer ici ferait deux
 * définitions de « une date est du texte ISO », qui finiraient par diverger.
 */

/**
 * Version du format de carnet exporté.
 *
 * À incrémenter **uniquement** pour une modification incompatible (champ obligatoire
 * ajouté, sens d'un champ changé). Un champ facultatif ajouté ne la change pas : les
 * anciennes installations l'ignoreront grâce à `ignoreUnknownKeys`.
 */
const val SCHEMA_VERSION_CARNET: Int = 1

/** Préfixe du nom de fichier proposé au sélecteur de document (US-B9, scénario 1). */
private const val PREFIXE_FICHIER_CARNET: String = "carnet-fahasalamana-"

/** Extension et type du fichier : du JSON, lisible par un humain comme par l'application. */
const val EXTENSION_FICHIER_CARNET: String = ".json"

/**
 * Le carnet complet d'un téléphone, tel qu'il est écrit dans le fichier.
 *
 * @param schemaVersion voir [SCHEMA_VERSION_CARNET].
 * @param exporteLe jour de l'export, en texte ISO `yyyy-MM-dd` comme partout ailleurs
 *   dans le projet (règle 2 de CLAUDE.md : jamais un epoch pour une date). Il correspond
 *   au jour inscrit dans le nom du fichier.
 * @param enfants les enfants du carnet, chacun avec ses doses reçues.
 */
@Serializable
data class CarnetExport(
    val schemaVersion: Int = SCHEMA_VERSION_CARNET,
    @Serializable(with = SerialiseurLocalDate::class) val exporteLe: LocalDate,
    val enfants: List<EnfantExport> = emptyList(),
)

/**
 * Un enfant et ses doses reçues.
 *
 * @param id UUID d'origine, **jamais régénéré** : c'est la clé de fusion de B17.
 * @param sexe nom de la constante [Sexe] (`GARCON`, `FILLE`, `NON_PRECISE`), et non
 *   l'énumération elle-même : une valeur inconnue venue d'une version ultérieure doit
 *   pouvoir être ramenée à `NON_PRECISE` au lieu de faire échouer la lecture de tout le
 *   fichier. Même tolérance que côté base (`EntitesPersonnelles`).
 */
@Serializable
data class EnfantExport(
    val id: String,
    val prenom: String,
    @Serializable(with = SerialiseurLocalDate::class) val dateNaissance: LocalDate,
    val sexe: String,
    val vaccins: List<DoseExport> = emptyList(),
)

/**
 * Une dose reçue, sous l'enfant qui l'a reçue.
 *
 * @param id UUID d'origine de la saisie, clé de fusion de B17.
 * @param vaccinId identifiant de la ligne du calendrier (§B5.1), stable dans le temps.
 *   Il peut désigner une dose retirée d'une version ultérieure du calendrier : la saisie
 *   reste valide et doit être conservée (décision n° 5 du suivi).
 * @param lieu et @param lot facultatifs, comme à la saisie (US-B3).
 */
@Serializable
data class DoseExport(
    val id: String,
    val vaccinId: String,
    @Serializable(with = SerialiseurLocalDate::class) val date: LocalDate,
    val lieu: String? = null,
    val lot: String? = null,
)

/**
 * Le format de lecture et d'écriture du fichier de carnet.
 *
 * - `prettyPrint` : le §B8 (point 3) demande un fichier **en clair et lisible** par son
 *   propriétaire. Un carnet fait quelques kilo-octets, l'indentation ne coûte rien.
 * - `encodeDefaults` : `schemaVersion` et les listes vides sont toujours écrits, pour que
 *   le fichier soit complet et relisible sans connaître les valeurs par défaut du code.
 * - `ignoreUnknownKeys` : clause de compatibilité ascendante (voir l'en-tête du fichier).
 * - pas de `isLenient` : un fichier mal formé doit échouer franchement, jamais être deviné.
 *   C'est B17 qui traduira cet échec en message « fichier illisible ».
 */
val jsonCarnet: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Nom de fichier proposé au sélecteur de document : `carnet-fahasalamana-AAAAMMJJ.json`
 * (US-B9, scénario 1).
 *
 * Le jour est celui de l'horloge injectée, pas `LocalDate.now()` : c'est aussi celui que
 * porte [CarnetExport.exporteLe], pour que le nom du fichier et son contenu ne puissent
 * pas désigner deux jours différents.
 *
 * Date **sans séparateur** (`20260916`) : elle traverse tous les systèmes de fichiers et
 * trie les exports dans l'ordre chronologique dans n'importe quel gestionnaire de fichiers.
 */
fun nomFichierCarnet(jour: LocalDate): String =
    PREFIXE_FICHIER_CARNET + jour.format(DateTimeFormatter.BASIC_ISO_DATE) + EXTENSION_FICHIER_CARNET

/**
 * Assemble le carnet à écrire à partir des objets du domaine.
 *
 * Les doses sont rangées sous leur enfant par `enfantId`. **Une dose dont l'enfant n'est
 * pas dans la liste est ignorée** : elle ne peut pas exister en base (cascade de
 * suppression), et l'écrire sans son enfant produirait un fichier qu'aucun import ne
 * saurait rattacher.
 *
 * Deux tris, pour qu'un même carnet exporté deux fois produise deux fichiers identiques —
 * ce qui rend un export comparable au précédent avec un simple `diff` :
 * - les enfants par prénom puis par identifiant ;
 * - les doses par date puis par identifiant de vaccin.
 */
fun carnetExport(
    enfants: List<Enfant>,
    doses: List<VaccinAdministre>,
    exporteLe: LocalDate,
): CarnetExport {
    val dosesParEnfant = doses.groupBy { it.enfantId }
    return CarnetExport(
        schemaVersion = SCHEMA_VERSION_CARNET,
        exporteLe = exporteLe,
        enfants = enfants
            .sortedWith(compareBy({ it.prenom.lowercase() }, { it.id }))
            .map { enfant ->
                EnfantExport(
                    id = enfant.id,
                    prenom = enfant.prenom,
                    dateNaissance = enfant.dateNaissance,
                    sexe = enfant.sexe.name,
                    vaccins = dosesParEnfant[enfant.id]
                        .orEmpty()
                        .sortedWith(compareBy({ it.date }, { it.vaccinId }))
                        .map { dose ->
                            DoseExport(
                                id = dose.id,
                                vaccinId = dose.vaccinId,
                                date = dose.date,
                                lieu = dose.lieu,
                                lot = dose.lot,
                            )
                        },
                )
            },
    )
}

/** Le texte JSON à écrire dans le document choisi par l'utilisateur. */
fun ecrireCarnet(carnet: CarnetExport): String = jsonCarnet.encodeToString(carnet)

/**
 * Relit un carnet depuis le **texte** d'un fichier.
 *
 * Écrite ici avec l'écriture, parce que c'est l'autre moitié du même contrat : c'est elle
 * qui rend l'aller-retour vérifiable en JVM dès B16, et c'est ce test qui protège le
 * format le jour où B17 sera écrite.
 *
 * Lève si le texte n'est pas du JSON valide ou si un champ obligatoire manque.
 *
 * TODO(B17) — ce qui manque ici pour l'import, et qui n'appartient pas à B16 :
 *  - **ouvrir le fichier** choisi par `ActionResultContracts.OpenDocument` et en lire les
 *    octets ; aucune lecture de fichier n'est faite dans B16 ;
 *  - **refuser un `schemaVersion` inconnu** (`carnet.schemaVersion > SCHEMA_VERSION_CARNET`)
 *    au lieu d'importer un fichier à moitié compris ;
 *  - **traduire l'échec** en message « fichier illisible » plutôt qu'en exception ;
 *  - **fusionner par identifiant** dans la base (`EnfantDao.enregistrerTous`,
 *    `VaccinAdministreDao`), produire le `ResultatImport` (ajoutés / mis à jour / ignorés)
 *    et appeler `PlanificateurRappels.replanifierTout()` (§B8).
 *    Point de vigilance déjà relevé au suivi : `INSERT OR REPLACE` ne conserve pas
 *    l'identifiant de la ligne remplacée en cas de conflit sur (enfantId, vaccinId).
 */
fun lireCarnet(texte: String): CarnetExport = jsonCarnet.decodeFromString(texte)

/**
 * Les enfants du carnet, en objets du domaine.
 *
 * Un `sexe` inconnu est ramené à [Sexe.NON_PRECISE] : un fichier venu d'une version
 * ultérieure ne doit pas rendre l'enfant illisible pour une valeur d'affichage.
 */
fun CarnetExport.versEnfants(): List<Enfant> = enfants.map { enfant ->
    Enfant(
        id = enfant.id,
        prenom = enfant.prenom,
        dateNaissance = enfant.dateNaissance,
        sexe = Sexe.entries.firstOrNull { it.name == enfant.sexe } ?: Sexe.NON_PRECISE,
    )
}

/**
 * Toutes les doses du carnet, à plat, en objets du domaine.
 *
 * `enfantId` est reconstruit depuis l'enfant qui porte la dose : c'est lui qui fait
 * autorité, le fichier ne le répète pas.
 */
fun CarnetExport.versDoses(): List<VaccinAdministre> = enfants.flatMap { enfant ->
    enfant.vaccins.map { dose ->
        VaccinAdministre(
            id = dose.id,
            enfantId = enfant.id,
            vaccinId = dose.vaccinId,
            date = dose.date,
            lieu = dose.lieu,
            lot = dose.lot,
        )
    }
}

/*
 * Compteurs du message de confirmation (US-B9 : « contenant enfants et vaccins
 * administrés »). Déclarés en **extensions** et non dans le corps de [CarnetExport] :
 * kotlinx.serialization ne sérialise que les propriétés du constructeur, mais une
 * extension retire toute ambiguïté — ces deux valeurs ne peuvent pas se retrouver dans
 * le fichier.
 */

/** Nombre d'enfants dans le carnet exporté. */
val CarnetExport.nbEnfants: Int get() = enfants.size

/** Nombre total de doses reçues, tous enfants confondus. */
val CarnetExport.nbDoses: Int get() = enfants.sumOf { it.vaccins.size }
