package mg.univ.fahasalamana.domain

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Aller-retour du carnet exporté (B16, US-B9 ; §B9 « `ExportImportCarnet` : aller-retour
 * sans perte », Dev A).
 *
 * **Ce test est le contrat de B17.** Le fichier d'export circule entre deux téléphones qui
 * ne font pas tourner le même APK : ce qu'un téléphone écrit, un autre doit le relire à
 * l'identique. Tant que l'import n'est pas écrit, ces cas sont le seul garde-fou du format ;
 * une fois B17 écrite, ce sont eux qui diront qu'un changement de modèle a cassé la
 * compatibilité — bien avant qu'un carnet réel soit perdu.
 *
 * Test **JVM pur** : `domain/ExportImportCarnet.kt` ne connaît ni Android, ni Room, ni le
 * Storage Access Framework (règle 1 de CLAUDE.md). Ce qui n'est donc pas couvert ici :
 * l'écriture dans le document choisi par l'utilisateur (`platform/ExportCarnetSaf.kt`,
 * vérifiable seulement sur téléphone) et la lecture du carnet en base
 * (`EnfantRepositoryImpl.exporter`, qui relève des tests DAO instrumentés de B20).
 */
class ExportImportCarnetTest {

    // ----------------------------------------------------------------------
    // Un carnet de démonstration : deux enfants, plusieurs doses chacun,
    // et toutes les combinaisons de champs facultatifs.
    // ----------------------------------------------------------------------

    private val jourExport = LocalDate.of(2026, 9, 16)

    /** UUID fixes et non `randomUUID()` : un test doit échouer toujours ou jamais. */
    private val faly = Enfant(
        id = "11111111-1111-4111-8111-111111111111",
        prenom = "Faly",
        dateNaissance = LocalDate.of(2026, 1, 1),
        sexe = Sexe.GARCON,
    )

    private val hanta = Enfant(
        id = "22222222-2222-4222-8222-222222222222",
        prenom = "Hanta",
        dateNaissance = LocalDate.of(2025, 3, 15),
        sexe = Sexe.FILLE,
    )

    private val dosesDeFaly = listOf(
        // Lieu et lot renseignés tous les deux.
        VaccinAdministre(
            id = "aaaa0001-0000-4000-8000-000000000001",
            enfantId = faly.id,
            vaccinId = "bcg",
            date = LocalDate.of(2026, 1, 2),
            lieu = "CSB2 Ankirihiry",
            lot = "BCG-2026-A",
        ),
        // Les deux facultatifs absents : le cas le plus fréquent en saisie réelle.
        VaccinAdministre(
            id = "aaaa0002-0000-4000-8000-000000000002",
            enfantId = faly.id,
            vaccinId = "vpo0",
            date = LocalDate.of(2026, 1, 2),
            lieu = null,
            lot = null,
        ),
        // Lieu seul.
        VaccinAdministre(
            id = "aaaa0003-0000-4000-8000-000000000003",
            enfantId = faly.id,
            vaccinId = "penta1",
            date = LocalDate.of(2026, 2, 13),
            lieu = "CSB1 Ambodimanga",
            lot = null,
        ),
        // Lot seul.
        VaccinAdministre(
            id = "aaaa0004-0000-4000-8000-000000000004",
            enfantId = faly.id,
            vaccinId = "penta2",
            date = LocalDate.of(2026, 3, 14),
            lieu = null,
            lot = "PENTA-77",
        ),
    )

    private val dosesDeHanta = listOf(
        VaccinAdministre(
            id = "bbbb0001-0000-4000-8000-000000000001",
            enfantId = hanta.id,
            vaccinId = "bcg",
            date = LocalDate.of(2025, 3, 16),
            lieu = "CSB2 Ankirihiry",
            lot = null,
        ),
        // Une dose d'une année différente : c'est celle qui attrape un format de date
        // tronqué à l'année en cours.
        VaccinAdministre(
            id = "bbbb0002-0000-4000-8000-000000000002",
            enfantId = hanta.id,
            vaccinId = "rr1",
            date = LocalDate.of(2025, 12, 10),
            lieu = null,
            lot = null,
        ),
    )

