package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import mg.univ.fahasalamana.data.local.PreferencesLocales
import mg.univ.fahasalamana.data.remote.AnnuaireDto
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.remote.CentreDto
import mg.univ.fahasalamana.data.remote.DistrictDto
import mg.univ.fahasalamana.data.remote.ReferenceApi
import mg.univ.fahasalamana.data.remote.RegionDto
import mg.univ.fahasalamana.data.remote.VaccinDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Mise à jour des contenus de référence depuis le réseau (B19, US-B11).
 *
 * C'est la stratégie de test que le §B9 confie explicitement à cette tâche : des **tests
 * JVM avec un faux `ReferenceApi`**, couvrant version supérieure, version égale, version
 * inférieure, temps dépassé, JSON invalide et `schemaVersion` inconnu.
 *
 * Deux choses sont vérifiées à chaque cas d'échec, et la seconde est la Definition of Done
 * de la tâche :
 *  1. l'issue rendue est la bonne ;
 *  2. **rien n'a été écrit** — `StockageEspion.aucuneEcriture` compte les remplacements, il
 *     ne les simule pas. Un test qui se contenterait de l'issue laisserait passer un code
 *     qui viderait la table avant de découvrir que le fichier est tronqué.
 *
 * Aucun Android ici, et c'est pour cela que [SynchroniseurReference] est une classe à part :
 * `ReferenceRepositoryImpl` traîne un `AssetManager` et un DataStore qu'aucun test JVM ne
 * peut instancier. Ce que ces tests ne couvrent pas : la transaction Room elle-même et le
 * fait qu'elle ne touche pas aux données personnelles, vérifiés sur appareil par B20.
 */
class SynchroniseurReferenceTest {

    private val api = FauxReferenceApi()
    private val stockage = StockageEspion()

    /** Le 20 octobre 2026, jour de la démonstration du jalon J4 (§B10.5). */
    private val aujourdHui = LocalDate.of(2026, 10, 20)

    private fun synchroniseur(delaiMax: Duration = Duration.ofSeconds(30)) = SynchroniseurReference(
        api = api,
        stockage = stockage,
        delaiMax = delaiMax,
        horloge = { aujourdHui },
    )

    // ----------------------------------------------------------------------
    // La comparaison de version décide (§B5.1)
    // ----------------------------------------------------------------------

    @Test
    fun versionSuperieure_remplaceLeContenu() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        api.reponseCalendrier = { calendrierPublie(version = 4) }
        api.reponseAnnuaire = { annuairePublie(version = 3) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(
            IssueMiseAJour.Remplace(versionPrecedente = 3, version = 4, publieLe = PUBLIE_LE),
            resultat.calendrier,
        )
        assertEquals(
            IssueMiseAJour.Remplace(versionPrecedente = 2, version = 3, publieLe = PUBLIE_LE),
            resultat.annuaire,
        )
        assertEquals(1, stockage.calendriersEcrits.size)
        assertEquals(1, stockage.annuairesEcrits.size)
        assertEquals(4, stockage.calendriersEcrits.single().version)
    }

