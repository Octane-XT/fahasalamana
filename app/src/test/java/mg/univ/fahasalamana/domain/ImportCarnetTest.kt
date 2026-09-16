package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Fusion par identifiant et refus d'un fichier inexploitable (B17, US-B9 scénario 2 ;
 * §B9 « import avec doublons, fichier invalide », Dev A).
 *
 * Test **JVM pur** : `domain/ImportCarnet.kt` ne connaît ni Android, ni Room, ni le Storage
 * Access Framework (règle 1 de CLAUDE.md). La décision d'import est donc vérifiable sans
 * appareil, et c'est elle qui porte tout le risque — l'écriture qui suit se contente
 * d'insérer les deux listes rendues ici.
 *
 * Ce qui n'est donc **pas** couvert et reste à vérifier à la main entre deux téléphones
 * (Definition of Done de B17) : la lecture du document choisi
 * (`platform/ImportCarnetSaf.kt`), la transaction d'écriture
 * (`EnfantRepositoryImpl.importer`, du ressort des tests instrumentés de B20) et la
 * replanification des rappels (`PlanificateurRappels.replanifierTout`, B12).
 *
 * `ExportImportCarnetTest` (B16) reste le contrat du **format** ; ce fichier-ci est le
 * contrat de la **fusion**. Les deux se lisent ensemble.
 */
class ImportCarnetTest {

    // ----------------------------------------------------------------------
    // Le carnet du téléphone A, celui qui exporte. UUID fixes : un test doit
    // échouer toujours ou jamais.
    // ----------------------------------------------------------------------

    private val jourExport = LocalDate.of(2026, 9, 16)

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

    private val bcgDeFaly = VaccinAdministre(
        id = "aaaa0001-0000-4000-8000-000000000001",
        enfantId = faly.id,
        vaccinId = "bcg",
        date = LocalDate.of(2026, 1, 2),
        lieu = "CSB2 Ankirihiry",
        lot = "BCG-2026-A",
    )

    private val penta1DeFaly = VaccinAdministre(
        id = "aaaa0002-0000-4000-8000-000000000002",
        enfantId = faly.id,
        vaccinId = "penta1",
        date = LocalDate.of(2026, 2, 13),
    )

    private val bcgDeHanta = VaccinAdministre(
        id = "bbbb0001-0000-4000-8000-000000000001",
        enfantId = hanta.id,
        vaccinId = "bcg",
        date = LocalDate.of(2025, 3, 16),
    )

    private val carnetDuTelephoneA: CarnetExport = carnetExport(
        enfants = listOf(faly, hanta),
        doses = listOf(bcgDeFaly, penta1DeFaly, bcgDeHanta),
        exporteLe = jourExport,
    )

    /** Raccourci : fusionner sur un téléphone dont on donne l'état courant. */
    private fun fusionSur(
        enfantsLocaux: List<Enfant> = emptyList(),
        dosesLocales: List<VaccinAdministre> = emptyList(),
        carnet: CarnetExport = carnetDuTelephoneA,
    ): FusionCarnet = fusionner(carnet, enfantsLocaux, dosesLocales)

    // ----------------------------------------------------------------------
    // Identifiant nouveau : le scénario 2 de US-B9, tel quel
    // ----------------------------------------------------------------------

    /**
     * Le cas de la Definition of Done : le carnet du téléphone A arrive sur un téléphone B
     * qui ne connaît personne. Tout est ajouté, avec les identifiants d'origine.
     */
    @Test
    fun surUnTelephoneVide_toutEstAjouteAvecLesIdentifiantsDOrigine() {
        val fusion = fusionSur()

        assertEquals(listOf(faly, hanta), fusion.enfantsAAjouter)
        assertEquals(
            listOf(bcgDeFaly, penta1DeFaly, bcgDeHanta).map { it.id }.sorted(),
            fusion.dosesAAjouter.map { it.id }.sorted(),
        )
        assertEquals(
            ResultatImport(enfantsAjoutes = 2, dosesAjoutees = 3),
            fusion.resultat,
        )
    }

