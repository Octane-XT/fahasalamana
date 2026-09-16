package mg.univ.fahasalamana.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B20, garantie n° 1 : **une suppression emporte exactement ce qu'elle doit emporter.**
 *
 * Deux cascades sont déclarées dans le schéma (§B5.2), et elles reposent sur le même
 * mécanisme SQLite (`ON DELETE CASCADE`, actif parce que Room ouvre la base avec
 * `PRAGMA foreign_keys = ON`) :
 *
 * - `enfants` → `vaccins_administres` : supprimer un enfant supprime ses doses reçues.
 *   Ce que l'écran `EditionEnfant` (B07) appelle est un simple `DELETE FROM enfants` ; si la
 *   cascade sautait, les doses de l'enfant supprimé resteraient en base, rattachées à
 *   personne — des données de santé orphelines que plus aucun écran ne montrerait et que
 *   l'export (B16) ne reprendrait pas.
 * - `regions` → `districts` → `centres` : deux niveaux, pour le contenu de référence.
 *
 * Le point vérifié n'est pas seulement « la cascade supprime », c'est aussi **« elle ne
 * supprime que ça »** : un second enfant, avec ses propres doses, doit sortir intact.
 */
@RunWith(AndroidJUnit4::class)
class CascadeSuppressionTest {

    private lateinit var base: AppDatabase
    private lateinit var enfants: EnfantDao
    private lateinit var administres: VaccinAdministreDao
    private lateinit var centres: CentreDao
    private lateinit var reference: ReferenceDao

    @Before
    fun ouvrirLaBase() {
        base = ouvrirBaseEnMemoire()
        enfants = base.enfantDao()
        administres = base.vaccinAdministreDao()
        centres = base.centreDao()
        reference = base.referenceDao()
    }

    @After
    fun fermerLaBase() {
        base.close()
    }

    @Test
    fun supprimerUnEnfant_emporteSesDoses_etSeulementLesSiennes() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        enfants.enregistrer(ENFANT_SOA)
        enfants.enregistrer(ENFANT_RANTO)
        administres.upsert(DOSE_BCG_SOA)
        administres.upsert(DOSE_PENTA_1_SOA)
        administres.upsert(DOSE_BCG_RANTO)
        administres.upsert(DOSE_PENTA_1_RANTO)
        assertEquals(
            "les quatre doses doivent être en base avant la suppression",
            4,
            base.compterLignes("vaccins_administres"),
        )

        enfants.supprimer(ENFANT_SOA.id)

        assertNull(
            "l'enfant supprimé n'est plus lisible",
            enfants.lireAvecVaccins(ENFANT_SOA.id),
        )
        assertEquals(
            "ses doses partent avec lui, sans que l'appelant ait à les supprimer une à une",
            0,
            administres.observerPourEnfant(ENFANT_SOA.id).first().size,
        )

        val ranto = requireNotNull(enfants.lireAvecVaccins(ENFANT_RANTO.id)) {
            "supprimer un enfant ne doit pas toucher à l'autre"
        }
        assertEquals("le second enfant garde ses deux doses", 2, ranto.administres.size)
        assertEquals(
            "la cascade n'emporte que les lignes de l'enfant supprimé",
            2,
            base.compterLignes("vaccins_administres"),
        )
        assertEquals(
            "supprimer une donnée personnelle ne touche pas au contenu de référence",
            CALENDRIER_V1.size,
            base.compterLignes("vaccins_reference"),
        )
    }

    @Test
    fun supprimerUneRegion_emporteSesDistricts_puisLeursCentres() = runBlocking {
        reference.remplacerAnnuaire(
            ANNUAIRE_V1_REGIONS,
            ANNUAIRE_V1_DISTRICTS,
            ANNUAIRE_V1_CENTRES,
        )
        assertEquals(2, base.compterLignes("regions"))
        assertEquals(3, base.compterLignes("districts"))
        assertEquals(5, base.compterLignes("centres"))

        supprimerLaRegion(REGION_BOENY.id)

        assertEquals(
            "la région supprimée a disparu",
            listOf(REGION_ANALAMANGA.id),
            centres.observerRegions().first().map { it.id },
        )
        assertEquals(
            "premier niveau : le district de cette région part avec elle",
            0,
            centres.observerDistricts(REGION_BOENY.id).first().size,
        )
        assertEquals(
            "second niveau : les centres de ce district partent aussi",
            0,
            centres.observerCentres(DISTRICT_MAHAJANGA_I.id).first().size,
        )
        assertEquals(
            "l'autre région garde ses deux districts",
            2,
            centres.observerDistricts(REGION_ANALAMANGA.id).first().size,
        )
        assertEquals("et ses trois centres", 3, base.compterLignes("centres"))
    }

    /**
     * Supprime une seule région.
     *
     * Aucune DAO ne sait le faire, et c'est normal : l'annuaire est remplacé en bloc
     * (`ReferenceDao.remplacerAnnuaire`), jamais ligne à ligne. Ajouter une fonction de
     * suppression à la production pour les besoins d'un test ajouterait du code que rien
     * n'appelle ; le test passe donc par du SQL, sur la connexion ouverte par Room — donc
     * avec les clés étrangères actives, ce qui est justement ce qu'on veut observer.
     *
     * L'identifiant est interpolé plutôt que lié : c'est une constante de test, pas une
     * saisie utilisateur.
     */
    private fun supprimerLaRegion(id: String) {
        base.openHelper.writableDatabase.execSQL("DELETE FROM regions WHERE id = '$id'")
    }
}
