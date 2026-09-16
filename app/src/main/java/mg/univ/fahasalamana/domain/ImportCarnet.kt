package mg.univ.fahasalamana.domain

import kotlinx.serialization.SerializationException
import java.time.format.DateTimeParseException

/*
 * ---------------------------------------------------------------------------
 * IMPORT D'UN CARNET VENU D'UN AUTRE TÉLÉPHONE (B17, US-B9 scénario 2)
 * ---------------------------------------------------------------------------
 *
 * Deux décisions sont prises ici, et elles sont écrites avant le code parce qu'elles
 * engagent des données de santé.
 *
 * 1. CE QUI EST REFUSÉ, ET REFUSÉ AVANT TOUTE ÉCRITURE
 * ----------------------------------------------------
 * `lireCarnet()` (B16) lève dès que le texte n'est pas un carnet : JSON invalide, champ
 * obligatoire absent, date hors format ISO. [analyserCarnet] est la porte d'entrée de
 * l'import : elle traduit ces échecs en [LectureCarnet.Illisible] et contrôle en plus le
 * `schemaVersion`. Tant qu'elle n'a pas rendu [LectureCarnet.Lu], **rien n'est écrit en
 * base** — le repository ne reçoit qu'un [CarnetExport] déjà validé.
 *
 * Deux familles d'exceptions à attraper, et pas une seule : le décodeur lève une
 * `SerializationException`, mais `SerialiseurLocalDate` appelle `LocalDate.parse`, qui lève
 * une `DateTimeParseException` — que kotlinx.serialization enveloppe ou non selon les
 * versions. `ExportImportCarnetTest.dateIllisible_echoueFranchement` accepte les deux
 * volontairement ; ici, les deux sont rattrapées.
 *
 * 2. QUI GAGNE QUAND LE MÊME IDENTIFIANT EXISTE DES DEUX CÔTÉS
 * ------------------------------------------------------------
 * **Le téléphone gagne. L'import n'ajoute que ce qui manque, il ne remplace jamais rien.**
 *
 * Le CDC ne tranche pas (US-B9 dit « fusion par identifiant, sans doublon », §B6 nomme
 * `importer(carnet): ResultatImport` sans en décrire la sémantique). Les trois options
 * possibles, et pourquoi celle-ci :
 *
 * - *le fichier gagne* (remplacement) : un export a une date (`exporteLe`) mais les lignes
 *   locales n'en ont pas — rien ne permet de savoir laquelle des deux valeurs est la plus
 *   récente. Réimporter un export d'il y a trois mois écraserait donc silencieusement une
 *   date de vaccination corrigée hier sur ce téléphone. L'application n'a ni annulation ni
 *   sauvegarde (`allowBackup=false`, §B8 point 5) : cette perte serait définitive.
 * - *l'utilisateur choisit* : un carnet contient couramment plusieurs dizaines de doses. Un
 *   arbitrage ligne à ligne demanderait un écran de résolution de conflits qui n'est ni dans
 *   les wireframes (§B7.2) ni dans les 2 j/h de la tâche, et poserait au parent des
 *   questions auxquelles il ne peut pas répondre de mémoire.
 * - *le téléphone gagne* : aucune donnée déjà saisie ici ne peut être perdue par un import.
 *   Tout ce qui manque est ajouté — c'est bien une fusion, et deux téléphones qui
 *   s'échangent leurs carnets convergent vers l'union des deux. Ce qui existait déjà est
 *   conservé tel quel, et le rapport d'import ([ResultatImport]) le dit en clair, de sorte
 *   qu'une valeur divergente reste corrigeable à la main sur la fiche de l'enfant.
 *
 * Conséquence assumée : une correction faite sur le téléphone d'origine (une faute de frappe
 * dans un prénom, une date rectifiée) ne se propage pas à un téléphone qui connaît déjà cet
 * enfant. Le rapport annonce « déjà enregistré » et non « mis à jour », donc l'utilisateur
 * n'est pas laissé à croire le contraire. **À reconsidérer** si une V2 ajoute un horodatage
 * de modification par ligne : on pourrait alors faire gagner la valeur la plus récente,
 * ce que ni le format de fichier ni le schéma Room ne permettent aujourd'hui.
 *
 * 3. CE QUE CETTE POLITIQUE RÈGLE AU PASSAGE : LE PIÈGE DU `INSERT OR REPLACE`
 * ----------------------------------------------------------------------------
 * `VaccinAdministreDao.upsert` est un `INSERT OR REPLACE` (règle R5 : l'unicité porte sur
 * (enfantId, vaccinId), qui n'est pas la clé primaire). Sur conflit, SQLite **supprime la
 * ligne en place et insère la nouvelle** : c'est l'identifiant du fichier importé qui
 * survivrait, et l'identifiant local disparaîtrait — alors que c'est lui, et lui seul, qui
 * permet de fusionner les imports suivants sans doublon.
 *
 * Comme rien n'est jamais remplacé, aucune écriture de l'import ne peut déclencher ce
 * conflit : une dose dont le couple (enfant, vaccin) est déjà en base n'est pas réécrite,
 * **même si son identifiant de dose diffère**. C'est ce que vérifie
 * `doseDejaPresenteSousUnAutreIdentifiant_nEstPasReinseree` dans `ImportCarnetTest`.
 *
 * Fichier **pur Kotlin** (règle 1 de CLAUDE.md) : ni Android, ni Room, ni SAF. La lecture du
 * document choisi est dans `platform/ImportCarnetSaf.kt`, l'écriture en base dans
 * `EnfantRepository.importer()`, et l'enchaînement des deux dans
 * `ui/importation/ImportCarnetViewModel.kt`.
 */