    /**
     * Les doses ajoutées gardent le rattachement à leur enfant.
     *
     * Le fichier ne répète pas `enfantId` dans chaque dose (§ format, B16) : c'est l'enfant
     * qui la contient qui fait autorité. Une erreur de reconstruction rattacherait les doses
     * de Hanta à Faly, et le carnet importé serait faux sans qu'aucun compteur ne le dise.
     */
    @Test
    fun lesDosesAjoutees_restentRattacheesALeurEnfant() {
        val parEnfant = fusionSur().dosesAAjouter.groupBy { it.enfantId }

        assertEquals(setOf(faly.id, hanta.id), parEnfant.keys)
        assertEquals(
            listOf(bcgDeFaly, penta1DeFaly).sortedBy { it.id },
            parEnfant.getValue(faly.id).sortedBy { it.id },
        )
        assertEquals(listOf(bcgDeHanta), parEnfant.getValue(hanta.id))
    }

    // ----------------------------------------------------------------------
    // Identifiant existant : fusion, jamais doublon, jamais écrasement
    // ----------------------------------------------------------------------

    /**
     * Le même fichier importé deux fois de suite n'ajoute rien la seconde fois.
     *
     * C'est l'accident le plus banal (le parent touche deux fois le bouton, ou réimporte
     * le fichier de la semaine dernière) et celui qui doublerait tout le carnet si la
     * fusion se faisait par autre chose que l'identifiant.
     */
    @Test
    fun memeFichierImporteDeuxFois_nAjouteRienLaSecondeFois() {
        val premier = fusionSur()

        val second = fusionSur(
            enfantsLocaux = premier.enfantsAAjouter,
            dosesLocales = premier.dosesAAjouter,
        )

        assertEquals(emptyList<Enfant>(), second.enfantsAAjouter)
        assertEquals(emptyList<VaccinAdministre>(), second.dosesAAjouter)
        assertEquals(
            ResultatImport(enfantsFusionnes = 2, dosesFusionnees = 3),
            second.resultat,
        )
        assertTrue(second.resultat.rienAjoute)
    }

    /**
     * Un enfant déjà présent n'est pas dupliqué, **et ses doses manquantes sont ajoutées**.
     *
     * C'est le cœur de la fusion : deux parents saisissent chacun sur leur téléphone, et
     * l'import complète le carnet au lieu de créer un second Faly.
     */
    @Test
    fun enfantDejaPresent_nEstPasDuplique_etSesDosesManquantesSontAjoutees() {
        val fusion = fusionSur(
            enfantsLocaux = listOf(faly),
            dosesLocales = listOf(bcgDeFaly),
        )

        assertEquals(listOf(hanta), fusion.enfantsAAjouter)
        assertEquals(listOf(penta1DeFaly, bcgDeHanta).sortedBy { it.id }, fusion.dosesAAjouter.sortedBy { it.id })
        assertEquals(
            ResultatImport(
                enfantsAjoutes = 1,
                enfantsFusionnes = 1,
                dosesAjoutees = 2,
                dosesFusionnees = 1,
            ),
            fusion.resultat,
        )
    }

    /**
     * **Le téléphone gagne.** Le même identifiant portant des valeurs différentes des deux
     * côtés ne produit aucune écriture : la valeur locale reste en place.
     *
     * Décision écrite en tête de `domain/ImportCarnet.kt` : rien ne permet de savoir
     * laquelle des deux valeurs est la plus récente, et l'application n'a ni annulation ni
     * sauvegarde (§B8 point 5). Une date de vaccination corrigée sur ce téléphone ne peut
     * donc pas être écrasée par un fichier d'âge inconnu.
     */
    @Test
    fun memeIdentifiantValeursDifferentes_leTelephoneGagne() {
        val falyCorrigeSurCeTelephone = faly.copy(
            prenom = "Faly Andriamahefa",
            dateNaissance = LocalDate.of(2026, 1, 3),
        )
        val bcgCorrigeSurCeTelephone = bcgDeFaly.copy(
            date = LocalDate.of(2026, 1, 5),
            lieu = "CSB1 Ambodimanga",
        )

        val fusion = fusionSur(
            enfantsLocaux = listOf(falyCorrigeSurCeTelephone),
            dosesLocales = listOf(bcgCorrigeSurCeTelephone),
        )

        // Rien à écrire pour ces deux lignes-là : elles sont reconnues, donc conservées.
        assertFalse(fusion.enfantsAAjouter.any { it.id == faly.id })
        assertFalse(fusion.dosesAAjouter.any { it.id == bcgDeFaly.id })
        assertEquals(1, fusion.resultat.enfantsFusionnes)
        assertEquals(1, fusion.resultat.dosesFusionnees)
    }