    private val toutesLesDoses = dosesDeFaly + dosesDeHanta

    private val carnet: CarnetExport =
        carnetExport(listOf(faly, hanta), toutesLesDoses, jourExport)

    /** L'aller-retour complet : ce que ferait un second téléphone en relisant le fichier. */
    private fun allerRetour(depart: CarnetExport = carnet): CarnetExport =
        lireCarnet(ecrireCarnet(depart))

    // ----------------------------------------------------------------------
    // Aller-retour : rien ne se perd, rien ne change
    // ----------------------------------------------------------------------

    /**
     * Le cas de la Definition of Done : deux enfants, plusieurs doses, sérialisé puis
     * désérialisé, et strictement identique.
     *
     * L'égalité porte sur le `CarnetExport` entier — en-tête, enfants, doses, ordre des
     * listes — parce que ce sont des `data class` : une seule assertion couvre tous les
     * champs, y compris ceux qu'une évolution ultérieure ajouterait.
     */
    @Test
    fun allerRetour_deuxEnfantsAvecDoses_rendUnCarnetStrictementIdentique() {
        assertEquals(carnet, allerRetour())
    }

    /** Le même aller-retour, vu depuis les objets du domaine : c'est ce que B17 réinsérera. */
    @Test
    fun allerRetour_rendLesMemesEnfantsEtLesMemesDoses() {
        val relu = allerRetour()

        assertEquals(listOf(faly, hanta), relu.versEnfants())
        assertEquals(dosesDeFaly + dosesDeHanta, relu.versDoses())
    }

    /**
     * **Les identifiants partent et reviennent tels quels.**
     *
     * C'est le point le plus important du format : B17 fusionne par identifiant. Un UUID
     * régénéré à l'écriture ou à la lecture ferait de chaque import une duplication du
     * carnet, sans aucun message d'erreur pour le signaler.
     */
    @Test
    fun allerRetour_conserveLesIdentifiantsDesEnfantsEtDesDoses() {
        val relu = allerRetour()

        assertEquals(listOf(faly.id, hanta.id), relu.enfants.map { it.id })
        assertEquals(
            toutesLesDoses.map { it.id }.sorted(),
            relu.versDoses().map { it.id }.sorted(),
        )
    }

    /**
     * Les dates traversent le fichier **en texte ISO**, et reviennent en `LocalDate`
     * (règle 2 de CLAUDE.md : jamais un epoch pour une date métier).
     *
     * Un epoch aurait une double conséquence ici : le fichier deviendrait illisible pour son
     * propriétaire, et la date changerait de jour entre deux téléphones réglés sur des
     * fuseaux différents.
     */
    @Test
    fun allerRetour_conserveLesDates_ecritesEnTexteIso() {
        val texte = ecrireCarnet(carnet)
        val relu = lireCarnet(texte)

        assertTrue("date de naissance en ISO", texte.contains("\"2026-01-01\""))
        assertTrue("date d'une dose en ISO", texte.contains("\"2026-02-13\""))
        assertTrue("jour d'export en ISO", texte.contains("\"2026-09-16\""))

        assertEquals(jourExport, relu.exporteLe)
        assertEquals(LocalDate.of(2026, 1, 1), relu.enfants.first { it.id == faly.id }.dateNaissance)
        assertEquals(
            LocalDate.of(2026, 2, 13),
            relu.versDoses().single { it.vaccinId == "penta1" }.date,
        )
        assertEquals(
            LocalDate.of(2025, 12, 10),
            relu.versDoses().single { it.vaccinId == "rr1" }.date,
        )
    }

