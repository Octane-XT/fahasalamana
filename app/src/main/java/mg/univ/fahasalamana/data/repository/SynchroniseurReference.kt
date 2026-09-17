package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import mg.univ.fahasalamana.data.remote.AnnuaireDto
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.remote.ReferenceApi
import mg.univ.fahasalamana.platform.ZONE_MADAGASCAR
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * ---------------------------------------------------------------------------
 * MISE À JOUR DES CONTENUS DE RÉFÉRENCE DEPUIS LE RÉSEAU (B19, US-B11)
 * ---------------------------------------------------------------------------
 *
 * LA PROPRIÉTÉ À TENIR : « UN ÉCHEC RÉSEAU SANS EFFET SUR LA BASE »
 * ------------------------------------------------------------------
 * C'est la Definition of Done de la tâche, et elle se lit dans l'ordre des opérations
 * ci-dessous. Pour **chacun** des deux fichiers, et indépendamment de l'autre :
 *
 *   1. télécharger le fichier **entier** et le désérialiser  → tout échec sort ici ;
 *   2. vérifier le `schemaVersion`                           → tout refus sort ici ;
 *   3. comparer `distant.version` à la version en base       → « à jour » sort ici ;
 *   4. seulement alors, remplacer — en une transaction.
 *
 * Aucune écriture n'est possible avant l'étape 4, et l'étape 4 est atomique
 * (`ReferenceDao.remplacerCalendrier` / `remplacerAnnuaire`, B02). Coupure en plein
 * téléchargement, JSON tronqué, HTTP 404, page HTML servie à la place du fichier,
 * `schemaVersion` inconnu : dans tous les cas la base est exactement dans l'état d'avant.
 * Ce n'est pas une intention, c'est la forme du code — et les tests JVM de
 * `SynchroniseurReferenceTest` comptent les écritures pour le vérifier.
 *
 * POURQUOI UNE CLASSE À PART ET PAS DU CODE DANS `ReferenceRepositoryImpl`
 * ------------------------------------------------------------------------
 * Le §B9 confie à B19 des « tests JVM avec un faux `ReferenceApi` ». Or
 * `ReferenceRepositoryImpl` dépend de `SourcesEmbarquees` (un `AssetManager`) et de
 * `PreferencesLocales` (un DataStore) : deux classes qui exigent un `Context` Android et
 * qu'aucun test JVM ne peut instancier. Toute la logique de synchronisation vit donc ici,
 * **sans Android et sans Room**, derrière deux frontières minces :
 *   - [ReferenceApi] côté réseau, que le test remplace par un faux ;
 *   - [StockageReference] côté local, que le test remplace par un espion.
 * `ReferenceRepositoryImpl` ne fait plus que brancher les deux sur ses vrais DAO.
 *
 * C'est le même découpage qu'en B04 avec `SourcesEmbarquees` (lire ≠ décider) et qu'en B11
 * avec `TraductionRappels` (calculer ≠ appeler WorkManager).
 *
 * CE QUI N'EST JAMAIS TOUCHÉ
 * ---------------------------
 * [StockageReference] n'offre **aucune** façon d'écrire dans `enfants` ou dans
 * `vaccins_administres` : ni fonction, ni paramètre, ni type. La garantie de la règle 8 de
 * CLAUDE.md ne repose donc pas sur la discipline de celui qui écrit ce fichier, mais sur ce
 * que l'interface rend possible. Les tests instrumentés de B20 la vérifient côté base.
 */

/**
 * Le format des fichiers publiés sous `v1/` que cette version de l'application sait lire
 * (§B5.1).
 *
 * Une seule valeur pour les deux chemins de lecture — le fichier embarqué d'`assets/` (B04)
 * et le fichier distant (B19) —, parce que c'est un seul et même contrat. Une évolution
 * incompatible publiera `v2/` et fera passer cette constante à 2 dans la version de
 * l'application qui saura le lire.
 */
