package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.data.local.EnfantDao
import mg.univ.fahasalamana.data.local.VaccinAdministreDao
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.local.toEntity
import mg.univ.fahasalamana.domain.CarnetExport
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.carnetExport
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
 */
class EnfantRepositoryImpl(
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
}
