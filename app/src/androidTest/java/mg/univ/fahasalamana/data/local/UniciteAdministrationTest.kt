package mg.univ.fahasalamana.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B20, garantie n° 2 : **les contraintes portées par la table `vaccins_administres`.**
 *
 * La règle R5 — une seule dose enregistrée par couple (enfant, vaccin) — est tenue par la base
 * et non par le code appelant : c'est l'index unique `(enfantId, vaccinId)` du §B5.2.
 *
 * Mais la contrainte seule ne dit pas ce qui se passe quand on la viole, et c'est là que se
 * joue le comportement attendu par l'utilisateur. `VaccinAdministreDao.upsert` est un
 * `@Insert(onConflict = REPLACE)` choisi en B02 : corriger une saisie doit **remplacer** la
 * ligne existante, pas échouer, et pas créer un doublon. Un test qui se contenterait
 * d'attendre une `SQLiteConstraintException` documenterait l'inverse de la décision prise.
 *
 * Le second test garde l'index lui-même : l'unicité porte sur le **couple**, pas sur
 * `enfantId` seul ni sur `vaccinId` seul. Écrite à l'envers, la contrainte ferait disparaître
 * une dose à chaque nouvelle saisie — et le premier test, lui, continuerait de passer.
 *
 * Le troisième vérifie le format de la colonne `date`, dont dépend le tri SQL de cette DAO.
 */
@RunWith(AndroidJUnit4::class)
class UniciteAdministrationTest {

    private lateinit var base: AppDatabase
    private lateinit var enfants: EnfantDao
    private lateinit var administres: VaccinAdministreDao
    private lateinit var reference: ReferenceDao

    @Before
    fun ouvrirLaBase() {
        base = ouvrirBaseEnMemoire()
        enfants = base.enfantDao()
        administres = base.vaccinAdministreDao()
        reference = base.referenceDao()
    }

    @After
    fun fermerLaBase() {
        base.close()
    }

    @Test
    fun uneSecondeSaisiePourLeMemeCouple_remplaceLaPremiere() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        enfants.enregistrer(ENFANT_SOA)

        administres.upsert(DOSE_PENTA_1_SOA)
        // Même enfant, même vaccin : le couple est déjà pris. La contrainte est violée
        // volontairement — et `INSERT OR REPLACE` doit la rattraper sans lever d'exception.
        administres.upsert(DOSE_PENTA_1_SOA_CORRIGEE)

        val doses = administres.observerPourEnfant(ENFANT_SOA.id).first()
        assertEquals("R5 : une seule dose par couple (enfant, vaccin), pas deux", 1, doses.size)
        assertEquals(
            "la ligne qui reste porte les valeurs de la seconde saisie : date, lieu et lot corrigés",
            DOSE_PENTA_1_SOA_CORRIGEE,
            doses.single(),
        )

        // Conséquence à connaître : `INSERT OR REPLACE` ne met pas à jour la ligne en conflit,
        // il la **supprime** puis en insère une nouvelle. L'identifiant technique qui survit
        // est donc celui de la seconde saisie ; celui de la première n'existe plus nulle part.
        // Rien ne doit donc mémoriser un identifiant d'administration au-delà d'un upsert.
        assertFalse(
            "l'identifiant de la première saisie a été remplacé, pas conservé",
            laDoseExiste(DOSE_PENTA_1_SOA.id),
        )
        assertEquals(
            "et la table ne contient bien qu'une ligne, pas deux",
            1,
            base.compterLignes("vaccins_administres"),
        )
    }

    @Test
    fun lUnicitePorteSurLeCouple_lesAutresCombinaisonsCoexistent() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        enfants.enregistrer(ENFANT_SOA)
        enfants.enregistrer(ENFANT_RANTO)

        administres.upsert(DOSE_BCG_SOA)
        administres.upsert(DOSE_PENTA_1_SOA) // même enfant, autre vaccin
        administres.upsert(DOSE_BCG_RANTO) // même vaccin, autre enfant

        assertEquals(
            "un enfant reçoit évidemment plusieurs vaccins",
            listOf(VACCIN_BCG.id, VACCIN_PENTA_1.id),
            administres.observerPourEnfant(ENFANT_SOA.id).first().map { it.vaccinId },
        )
        assertEquals(
            "et un même vaccin est reçu par plusieurs enfants",
            listOf(VACCIN_BCG.id),
            administres.observerPourEnfant(ENFANT_RANTO.id).first().map { it.vaccinId },
        )
        assertEquals(
            "aucune des trois lignes n'en a chassé une autre",
            3,
            base.compterLignes("vaccins_administres"),
        )
    }

    @Test
    fun lesDatesDesDosesSontEcritesEnTexteIso_etSeTrientChronologiquement() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        enfants.enregistrer(ENFANT_RANTO)

        // Saisies dans le désordre, et à cheval sur deux années.
        administres.upsert(DOSE_PENTA_2_RANTO) // 07/01/2026, dose reçue en retard
        administres.upsert(DOSE_BCG_RANTO) // 04/09/2025
        administres.upsert(DOSE_PENTA_1_RANTO) // 15/10/2025

        assertEquals(
            "la colonne `date` contient du texte ISO yyyy-MM-dd, jamais un epoch (§B5.2)",
            "2025-09-04",
            base.texteBrut("vaccins_administres", "date", DOSE_BCG_RANTO.id),
        )
        assertEquals(
            "2026-01-07",
            base.texteBrut("vaccins_administres", "date", DOSE_PENTA_2_RANTO.id),
        )

        // `observerPourEnfant` trie en SQL (`ORDER BY date ASC`). Ce tri n'est juste que parce
        // que l'ordre lexicographique du texte ISO est l'ordre chronologique : avec un format
        // `dd/MM/yyyy`, « 07/01/2026 » passerait avant « 15/10/2025 » et la fiche afficherait
        // les doses dans le désordre dès qu'un carnet traverse le 31 décembre.
        assertEquals(
            "les doses remontent dans l'ordre chronologique, changement d'année compris",
            listOf(VACCIN_BCG.id, VACCIN_PENTA_1.id, VACCIN_PENTA_2.id),
            administres.observerPourEnfant(ENFANT_RANTO.id).first().map { it.vaccinId },
        )
    }

    /** Une ligne porte-t-elle encore cet identifiant technique ? Aucune DAO ne lit par `id` : l'application n'en a pas l'usage. */
    private fun laDoseExiste(id: String): Boolean =
        base.texteBrut("vaccins_administres", "id", id) != null
}