    /**
     * Lieu et lot sont facultatifs (US-B3) : absents ils reviennent absents, renseignés ils
     * reviennent au caractère près.
     *
     * Le piège évité ici est la chaîne vide : un `null` relu en `""` afficherait un lieu
     * vide dans la fiche au lieu de ne rien afficher.
     */
    @Test
    fun allerRetour_conserveLesChampsFacultatifs_absentsCommeRenseignes() {
        // Indexé par identifiant de dose et non par `vaccinId` : deux enfants ont chacun
        // reçu le BCG, et `vaccinId` n'est unique que **par enfant** (contrainte R5).
        val relues = allerRetour().versDoses().associateBy { it.id }

        fun dose(source: VaccinAdministre) = relues.getValue(source.id)

        val (bcgDeFaly, vpo0, penta1, penta2) = dosesDeFaly

        assertEquals("CSB2 Ankirihiry", dose(bcgDeFaly).lieu)
        assertEquals("BCG-2026-A", dose(bcgDeFaly).lot)

        assertNull(dose(vpo0).lieu)
        assertNull(dose(vpo0).lot)

        assertEquals("CSB1 Ambodimanga", dose(penta1).lieu)
        assertNull(dose(penta1).lot)

        assertNull(dose(penta2).lieu)
        assertEquals("PENTA-77", dose(penta2).lot)
    }

    /**
     * Un enfant tout juste ajouté n'a encore reçu aucune dose. Il doit partir dans le
     * fichier avec une liste vide, et non disparaître de l'export.
     */
    @Test
    fun allerRetour_enfantSansAucuneDose_estExporteAvecUneListeVide() {
        val tiana = Enfant(
            id = "33333333-3333-4333-8333-333333333333",
            prenom = "Tiana",
            dateNaissance = LocalDate.of(2026, 9, 10),
            sexe = Sexe.NON_PRECISE,
        )

        val avecTiana = carnetExport(listOf(faly, hanta, tiana), toutesLesDoses, jourExport)
        val relu = lireCarnet(ecrireCarnet(avecTiana))

        assertEquals(avecTiana, relu)
        assertEquals(3, relu.nbEnfants)
        assertEquals(toutesLesDoses.size, relu.nbDoses)

        val relueTiana = relu.enfants.single { it.id == tiana.id }
        assertEquals(emptyList<DoseExport>(), relueTiana.vaccins)
        assertEquals(tiana, relu.versEnfants().single { it.id == tiana.id })
    }

    /** Le sexe voyage en texte et revient en énumération, `NON_PRECISE` compris. */
    @Test
    fun allerRetour_conserveLeSexe() {
        val relus = allerRetour().versEnfants().associateBy { it.id }

        assertEquals(Sexe.GARCON, relus.getValue(faly.id).sexe)
        assertEquals(Sexe.FILLE, relus.getValue(hanta.id).sexe)
    }

    // ----------------------------------------------------------------------
    // Le fichier lui-même : en-tête, nom, stabilité
    // ----------------------------------------------------------------------

    /** `schemaVersion` est toujours écrit, même s'il vaut la valeur par défaut du code. */
    @Test
    fun fichier_porteToujoursSonSchemaVersion() {
        val texte = ecrireCarnet(carnet)

        assertTrue("schemaVersion absent du fichier", texte.contains("\"schemaVersion\""))
        assertEquals(SCHEMA_VERSION_CARNET, lireCarnet(texte).schemaVersion)
    }

    /**
     * Le nom de fichier du scénario 1 de US-B9 : `carnet-fahasalamana-AAAAMMJJ.json`.
     *
     * Le second cas est celui qui attrape un format de date sans zéro de remplissage, qui
     * produirait `carnet-fahasalamana-202637.json` — un nom qui ne trie plus et qui ne se
     * relit plus.
     */
    @Test
    fun nomFichierCarnet_suitLeFormatDuScenario() {
        assertEquals("carnet-fahasalamana-20260916.json", nomFichierCarnet(jourExport))
        assertEquals(
            "carnet-fahasalamana-20260307.json",
            nomFichierCarnet(LocalDate.of(2026, 3, 7)),
        )
        assertTrue(nomFichierCarnet(jourExport).endsWith(EXTENSION_FICHIER_CARNET))
    }