    @Test
    fun versionEgale_neRemplaceRien() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        api.reponseCalendrier = { calendrierPublie(version = 3) }
        api.reponseAnnuaire = { annuairePublie(version = 2) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.DejaAJour(version = 3), resultat.calendrier)
        assertEquals(IssueMiseAJour.DejaAJour(version = 2), resultat.annuaire)
        assertTrue("« vous êtes à jour » n'écrit rien", stockage.aucuneEcriture)
        assertTrue(resultat.toutEtaitAJour)
    }

    /**
     * Version distante **inférieure** : une publication retirée, ou un miroir en retard.
     *
     * Ce n'est pas une erreur — le parent est bien à jour — et surtout ce n'est pas un
     * remplacement : revenir à une version antérieure violerait le §B5.1 aussi sûrement
     * qu'un remplacement sans comparaison.
     */
    @Test
    fun versionInferieure_neRemplaceRien() = runTest {
        stockage.calendrierEnBase = 5
        stockage.annuaireEnBase = 4
        api.reponseCalendrier = { calendrierPublie(version = 2) }
        api.reponseAnnuaire = { annuairePublie(version = 1) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.DejaAJour(version = 5), resultat.calendrier)
        assertEquals(IssueMiseAJour.DejaAJour(version = 4), resultat.annuaire)
        assertTrue(stockage.aucuneEcriture)
        assertEquals("la version en base ne bouge pas", 5, stockage.calendrierEnBase)
    }

    /** Premier chargement sur un téléphone où rien n'a jamais été chargé (version 0). */
    @Test
    fun aucunContenuEnBase_installeLaVersionPubliee() = runTest {
        api.reponseCalendrier = { calendrierPublie(version = 1) }
        api.reponseAnnuaire = { annuairePublie(version = 1) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(
            IssueMiseAJour.Remplace(
                versionPrecedente = PreferencesLocales.VERSION_ABSENTE,
                version = 1,
                publieLe = PUBLIE_LE,
            ),
            resultat.calendrier,
        )
        assertEquals(1, stockage.calendriersEcrits.size)
    }

    // ----------------------------------------------------------------------
    // Échec réseau sans effet sur la base (Definition of Done de B19)
    // ----------------------------------------------------------------------

    @Test
    fun tempsDepasse_nEcritRien() = runTest {
        stockage.calendrierEnBase = 3
        api.reponseCalendrier = {
            // Temps virtuel de `runTest` : le test ne dure pas cinq minutes.
            delay(Duration.ofMinutes(5).toMillis())
            calendrierPublie(version = 4)
        }
        api.reponseAnnuaire = { throw IOException("hors réseau") }

        val resultat = synchroniseur(delaiMax = Duration.ofSeconds(30)).synchroniser()

        assertEquals(IssueMiseAJour.Echec, resultat.calendrier)
        assertTrue("un dépassement de délai ne doit rien écrire", stockage.aucuneEcriture)
        assertEquals("la version en base est intacte", 3, stockage.calendrierEnBase)
    }

    /** Connexion coupée, DNS injoignable, mode avion : tout arrive en `IOException`. */
    @Test
    fun reseauInjoignable_nEcritRien() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        api.reponseCalendrier = { throw IOException("Unable to resolve host") }
        api.reponseAnnuaire = { throw IOException("Software caused connection abort") }

        val resultat = synchroniseur().synchroniser()

        assertEquals(ResultatSync.echecTotal(), resultat)
        assertTrue(stockage.aucuneEcriture)
    }

    /** 404 : le cas réel tant que le dépôt `fahasalamana-data` n'est pas publié. */
    @Test
    fun http404_nEcritRien() = runTest {
        api.reponseCalendrier = { throw erreurHttp(404) }
        api.reponseAnnuaire = { throw erreurHttp(404) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.Echec, resultat.calendrier)
        assertEquals(IssueMiseAJour.Echec, resultat.annuaire)
        assertTrue(stockage.aucuneEcriture)
    }

    /** JSON tronqué par une coupure en plein téléchargement : le décodeur lève. */
    @Test
    fun jsonInvalide_nEcritRien() = runTest {
        stockage.calendrierEnBase = 3
        api.reponseCalendrier = {
            throw SerializationException("Unexpected EOF while reading the object")
        }
        api.reponseAnnuaire = { annuairePublie(version = 2) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.FormatInvalide, resultat.calendrier)
        assertTrue("un fichier illisible ne remplace rien", stockage.calendriersEcrits.isEmpty())
        assertEquals(3, stockage.calendrierEnBase)
    }

    /**
     * Le serveur répond une page HTML (redirection, portail captif, dépôt renommé).
     *
     * Le convertisseur kotlinx-serialization ne filtre pas sur le type de contenu : il tente
     * de désérialiser le corps quel qu'il soit, et lève sur le premier `<`. Même issue qu'un
     * JSON tronqué, et pour la même raison — le réseau va bien, c'est le contenu qui ne va pas.
     */
    @Test
    fun serveurRepondDuHtml_estUnFormatInvalide() = runTest {
        api.reponseCalendrier = {
            throw SerializationException("Expected start of the object '{', but had '<' instead")
        }
        api.reponseAnnuaire = {
            throw SerializationException("Expected start of the object '{', but had '<' instead")
        }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.FormatInvalide, resultat.calendrier)
        assertEquals(IssueMiseAJour.FormatInvalide, resultat.annuaire)
        assertTrue(stockage.aucuneEcriture)
    }

    /**
     * `publieLe` hors format ISO : `SerialiseurLocalDate` (B04) laisse remonter une
     * `DateTimeParseException` que kotlinx.serialization enveloppe ou non selon les versions.
     * Les deux familles sont rattrapées, comme en B17 dans `domain/ImportCarnet.kt`.
     */
    @Test
    fun dateDePublicationIllisible_estUnFormatInvalide() = runTest {
        api.reponseCalendrier = { throw DateTimeParseException("Text '14/09/2026'", "14/09/2026", 0) }
        api.reponseAnnuaire = { annuairePublie(version = 1) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.FormatInvalide, resultat.calendrier)
        assertTrue(stockage.calendriersEcrits.isEmpty())
    }

    /**
     * `schemaVersion` inattendu : le fichier est refusé **en bloc**.
     *
     * Le cas ne devrait pas se produire — une évolution incompatible publie sous `v2/` et
     * laisse `v1/` en place (§B5.1) — mais un `v2/` servi par erreur sur l'URL `v1/` ne doit
     * pas être lu à moitié : un calendrier partiellement compris produirait de fausses dates
     * dans toutes les fiches.
     */
    @Test
    fun schemaInconnu_nEcritRien() = runTest {
        stockage.calendrierEnBase = 3
        api.reponseCalendrier = { calendrierPublie(version = 9, schemaVersion = 2) }
        api.reponseAnnuaire = { annuairePublie(version = 9, schemaVersion = 2) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.SchemaInconnu(schemaVersion = 2), resultat.calendrier)
        assertEquals(IssueMiseAJour.SchemaInconnu(schemaVersion = 2), resultat.annuaire)
        assertTrue(
            "un format inconnu est refusé avant tout remplacement",
            stockage.aucuneEcriture,
        )
        assertEquals(3, stockage.calendrierEnBase)
    }

    /** Une transaction qui échoue n'a rien écrit : l'ancien contenu reste en place. */
    @Test
    fun echecDEcritureEnBase_estRapporteSansBloquerLAutreContenu() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        stockage.echouerSurLeCalendrier = true
        api.reponseCalendrier = { calendrierPublie(version = 4) }
        api.reponseAnnuaire = { annuairePublie(version = 3) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.Echec, resultat.calendrier)
        assertTrue("l'annuaire se fait quand même", resultat.annuaire is IssueMiseAJour.Remplace)
        assertFalse("pas de replanification sans nouveau calendrier", resultat.calendrierRemplace)
    }

    // ----------------------------------------------------------------------
    // Calendrier et annuaire sont indépendants
    // ----------------------------------------------------------------------

    @Test
    fun echecDuCalendrier_nEmpechePasLaMiseAJourDeLAnnuaire() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        api.reponseCalendrier = { throw IOException("connexion perdue") }
        api.reponseAnnuaire = { annuairePublie(version = 3) }

        val resultat = synchroniseur().synchroniser()

        assertEquals(IssueMiseAJour.Echec, resultat.calendrier)
        assertEquals(
            IssueMiseAJour.Remplace(versionPrecedente = 2, version = 3, publieLe = PUBLIE_LE),
            resultat.annuaire,
        )
        assertTrue("le calendrier n'a pas été touché", stockage.calendriersEcrits.isEmpty())
        assertEquals(1, stockage.annuairesEcrits.size)
    }

    /**
     * Nouvel annuaire seul : aucune date d'échéancier ne change, donc **aucun rappel à
     * replanifier** (§B8). C'est ce drapeau que `ReglagesViewModel` consulte.
     */
    @Test
    fun seulLAnnuaireChange_neDeclenchePasDeReplanification() = runTest {
        stockage.calendrierEnBase = 3
        stockage.annuaireEnBase = 2
        api.reponseCalendrier = { calendrierPublie(version = 3) }
        api.reponseAnnuaire = { annuairePublie(version = 3) }

        val resultat = synchroniseur().synchroniser()

        assertFalse(resultat.calendrierRemplace)
        assertTrue(resultat.contenuRemplace)
    }

    @Test
    fun nouveauCalendrier_declencheLaReplanification() = runTest {
        stockage.calendrierEnBase = 3
        api.reponseCalendrier = { calendrierPublie(version = 4) }
        api.reponseAnnuaire = { throw IOException("hors réseau") }

        val resultat = synchroniseur().synchroniser()

        assertTrue(resultat.calendrierRemplace)
    }

    // ----------------------------------------------------------------------
    // Date de dernière vérification
    // ----------------------------------------------------------------------

    @Test
    fun uneComparaisonAboutie_inscritLaDateDuJour() = runTest {
        api.reponseCalendrier = { throw IOException("hors réseau") }
        api.reponseAnnuaire = { annuairePublie(version = 1) }

        synchroniseur().synchroniser()

        assertEquals(listOf(aujourdHui), stockage.verifications)
    }

    /**
     * Téléphone hors réseau : **aucune** vérification n'a eu lieu.
     *
     * Inscrire la date du jour laisserait l'écran des réglages annoncer un contrôle qui n'a
     * jamais abouti, et masquerait la date du dernier vrai contrôle.
     */
    @Test
    fun echecTotal_nInscritAucuneDate() = runTest {
        api.reponseCalendrier = { throw IOException("hors réseau") }
        api.reponseAnnuaire = { throw IOException("hors réseau") }

        val resultat = synchroniseur().synchroniser()

        assertFalse(resultat.verificationAboutie)
        assertTrue(stockage.verifications.isEmpty())
    }
}

