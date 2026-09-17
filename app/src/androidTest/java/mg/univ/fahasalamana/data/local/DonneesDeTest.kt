package mg.univ.fahasalamana.data.local

import mg.univ.fahasalamana.domain.Sexe
import java.time.LocalDate

/*
 * ---------------------------------------------------------------------------
 * Jeu de données des tests DAO instrumentés (B20)
 * ---------------------------------------------------------------------------
 *
 * Un seul jeu, partagé par les trois fichiers de test, pour que les scénarios se lisent
 * comme des phrases (« Soa a reçu son BCG, on supprime Soa ») plutôt que comme des
 * constructions d'entités.
 *
 * Les identifiants de vaccin, de région et de district sont ceux des fichiers réellement
 * embarqués (`assets/calendrier.json`, `assets/csb.json`, §B5.1) : un test qui inventerait
 * des identifiants ne dirait rien du comportement de l'application. Les valeurs de vaccin
 * (`ordre`, `ageJours`, `dependDe`, `toleranceJours`) sont recopiées du calendrier publié.
 *
 * **Aucune date n'est calculée à partir d'aujourd'hui.** Ces tests portent sur la base, pas
 * sur les statuts : un `LocalDate.now()` rendrait leur résultat dépendant du jour où on les
 * lance, pour rien. Les dates ci-dessous sont fixes et cohérentes entre elles :
 *
 *   Soa, née le 15/01/2026
 *     BCG     le 16/01/2026  (le lendemain de la naissance)
 *     penta1  le 26/02/2026  (42 jours après la naissance, l'âge prévu)
 *     penta2  le 26/03/2026  (28 jours après penta1, comme le prévoit `dependDe`)
 *
 *   Ranto, né le 03/09/2025
 *     BCG     le 04/09/2025
 *     penta1  le 15/10/2025  (42 jours)
 *     penta2  le 07/01/2026  — dose reçue **en retard**, et l'année change entre-temps :
 *                              c'est ce qui rend le tri SQL sur la colonne `date` vérifiable.
 */

// --- Fabriques -------------------------------------------------------------
//
// Des valeurs par défaut pour tout ce qui ne joue aucun rôle dans un test donné : ce qui
// reste écrit à l'appel est exactement ce qui compte pour le scénario.

fun vaccinDeTest(
    id: String,
    nom: String,
    dose: String,
    ordre: Int,
    ageJours: Int,
    dependDe: String? = null,
    toleranceJours: Int = 14,
    description: String = "",
): VaccinReferenceEntity = VaccinReferenceEntity(
    id = id,
    nom = nom,
    dose = dose,
    ordre = ordre,
    ageJours = ageJours,
    dependDe = dependDe,
    toleranceJours = toleranceJours,
    description = description,
)

fun enfantDeTest(
    id: String,
    prenom: String,
    dateNaissance: LocalDate,
    sexe: Sexe = Sexe.NON_PRECISE,
    creeLe: Long = 1_760_000_000_000L,
): EnfantEntity = EnfantEntity(
    id = id,
    prenom = prenom,
    dateNaissance = dateNaissance,
    sexe = sexe.name,
    creeLe = creeLe,
)

fun doseDeTest(
    id: String,
    enfantId: String,
    vaccinId: String,
    date: LocalDate,
    lieu: String? = null,
    lot: String? = null,
): VaccinAdministreEntity = VaccinAdministreEntity(
    id = id,
    enfantId = enfantId,
    vaccinId = vaccinId,
    date = date,
    lieu = lieu,
    lot = lot,
)

fun centreDeTest(
    id: String,
    districtId: String,
    nom: String,
    type: String = "CSB2",
    telephone: String = "+261 32 00 000 00",
    horaires: String = "Lun–Ven 8h00–16h00, vaccination mardi matin",
    adresse: String = nom,
): CentreEntity = CentreEntity(
    id = id,
    districtId = districtId,
    nom = nom,
    type = type,
    telephone = telephone,
    horaires = horaires,
    adresse = adresse,
)

// --- Calendrier vaccinal ---------------------------------------------------

val VACCIN_BCG = vaccinDeTest(
    id = "bcg",
    nom = "BCG",
    dose = "dose unique",
    ordre = 1,
    ageJours = 0,
    toleranceJours = 30,
    description = "À la naissance",
)

val VACCIN_PENTA_1 = vaccinDeTest(
    id = "penta1",
    nom = "Pentavalent",
    dose = "1re dose",
    ordre = 3,
    ageJours = 42,
    description = "6 semaines",
)