    /**
     * Deux exports du même carnet produisent deux fichiers identiques, quel que soit l'ordre
     * dans lequel la base a rendu les lignes.
     *
     * Sans cela, comparer l'export d'aujourd'hui à celui d'hier serait impossible : le `diff`
     * afficherait un carnet entièrement modifié alors que rien n'a bougé.
     */
    @Test
    fun exportStable_memeTexteQuelQueSoitLOrdreDesListes() {
        val melange = carnetExport(
            enfants = listOf(hanta, faly),
            doses = toutesLesDoses.reversed(),
            exporteLe = jourExport,
        )

        assertEquals(ecrireCarnet(carnet), ecrireCarnet(melange))
    }

    /**
     * Une dose dont l'enfant n'est pas dans la liste n'est pas écrite : le fichier ne peut
     * pas contenir de dose orpheline, qu'aucun import ne saurait rattacher.
     *
     * Le cas ne se produit pas depuis la base (cascade de suppression sur
     * `vaccins_administres`), mais c'est cette garantie de format qui permet à B17 de ne pas
     * avoir à traiter ce cas.
     */
    @Test
    fun doseSansEnfantDansLaListe_estIgnoree() {
        val orpheline = VaccinAdministre(
            id = "cccc0001-0000-4000-8000-000000000001",
            enfantId = "enfant-supprime",
            vaccinId = "bcg",
            date = LocalDate.of(2026, 5, 4),
        )

        val avecOrpheline = carnetExport(
            enfants = listOf(faly, hanta),
            doses = toutesLesDoses + orpheline,
            exporteLe = jourExport,
        )

        assertEquals(toutesLesDoses.size, avecOrpheline.nbDoses)
        assertEquals(carnet, avecOrpheline)
    }

    /** Les compteurs du message de confirmation décrivent bien le contenu du fichier. */
    @Test
    fun compteurs_decriventLeContenuDuFichier() {
        assertEquals(2, carnet.nbEnfants)
        assertEquals(6, carnet.nbDoses)
        assertEquals(carnet.nbDoses, carnet.versDoses().size)
    }

    // ----------------------------------------------------------------------
    // Compatibilité et fichiers inattendus — ce que B17 devra traiter
    // ----------------------------------------------------------------------