/**
 * Les formats de fichier que cette version sait importer.
 *
 * Un intervalle et non une égalité : le jour où [SCHEMA_VERSION_CARNET] passera à 2, les
 * carnets de format 1 devront continuer d'être importables — c'est tout l'intérêt d'avoir
 * numéroté le format. Une valeur hors de cet intervalle (un 0, un négatif, un format à
 * venir) est refusée : un fichier écrit par une version ultérieure peut porter des champs
 * chargés de sens que celle-ci ignorerait en silence.
 *
 * Un fichier sans clé `schemaVersion` est lu comme un format 1, par la valeur par défaut de
 * [CarnetExport.schemaVersion].
 */
val VERSIONS_CARNET_IMPORTABLES: IntRange = 1..SCHEMA_VERSION_CARNET

/**
 * Ce que donne la lecture d'un fichier choisi par l'utilisateur, **avant** toute écriture.
 *
 * Un `sealed interface` et non une exception : un fichier illisible n'est pas un incident
 * technique, c'est une issue normale de l'import (le parent a pu désigner une photo), et
 * elle doit être présentée comme un message, pas comme un plantage.
 */
sealed interface LectureCarnet {

    /** Le fichier est un carnet d'un format connu. Rien n'est encore écrit en base. */
    data class Lu(val carnet: CarnetExport) : LectureCarnet

    /**
     * Le fichier n'est pas un carnet exporté par l'application, ou il est abîmé.
     *
     * Aucun détail technique n'est transporté : le message affiché ne doit contenir ni
     * chemin de document, ni extrait du fichier (§B8).
     */
    data object Illisible : LectureCarnet

    /**
     * Le fichier est un carnet, mais d'un format que cette version ne sait pas lire.
     *
     * @param version le `schemaVersion` trouvé dans le fichier, affiché tel quel pour que
     *   le parent puisse le citer s'il demande de l'aide.
     */
    data class VersionInconnue(val version: Int) : LectureCarnet
}

/**
 * Lit le texte d'un fichier et dit s'il est importable.
 *
 * Aucune écriture, aucun effet de bord : c'est la fonction qui garantit le « rien n'a été
 * modifié dans votre carnet » affiché en cas de refus.
 */