val VACCIN_PENTA_2 = vaccinDeTest(
    id = "penta2",
    nom = "Pentavalent",
    dose = "2e dose",
    ordre = 7,
    ageJours = 28,
    dependDe = VACCIN_PENTA_1.id,
    description = "4 semaines après la 1re dose",
)

val VACCIN_RR_1 = vaccinDeTest(
    id = "rr1",
    nom = "Rougeole-Rubéole",
    dose = "1re dose",
    ordre = 15,
    ageJours = 270,
    toleranceJours = 30,
    description = "9 mois",
)

/** La même ligne de calendrier, avec une tolérance élargie : sert à montrer qu'un remplacement écrase le contenu au lieu de le fusionner. */
val VACCIN_PENTA_1_REVISE = VACCIN_PENTA_1.copy(toleranceJours = 21)

/** Version en place au moment où l'utilisateur saisit ses doses. */
val CALENDRIER_V1 = listOf(VACCIN_BCG, VACCIN_PENTA_1, VACCIN_PENTA_2)

/**
 * Version suivante du calendrier publié. Trois changements, tous nécessaires aux tests :
 * `penta2` **retiré**, `penta1` révisé, `rr1` ajouté. Peu importe la raison d'un retrait
 * (changement de protocole, fusion de doses, erreur corrigée) : le §B5.2 exige qu'une dose
 * déjà administrée y survive.
 */
val CALENDRIER_V2 = listOf(VACCIN_BCG, VACCIN_PENTA_1_REVISE, VACCIN_RR_1)

// --- Enfants ---------------------------------------------------------------

val ENFANT_SOA = enfantDeTest(
    id = "enfant-soa",
    prenom = "Soa",
    dateNaissance = LocalDate.of(2026, 1, 15),
    sexe = Sexe.FILLE,
    creeLe = 1_760_000_000_000L,
)

val ENFANT_RANTO = enfantDeTest(
    id = "enfant-ranto",
    prenom = "Ranto",
    dateNaissance = LocalDate.of(2025, 9, 3),
    sexe = Sexe.GARCON,
    creeLe = 1_760_000_060_000L,
)

// --- Doses reçues ----------------------------------------------------------

val DOSE_BCG_SOA = doseDeTest(
    id = "dose-soa-bcg",
    enfantId = ENFANT_SOA.id,
    vaccinId = VACCIN_BCG.id,
    date = LocalDate.of(2026, 1, 16),
    lieu = "CSB2 Isotry",
    lot = "L-2026-042",
)

val DOSE_PENTA_1_SOA = doseDeTest(
    id = "dose-soa-penta1",
    enfantId = ENFANT_SOA.id,
    vaccinId = VACCIN_PENTA_1.id,
    date = LocalDate.of(2026, 2, 26),
    lieu = "CSB2 Isotry",
    lot = "L-2026-051",
)

/**
 * La même dose ressaisie : même enfant, même vaccin, mais date, lieu, lot **et identifiant**
 * différents. C'est le cas d'un carnet importé depuis un autre téléphone (B17), où la dose a
 * été saisie deux fois avec deux UUID différents ; à l'écran `SaisieVaccin` (B09), une
 * correction réutilise au contraire l'identifiant existant.
 */
val DOSE_PENTA_1_SOA_CORRIGEE = doseDeTest(
    id = "dose-soa-penta1-corrigee",
    enfantId = ENFANT_SOA.id,
    vaccinId = VACCIN_PENTA_1.id,
    date = LocalDate.of(2026, 3, 2),
    lieu = "CSB1 Ambohivato",
    lot = "L-2026-052",
)

val DOSE_PENTA_2_SOA = doseDeTest(
    id = "dose-soa-penta2",
    enfantId = ENFANT_SOA.id,
    vaccinId = VACCIN_PENTA_2.id,
    date = LocalDate.of(2026, 3, 26),
    lieu = "CSB2 Isotry",
    lot = "L-2026-063",
)

val DOSE_BCG_RANTO = doseDeTest(
    id = "dose-ranto-bcg",
    enfantId = ENFANT_RANTO.id,
    vaccinId = VACCIN_BCG.id,
    date = LocalDate.of(2025, 9, 4),
    lieu = "CSB2 Mahajanga Centre",
    lot = "L-2025-118",
)