const val SCHEMA_REFERENCE_SUPPORTE: Int = 1

/**
 * Plafond de temps accordé au téléchargement d'**un** fichier de référence.
 *
 * Volontairement plus large que le `callTimeout` d'OkHttp (60 s, voir
 * `construireReferenceApi`) : dans le cours normal des choses, c'est OkHttp qui rend la main
 * le premier, avec une vraie `IOException` et une cause diagnosticable. Ce `withTimeout` est
 * le filet posé par-dessus, pour le cas où l'appel resterait bloqué ailleurs que dans une
 * socket. Le §B0 (couche 2) le demande explicitement pour B19.
 *
 * Par fichier et non pour les deux : l'annuaire pèse 115 Ko contre 4 Ko au calendrier, et un
 * calendrier lent ne doit pas consommer le temps de l'annuaire.
 */
val DELAI_MAX_TELECHARGEMENT: Duration = Duration.ofSeconds(90)

/**
 * Tout ce que la synchronisation a le droit de faire du stockage local — et rien de plus.
 *
 * Cinq fonctions : lire les deux versions déjà chargées, remplacer l'un ou l'autre contenu,
 * inscrire la date de vérification. Aucune donnée personnelle n'est atteignable par cette
 * interface.
 *
 * Les deux `remplacer…` prennent le **DTO publié** et non des entités Room : l'aplatissement
 * (`MappageReference`, B04) et la transaction (`ReferenceDao`, B02) sont l'affaire de
 * l'implémentation, côté `data/local`. Ce fichier reste ainsi sans Room, donc testable en JVM.
 */
interface StockageReference {

    /** Version du calendrier en base, `PreferencesLocales.VERSION_ABSENTE` si rien n'est chargé. */
    suspend fun versionCalendrier(): Int

    /** Version de l'annuaire en base, `PreferencesLocales.VERSION_ABSENTE` si rien n'est chargé. */
    suspend fun versionAnnuaire(): Int

    /**
     * Remplace **tout** le calendrier par le fichier publié, en une transaction, et note sa
     * version, sa source et sa date de publication.
     */
    suspend fun remplacerCalendrier(publie: CalendrierDto)

    /** Remplace **tout** l'annuaire par le fichier publié, en une transaction, et note sa version. */
    suspend fun remplacerAnnuaire(publie: AnnuaireDto)

    /** Note le jour où une vérification a effectivement abouti, pour l'écran des réglages. */
    suspend fun enregistrerVerification(jour: LocalDate)
}

/**
 * Vérifie s'il existe une version plus récente des contenus de référence, et l'installe le
 * cas échéant (US-B11).
 *
 * @param api le client des fichiers publiés. Remplacé par un faux dans les tests JVM.
 * @param stockage la base locale, vue par les cinq seules opérations dont la synchronisation
 *   a besoin.
 * @param delaiMax plafond de temps par fichier ; paramètre pour que le test du « temps
 *   dépassé » n'ait pas à attendre 90 secondes.
 * @param horloge jour courant. Paramètre et non appel implicite, comme partout ailleurs
 *   dans le projet (règle 2 de CLAUDE.md, `PlanificateurRappels`) : un test fige la date.
 */