    /**
     * **Le piège du `INSERT OR REPLACE`** (point de vigilance n° 11 du suivi).
     *
     * Les deux téléphones ont saisi chacun de leur côté le BCG de Faly : même enfant, même
     * vaccin, **deux identifiants de dose différents**. La règle R5 n'autorise qu'une dose
     * par couple (enfant, vaccin), et cet index unique n'est pas la clé primaire : un
     * `INSERT OR REPLACE` supprimerait la ligne locale pour insérer celle du fichier, et
     * l'identifiant local — celui qui sert à fusionner les imports suivants — disparaîtrait.
     *
     * La dose n'est donc pas réinsérée : elle est comptée comme fusionnée.
     */
    @Test
    fun doseDejaPresenteSousUnAutreIdentifiant_nEstPasReinseree() {
        val memeBcgSaisiIci = bcgDeFaly.copy(
            id = "99999999-9999-4999-8999-999999999999",
            lieu = null,
            lot = null,
        )

        val fusion = fusionSur(
            enfantsLocaux = listOf(faly),
            dosesLocales = listOf(memeBcgSaisiIci),
        )

        assertFalse(
            "le couple (enfant, vaccin) est déjà pris : rien ne doit être inséré pour lui",
            fusion.dosesAAjouter.any { it.enfantId == faly.id && it.vaccinId == "bcg" },
        )
        assertEquals(listOf(penta1DeFaly, bcgDeHanta).sortedBy { it.id }, fusion.dosesAAjouter.sortedBy { it.id })
        assertEquals(1, fusion.resultat.dosesFusionnees)
    }

    /**
     * Le même vaccin pour **un autre enfant** est bien ajouté : l'unicité de R5 porte sur le
     * couple, pas sur le seul `vaccinId`.
     */
    @Test
    fun memeVaccinPourUnAutreEnfant_estAjoute() {
        val fusion = fusionSur(
            enfantsLocaux = listOf(faly),
            dosesLocales = listOf(bcgDeFaly),
        )

        assertTrue(fusion.dosesAAjouter.any { it.enfantId == hanta.id && it.vaccinId == "bcg" })
    }

    // ----------------------------------------------------------------------
    // Doses en doublon dans le fichier lui-même
    // ----------------------------------------------------------------------

    /**
     * Un fichier bricolé à la main peut porter deux fois la même dose. Elle n'est lue
     * qu'une fois : sans cela, l'insertion en bloc échouerait sur la clé primaire et ferait
     * perdre tout l'import.
     *
     * Le carnet est écrit ici à la main plutôt que par `carnetExport()`, qui n'aurait pas
     * pu produire ce doublon (la base a une clé primaire).
     */
    @Test
    fun doseEnDoublonDansLeFichier_nEstLueQuUneFois() {
        val carnet = CarnetExport(
            exporteLe = jourExport,
            enfants = listOf(
                EnfantExport(
                    id = faly.id,
                    prenom = faly.prenom,
                    dateNaissance = faly.dateNaissance,
                    sexe = faly.sexe.name,
                    vaccins = listOf(
                        DoseExport(id = bcgDeFaly.id, vaccinId = "bcg", date = bcgDeFaly.date),
                        // Deux fois la même ligne, au caractère près.
                        DoseExport(id = bcgDeFaly.id, vaccinId = "bcg", date = bcgDeFaly.date),
                    ),
                ),
            ),
        )

        val fusion = fusionner(carnet, emptyList(), emptyList())

        assertEquals(1, fusion.dosesAAjouter.size)
        assertEquals(1, fusion.resultat.dosesAjoutees)
        assertEquals(1, fusion.resultat.dosesIgnorees)
        assertEquals(1, fusion.resultat.lignesIgnorees)
    }

