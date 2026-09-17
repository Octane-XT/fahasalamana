package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mg.univ.fahasalamana.data.local.CentreDao
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.local.ReferenceDao
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.VaccinReferenceDao
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.local.versEntites
import mg.univ.fahasalamana.data.remote.AnnuaireDto
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.remote.ReferenceApi
import mg.univ.fahasalamana.domain.VaccinReference
import java.time.LocalDate

/**
 * Implémentation de [ReferenceRepository] : la base locale d'abord, les fichiers
 * embarqués seulement pour l'amorcer.
 *
 * Quatre responsabilités, et pas une de plus :
 * 1. exposer le calendrier de la base en `Flow` de modèles du domaine ;
 * 2. exposer la provenance du contenu chargé ;
 * 3. amorcer la base au premier lancement avec les fichiers d'`assets/` ;
 * 4. (B19) installer une version plus récente publiée sur le réseau, sur demande.
 *
 * Il écrit **uniquement** par `ReferenceDao`, dont les deux fonctions sont
 * transactionnelles et ne portent que sur les quatre tables de référence : aucune
 * ligne d'`enfants` ni de `vaccins_administres` n'est lue, écrite ou supprimée ici.
 *
 * (B19) La décision de mise à jour — comparer les versions, refuser un format inconnu,
 * ne remplacer qu'après un téléchargement complet — n'est pas dans cette classe mais dans
 * [SynchroniseurReference], qui est sans Android et donc testable en JVM (§B9). Ce que cette
 * classe fournit à ce synchroniseur, c'est [StockageLocal] : la traduction des cinq
 * opérations dont il a besoin vers les DAO de B02 et les préférences de B04. Les deux
 * chemins de chargement, embarqué et distant, passent ainsi par le même code d'écriture.
 */
class ReferenceRepositoryImpl(
    private val vaccinReferenceDao: VaccinReferenceDao,
    private val centreDao: CentreDao,
    private val referenceDao: ReferenceDao,
    private val sources: SourcesEmbarquees,
    private val preferences: PreferencesLocales,
    // (B19) Client des fichiers publiés. Injecté et non construit ici : c'est Koin qui tient
    // le cycle de vie du client HTTP. Sans `val` : il ne sert qu'à monter le synchroniseur
    // ci-dessous, et le repository n'a aucune raison de garder une référence au réseau.
    api: ReferenceApi,
) : ReferenceRepository {

    /** (B19) Le synchroniseur et son accès au stockage local, montés une fois pour toutes. */
    private val synchroniseur = SynchroniseurReference(api = api, stockage = StockageLocal())

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
     * du code) et doit se voir tout de suite — d'où le `check`, qui lève.
     *
     * (B19) Le fichier **distant**, lui, ne peut pas faire planter l'application : le même
     * contrôle y est refait par [SynchroniseurReference], mais il y rend
     * `IssueMiseAJour.SchemaInconnu`. Deux traitements pour un seul critère, parce qu'une
     * erreur de build et un fichier mal publié n'appellent pas la même réaction. Le critère,
     * lui, est unique : [SCHEMA_REFERENCE_SUPPORTE].
     */
    private fun verifierSchema(schemaVersion: Int, fichier: String) {
        check(schemaVersion == SCHEMA_REFERENCE_SUPPORTE) {
            "$fichier annonce schemaVersion=$schemaVersion, " +
                "cette version lit le format $SCHEMA_REFERENCE_SUPPORTE"
        }
    }

    // --- Mise à jour depuis le réseau (B19, US-B11) ---------------------------

    /**
     * Délègue à [SynchroniseurReference], qui porte toute la décision.
     *
     * Une ligne, et c'est voulu : ce qui se joue dans une mise à jour — l'ordre
     * télécharger / valider / comparer / remplacer — est ce que B19 doit pouvoir montrer,
     * et il se lit en un seul endroit plutôt que dispersé entre un repository Android et un
     * client Retrofit.
     */
    override suspend fun mettreAJour(): ResultatSync = synchroniseur.synchroniser()

    /**
     * L'accès au stockage local vu par la synchronisation (B19).
     *
     * `inner` : cette classe n'a aucun état propre, elle n'est que la traduction des cinq
     * opérations de [StockageReference] vers les DAO et les préférences de l'instance
     * englobante. La déclarer ici plutôt que de faire implémenter [StockageReference] par
     * `ReferenceRepositoryImpl` lui-même évite d'ajouter cinq fonctions publiques au
     * repository : personne d'autre que le synchroniseur n'a à pouvoir remplacer un contenu
     * de référence sans passer par `mettreAJour()`.
     *
     * Les deux remplacements reprennent **exactement** le chemin de l'amorçage embarqué
     * (`versEntites()` puis `ReferenceDao`), à une différence près : ils ne testent pas si la
     * base est vide, puisqu'ici le but est justement de remplacer un contenu existant.
     */
    private inner class StockageLocal : StockageReference {

        override suspend fun versionCalendrier(): Int = preferences.calendrierVersion.first()

        override suspend fun versionAnnuaire(): Int = preferences.annuaireVersion.first()

        override suspend fun remplacerCalendrier(publie: CalendrierDto) {
            // Transaction : soit tout le nouveau calendrier est en base, soit l'ancien est
            // intact. Aucune ligne d'`enfants` ni de `vaccins_administres` n'est touchée —
            // les identifiants de dose étant stables (§B5.1), les vaccins déjà saisis
            // retrouvent leur ligne de référence après le remplacement.
            referenceDao.remplacerCalendrier(publie.versEntites())

            // Après la transaction, pour ne jamais annoncer une version que la base n'aurait
            // pas. Les trois valeurs partent dans un seul `edit` (B04) : l'écran « À propos
            // des données » ne peut pas afficher la nouvelle version avec l'ancienne source.
            preferences.enregistrerInfosCalendrier(
                version = publie.version,
                source = publie.source,
                publieLe = publie.publieLe,
            )
        }

        override suspend fun remplacerAnnuaire(publie: AnnuaireDto) {
            val entites = publie.versEntites()
            referenceDao.remplacerAnnuaire(
                regions = entites.regions,
                districts = entites.districts,
                centres = entites.centres,
            )
            preferences.enregistrerVersionAnnuaire(publie.version)
        }

        override suspend fun enregistrerVerification(jour: LocalDate) {
            preferences.enregistrerDerniereVerification(jour)
        }
    }
}