val DOSE_PENTA_1_RANTO = doseDeTest(
    id = "dose-ranto-penta1",
    enfantId = ENFANT_RANTO.id,
    vaccinId = VACCIN_PENTA_1.id,
    date = LocalDate.of(2025, 10, 15),
    lieu = "CSB2 Mahajanga Centre",
    lot = "L-2025-133",
)

/** Dose reçue en retard : l'année change entre `penta1` et `penta2`. */
val DOSE_PENTA_2_RANTO = doseDeTest(
    id = "dose-ranto-penta2",
    enfantId = ENFANT_RANTO.id,
    vaccinId = VACCIN_PENTA_2.id,
    date = LocalDate.of(2026, 1, 7),
    lieu = "CSB2 Mahajanga Centre",
    lot = "L-2026-004",
)

// --- Annuaire des centres --------------------------------------------------
//
// Deux régions, pour que la cascade « région → districts → centres » puisse être observée
// sur l'une sans que l'autre bouge.

val REGION_ANALAMANGA = RegionEntity(id = "analamanga", nom = "Analamanga")
val REGION_BOENY = RegionEntity(id = "boeny", nom = "Boeny")

val DISTRICT_ANTANANARIVO_I = DistrictEntity(
    id = "antananarivo-i",
    regionId = REGION_ANALAMANGA.id,
    nom = "Antananarivo I",
)
val DISTRICT_AMBOHIDRATRIMO = DistrictEntity(
    id = "ambohidratrimo",
    regionId = REGION_ANALAMANGA.id,
    nom = "Ambohidratrimo",
)
val DISTRICT_MAHAJANGA_I = DistrictEntity(
    id = "mahajanga-i",
    regionId = REGION_BOENY.id,
    nom = "Mahajanga I",
)

val CENTRE_AMBOHIVATO = centreDeTest(
    id = "antananarivo-i-csb1-ambohivato",
    districtId = DISTRICT_ANTANANARIVO_I.id,
    nom = "CSB1 Ambohivato",
    type = "CSB1",
    telephone = "+261 32 38 242 23",
    adresse = "Ambohivato, Antananarivo I",
)
val CENTRE_ISOTRY = centreDeTest(
    id = "antananarivo-i-csb2-isotry",
    districtId = DISTRICT_ANTANANARIVO_I.id,
    nom = "CSB2 Isotry",
    telephone = "+261 32 58 180 80",
    adresse = "Isotry, Antananarivo I",
)
val CENTRE_AMBOHIDRATRIMO = centreDeTest(
    id = "ambohidratrimo-csb2-ambohidratrimo",
    districtId = DISTRICT_AMBOHIDRATRIMO.id,
    nom = "CSB2 Ambohidratrimo",
    adresse = "Ambohidratrimo",
)
val CENTRE_MAHAJANGA_CENTRE = centreDeTest(
    id = "mahajanga-i-csb2-mahajanga-centre",
    districtId = DISTRICT_MAHAJANGA_I.id,
    nom = "CSB2 Mahajanga Centre",
    telephone = "+261 32 11 090 45",
    adresse = "Mahajanga Be, Mahajanga I",
)
val CENTRE_MAROLAMBO = centreDeTest(
    id = "mahajanga-i-csb1-marolambo",
    districtId = DISTRICT_MAHAJANGA_I.id,
    nom = "CSB1 Marolambo",
    type = "CSB1",
    adresse = "Marolambo, Mahajanga I",
)

/** Annuaire en place : 2 régions, 3 districts, 5 centres. */
val ANNUAIRE_V1_REGIONS = listOf(REGION_ANALAMANGA, REGION_BOENY)
val ANNUAIRE_V1_DISTRICTS =
    listOf(DISTRICT_ANTANANARIVO_I, DISTRICT_AMBOHIDRATRIMO, DISTRICT_MAHAJANGA_I)
val ANNUAIRE_V1_CENTRES = listOf(
    CENTRE_AMBOHIVATO,
    CENTRE_ISOTRY,
    CENTRE_AMBOHIDRATRIMO,
    CENTRE_MAHAJANGA_CENTRE,
    CENTRE_MAROLAMBO,
)

/** Annuaire suivant : Boeny disparaît, et le téléphone d'un centre conservé change. */
val ANNUAIRE_V2_REGIONS = listOf(REGION_ANALAMANGA)
val ANNUAIRE_V2_DISTRICTS = listOf(DISTRICT_ANTANANARIVO_I)
val ANNUAIRE_V2_CENTRES = listOf(CENTRE_ISOTRY.copy(telephone = "+261 34 07 122 11"))