    /**
     * Deux doses d'identifiants différents pour le même couple (enfant, vaccin) dans un même
     * fichier : la première est prise, la seconde ignorée. La règle R5 s'applique au contenu
     * du fichier comme à la base.
     */
    @Test
    fun deuxDosesPourLeMemeCoupleDansLeFichier_uneSeuleEstPrise() {
        val carnet = CarnetExport(
            exporteLe = jourExport,
            enfants = listOf(
                EnfantExport(
                    id = faly.id,
                    prenom = faly.prenom,
                    dateNaissance = faly.dateNaissance,
                    sexe = faly.sexe.name,
                    vaccins = listOf(
                        DoseExport(id = "dose-1", vaccinId = "bcg", date = LocalDate.of(2026, 1, 2)),
                        DoseExport(id = "dose-2", vaccinId = "bcg", date = LocalDate.of(2026, 1, 9)),
                    ),
                ),
            ),
        )

        val fusion = fusionner(carnet, emptyList(), emptyList())

        assertEquals(listOf("dose-1"), fusion.dosesAAjouter.map { it.id })
        assertEquals(1, fusion.resultat.dosesIgnorees)
    }

    /** Un enfant en double dans le fichier n'est ajouté qu'une fois. */
    @Test
    fun enfantEnDoublonDansLeFichier_nEstAjouteQuUneFois() {
        val unEnfant = EnfantExport(
            id = faly.id,
            prenom = faly.prenom,
            dateNaissance = faly.dateNaissance,
            sexe = faly.sexe.name,
        )
        val carnet = CarnetExport(exporteLe = jourExport, enfants = listOf(unEnfant, unEnfant))

        val fusion = fusionner(carnet, emptyList(), emptyList())

        assertEquals(listOf(faly), fusion.enfantsAAjouter)
        assertEquals(1, fusion.resultat.enfantsAjoutes)
        assertEquals(1, fusion.resultat.enfantsIgnores)
    }

    // ----------------------------------------------------------------------
    // Fichier vide, fichier sans enfant
    // ----------------------------------------------------------------------

    /** Un carnet sans aucun enfant ne produit aucune écriture, et le rapport le dit. */
    @Test
    fun carnetSansAucunEnfant_neProduitAucuneEcriture() {
        val fusion = fusionner(
            carnetExport(emptyList(), emptyList(), jourExport),
            enfantsLocaux = listOf(faly),
            dosesLocales = listOf(bcgDeFaly),
        )

        assertEquals(emptyList<Enfant>(), fusion.enfantsAAjouter)
        assertEquals(emptyList<VaccinAdministre>(), fusion.dosesAAjouter)
        assertEquals(ResultatImport(), fusion.resultat)
        assertTrue(fusion.resultat.fichierSansContenu)
    }

    /** Un fichier vide, ou réduit à des espaces, n'est pas un carnet. */
    @Test
    fun fichierVide_estRefuse() {
        assertEquals(LectureCarnet.Illisible, analyserCarnet(""))
        assertEquals(LectureCarnet.Illisible, analyserCarnet("   \n  "))
    }

    // ----------------------------------------------------------------------
    // Refus propre : ni écriture, ni exception
    // ----------------------------------------------------------------------