class SynchroniseurReference(
    private val api: ReferenceApi,
    private val stockage: StockageReference,
    private val delaiMax: Duration = DELAI_MAX_TELECHARGEMENT,
    private val horloge: () -> LocalDate = { LocalDate.now(ZONE_MADAGASCAR) },
) {

    /**
     * Vérifie les deux fichiers et rend le compte rendu complet.
     *
     * **Ne lève jamais**, sauf annulation du scope appelant : c'est le contrat du §B0
     * (couche 2), « un résultat typé plutôt que des exceptions remontées à l'interface ».
     *
     * Les deux fichiers sont traités **l'un après l'autre et indépendamment**. En série et
     * non en parallèle : un `coroutineScope { async … }` ferait tomber la seconde requête
     * avec la première (l'annulation d'un enfant annule ses frères), ce qui est exactement
     * le contraire de l'indépendance demandée. Et il n'y a rien à gagner à télécharger 115 Ko
     * et 4 Ko en même temps sur la connexion qui justifie justement ce plafond de temps.
     *
     * La date de vérification n'est inscrite que si **au moins une** comparaison a abouti :
     * un téléphone hors réseau n'a rien vérifié du tout, et l'écran doit continuer d'afficher
     * la date du dernier vrai contrôle.
     */
    suspend fun synchroniser(): ResultatSync {
        val calendrier = mettreAJourCalendrier()
        val annuaire = mettreAJourAnnuaire()

        val resultat = ResultatSync(calendrier = calendrier, annuaire = annuaire)
        if (resultat.verificationAboutie) {
            // Échec ignoré : la date de vérification est un confort d'affichage. Ne pas
            // pouvoir l'écrire ne doit pas transformer une mise à jour réussie en échec.
            try {
                stockage.enregistrerVerification(horloge())
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (erreur: Exception) {
                // Rien à faire : le contenu, lui, est bien en base.
            }
        }
        return resultat
    }

    // --- Calendrier vaccinal --------------------------------------------------

    private suspend fun mettreAJourCalendrier(): IssueMiseAJour {
        // Étape 1 : le fichier est ici entièrement téléchargé et désérialisé, ou bien on sort
        // sans avoir rien touché.
        val publie = when (val telechargement = telecharger { api.calendrier() }) {
            is Telechargement.Echoue -> return telechargement.issue
            is Telechargement.Recu -> telechargement.contenu
        }

        // Étape 2 : un format inattendu est refusé en bloc, jamais lu à moitié.
        if (publie.schemaVersion != SCHEMA_REFERENCE_SUPPORTE) {
            return IssueMiseAJour.SchemaInconnu(publie.schemaVersion)
        }

        // Étape 3 : la comparaison de version décide (§B5.1). Une version égale ou
        // inférieure n'est pas une erreur, c'est « vous êtes à jour ».
        val versionLocale = stockage.versionCalendrier()
        if (publie.version <= versionLocale) return IssueMiseAJour.DejaAJour(versionLocale)

        // Étape 4, et seulement maintenant : le remplacement transactionnel.
        return executerSansEchouer(repli = IssueMiseAJour.Echec) {
            stockage.remplacerCalendrier(publie)
            IssueMiseAJour.Remplace(
                versionPrecedente = versionLocale,
                version = publie.version,
                publieLe = publie.publieLe,
            )
        }
    }

    // --- Annuaire des centres -------------------------------------------------

    /**
     * Même déroulé que pour le calendrier, sur l'autre fichier.
     *
     * Les deux fonctions sont volontairement écrites en entier plutôt que factorisées :
     * `CalendrierDto` et `AnnuaireDto` n'ont pas de supertype commun (ce sont des objets de
     * transport calqués sur le contrat publié, B04), et les réunir demanderait une demi-douzaine
     * de lambdas d'accès qui rendraient illisible l'ordre des quatre étapes — c'est-à-dire
     * précisément ce que cette tâche doit pouvoir défendre en soutenance.
     */
    private suspend fun mettreAJourAnnuaire(): IssueMiseAJour {
        val publie = when (val telechargement = telecharger { api.annuaire() }) {
            is Telechargement.Echoue -> return telechargement.issue
            is Telechargement.Recu -> telechargement.contenu
        }

        if (publie.schemaVersion != SCHEMA_REFERENCE_SUPPORTE) {
            return IssueMiseAJour.SchemaInconnu(publie.schemaVersion)
        }

        val versionLocale = stockage.versionAnnuaire()
        if (publie.version <= versionLocale) return IssueMiseAJour.DejaAJour(versionLocale)

        return executerSansEchouer(repli = IssueMiseAJour.Echec) {
            stockage.remplacerAnnuaire(publie)
            IssueMiseAJour.Remplace(
                versionPrecedente = versionLocale,
                version = publie.version,
                publieLe = publie.publieLe,
            )
        }
    }

    // --- Téléchargement -------------------------------------------------------

    /** Un fichier reçu et désérialisé, ou la raison pour laquelle il ne l'a pas été. */
    private sealed interface Telechargement<out T> {
        data class Recu<out T>(val contenu: T) : Telechargement<T>
        data class Echoue(val issue: IssueMiseAJour) : Telechargement<Nothing>
    }

    /**
     * Télécharge un fichier publié et traduit tout échec en [IssueMiseAJour].
     *
     * **L'ordre des `catch` n'est pas indifférent** et c'est le piège numéro un de cette
     * tâche : `TimeoutCancellationException` **hérite de** `CancellationException`. Attrapée
     * après elle, la clause de rethrow de l'annulation ferait remonter le dépassement de
     * délai jusqu'à l'interface sous forme d'exception — exactement ce que le §B0 interdit,
     * et le `viewModelScope` s'en trouverait annulé. Le dépassement est donc traité d'abord,
     * et l'annulation véritable (ViewModel détruit, écran quitté) juste après, seule à
     * remonter.
     *
     * Les autres familles, dans l'ordre de leurs sous-typages :
     *  - `SerializationException` (JSON tronqué, champ absent, page HTML) → format invalide ;
     *  - `DateTimeParseException` (`publieLe` hors ISO, levée par `SerialiseurLocalDate` et
     *    enveloppée ou non selon les versions de kotlinx.serialization — même précaution
     *    qu'en B17 dans `domain/ImportCarnet.kt`) → format invalide ;
     *  - `IllegalArgumentException`, filet dont `SerializationException` hérite, placé après
     *    elle et jamais avant → format invalide ;
     *  - `IOException` (pas de réseau, connexion coupée, DNS) et `HttpException` (404 tant
     *    que le dépôt de données n'existe pas, 500, 403) → échec ;
     *  - `Exception`, dernier filet, pour qu'aucune surprise ne traverse l'écran.
     */
    private suspend fun <T> telecharger(appel: suspend () -> T): Telechargement<T> = try {
        Telechargement.Recu(withTimeout(delaiMax.toMillis()) { appel() })
    } catch (tempsDepasse: TimeoutCancellationException) {
        Telechargement.Echoue(IssueMiseAJour.Echec)
    } catch (annulation: CancellationException) {
        throw annulation
    } catch (erreur: SerializationException) {
        Telechargement.Echoue(IssueMiseAJour.FormatInvalide)
    } catch (erreur: DateTimeParseException) {
        Telechargement.Echoue(IssueMiseAJour.FormatInvalide)
    } catch (erreur: IllegalArgumentException) {
        Telechargement.Echoue(IssueMiseAJour.FormatInvalide)
    } catch (erreur: IOException) {
        Telechargement.Echoue(IssueMiseAJour.Echec)
    } catch (erreur: HttpException) {
        Telechargement.Echoue(IssueMiseAJour.Echec)
    } catch (erreur: Exception) {
        Telechargement.Echoue(IssueMiseAJour.Echec)
    }

    // --- Écritures locales ----------------------------------------------------

    /**
     * Exécute une écriture locale et rend [repli] si elle échoue.
     *
     * Une écriture qui échoue n'a rien écrit : `ReferenceDao` remplace en transaction, donc
     * l'ancien contenu est intact (B02). Sans ce garde-fou, un échec sur le calendrier
     * remonterait à travers [synchroniser] et empêcherait l'annuaire d'être traité — les deux
     * fichiers doivent rester indépendants **y compris** quand c'est la base qui refuse.
     */
    private suspend fun <T> executerSansEchouer(repli: T, bloc: suspend () -> T): T = try {
        bloc()
    } catch (annulation: CancellationException) {
        throw annulation
    } catch (erreur: Exception) {
        repli
    }
}