// --- Doubles -----------------------------------------------------------------

/**
 * Le faux client réseau : chaque réponse est une lambda, donc aussi bien un fichier qu'une
 * exception ou une attente. Aucun serveur, aucun `MockWebServer` : c'est la couche au-dessus
 * de Retrofit qui est testée ici, pas Retrofit.
 */
private class FauxReferenceApi : ReferenceApi {

    var reponseCalendrier: suspend () -> CalendrierDto = { calendrierPublie(version = 1) }
    var reponseAnnuaire: suspend () -> AnnuaireDto = { annuairePublie(version = 1) }

    override suspend fun calendrier(): CalendrierDto = reponseCalendrier()

    override suspend fun annuaire(): AnnuaireDto = reponseAnnuaire()
}

/**
 * Le stockage local espionné.
 *
 * Il **compte les écritures** au lieu de les simuler : c'est ce qui permet d'affirmer
 * « échec réseau sans effet sur la base » autrement que par relecture du code.
 */
private class StockageEspion(
    var calendrierEnBase: Int = PreferencesLocales.VERSION_ABSENTE,
    var annuaireEnBase: Int = PreferencesLocales.VERSION_ABSENTE,
) : StockageReference {

    val calendriersEcrits = mutableListOf<CalendrierDto>()
    val annuairesEcrits = mutableListOf<AnnuaireDto>()
    val verifications = mutableListOf<LocalDate>()

    /** Simule une transaction Room qui échoue : rien n'est écrit, l'ancien contenu reste. */
    var echouerSurLeCalendrier: Boolean = false

    val aucuneEcriture: Boolean
        get() = calendriersEcrits.isEmpty() && annuairesEcrits.isEmpty()

    override suspend fun versionCalendrier(): Int = calendrierEnBase

    override suspend fun versionAnnuaire(): Int = annuaireEnBase

    override suspend fun remplacerCalendrier(publie: CalendrierDto) {
        if (echouerSurLeCalendrier) error("base indisponible")
        calendriersEcrits += publie
        calendrierEnBase = publie.version
    }

    override suspend fun remplacerAnnuaire(publie: AnnuaireDto) {
        annuairesEcrits += publie
        annuaireEnBase = publie.version
    }

    override suspend fun enregistrerVerification(jour: LocalDate) {
        verifications += jour
    }
}

