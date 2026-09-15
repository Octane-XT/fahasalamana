package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import mg.univ.fahasalamana.data.local.CentreDao
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.ReferenceDao
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.VaccinReferenceDao
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.local.versEntites
import mg.univ.fahasalamana.domain.VaccinReference

/**
 * Implémentation de [ReferenceRepository] : la base locale d'abord, les fichiers
 * embarqués seulement pour l'amorcer.
 *
 * Trois responsabilités, et pas une de plus :
 * 1. exposer le calendrier de la base en `Flow` de modèles du domaine ;
 * 2. exposer la provenance du contenu chargé ;
 * 3. amorcer la base au premier lancement avec les fichiers d'`assets/`.
 *
 * Il écrit **uniquement** par `ReferenceDao`, dont les deux fonctions sont
 * transactionnelles et ne portent que sur les quatre tables de référence : aucune
 * ligne d'`enfants` ni de `vaccins_administres` n'est lue, écrite ou supprimée ici.
 */
class ReferenceRepositoryImpl(
    private val vaccinReferenceDao: VaccinReferenceDao,
    private val centreDao: CentreDao,
    private val referenceDao: ReferenceDao,
    private val sources: SourcesEmbarquees,
    private val preferences: PreferencesLocales,
) : ReferenceRepository {

    override fun observerCalendrier(): Flow<List<VaccinReference>> =
        vaccinReferenceDao.observerCalendrier().map { it.toDomain() }

    /**
     * Recompose les informations de source à partir des trois valeurs conservées dans
     * DataStore au moment du chargement.
     *
     * Elles sont dans DataStore et non en base parce qu'il s'agit d'une seule ligne de
     * métadonnées : lui consacrer une septième table ferait un schéma pour rien.
     *
     * **N'émet rien tant qu'aucun contenu n'a été chargé** : une date de publication
     * inventée s'afficherait à l'écran comme une vraie. L'écran qui collecte ce flux
     * reste donc dans son état de chargement pendant les quelques millisecondes du
     * premier amorçage — et y resterait si l'amorçage échouait, ce qui est le bon
     * comportement : mieux vaut ne rien afficher qu'une provenance fausse.
     */
    override fun observerInfosSource(): Flow<InfosSource> = combine(
        preferences.calendrierSource,
        preferences.calendrierPublieLe,
        preferences.calendrierVersion,
    ) { source, publieLe, version ->
        publieLe?.let { InfosSource(source = source, publieLe = it, version = version) }
    }.filterNotNull().distinctUntilChanged()

    /**
     * Amorçage du contenu de référence au premier lancement.
     *
     * Les deux fichiers sont traités **indépendamment** : une base dont le calendrier
     * est chargé mais pas l'annuaire (échec à mi-parcours du tout premier lancement)
     * se répare au lancement suivant, fichier par fichier.
     *
     * Le témoin de « déjà chargé » est le contenu de la base lui-même (`COUNT(*)`) et
     * non un drapeau dans DataStore : effacer les données de l'application depuis les
     * réglages Android vide les deux, mais un DataStore corrompu ou restauré seul ne
     * peut pas faire croire à tort que la base est remplie.
     */
    override suspend fun chargerEmbarqueSiVide() {
        chargerCalendrierSiVide()
        chargerAnnuaireSiVide()
    }

    private suspend fun chargerCalendrierSiVide() {
        if (vaccinReferenceDao.compter() > 0) return

        val publie = sources.calendrier()
        verifierSchema(publie.schemaVersion, "calendrier.json")

        // Remplacement transactionnel : soit les 16 doses sont en base, soit aucune.
        referenceDao.remplacerCalendrier(publie.versEntites())

        // Écrit après la transaction, pour ne pas annoncer une version que la base
        // n'aurait finalement pas. L'inverse (base remplie, provenance non écrite) ne
        // laisse qu'un bandeau de source vide, réparable par une mise à jour (B19).
        preferences.enregistrerInfosCalendrier(
            version = publie.version,
            source = publie.source,
            publieLe = publie.publieLe,
        )
    }

    private suspend fun chargerAnnuaireSiVide() {
        if (centreDao.compterCentres() > 0) return

        val publie = sources.annuaire()
        verifierSchema(publie.schemaVersion, "csb.json")

        val entites = publie.versEntites()
        referenceDao.remplacerAnnuaire(
            regions = entites.regions,
            districts = entites.districts,
            centres = entites.centres,
        )

        preferences.enregistrerVersionAnnuaire(publie.version)
    }

    /**
     * Refuse un fichier dont le format n'est pas celui que cette version sait lire (§B5.1).
     *
     * Sur un fichier embarqué, un écart signale une erreur de build (`assets/` désynchronisé
     * du code) et doit se voir tout de suite. La même vérification protégera B19 d'un
     * `v2/` servi par erreur sur l'URL `v1/`.
     */
    private fun verifierSchema(schemaVersion: Int, fichier: String) {
        check(schemaVersion == SCHEMA_SUPPORTE) {
            "$fichier annonce schemaVersion=$schemaVersion, cette version lit le format $SCHEMA_SUPPORTE"
        }
    }

    private companion object {
        /** Format des fichiers publiés sous `v1/` (§B5.1). Une évolution incompatible publiera `v2/`. */
        const val SCHEMA_SUPPORTE = 1
    }
}