    /**
     * Les trois façons d'abîmer un fichier, toutes traduites en [LectureCarnet.Illisible] et
     * non en exception : le parent a pu désigner une photo, ou le transfert a pu tronquer le
     * carnet.
     *
     * La date illisible est celle qui compte le plus ici : elle remonte de `LocalDate.parse`
     * sous forme de `DateTimeParseException`, pas de `SerializationException`, et n'attraper
     * que la seconde laisserait l'application planter (voir
     * `ExportImportCarnetTest.dateIllisible_echoueFranchement`).
     */
    @Test
    fun fichierAbime_estRefuseSansException() {
        assertEquals(LectureCarnet.Illisible, analyserCarnet("ceci n'est pas un carnet"))

        assertEquals(
            LectureCarnet.Illisible,
            analyserCarnet(
                """
                {
                  "schemaVersion": 1,
                  "exporteLe": "2026-09-16",
                  "enfants": [
                    { "id": "e1", "dateNaissance": "2026-01-01", "sexe": "GARCON", "vaccins": [] }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            LectureCarnet.Illisible,
            analyserCarnet(
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
            ),
        )
    }

    /**
     * Un fichier écrit par une version ultérieure est refusé, et le format trouvé est
     * rapporté pour que le message puisse le citer.
     *
     * Il n'est **pas** importé à moitié : un champ ajouté par cette version future pourrait
     * changer le sens de ce qui est déjà lisible (une dose annulée, par exemple), et
     * `ignoreUnknownKeys` le passerait sous silence.
     */
    @Test
    fun versionInconnue_estRefusee() {
        val fichier = """
            {
              "schemaVersion": 2,
              "exporteLe": "2026-09-16",
              "enfants": [
                { "id": "e1", "prenom": "Faly", "dateNaissance": "2026-01-01",
                  "sexe": "GARCON", "vaccins": [] }
              ]
            }
        """.trimIndent()

        assertEquals(LectureCarnet.VersionInconnue(2), analyserCarnet(fichier))
    }

    /** Un `schemaVersion` absurde (0, négatif) est refusé de la même façon qu'un format futur. */
    @Test
    fun versionAbsurde_estRefusee() {
        assertEquals(
            LectureCarnet.VersionInconnue(0),
            analyserCarnet("""{ "schemaVersion": 0, "exporteLe": "2026-09-16", "enfants": [] }"""),
        )
        assertEquals(
            LectureCarnet.VersionInconnue(-1),
            analyserCarnet("""{ "schemaVersion": -1, "exporteLe": "2026-09-16", "enfants": [] }"""),
        )
    }

    /** Le format courant est accepté, et un fichier sans `schemaVersion` est lu comme un format 1. */
    @Test
    fun formatCourant_estAccepte() {
        val relu = analyserCarnet(ecrireCarnet(carnetDuTelephoneA))
        assertEquals(LectureCarnet.Lu(carnetDuTelephoneA), relu)

        val sansVersion = analyserCarnet("""{ "exporteLe": "2026-09-16", "enfants": [] }""")
        assertTrue("un fichier sans schemaVersion doit être lu", sansVersion is LectureCarnet.Lu)
        assertEquals(
            SCHEMA_VERSION_CARNET,
            (sansVersion as LectureCarnet.Lu).carnet.schemaVersion,
        )
    }

    // ----------------------------------------------------------------------
    // Le rapport d'import
    // ----------------------------------------------------------------------

    /**
     * Le chemin complet, tel qu'il est vécu : un fichier écrit par le téléphone A, relu et
     * fusionné sur le téléphone B qui connaissait déjà Faly et son BCG.
     *
     * C'est ce rapport-là que le parent lit pour vérifier que son carnet est complet.
     */
    @Test
    fun rapport_compteAjoutsEtFusions_surLeCheminComplet() {
        val lecture = analyserCarnet(ecrireCarnet(carnetDuTelephoneA))
        assertTrue(lecture is LectureCarnet.Lu)

        val fusion = fusionner(
            (lecture as LectureCarnet.Lu).carnet,
            enfantsLocaux = listOf(faly),
            dosesLocales = listOf(bcgDeFaly),
        )

        assertEquals(
            ResultatImport(
                enfantsAjoutes = 1,
                enfantsFusionnes = 1,
                dosesAjoutees = 2,
                dosesFusionnees = 1,
            ),
            fusion.resultat,
        )
        assertFalse(fusion.resultat.rienAjoute)
        assertFalse(fusion.resultat.fichierSansContenu)
        assertEquals(0, fusion.resultat.lignesIgnorees)
    }

    /** Les compteurs décrivent exactement les deux listes à écrire : aucun écart possible. */
    @Test
    fun rapport_correspondAuxListesAEcrire() {
        val fusion = fusionSur(enfantsLocaux = listOf(hanta))

        assertEquals(fusion.enfantsAAjouter.size, fusion.resultat.enfantsAjoutes)
        assertEquals(fusion.dosesAAjouter.size, fusion.resultat.dosesAjoutees)
    }
}
