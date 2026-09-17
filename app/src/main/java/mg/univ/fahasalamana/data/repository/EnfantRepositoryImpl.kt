package mg.univ.fahasalamana.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.data.local.EnfantDao
import mg.univ.fahasalamana.data.local.VaccinAdministreDao
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.local.toEntity
import mg.univ.fahasalamana.domain.CarnetExport
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.ResultatImport
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.carnetExport
import mg.univ.fahasalamana.domain.fusionner
import java.time.LocalDate

/**
 * Implémentation de [EnfantRepository] : deux DAO, aucune règle métier.
 *
 * Les contraintes qui pourraient ressembler à des règles sont portées par la base et non
 * par ce code : la cascade de suppression et l'unicité (enfant, vaccin) de R5 sont des
 * contraintes déclarées sur les entités. Ce qui reste ici est de la traduction.
 *
 * Aucun `Dispatchers.IO` explicite : Room bascule lui-même les fonctions `suspend` et les
 * `Flow` de requête sur son exécuteur.
 *
 * @param base la base elle-même, en plus des deux DAO : l'import (B17) écrit dans les deux
 *   tables personnelles et doit le faire en une seule transaction. `withTransaction`
 *   (`room-ktx`) est le seul moyen d'en ouvrir une **à cheval sur deux DAO** ; un
 *   `@Transaction` de DAO ne couvrirait que les requêtes de sa propre interface.
 */
class EnfantRepositoryImpl(
    private val base: AppDatabase,
    private val enfantDao: EnfantDao,
    private val vaccinAdministreDao: VaccinAdministreDao,
) : EnfantRepository {

    override fun observerTous(): Flow<List<EnfantAvecVaccins>> = enfantDao.observerTous()

    override fun observer(id: String): Flow<EnfantAvecVaccins?> = enfantDao.observer(id)

    /**
     * Création et modification passent par le même `Upsert`, l'identifiant de l'enfant
     * étant déjà fixé par l'appelant.
     *
     * La lecture préalable ne sert qu'à **conserver `creeLe`** : cet instant technique
     * n'appartient pas au modèle de `domain` (ce n'est pas une date métier, il ne s'affiche
     * jamais), donc `Enfant.toEntity()` le remplacerait par l'instant courant à chaque
     * modification. Or il départage deux enfants de même prénom dans l'ordre de la liste :
     * corriger une faute de frappe sur un prénom ferait autrement sauter l'enfant de place.
     */
    override suspend fun enregistrer(enfant: Enfant) {
        val creeLeDOrigine = enfantDao.lireAvecVaccins(enfant.id)?.enfant?.creeLe
        enfantDao.enregistrer(
            enfant.toEntity(creeLe = creeLeDOrigine ?: System.currentTimeMillis()),
        )
    }

    /** Les doses de l'enfant partent avec lui : `ON DELETE CASCADE` sur `vaccins_administres`. */
    override suspend fun supprimer(id: String) = enfantDao.supprimer(id)

    /**
     * `upsert` du DAO, c'est-à-dire `INSERT OR REPLACE` : R5 (une seule dose par couple
     * enfant/vaccin) est tenue par un index unique qui n'est pas la clé primaire, et c'est
     * la seule stratégie qui rattrape ce conflit-là sans perdre la saisie.
     */
    override suspend fun enregistrerAdministration(v: VaccinAdministre) =
        vaccinAdministreDao.upsert(v.toEntity())

    override suspend fun supprimerAdministration(id: String) = vaccinAdministreDao.supprimer(id)

    /**
     * Une seule lecture, puis une traduction vers le format de fichier (US-B9, scénario 1).
     *
     * `lireToutAvecVaccins()` est annotée `@Transaction` : les enfants et leurs doses sont
     * lus dans le même instantané de la base. Sans cela, une saisie faite pendant l'export
     * pourrait produire un fichier où une dose manque à un enfant déjà écrit.
     *
     * L'assemblage lui-même (regroupement des doses sous leur enfant, tris, en-tête) est
     * dans `domain/ExportImportCarnet.kt` : ce repository n'écrit pas le format, il fournit
     * la matière. C'est ce qui rend le format testable en JVM sans base.
     */
    override suspend fun exporter(jour: LocalDate): CarnetExport {
        val carnet = enfantDao.lireToutAvecVaccins()
        return carnetExport(
            enfants = carnet.map { it.enfant.toDomain() },
            doses = carnet.flatMap { it.administres.toDomain() },
            exporteLe = jour,
        )
    }

    /**
     * Fusion d'un carnet importé (US-B9, scénario 2).
     *
     * **Lecture, décision et écriture dans une seule transaction**, et dans cet ordre :
     * - lire l'état courant à l'intérieur de la transaction, et non avant, sinon une saisie
     *   faite pendant l'import déciderait de ce qui est « déjà présent » sur un instantané
     *   périmé — et ferait échouer l'insertion sur l'index unique de R5 ;
     * - décider dans `fusionner()`, une fonction pure du domaine : ce repository ne contient
     *   aucune des règles de la fusion (règle 1 de CLAUDE.md) ;
     * - écrire les deux tables ensemble. Un import interrompu à mi-chemin laisserait des
     *   enfants sans leurs doses : leur échéancier afficherait des vaccins déjà reçus comme
     *   « à faire », et `replanifierTout()` programmerait des rappels pour des doses déjà
     *   administrées. Tout ou rien.
     *
     * `enregistrerTous` est un `@Upsert` (conflit sur la clé primaire) et `insererTous` un
     * `INSERT` sans stratégie de remplacement : aucune ligne existante ne peut être écrasée
     * par cet appel, quelle que soit l'erreur, parce que la liste transmise ne contient que
     * du nouveau. En cas de conflit inattendu, l'exception remonte et la transaction est
     * annulée en entier — la base reste exactement dans l'état d'avant l'import.
     *
     * Les enfants ajoutés reçoivent l'instant courant comme `creeLe` : le fichier ne le
     * transporte pas (ce n'est pas une date métier, §B8 — rien d'autre que ce que le parent
     * a saisi ne sort du téléphone). Conséquence sans effet visible : ils sont créés « à
     * l'instant » sur ce téléphone, ce qui ne sert qu'à départager deux prénoms identiques
     * dans l'ordre de la liste.
     */
    override suspend fun importer(carnet: CarnetExport): ResultatImport = base.withTransaction {
        val existant = enfantDao.lireToutAvecVaccins()

        val fusion = fusionner(
            carnet = carnet,
            enfantsLocaux = existant.map { it.enfant.toDomain() },
            dosesLocales = existant.flatMap { it.administres.toDomain() },
        )

        if (fusion.enfantsAAjouter.isNotEmpty()) {
            enfantDao.enregistrerTous(fusion.enfantsAAjouter.map { it.toEntity() })
        }
        if (fusion.dosesAAjouter.isNotEmpty()) {
            vaccinAdministreDao.insererTous(fusion.dosesAAjouter.toEntity())
        }

        fusion.resultat
    }
}
