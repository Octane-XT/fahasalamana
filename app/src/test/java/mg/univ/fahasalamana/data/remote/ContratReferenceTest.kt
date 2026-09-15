package mg.univ.fahasalamana.data.remote

import kotlinx.serialization.decodeFromString
import mg.univ.fahasalamana.data.local.versEntites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Vérifie que les fichiers réellement embarqués dans `assets/` (B03) respectent le contrat
 * du §B5.1, et qu'ils s'aplatissent correctement en entités Room (§B5.2).
 *
 * Test **JVM et non instrumenté** : l'`AssetManager` d'Android n'est pas nécessaire pour
 * lire un fichier, seulement pour le lire depuis un APK. Les deux JSON sont ajoutés au
 * classpath des tests par le `sourceSets` de `app/build.gradle.kts` — ce sont donc bien les
 * fichiers embarqués qui sont testés ici, pas une copie qui aurait divergé.
 *
 * Ce que ce test ne couvre pas : l'insertion en base et le remplacement transactionnel,
 * qui demandent un appareil et relèvent de B20.
 */
class ContratReferenceTest {

    private val calendrier: CalendrierDto =
        jsonReference.decodeFromString(lireRessource(FICHIER_CALENDRIER))

    private val annuaire: AnnuaireDto =
        jsonReference.decodeFromString(lireRessource(FICHIER_ANNUAIRE))

    // ----------------------------------------------------------------------
    // En-tête commun aux deux fichiers (§B5.1)
    // ----------------------------------------------------------------------

    @Test
    fun calendrier_porteUnEnTeteConformeAuContrat() {
        assertEquals(1, calendrier.schemaVersion)
        assertTrue("version doit être un entier positif", calendrier.version >= 1)
        assertEquals(LocalDate.of(2026, 9, 14), calendrier.publieLe)
        assertTrue("la mention de source est affichée à l'écran", calendrier.source.isNotBlank())
    }

    @Test
    fun annuaire_porteUnEnTeteConformeAuContrat() {
        assertEquals(1, annuaire.schemaVersion)
        assertTrue("version doit être un entier positif", annuaire.version >= 1)
        assertEquals(LocalDate.of(2026, 9, 14), annuaire.publieLe)
        assertTrue("la mention de source est affichée à l'écran", annuaire.source.isNotBlank())
    }