    /**
     * Compatibilité ascendante : un champ ajouté par une version ultérieure de
     * l'application ne rend pas le fichier illisible pour les APK déjà installés.
     *
     * Même clause que pour les fichiers de référence du §B5.1.
     */
    @Test
    fun cleInconnue_estIgnoreeSansFaireEchouerLaLecture() {
        val relu = lireCarnet(
            """
            {
              "schemaVersion": 1,
              "exporteLe": "2026-09-16",
              "commentaire": "champ ajouté par une version ultérieure",
              "enfants": [
                {
                  "id": "e1", "prenom": "Faly", "dateNaissance": "2026-01-01",
                  "sexe": "GARCON", "poidsNaissance": 3.2,
                  "vaccins": [
                    { "id": "d1", "vaccinId": "bcg", "date": "2026-01-02",
                      "lieu": null, "lot": null, "rappelEnvoye": true }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, relu.nbEnfants)
        assertEquals(1, relu.nbDoses)
        assertEquals("Faly", relu.enfants.single().prenom)
    }

    /**
     * Un `sexe` inconnu ne rend pas l'enfant illisible : il est ramené à `NON_PRECISE`.
     *
     * C'est une valeur d'affichage ; refuser tout le carnet pour elle ferait perdre des
     * dates de vaccination à cause d'un libellé.
     */
    @Test
    fun sexeInconnu_estRameneANonPrecise() {
        val relu = lireCarnet(
            """
            {
              "schemaVersion": 1,
              "exporteLe": "2026-09-16",
              "enfants": [
                { "id": "e1", "prenom": "Faly", "dateNaissance": "2026-01-01",
                  "sexe": "AUTRE", "vaccins": [] }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(Sexe.NON_PRECISE, relu.versEnfants().single().sexe)
    }

    /**
     * Un `schemaVersion` inconnu **se relit** : c'est voulu, le format n'a pas de raison de
     * refuser la lecture d'un en-tête qu'il comprend.
     *
     * TODO(B17) — c'est l'**import** qui doit refuser ce fichier, avant d'écrire quoi que ce
     * soit en base : un fichier produit par une version ultérieure peut contenir des champs
     * porteurs de sens que cette version-ci ignorerait silencieusement. Ce test fige ce que
     * B17 aura sous la main pour prendre sa décision.
     */
    @Test
    fun schemaVersionInconnu_estRelu_etResteARefuserParLImport() {
        val relu = lireCarnet(
            """
            {
              "schemaVersion": 2,
              "exporteLe": "2026-09-16",
              "enfants": []
            }
            """.trimIndent(),
        )

        assertNotEquals(SCHEMA_VERSION_CARNET, relu.schemaVersion)
        assertEquals(2, relu.schemaVersion)
    }

    /** Un fichier qui n'est pas du JSON échoue franchement, il n'est jamais deviné. */
    @Test
    fun texteQuiNestPasDuJson_echoueFranchement() {
        assertThrows(SerializationException::class.java) {
            lireCarnet("ceci n'est pas un carnet")
        }
    }

    /**
     * Un champ obligatoire manquant échoue aussi : sans `prenom`, l'enfant serait importé
     * sans nom et deviendrait impossible à reconnaître dans la liste.
     */
    @Test
    fun champObligatoireManquant_echoueFranchement() {
        assertThrows(SerializationException::class.java) {
            lireCarnet(
                """
                {
                  "schemaVersion": 1,
                  "exporteLe": "2026-09-16",
                  "enfants": [
                    { "id": "e1", "dateNaissance": "2026-01-01", "sexe": "GARCON", "vaccins": [] }
                  ]
                }
                """.trimIndent(),
            )
        }
    }

    /**
     * Une date illisible échoue au lieu d'être approximée.
     *
     * L'exception vient de `LocalDate.parse` dans `SerialiseurLocalDate`, pas du décodeur
     * JSON : c'est une `DateTimeParseException` si kotlinx.serialization la laisse passer
     * telle quelle, une `SerializationException` s'il l'enveloppe. Le test accepte les deux
     * **volontairement** : rien n'est exécuté sur ce poste, et parier sur le type exact
     * ferait échouer un test qui a raison sur le fond.
     *
     * **Ce qui compte pour B17** : ces deux-là sont à attraper pour annoncer « fichier
     * illisible », et attraper la seule `SerializationException` ne suffirait pas.
     */
    @Test
    fun dateIllisible_echoueFranchement() {
        val erreur = assertThrows(RuntimeException::class.java) {
            lireCarnet(
                """
                {
                  "schemaVersion": 1,
                  "exporteLe": "2026-09-16",
                  "enfants": [
                    { "id": "e1", "prenom": "Faly", "dateNaissance": "01/01/2026",
                      "sexe": "GARCON", "vaccins": [] }
                  ]
                }
                """.trimIndent(),
            )
        }

        assertTrue(
            "type inattendu : ${erreur::class.java.name}",
            erreur is DateTimeParseException || erreur is SerializationException,
        )
    }

    /** Un carnet sans aucun enfant reste un fichier valide : c'est l'import qui décidera. */
    @Test
    fun carnetVide_serialiseEtRelu_sansErreur() {
        val vide = carnetExport(emptyList(), emptyList(), jourExport)

        val relu = allerRetour(vide)

        assertEquals(vide, relu)
        assertEquals(0, relu.nbEnfants)
        assertEquals(0, relu.nbDoses)
        assertEquals(jourExport, relu.exporteLe)
    }
}
