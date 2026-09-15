package mg.univ.fahasalamana.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mg.univ.fahasalamana.domain.Sexe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Definition of Done de B02 : insérer un enfant et une administration, relire la relation.
 *
 * Base **en mémoire** : chaque test repart d'une base vide et rien n'est écrit sur l'appareil.
 * Aucune `allowMainThreadQueries()` — les tests instrumentés ne tournent pas sur le fil
 * principal, `runBlocking` suffit et la DAO reste testée telle qu'elle est utilisée.
 *
 * Les tests DAO complets (cascade de suppression, unicité (enfant, vaccin), remplacement du
 * calendrier sans toucher aux administrations) sont la tâche B20.
 */
@RunWith(AndroidJUnit4::class)
class EnfantDaoTest {

    private lateinit var base: AppDatabase
    private lateinit var enfants: EnfantDao
    private lateinit var administres: VaccinAdministreDao
    private lateinit var reference: ReferenceDao

    @Before
    fun ouvrirLaBase() {
        val contexte = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexte, AppDatabase::class.java).build()
        enfants = base.enfantDao()
        administres = base.vaccinAdministreDao()
        reference = base.referenceDao()
    }

    @After
    fun fermerLaBase() {
        base.close()
    }

    @Test
    fun enfantEtAdministrationInseres_laRelationLesRenvoieEnsemble() = runBlocking {
        // Le calendrier de référence d'abord : une administration renvoie à un vaccin existant.
        reference.remplacerCalendrier(listOf(BCG))

        enfants.enregistrer(SOA)
        administres.upsert(BCG_DE_SOA)

        val lu = enfants.observer(SOA.id).first()

        assertNotNull("l'enfant inséré doit être relu", lu)
        requireNotNull(lu)
        assertEquals("Soa", lu.enfant.prenom)
        assertEquals(LocalDate.of(2026, 1, 15), lu.enfant.dateNaissance)
        assertEquals(Sexe.FILLE, lu.enfant.toDomain().sexe)
        assertEquals(1, lu.administres.size)
        assertEquals("bcg", lu.administres.first().vaccinId)
        assertEquals(LocalDate.of(2026, 1, 16), lu.administres.first().date)
        assertEquals("CSB2 Ankirihiry", lu.administres.first().lieu)
    }

    @Test
    fun listeDesEnfants_porteAussiLesAdministrations() = runBlocking {
        reference.remplacerCalendrier(listOf(BCG))
        enfants.enregistrer(SOA)
        administres.upsert(BCG_DE_SOA)

        val tous = enfants.observerTous().first()

        assertEquals(1, tous.size)
        assertEquals(1, tous.first().administres.size)
    }

    /**
     * Les dates métier sont stockées en texte ISO `yyyy-MM-dd` et jamais en epoch (§B5.2) :
     * c'est ce que l'export relu par un humain contiendra, et ce dont dépend l'ordre des
     * `ORDER BY date`. La lecture passe ici par une requête brute, seule façon de voir la
     * colonne avant conversion.
     */
    @Test
    fun laDateDeNaissanceEstStockeeEnTexteIso() = runBlocking {
        enfants.enregistrer(SOA)

        base.query("SELECT dateNaissance FROM enfants WHERE id = ?", arrayOf<Any?>(SOA.id)).use { curseur ->
            assertEquals(1, curseur.count)
            curseur.moveToFirst()
            assertEquals("2026-01-15", curseur.getString(0))
        }
    }

    private companion object {
        val BCG = VaccinReferenceEntity(
            id = "bcg",
            nom = "BCG",
            dose = "dose unique",
            ordre = 1,
            ageJours = 0,
            dependDe = null,
            toleranceJours = 30,
            description = "À la naissance",
        )

        val SOA = EnfantEntity(
            id = "enfant-de-test",
            prenom = "Soa",
            dateNaissance = LocalDate.of(2026, 1, 15),
            sexe = Sexe.FILLE.name,
            creeLe = 1_760_000_000_000L,
        )

        val BCG_DE_SOA = VaccinAdministreEntity(
            id = "administration-de-test",
            enfantId = SOA.id,
            vaccinId = BCG.id,
            date = LocalDate.of(2026, 1, 16),
            lieu = "CSB2 Ankirihiry",
            lot = "L-2026-042",
        )
    }
}