    /** `publieLe` doit arriver en `LocalDate` et non en `String` (règle 2 de CLAUDE.md). */
    @Test
    fun publieLe_estDeserialiseEnLocalDate() {
        val relu: CalendrierDto = jsonReference.decodeFromString(
            """
            {"schemaVersion":1,"version":7,"publieLe":"2027-02-28","source":"s","vaccins":[]}
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2027, 2, 28), relu.publieLe)
    }

    /**
     * Compatibilité ascendante du §B5.1 : un champ ajouté par une future publication `v1/`
     * ne doit pas faire échouer la lecture sur les APK déjà installés.
     */
    @Test
    fun cleInconnue_estIgnoreeSansFaireEchouerLaLecture() {
        val relu: CalendrierDto = jsonReference.decodeFromString(
            """
            {
              "schemaVersion": 1, "version": 4, "publieLe": "2026-10-01", "source": "s",
              "commentaire": "champ ajouté par une version ultérieure",
              "vaccins": [
                { "id": "bcg", "nom": "BCG", "dose": "dose unique", "ordre": 1, "ageJours": 0,
                  "dependDe": null, "toleranceJours": 30, "description": "À la naissance",
                  "rappelSms": true }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, relu.vaccins.size)
        assertEquals("bcg", relu.vaccins.single().id)
    }

    // ----------------------------------------------------------------------
    // calendrier.json
    // ----------------------------------------------------------------------

    /** Chiffre de la Definition of Done de B04 et du §B5.1. */
    @Test
    fun calendrier_contientSeizeDoses() {
        assertEquals(16, calendrier.vaccins.size)
    }

    @Test
    fun calendrier_identifiantsUniques_etOrdresContigusAPartirDeUn() {
        val identifiants = calendrier.vaccins.map { it.id }
        assertEquals("identifiants en double", identifiants.size, identifiants.toSet().size)

        val ordres = calendrier.vaccins.map { it.ordre }.sorted()
        assertEquals((1..calendrier.vaccins.size).toList(), ordres)
    }

    /**
     * Une chaîne de dépendances cassée produirait des lignes sans date prévue
     * (`LigneEcheancier.prevuLe = null`) : le contenu publié ne doit pas en contenir.
     */
    @Test
    fun calendrier_chaqueDependanceDesigneUneDoseDuMemeFichier() {
        val identifiants = calendrier.vaccins.map { it.id }.toSet()

        calendrier.vaccins.mapNotNull { it.dependDe }.forEach { parent ->
            assertTrue("dépendance vers une dose absente du calendrier : $parent", parent in identifiants)
        }
    }

    @Test
    fun calendrier_premiereDose_estSansDependance() {
        val bcg = calendrier.vaccins.single { it.id == "bcg" }

        assertNull(bcg.dependDe)
        assertEquals(0, bcg.ageJours)
    }

    @Test
    fun calendrier_versEntites_conserveIdentifiantsOrdreEtDependances() {
        val entites = calendrier.versEntites()

        assertEquals(16, entites.size)
        assertEquals(calendrier.vaccins.map { it.id }, entites.map { it.id })
        assertEquals(calendrier.vaccins.map { it.ordre }, entites.map { it.ordre })
        assertEquals(calendrier.vaccins.map { it.dependDe }, entites.map { it.dependDe })
        assertEquals(calendrier.vaccins.map { it.toleranceJours }, entites.map { it.toleranceJours })
    }

    // ----------------------------------------------------------------------
    // csb.json
    // ----------------------------------------------------------------------

    @Test
    fun annuaire_contientLes23Regions() {
        assertEquals(23, annuaire.regions.size)
    }

    /** Bornes du §B5.1 : 2 à 6 districts par région, 3 à 8 centres par district. */
    @Test
    fun annuaire_respecteLesBornesDeGeneration() {
        annuaire.regions.forEach { region ->
            assertTrue(
                "${region.id} : ${region.districts.size} districts hors de 2..6",
                region.districts.size in 2..6,
            )
            region.districts.forEach { district ->
                assertTrue(
                    "${district.id} : ${district.centres.size} centres hors de 3..8",
                    district.centres.size in 3..8,
                )
            }
        }
    }

    /**
     * Chiffre de la Definition of Done de B04 (« ≈ 400 centres »).
     * À mettre à jour si l'annuaire est régénéré par B03.
     */
    @Test
    fun annuaire_livre_contient402CentresDans76Districts() {
        val entites = annuaire.versEntites()

        assertEquals(76, entites.districts.size)
        assertEquals(402, entites.centres.size)
    }

    @Test
    fun annuaire_versEntites_aplatitSansPerdreDeRattachement() {
        val entites = annuaire.versEntites()
        val identifiantsRegions = entites.regions.map { it.id }.toSet()
        val identifiantsDistricts = entites.districts.map { it.id }.toSet()

        assertEquals(annuaire.regions.size, entites.regions.size)
        entites.districts.forEach { district ->
            assertTrue(
                "district ${district.id} rattaché à une région absente : ${district.regionId}",
                district.regionId in identifiantsRegions,
            )
        }
        entites.centres.forEach { centre ->
            assertTrue(
                "centre ${centre.id} rattaché à un district absent : ${centre.districtId}",
                centre.districtId in identifiantsDistricts,
            )
        }
    }

    @Test
    fun annuaire_identifiantsUniquesAuxTroisNiveaux() {
        val entites = annuaire.versEntites()

        assertEquals(entites.regions.size, entites.regions.map { it.id }.toSet().size)
        assertEquals(entites.districts.size, entites.districts.map { it.id }.toSet().size)
        assertEquals(entites.centres.size, entites.centres.map { it.id }.toSet().size)
    }

    /**
     * Les champs affichés par l'écran de détail (B14) et par le bouton « Appeler » doivent
     * tous être présents : un centre sans téléphone afficherait un bouton inerte.
     */
    @Test
    fun annuaire_chaqueCentrePorteSesChampsAffiches() {
        val entites = annuaire.versEntites()

        entites.centres.forEach { centre ->
            assertTrue("nom vide : ${centre.id}", centre.nom.isNotBlank())
            assertTrue("type vide : ${centre.id}", centre.type.isNotBlank())
            assertTrue("téléphone vide : ${centre.id}", centre.telephone.isNotBlank())
            assertTrue("horaires vides : ${centre.id}", centre.horaires.isNotBlank())
            assertTrue("adresse vide : ${centre.id}", centre.adresse.isNotBlank())
        }
    }

    /** Le renommage `type` -> `typeCentre` ne doit pas perdre la valeur publiée. */
    @Test
    fun centre_typePublie_arriveDansLaColonneType() {
        val premier = annuaire.regions.first().districts.first().centres.first()
        val entite = annuaire.versEntites().centres.single { it.id == premier.id }

        assertTrue(premier.typeCentre.isNotBlank())
        assertEquals(premier.typeCentre, entite.type)
    }

    // ----------------------------------------------------------------------

    private fun lireRessource(nom: String): String {
        val flux = javaClass.getResourceAsStream("/$nom")
        assertNotNull(
            "Ressource /$nom absente du classpath de test : vérifier le bloc sourceSets " +
                "de app/build.gradle.kts, qui ajoute src/main/assets aux ressources de test",
            flux,
        )
        return flux!!.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val FICHIER_CALENDRIER = "calendrier.json"
        const val FICHIER_ANNUAIRE = "csb.json"
    }
}