// --- Fichiers publiés de test ------------------------------------------------

private val PUBLIE_LE: LocalDate = LocalDate.of(2026, 10, 20)

/** Un `calendrier.json` réduit à une dose : ce qui est testé ici, c'est l'en-tête (§B5.1). */
private fun calendrierPublie(version: Int, schemaVersion: Int = 1) = CalendrierDto(
    schemaVersion = schemaVersion,
    version = version,
    publieLe = PUBLIE_LE,
    source = "Calendrier de démonstration — projet universitaire.",
    vaccins = listOf(
        VaccinDto(
            id = "bcg",
            nom = "BCG",
            dose = "dose unique",
            ordre = 1,
            ageJours = 0,
            dependDe = null,
            toleranceJours = 30,
            description = "À la naissance",
        ),
    ),
)

/** Un `csb.json` réduit à un centre, pour la même raison. */
private fun annuairePublie(version: Int, schemaVersion: Int = 1) = AnnuaireDto(
    schemaVersion = schemaVersion,
    version = version,
    publieLe = PUBLIE_LE,
    source = "Annuaire fictif généré pour un projet universitaire",
    regions = listOf(
        RegionDto(
            id = "atsinanana",
            nom = "Atsinanana",
            districts = listOf(
                DistrictDto(
                    id = "toamasina-1",
                    nom = "Toamasina I",
                    centres = listOf(
                        CentreDto(
                            id = "csb2-ankirihiry",
                            nom = "CSB2 Ankirihiry",
                            typeCentre = "CSB2",
                            telephone = "+261 34 00 000 00",
                            horaires = "Lun–Ven 7h30–16h00",
                            adresse = "Ankirihiry, Toamasina",
                        ),
                    ),
                ),
            ),
        ),
    ),
)

/** Une réponse HTTP en erreur, telle que Retrofit la lève sur une fonction `suspend`. */
private fun erreurHttp(code: Int): HttpException = HttpException(
    Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())),
)