fun analyserCarnet(texte: String): LectureCarnet {
    val carnet = try {
        lireCarnet(texte)
    } catch (erreur: SerializationException) {
        // JSON invalide, champ obligatoire absent, type inattendu.
        return LectureCarnet.Illisible
    } catch (erreur: DateTimeParseException) {
        // Date hors format ISO, levée par `SerialiseurLocalDate` et laissée passer telle quelle.
        return LectureCarnet.Illisible
    } catch (erreur: IllegalArgumentException) {
        // Filet : `SerializationException` en hérite, mais le décodeur peut aussi lever une
        // `IllegalArgumentException` nue. Attrapée après la précédente, jamais avant.
        return LectureCarnet.Illisible
    }

    return if (carnet.schemaVersion in VERSIONS_CARNET_IMPORTABLES) {
        LectureCarnet.Lu(carnet)
    } else {
        LectureCarnet.VersionInconnue(carnet.schemaVersion)
    }
}

/**
 * Le rapport d'import, affiché au parent après la fusion (US-B9 scénario 2).
 *
 * Sans lui, un import est une boîte noire : l'utilisateur ne peut pas vérifier que son
 * carnet est complet, ni comprendre pourquoi le fichier contient dix vaccins et la fiche
 * n'en montre pas dix de plus. Les trois issues possibles d'une ligne sont donc comptées
 * séparément.
 *
 * @param enfantsAjoutes enfants qui n'étaient pas sur ce téléphone et qui y sont maintenant.
 * @param enfantsFusionnes enfants **reconnus par leur identifiant** et conservés tels quels.
 *   Leurs doses manquantes, elles, ont bien été ajoutées : c'est le cœur de la fusion.
 * @param enfantsIgnores enfants apparaissant deux fois dans le fichier lui-même. Impossible
 *   dans un fichier produit par l'application (l'identifiant est clé primaire en base) ;
 *   compté pour qu'un fichier bricolé à la main ne fasse pas échouer l'import entier.
 * @param dosesAjoutees doses inscrites au carnet par cet import.
 * @param dosesFusionnees doses déjà connues de ce téléphone, **par leur identifiant ou par
 *   le couple (enfant, vaccin)** de la règle R5, et laissées intactes.
 * @param dosesIgnorees doses en double dans le fichier lui-même.
 */
data class ResultatImport(
    val enfantsAjoutes: Int = 0,
    val enfantsFusionnes: Int = 0,
    val enfantsIgnores: Int = 0,
    val dosesAjoutees: Int = 0,
    val dosesFusionnees: Int = 0,
    val dosesIgnorees: Int = 0,
) {

    /** Lignes en double **dans le fichier**, signalées à part : elles disent que le fichier est anormal. */
    val lignesIgnorees: Int get() = enfantsIgnores + dosesIgnorees

    /** Le fichier ne contenait rien du tout : ni enfant, ni dose. */
    val fichierSansContenu: Boolean
        get() = enfantsAjoutes == 0 && enfantsFusionnes == 0 && enfantsIgnores == 0 &&
            dosesAjoutees == 0 && dosesFusionnees == 0 && dosesIgnorees == 0

    /** Tout ce que portait le fichier était déjà là : le carnet n'a pas changé. */
    val rienAjoute: Boolean get() = enfantsAjoutes == 0 && dosesAjoutees == 0
}

/**
 * Ce qu'il y a à écrire en base, et le rapport qui va avec.
 *
 * Deux listes seulement, **et aucune liste de mise à jour** : c'est la politique de fusion
 * du haut de ce fichier rendue visible dans le type. Un import ne peut pas modifier une
 * ligne existante, parce qu'il n'y a nulle part où en exprimer une.
 */
data class FusionCarnet(
    val enfantsAAjouter: List<Enfant>,
    val dosesAAjouter: List<VaccinAdministre>,
    val resultat: ResultatImport,
)

/**
 * Fusionne un carnet importé avec le contenu actuel du téléphone.
 *
 * Fonction **pure** : elle ne lit ni n'écrit rien, elle décide. C'est ce qui la rend
 * testable en JVM (`ImportCarnetTest`), là où le repository qui l'appelle demande un
 * appareil.
 *
 * Trois clés de reconnaissance, dans cet ordre :
 * 1. **l'identifiant de l'enfant** — un enfant déjà présent n'est jamais dupliqué ;
 * 2. **l'identifiant de la dose** — la même saisie, réimportée, est reconnue ;
 * 3. **le couple (enfantId, vaccinId)** — la règle R5 n'autorise qu'une dose par couple.
 *    Deux téléphones ayant saisi chacun de leur côté le même vaccin pour le même enfant
 *    produisent deux identifiants différents pour une même réalité : sans cette
 *    troisième clé, l'import violerait l'index unique de la base (et, avec un
 *    `INSERT OR REPLACE`, écraserait l'identifiant local — voir l'en-tête du fichier).
 *
 * Les doses d'un enfant déjà présent sont traitées comme les autres : c'est ainsi qu'un
 * carnet tenu à deux (un parent sur chaque téléphone) se complète au lieu de se dupliquer.
 *
 * Aucun contrôle sur l'enfant d'une dose : le format imbrique les doses **sous** leur
 * enfant, et `versDoses()` reconstruit `enfantId` depuis l'enfant qui la porte — un fichier
 * ne peut donc pas contenir de dose orpheline (`ExportImportCarnetTest`).
 *
 * @param enfantsLocaux les enfants déjà en base.
 * @param dosesLocales toutes les doses déjà en base, tous enfants confondus.
 */
fun fusionner(
    carnet: CarnetExport,
    enfantsLocaux: List<Enfant>,
    dosesLocales: List<VaccinAdministre>,
): FusionCarnet {
    val enfantsDejaEnBase = enfantsLocaux.mapTo(mutableSetOf()) { it.id }
    val dosesDejaEnBase = dosesLocales.mapTo(mutableSetOf()) { it.id }
    val couplesDejaEnBase = dosesLocales.mapTo(mutableSetOf()) { it.enfantId to it.vaccinId }

    // Ce que le fichier a déjà fourni : protège des doublons internes au fichier, qui
    // feraient échouer l'insertion en bloc sur la clé primaire ou sur l'index unique.
    val enfantsVus = mutableSetOf<String>()
    val dosesVues = mutableSetOf<String>()
    val couplesVus = mutableSetOf<Pair<String, String>>()

    val enfantsAAjouter = mutableListOf<Enfant>()
    var enfantsFusionnes = 0
    var enfantsIgnores = 0

    carnet.versEnfants().forEach { enfant ->
        when {
            enfant.id in enfantsDejaEnBase -> enfantsFusionnes++
            !enfantsVus.add(enfant.id) -> enfantsIgnores++
            else -> enfantsAAjouter += enfant
        }
    }

    val dosesAAjouter = mutableListOf<VaccinAdministre>()
    var dosesFusionnees = 0
    var dosesIgnorees = 0

    carnet.versDoses().forEach { dose ->
        val couple = dose.enfantId to dose.vaccinId
        if (dose.id in dosesDejaEnBase || couple in couplesDejaEnBase) {
            dosesFusionnees++
            return@forEach
        }
        // Les deux `add` sont évalués tous les deux : un `||` court-circuiterait le second et
        // laisserait le couple non enregistré, donc réinsérable plus bas dans le fichier.
        val identifiantNouveau = dosesVues.add(dose.id)
        val coupleNouveau = couplesVus.add(couple)
        if (identifiantNouveau && coupleNouveau) {
            dosesAAjouter += dose
        } else {
            dosesIgnorees++
        }
    }

    return FusionCarnet(
        enfantsAAjouter = enfantsAAjouter,
        dosesAAjouter = dosesAAjouter,
        resultat = ResultatImport(
            enfantsAjoutes = enfantsAAjouter.size,
            enfantsFusionnes = enfantsFusionnes,
            enfantsIgnores = enfantsIgnores,
            dosesAjoutees = dosesAAjouter.size,
            dosesFusionnees = dosesFusionnees,
            dosesIgnorees = dosesIgnorees,
        ),
    )
}
